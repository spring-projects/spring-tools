/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.stereotypes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.Location;
import org.jmolecules.stereotype.catalog.StereotypeDefinition.Assignment.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.beans.CachedBean;
import org.springframework.ide.vscode.boot.java.handlers.SymbolProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.Streams;

/**
 * @author Martin Lippert
 */
public class StereotypesIndexer implements SymbolProvider {
	
	private static final Logger log = LoggerFactory.getLogger(StereotypesIndexer.class);

	private final StereotypeCatalogRegistry stereotypeCatalogRegistry;
	
	public StereotypesIndexer(StereotypeCatalogRegistry stereotypeCatalogRegistry) {
		this.stereotypeCatalogRegistry = stereotypeCatalogRegistry;
	}
	
	@Override
	public void addSymbols(Annotation node, ITypeBinding typeBinding, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context, TextDocument doc) {
		ASTNode parent = node.getParent();
		
		if (parent instanceof AnnotationTypeDeclaration) {
			AnnotationTypeDeclaration annotationType = (AnnotationTypeDeclaration) parent;
			ITypeBinding annotationBinding = annotationType.resolveBinding();
			
			StereotypeDefinitionElement stereotypeDefinitionElement = new StereotypeDefinitionElement(annotationBinding.getQualifiedName(), Type.IS_ANNOTATED);
			context.getBeans().add(new CachedBean(context.getDocURI(), stereotypeDefinitionElement));
		}
	}
	
	@Override
	public void addSymbols(RecordDeclaration recordDeclaration, SpringIndexerJavaContext context, TextDocument doc) {
		try {
			createStereotypeElementForType(recordDeclaration, context, doc);
		}
		catch (BadLocationException e) {
			log.error("error identifying location of type declaration", e);
		}
	}
	
	@Override
	public void addSymbols(PackageDeclaration packageDeclaration, SpringIndexerJavaContext context, TextDocument doc) {
		try {
			createStereotypeElementForPackage(packageDeclaration, context, doc);
		}
		catch (BadLocationException e) {
			log.error("error identifying location of type declaration", e);
		}
	}
	
	private void createStereotypeElementForPackage(PackageDeclaration packageDeclaration, SpringIndexerJavaContext context, TextDocument doc) throws BadLocationException {
		if (!context.getDocURI().endsWith("package-info.java")) {
			return;
		}
		
		IPackageBinding packageBinding = packageDeclaration.resolveBinding();
		if (packageBinding == null) {
			return;
		}
		
		@SuppressWarnings("unchecked")
		List<Annotation> annotations = packageDeclaration.annotations();
		
		List<String> annotationTypes = annotations.stream()
			.map(annotation -> annotation.resolveAnnotationBinding())
			.filter(binding -> binding != null)
			.map(binding -> binding.getAnnotationType().getQualifiedName())
			.toList();
		
		StereotypePackageElement packageElement = new StereotypePackageElement(packageBinding.getName(), annotationTypes);
		context.getBeans().add(new CachedBean(context.getDocURI(), packageElement));
		
		Name astNodeForLocation = packageDeclaration.getName();
		Location location = new Location(doc.getUri(), doc.toRange(astNodeForLocation.getStartPosition(), astNodeForLocation.getLength()));
		StereotypeClassElement classElement = new StereotypeClassElement(packageBinding.getName() + ".package-info", location, Set.of(), annotationTypes);
		context.getBeans().add(new CachedBean(context.getDocURI(), classElement));
	}

	@Override
	public void addSymbols(TypeDeclaration typeDeclaration, SpringIndexerJavaContext context, TextDocument doc) {
		try {
			createStereotypeElementForType(typeDeclaration, context, doc);
		}
		catch (BadLocationException e) {
			log.error("error identifying location of type declaration", e);
		}
	}
	
	private void createStereotypeElementForType(AbstractTypeDeclaration typeDeclaration, SpringIndexerJavaContext context, TextDocument doc) throws BadLocationException {
		ITypeBinding typeBinding = typeDeclaration.resolveBinding();
		if (typeBinding == null) {
			return;
		}
		
		// identify stereotype definitions themselves
		if (typeDeclaration instanceof TypeDeclaration && ((TypeDeclaration) typeDeclaration).isInterface()) {
			Collection<Annotation> annotations = ASTUtils.getAnnotations(typeDeclaration);
			boolean isStereotypeAnnotated = annotations.stream()
				.anyMatch(annotation -> annotation.resolveTypeBinding().getQualifiedName().equals(Annotations.JMOLECULES_STEREOTYPE));
			
			if (isStereotypeAnnotated) {
				StereotypeDefinitionElement stereotypeDefinitionElement = new StereotypeDefinitionElement(typeBinding.getQualifiedName(), Type.IS_ANNOTATED);
				context.getBeans().add(new CachedBean(context.getDocURI(), stereotypeDefinitionElement));
			}
		}
		
		// capture type information element for later tree generation
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(typeDeclaration);
		
		String qualifiedName = typeBinding.getBinaryName();
		Set<String> supertypes = ASTUtils.findSupertypes(typeBinding);
		
		List<IAnnotationBinding> superTypeAnnotations = new ArrayList<>();
		
		Iterator<ITypeBinding> hierarchyTypesBreadthFirstIterator = ASTUtils.getHierarchyTypesBreadthFirstIterator(typeBinding);
		while (hierarchyTypesBreadthFirstIterator.hasNext()) {
			ITypeBinding superTypeBinding = hierarchyTypesBreadthFirstIterator.next();
			superTypeAnnotations.addAll(Arrays.asList(superTypeBinding.getAnnotations()));
		}

		Collection<Annotation> annotations = ASTUtils.getAnnotations(typeDeclaration);
		List<String> annotationTypes = getAnnotationTypes(annotationHierarchies, superTypeAnnotations, annotations);

		SimpleName astNodeForLocation = typeDeclaration.getName();
		Location location = new Location(doc.getUri(), doc.toRange(astNodeForLocation.getStartPosition(), astNodeForLocation.getLength()));
		
		StereotypeClassElement indexElement = new StereotypeClassElement(qualifiedName, location, supertypes, annotationTypes);
		
		indexMethods(indexElement, typeDeclaration, annotationHierarchies, doc);
		
		context.getBeans().add(new CachedBean(context.getDocURI(), indexElement));
	}

	private void indexMethods(StereotypeClassElement indexElement, AbstractTypeDeclaration typeDeclaration, AnnotationHierarchies annotationHierarchies, TextDocument doc) throws BadLocationException {
		MethodDeclaration[] methods = null;
		
		if (typeDeclaration instanceof TypeDeclaration) {
			methods = ((TypeDeclaration) typeDeclaration).getMethods();
		}
		else if (typeDeclaration instanceof RecordDeclaration) {
			methods = ((RecordDeclaration) typeDeclaration).getMethods();
		}
		
		if (methods == null) {
			return;
		}
		
		for (MethodDeclaration method : methods) {
			String methodName = method.getName().getFullyQualifiedName();

			Collection<Annotation> annotations = ASTUtils.getAnnotations(method);
			List<String> annotationTypes = getAnnotationTypes(annotationHierarchies, List.of(), annotations);
			
			if (annotationTypes.size() > 0) { // only index annotated methods to avoid creating all those useless index elements for each and every method
				SimpleName astNodeForLocation = method.getName();
				Location location = new Location(doc.getUri(), doc.toRange(astNodeForLocation.getStartPosition(), astNodeForLocation.getLength()));
				
				String methodSignature = ASTUtils.getMethodSignature(method, true);
				String methodLabel = ASTUtils.getMethodSignature(method, false);
	
				StereotypeMethodElement methodElement = new StereotypeMethodElement(methodName, methodLabel, methodSignature, location, annotationTypes);
				indexElement.addChild(methodElement);
			}
		}
	}

	private List<String> getAnnotationTypes(AnnotationHierarchies annotationHierarchies,
			List<IAnnotationBinding> superTypeAnnotations, Collection<Annotation> annotations) {
		Stream<IAnnotationBinding> annotationBindings = annotations.stream()
				.map(annotation -> annotation.resolveAnnotationBinding())
				.filter(binding -> binding != null)
				.flatMap(binding -> Stream.concat(Stream.of(binding), getMetaAnnotations(annotationHierarchies, binding).stream()))
				.filter(binding -> !binding.getAnnotationType().getQualifiedName().startsWith("java"));

		List<String> annotationTypes = Streams.concat(annotationBindings, superTypeAnnotations.stream())
				.distinct()
				.map(binding -> binding.getAnnotationType().getQualifiedName())
				.toList();
		
		return annotationTypes;
	}
	
	private Collection<IAnnotationBinding> getMetaAnnotations(AnnotationHierarchies annotationHierarchies, IAnnotationBinding annotationBinding) {
		List<IAnnotationBinding> result = new ArrayList<>();
		for (Iterator<IAnnotationBinding> itr = annotationHierarchies.iterator(annotationBinding); itr.hasNext();) {
			result.add(itr.next());
		}
		
		return result;
	}
	
	private boolean isStereotype(TypeDeclaration typeDeclaration, ITypeBinding binding) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(typeDeclaration);
		boolean isStereotype = annotationHierarchies.isAnnotatedWith(binding, Annotations.JMOLECULES_STEREOTYPE);
		
		return isStereotype;
	}
	
	
	
}
