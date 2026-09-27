/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
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
import java.util.Set;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * @author Martin Lippert
 */
public class ConfigurationPropertiesIndexer {
	
	private static final Logger log = LoggerFactory.getLogger(ConfigurationPropertiesIndexer.class);

	protected static Bean createBeanDefinition(AbstractTypeDeclaration type, SpringIndexerJavaContext context, TextDocument doc) {
		try {
			AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(type);
	
			ITypeBinding typeBinding = type.resolveBinding();
			
			String beanName = BeanUtils.getBeanNameFromType(type.getName().getFullyQualifiedName());
	
			SimpleName nameNode = type.getName();
			Location location = new Location(doc.getUri(), doc.toRange(nameNode.getStartPosition(), nameNode.getLength()));
	
			boolean isConfiguration = false; // otherwise, the ComponentSymbolProvider takes care of the bean definiton for this type
	
			InjectionPoint[] injectionPoints = ASTUtils.findInjectionPoints(type, doc);
	
			Set<String> supertypes = ASTUtils.findSupertypes(typeBinding);
	
			Collection<Annotation> annotationsOnType = ASTUtils.getAnnotations(type);
			List<AnnotationMetadata> annotationMetadata = ASTUtils.extractAnnotationMetadata(annotationsOnType, doc, annotationHierarchies);
			AnnotationMetadata[] annotationMetadataArrays = annotationMetadata.toArray(AnnotationMetadata[]::new);
	
			String name = BeanUtils.COMPONENT_LABEL_STRATEGY.createLabel(beanName, annotationMetadata, typeBinding.getName());
	
			return new Bean(beanName, typeBinding.getQualifiedName(), location, injectionPoints, supertypes, annotationMetadataArrays, isConfiguration, name);
		}
		catch (BadLocationException e) {
			log.error("error identifying config property field", e);
			throw new RuntimeException(e);
		}
	}

	public static void indexConfigurationProperties(Bean beanDefinition, AbstractTypeDeclaration abstractType, SpringIndexerJavaContext context, TextDocument doc) {
		if (beanDefinition == null) {
			beanDefinition = createBeanDefinition(abstractType, context, doc);
			context.getGeneratedIndexElements().add(new CachedIndexElement(context.getDocURI(), beanDefinition));
		}

		String prefix = resolvePrefix(attributeValue(beanDefinition, "prefix"), attributeValue(beanDefinition, "value"));

		if (abstractType instanceof TypeDeclaration type) {
			indexConfigurationPropertiesForType(beanDefinition, type, context, doc, prefix);
		}
		else if (abstractType instanceof RecordDeclaration record) {
			indexConfigurationPropertiesForRecord(beanDefinition, record, context, doc, prefix);
		}
	}

	private static String attributeValue(Bean beanDefinition, String attributeName) {
		return Arrays.stream(beanDefinition.getAnnotations())
			.filter(annotation -> Annotations.CONFIGURATION_PROPERTIES.equals(annotation.getAnnotationType()))
			.map(annotation -> annotation.getAttributes().get(attributeName))
			.filter(values -> values != null && values.length > 0)
			.map(values -> values[0].getName())
			.findFirst()
			.orElse(null);
	}

	/**
	 * Resolves a {@code @ConfigurationProperties} prefix from its attribute values - {@code prefix},
	 * falling back to {@code value} (aliases of one another on the annotation itself), with the
	 * trailing dot a property name is joined to.
	 *
	 * <p>Shared between this AST-based indexer and the JAR-based one
	 * ({@code JarConfigurationPropertiesScanner}, reading the same two attribute values off Jandex
	 * bytecode instead), so the one piece of real logic in "which properties does a
	 * {@code @ConfigurationProperties} class have" cannot drift between the two - see
	 * {@code docs/structure-view-dependencies.md}'s discussion of that risk.
	 */
	static String resolvePrefix(String prefixAttributeValue, String valueAttributeValue) {
		String prefix = prefixAttributeValue != null ? prefixAttributeValue : valueAttributeValue;
		return prefix == null ? "" : prefix + ".";
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
								context.markAsOwnIndexElement(field);
								ConfigPropertyIndexElement configPropElement = new ConfigPropertyIndexElement(prefix + name.getFullyQualifiedName(), fieldType.resolveBinding().getQualifiedName(), range,
										ASTUtils.contentHash(doc, field));
								
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
							context.markAsOwnIndexElement(field);
							ConfigPropertyIndexElement configPropElement = new ConfigPropertyIndexElement(prefix + name.getFullyQualifiedName(), fieldType.resolveBinding().getQualifiedName(), range,
									ASTUtils.contentHash(doc, field));
								
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
