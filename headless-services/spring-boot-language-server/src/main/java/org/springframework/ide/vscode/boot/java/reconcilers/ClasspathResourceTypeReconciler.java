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
package org.springframework.ide.vscode.boot.java.reconcilers;

import static org.springframework.ide.vscode.commons.java.SpringProjectUtil.springBootVersionGreaterOrEqual;

import java.net.URI;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;

/**
 * Validates that @Value annotations with "classpath:" or "classpath*:" prefixes
 * are used with compatible types that can receive Spring Resource objects.
 * 
 * Valid types for classpath resources include:
 * - org.springframework.core.io.Resource (and Resource[])
 * - java.io.InputStream (and InputStream[])
 * - java.io.File (and File[])
 * - java.net.URL (and URL[])
 * - java.nio.file.Path (and Path[])
 * 
 * Note: Array types of any valid resource type are also accepted.
 * 
 * @author Martin Lippert
 */
public class ClasspathResourceTypeReconciler implements JdtAstReconciler {
	
	// Valid types for classpath resource injection
	private static final Set<String> VALID_RESOURCE_TYPES = Set.of(
		"org.springframework.core.io.Resource",
		"org.springframework.core.io.Resource[]",
		"java.io.InputStream",
		"java.io.File",
		"java.net.URL",
		"java.nio.file.Path"
	);
	
	@Override
	public boolean isApplicable(IJavaProject project) {
		// Apply to all Spring Boot 2.0+ projects
		return springBootVersionGreaterOrEqual(2, 0, 0).test(project);
	}

	@Override
	public ProblemType getProblemType() {
		return Boot2JavaProblemType.VALUE_CLASSPATH_RESOURCE_TYPE;
	}

	@Override
	public ASTVisitor createVisitor(IJavaProject project, URI docUri, CompilationUnit cu, ReconcilingContext context) {
		AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

		return new ASTVisitor() {

			@Override
			public boolean visit(FieldDeclaration fieldDecl) {
				// Check annotations on the field
				for (Annotation annotation : ASTUtils.getAnnotations(fieldDecl)) {
					checkValueAnnotation(annotation, annotationHierarchies, fieldDecl.getType().resolveBinding(), context);
				}
				return super.visit(fieldDecl);
			}
			
			@Override
			public boolean visit(SingleVariableDeclaration param) {
				ITypeBinding paramType = param.getType().resolveBinding();
				for (Annotation annotation : ASTUtils.getAnnotations(param)) {
					checkValueAnnotation(annotation, annotationHierarchies, paramType, context);
				}
				return super.visit(param);
			}

			private void checkValueAnnotation(Annotation annotation, AnnotationHierarchies hierarchies, 
					ITypeBinding typeBinding, ReconcilingContext context) {
				
				// Check if it's a @Value annotation
				if (!hierarchies.isAnnotatedWith(annotation.resolveTypeBinding(), Annotations.VALUE)) {
					return;
				}
				
				// Extract the value string from the annotation
				String valueString = extractValueString(annotation);
				if (valueString == null || valueString.isEmpty()) {
					return;
				}
				
				// Check if the value starts with "classpath:" or "classpath*:"
				if (!isClasspathResource(valueString)) {
					return;
				}
				
				// Validate the type binding
				if (typeBinding == null) {
					return;
				}
				
				if (!isValidResourceType(typeBinding)) {
					String typeName = typeBinding.getQualifiedName();
					String message = String.format(
						"Type '%s' is not compatible with classpath resource injection. " +
						"Use org.springframework.core.io.Resource, java.io.InputStream, java.io.File, " +
						"java.net.URL, or java.nio.file.Path",
						typeName
					);
					
					ReconcileProblemImpl problem = new ReconcileProblemImpl(
						getProblemType(),
						message,
						annotation.getStartPosition(),
						annotation.getLength()
					);
					context.getProblemCollector().accept(problem);
				}
			}

			private String extractValueString(Annotation annotation) {
				// getAttribute handles both @Value("...") and @Value(value="...") cases
				return ASTUtils.getAttribute(annotation, "value")
					.map(expr -> ASTUtils.getExpressionValueAsString(expr, v -> {}))
					.orElse(null);
			}

			private boolean isClasspathResource(String value) {
				// Remove ${} placeholder wrappers to check the actual value
				String trimmed = value.trim();
				if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
					trimmed = trimmed.substring(2, trimmed.length() - 1).trim();
				}
				
				return trimmed.startsWith("classpath:") || trimmed.startsWith("classpath*:");
			}

			private boolean isValidResourceType(ITypeBinding typeBinding) {
				String qualifiedName = typeBinding.getQualifiedName();
				
				// Direct match with valid types
				if (VALID_RESOURCE_TYPES.contains(qualifiedName)) {
					return true;
				}
				
				// Check for array types - arrays of any valid resource type are also valid
				if (typeBinding.isArray()) {
					ITypeBinding elementType = typeBinding.getElementType();
					if (elementType != null) {
						String elementTypeName = elementType.getQualifiedName();
						// Check if the element type is one of the valid resource types
						if (VALID_RESOURCE_TYPES.contains(elementTypeName)) {
							return true;
						}
					}
				}
				
				return false;
			}
		};
	}
}

