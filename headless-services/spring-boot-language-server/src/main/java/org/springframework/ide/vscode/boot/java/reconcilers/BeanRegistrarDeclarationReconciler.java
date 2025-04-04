/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.openrewrite.java.RemoveAnnotation;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot4JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.BeanRegistrarElement;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.rewrite.config.RecipeScope;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;
import org.springframework.ide.vscode.commons.rewrite.java.ImportBeanRegistrarInConfigRecipe;
import org.springframework.ide.vscode.commons.util.UriUtil;

import com.google.common.collect.Streams;

public class BeanRegistrarDeclarationReconciler implements JdtAstReconciler {

	private final QuickfixRegistry registry;
	private final SpringMetamodelIndex springIndex;

	public BeanRegistrarDeclarationReconciler(QuickfixRegistry registry, SpringMetamodelIndex springIndex) {
		this.registry = registry;
		this.springIndex = springIndex;
	}
	
	@Override
	public boolean isApplicable(IJavaProject project) {
		Version version = SpringProjectUtil.getDependencyVersion(project, "spring-context");
		return version != null && version.compareTo(new Version(7, 0, 0, null)) >= 0;
	}

	@Override
	public ProblemType getProblemType() {
		return Boot4JavaProblemType.REGISTRAR_BEAN_DECLARATION;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docURI, CompilationUnit cu, ReconcilingContext context) {
		return new ASTVisitor() {

			@Override
			public boolean visit(TypeDeclaration node) {
				ITypeBinding type = node.resolveBinding();
				
				if (type == null) {
					return true;
				}
				
				// this is not a bean registrar, but maybe a config class that has changed and is now importing a bean registrar
				// so we need to mark the bean registrar for reconciling
				if (ASTUtils.findInTypeHierarchy(type, Set.of(Annotations.BEAN_REGISTRAR_INTERFACE)) == null) {
					identifyPossibleRegistrarsForReconciling(context);
					return true;
				}
				
				if (!context.isIndexComplete()) {
					throw new RequiredCompleteIndexException();
				}
				
				checkComponentAnnotations(context, project, docURI, cu, node, type);
				checkRegistrationViaImport(context, project, docURI, node, type);
					
				return true;
			}
			
		};
	}
	
	private void checkRegistrationViaImport(ReconcilingContext context, IJavaProject project, URI docURI, TypeDeclaration node, ITypeBinding type) {
		List<Bean> configBeans = new ArrayList<>();
		Path p = Path.of(docURI);
		List<Path> sourceFolders = IClasspathUtil.getSourceFolders(project.getClasspath()).map(f -> f.toPath()).filter(f -> p.startsWith(f)).collect(Collectors.toList());

		for (Bean b : springIndex.getBeansOfProject(project.getElementName())) {
			if (b.isConfiguration() && b.getLocation() != null) {
				Path configBeanPath = Path.of(URI.create(b.getLocation().getUri()));
				if (sourceFolders.stream().anyMatch(configBeanPath::startsWith)) {
					configBeans.add(b);
				}
			}
		}

		List<String> importingBeanRegistrarConfigs = getImportedBeanRegistrarConfigs(configBeans, type);
		if (configBeans.isEmpty() || importingBeanRegistrarConfigs.size() == 0) {

			ReconcileProblemImpl problem = new ReconcileProblemImpl(Boot4JavaProblemType.REGISTRAR_BEAN_DECLARATION, "No @Import found for bean registrar", node.getName().getStartPosition(), node.getName().getLength());
			List<FixDescriptor> fixes = configBeans.stream()
					.filter(b -> b.getLocation() != null && b.getLocation().getUri() != null)
					.map(b -> new FixDescriptor(ImportBeanRegistrarInConfigRecipe.class.getName(), List.of(b.getLocation().getUri()), "Add %s to `@Import` in %s".formatted(type.getName(), b.getName()))
							.withParameters(Map.of(
									"configBeanFqn", b.getType(),
									"beanRegFqn", type.getQualifiedName()
									))
							.withRecipeScope(RecipeScope.FILE)
							).toList();
			ReconcileUtils.setRewriteFixes(registry, problem, fixes);
			context.getProblemCollector().accept(problem);
			
			// record dependencies on types where we found import annotations for this bean registrar
			// mark this file
			
		}
		else {
			// record dependencies on types where we found import annotations for this bean registrar
			for (String typeOfConfigClassWithImport : importingBeanRegistrarConfigs) {
				context.addDependency(typeOfConfigClassWithImport);
			}

		}
	}
	
	private void checkComponentAnnotations(ReconcilingContext context, IJavaProject project, URI docURI, CompilationUnit cu, TypeDeclaration node, ITypeBinding type) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(node);
		for (Object o : node.modifiers()) {
			if (o instanceof Annotation a) {
				ITypeBinding ab = a.resolveTypeBinding();
				if (ab != null && annotationHierarchies.isAnnotatedWith(ab, Annotations.COMPONENT)) {
					ReconcileProblemImpl problem = new ReconcileProblemImpl(
							Boot4JavaProblemType.REGISTRAR_BEAN_INVALID_ANNOTATION,
							Boot4JavaProblemType.REGISTRAR_BEAN_INVALID_ANNOTATION.getLabel(),
							a.getTypeName().getStartPosition(), a.getTypeName().getLength());
					ReconcileUtils.setRewriteFixes(registry, problem, List.of(
							new FixDescriptor(RemoveAnnotation.class.getName(), List.of(docURI.toASCIIString()), "Remove `@%s`".formatted(ab.getName()))
							.withParameters(Map.of("annotationPattern", "@" + ab.getQualifiedName()))
							.withRecipeScope(RecipeScope.NODE)
							.withRangeScope(ReconcileUtils.createOpenRewriteRange(cu, node, null))
					));
					context.getProblemCollector().accept(problem);
				}				
			}
		}
	}
	
	private List<String> getImportedBeanRegistrarConfigs(List<Bean> configBeans, ITypeBinding beanRegType) {
		return configBeans.stream()
				.filter(configBean -> isImportingBeanRegistrar(configBean, beanRegType))
				.map(configBean -> configBean.getType())
				.toList();
	}
	
	private boolean isImportingBeanRegistrar(Bean configBean, ITypeBinding beanRegType) {
		if (configBean.getAnnotations() == null) {
			return false;
		}
		else {
			return Arrays.stream(configBean.getAnnotations())
					.filter(annotation -> Annotations.IMPORT.equals(annotation.getAnnotationType())) // look into @Import annotations only
					.flatMap(annotation -> annotation.getAttributes().get("value") == null ? Stream.empty() : Arrays.stream(annotation.getAttributes().get("value"))) // look into the attribute values of "value" attribute
					.anyMatch(annotationValue -> annotationValue.getName().equals(beanRegType.getQualifiedName()));
		}
	}
	
	/**
	 * This is basically the reverse check: it tries to identify possible bean registrars that need to be
	 * reconciled because the type this reconciler is currently looking at refers to it or has referred to it.
	 */
	protected void identifyPossibleRegistrarsForReconciling(ReconcilingContext context) {
		List<SpringIndexElement> createdIndexElements = context.getCreatedIndexElements();
		List<SpringIndexElement> previuosIndexElements = springIndex.getDocument(context.getDocURI()) != null ? 
				springIndex.getDocument(context.getDocURI()).getChildren() : Collections.emptyList();
		
		Set<String> importedTypesDelta = getImportAnnotationTypesDelta(createdIndexElements, previuosIndexElements);
		
		List<BeanRegistrarElement> registrarElements = springIndex.getNodesOfType(BeanRegistrarElement.class);
		
		registrarElements.stream()
				.filter(registrar -> importedTypesDelta.contains(registrar.getType()))
				.map(registrar -> registrar.getLocation().getUri())
				.map(docURI -> UriUtil.toFileString(docURI))
				.forEach(file -> context.markForAffetcedFilesIndexing(file));
	}

	public Set<String> getImportAnnotationTypesDelta(List<SpringIndexElement> updatedElements, List<SpringIndexElement> previousElements) {
		Set<String> updatedImportedTypes = getImportAnnotationTypes(updatedElements);
		Set<String> previousImportedTypes = getImportAnnotationTypes(previousElements);
		
		return Streams.concat(updatedImportedTypes.stream(), previousImportedTypes.stream()
				.filter(element -> 
					(updatedImportedTypes.contains(element) && !previousImportedTypes.contains(element))
					|| (!updatedImportedTypes.contains(element) && previousImportedTypes.contains(element))))
				.collect(Collectors.toSet());
	}
	
	public Set<String> getImportAnnotationTypes(List<SpringIndexElement> elements) {
		return elements.stream()
				.filter(element -> element instanceof Bean)
				.map(element -> (Bean) element)
				.flatMap(bean -> Arrays.stream(bean.getAnnotations()))
				.filter(annotation -> Annotations.IMPORT.equals(annotation.getAnnotationType())) // look into @Import annotations only
				.flatMap(annotation -> annotation.getAttributes().get("value") == null ? Stream.empty() : Arrays.stream(annotation.getAttributes().get("value"))) // look into the attribute values of "value" attribute
				.map(attributeValue -> attributeValue.getName())
				.distinct()
				.collect(Collectors.toSet());
	}

}
