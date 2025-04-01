/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.beans;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.handlers.SymbolProvider;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.CachedSymbol;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationAttributeValue;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class ConfigurationPropertiesSymbolProvider implements SymbolProvider {
	
	private static final Logger log = LoggerFactory.getLogger(ConfigurationPropertiesSymbolProvider.class);

	@Override
	public void addSymbols(Annotation node, ITypeBinding annotationType, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context, TextDocument doc) {
		try {
			if (node != null && node.getParent() != null) {
				if (node.getParent() instanceof AbstractTypeDeclaration abstractType) {
					createSymbolForType(abstractType, node, annotationType, metaAnnotations, context, doc);
				}
			}
		}
		catch (BadLocationException e) {
			log.error("", e);
		}
	}

	protected void createSymbolForType(AbstractTypeDeclaration type, Annotation node, ITypeBinding annotationType, Collection<ITypeBinding> metaAnnotations, SpringIndexerJavaContext context, TextDocument doc) throws BadLocationException {
		String annotationTypeName = annotationType.getName();
		
		Collection<String> metaAnnotationNames = metaAnnotations.stream()
				.map(ITypeBinding::getName)
				.collect(Collectors.toList());
		
		ITypeBinding typeBinding = type.resolveBinding();
		
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(type);
		boolean isComponentAnnotated = annotationHierarchies.isAnnotatedWith(typeBinding, Annotations.COMPONENT);
		
		if (!isComponentAnnotated) {
			String beanName = BeanUtils.getBeanNameFromType(type.getName().getFullyQualifiedName());

			Location location = new Location(doc.getUri(), doc.toRange(node.getStartPosition(), node.getLength()));
		
			WorkspaceSymbol symbol = new WorkspaceSymbol(
					ComponentSymbolProvider.beanLabel("+", annotationTypeName, metaAnnotationNames, beanName, typeBinding.getName()), SymbolKind.Interface,
					Either.forLeft(location));
		
			boolean isConfiguration = false; // otherwise, the ComponentSymbolProvider takes care of the bean definiton for this type

			InjectionPoint[] injectionPoints = ASTUtils.findInjectionPoints(type, doc);

			Set<String> supertypes = ASTUtils.findSupertypes(typeBinding);

			Collection<Annotation> annotationsOnType = ASTUtils.getAnnotations(type);

			AnnotationMetadata[] annotations = Stream.concat(
					Arrays.stream(ASTUtils.getAnnotationsMetadata(annotationsOnType, doc))
					,
					metaAnnotations.stream()
					.map(an -> new AnnotationMetadata(an.getQualifiedName(), true, null, null)))
					.toArray(AnnotationMetadata[]::new);
		
			Bean beanDefinition = new Bean(beanName, typeBinding.getQualifiedName(), location, injectionPoints, supertypes, annotations, isConfiguration, symbol.getName());
			
			indexConfigurationProperties(beanDefinition, type, context, doc);

			context.getGeneratedSymbols().add(new CachedSymbol(context.getDocURI(), context.getLastModified(), symbol));
			context.getBeans().add(new CachedBean(context.getDocURI(), beanDefinition));
		}
	}

	public static void indexConfigurationProperties(Bean beanDefinition, AbstractTypeDeclaration abstractType, SpringIndexerJavaContext context, TextDocument doc) {
		Optional<String> prefixValue = Arrays.stream(beanDefinition.getAnnotations())
			.filter(annotation -> Annotations.CONFIGURATION_PROPERTIES.equals(annotation.getAnnotationType()))
			.flatMap(annotation -> Arrays.stream(getPrefix(annotation)))
			.map(attribute -> attribute.getName())
			.findFirst();
		
		String prefix = "";
		if (prefixValue.isPresent()) {
			prefix = prefixValue.get() + ".";
		}
		
		if (abstractType instanceof TypeDeclaration type) {
			indexConfigurationPropertiesForType(beanDefinition, type, context, doc, prefix);
		}
		else if (abstractType instanceof RecordDeclaration record) {
			indexConfigurationPropertiesForRecord(beanDefinition, record, context, doc, prefix);
		}
	}

	private static AnnotationAttributeValue[] getPrefix(AnnotationMetadata annotation) {
		if (annotation.getAttributes().containsKey("prefix")) {
			return annotation.getAttributes().get("prefix");
		}
		else if (annotation.getAttributes().containsKey("value")) {
			return annotation.getAttributes().get("value");
		}
		else {
			return new AnnotationAttributeValue[0];
		}
	}
	
	public static void indexConfigurationPropertiesForType(Bean beanDefinition, TypeDeclaration type, SpringIndexerJavaContext context, TextDocument doc, String prefix) {
		
		FieldDeclaration[] fields = type.getFields();
		if (fields != null) {
			for (FieldDeclaration field : fields) {
				try {
					Type fieldType = field.getType();
					if (fieldType != null) {
						
						@SuppressWarnings("unchecked")
						List<VariableDeclarationFragment> fragments = field.fragments();

						for (VariableDeclarationFragment fragment : fragments) {
							SimpleName name = fragment.getName();

							if (name != null) {

								DocumentRegion nodeRegion = ASTUtils.nodeRegion(doc, field);
								Range range = doc.toRange(nodeRegion);
								ConfigPropertyIndexElement configPropElement = new ConfigPropertyIndexElement(prefix + name.getFullyQualifiedName(), fieldType.resolveBinding().getQualifiedName(), range);
								
								beanDefinition.addChild(configPropElement);
							}
						}
					}
				} catch (BadLocationException e) {
					log.error("error identifying config property field", e);
				}
			}
		}
		
	}
	
	public static void indexConfigurationPropertiesForRecord(Bean beanDefinition, RecordDeclaration record, SpringIndexerJavaContext context, TextDocument doc, String prefix) {
		
		@SuppressWarnings("unchecked")
		List<SingleVariableDeclaration> fields = record.recordComponents();

		if (fields != null) {
			for (SingleVariableDeclaration field : fields) {
				try {
					Type fieldType = field.getType();
					if (fieldType != null) {
						
						SimpleName name = field.getName();
						if (name != null) {

							DocumentRegion nodeRegion = ASTUtils.nodeRegion(doc, field);
							Range range = doc.toRange(nodeRegion);
							ConfigPropertyIndexElement configPropElement = new ConfigPropertyIndexElement(prefix + name.getFullyQualifiedName(), fieldType.resolveBinding().getQualifiedName(), range);
								
							beanDefinition.addChild(configPropElement);
						}
					}
				} catch (BadLocationException e) {
					log.error("error identifying config property field", e);
				}
			}
		}
		
	}
}
