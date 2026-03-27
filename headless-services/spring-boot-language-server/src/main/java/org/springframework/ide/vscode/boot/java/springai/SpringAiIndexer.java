/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.springai;

import java.util.Collection;
import java.util.Optional;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.lsp4j.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.beans.CachedIndexElement;
import org.springframework.ide.vscode.boot.java.springai.SpringAiAnnotationIndexElement.AnnotationType;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaContext;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

/**
 * Static utility class that indexes Spring AI method-level annotations
 * ({@code @Tool}, {@code @McpTool}, {@code @McpPrompt}, {@code @McpComplete},
 * {@code @McpElicitation}, {@code @McpSampling}).
 * <p>
 * When a surrounding {@link SpringIndexElement} parent (i.e. a Bean) is
 * provided the created index elements are attached as children. Otherwise they
 * are registered as standalone top-level elements on the document.
 *
 * @author Martin Lippert
 */
public class SpringAiIndexer {

	private static final Logger log = LoggerFactory.getLogger(SpringAiIndexer.class);

	public static void indexSpringAiMethods(SpringIndexElement parent, TypeDeclaration type,
			SpringIndexerJavaContext context, TextDocument doc) {

		MethodDeclaration[] methods = type.getMethods();
		if (methods == null) {
			return;
		}

		ITypeBinding typeBinding = type.resolveBinding();
		String containerBeanType = typeBinding != null ? typeBinding.getQualifiedName() : null;

		for (MethodDeclaration method : methods) {
			Collection<Annotation> annotations = ASTUtils.getAnnotations(method);

			for (Annotation annotation : annotations) {
				ITypeBinding annotationTypeBinding = annotation.resolveTypeBinding();
				if (annotationTypeBinding == null) {
					continue;
				}

				String fqn = annotationTypeBinding.getQualifiedName();

				try {
					if (Annotations.SPRING_AI_TOOL.equals(fqn)) {
						indexSpringAiAnnotation(parent, annotation, method, context, doc, containerBeanType, AnnotationType.TOOL);
					}
					else if (Annotations.SPRING_AI_MCP_TOOL.equals(fqn)) {
						indexSpringAiAnnotation(parent, annotation, method, context, doc, containerBeanType, AnnotationType.MCP_TOOL);
					}
					else if (Annotations.SPRING_AI_MCP_PROMPT.equals(fqn)) {
						indexSpringAiAnnotation(parent, annotation, method, context, doc, containerBeanType, AnnotationType.MCP_PROMPT);
					}
					else if (Annotations.SPRING_AI_MCP_COMPLETE.equals(fqn)) {
						indexSpringAiAnnotation(parent, annotation, method, context, doc, containerBeanType, AnnotationType.MCP_COMPLETE);
					}
					else if (Annotations.SPRING_AI_MCP_ELICITATION.equals(fqn)) {
						indexSpringAiAnnotation(parent, annotation, method, context, doc, containerBeanType, AnnotationType.MCP_ELICITATION);
					}
					else if (Annotations.SPRING_AI_MCP_SAMPLING.equals(fqn)) {
						indexSpringAiAnnotation(parent, annotation, method, context, doc, containerBeanType, AnnotationType.MCP_SAMPLING);
					}
				}
				catch (BadLocationException e) {
					log.error("error indexing Spring AI annotation in: " + doc.getUri(), e);
				}
			}
		}
	}

	private static void indexSpringAiAnnotation(SpringIndexElement parent, Annotation annotation,
			MethodDeclaration method, SpringIndexerJavaContext context, TextDocument doc, String containerBeanType,
			AnnotationType annotationType) throws BadLocationException {

		String name = getAnnotationStringAttribute(annotation, "name");
		if (name == null || name.isEmpty()) {
			name = method.getName().getIdentifier();
		}

		String description = getAnnotationStringAttribute(annotation, "description");
		if (description == null) {
			description = "";
		}

		String methodSignature = ASTUtils.getMethodSignature(method, true);

		SimpleName methodNameNode = method.getName();
		Location location = new Location(doc.getUri(),
				doc.toRange(methodNameNode.getStartPosition(), methodNameNode.getLength()));

		Collection<Annotation> annotationsOnMethod = ASTUtils.getAnnotations(method);
		AnnotationMetadata[] annotationsMetadata = ASTUtils.getAnnotationsMetadata(annotationsOnMethod, doc);

		SpringAiAnnotationIndexElement element = new SpringAiAnnotationIndexElement(annotationType, name, description,
				methodSignature, location, containerBeanType, annotationsMetadata);

		addElement(parent, element, context);
	}

	private static void addElement(SpringIndexElement parent, SpringIndexElement element,
			SpringIndexerJavaContext context) {
		if (parent != null) {
			parent.addChild(element);
		}
		else {
			context.getGeneratedIndexElements().add(new CachedIndexElement(context.getDocURI(), element));
		}
	}

	private static String getAnnotationStringAttribute(Annotation annotation, String attributeName) {
		Optional<Expression> attribute = ASTUtils.getAttribute(annotation, attributeName);
		if (attribute.isPresent()) {
			return ASTUtils.getExpressionValueAsString(attribute.get(), dep -> {});
		}
		return null;
	}

}
