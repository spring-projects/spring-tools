/*******************************************************************************
 * Copyright (c) 2024, 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.jpa.queries;

import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.embedded.lang.EmbeddedLangAstUtils;
import org.springframework.ide.vscode.boot.java.embedded.lang.EmbeddedLanguageSnippet;

public class JdtQueryVisitorUtils {
	
	private static final String QUERY = "Query";
	private static final String NAMED_QUERY = "NamedQuery";	
	private static final String NATIVE_QUERY = "NativeQuery";
	
	public record EmbeddedQueryExpression(EmbeddedLanguageSnippet query, boolean isNative) {};
	
	public static EmbeddedQueryExpression extractQueryExpression(AnnotationHierarchies annotationHierarchies, SingleMemberAnnotation a) {
		if (isNativeQueryAnnotation(annotationHierarchies, a) || isQueryJdbcAnnotation(annotationHierarchies, a)) {
			EmbeddedLanguageSnippet expression = EmbeddedLangAstUtils.extractEmbeddedExpression(a.getValue());
			return expression == null ? null : new EmbeddedQueryExpression(expression, true);
		} else if (isQueryAnnotation(annotationHierarchies, a)) {
			EmbeddedLanguageSnippet expression = EmbeddedLangAstUtils.extractEmbeddedExpression(a.getValue());
			return expression == null ? null : new EmbeddedQueryExpression(expression, false);
		}
		return null;
	}
	
	public static EmbeddedQueryExpression extractQueryExpression(AnnotationHierarchies annotationHierarchies, NormalAnnotation a) {
		Expression queryExpression = null;
		boolean isNative = false;
		if (isNativeQueryAnnotation(annotationHierarchies, a) || isQueryJdbcAnnotation(annotationHierarchies, a)) {
			for (Object value : a.values()) {
				if (value instanceof MemberValuePair) {
					MemberValuePair pair = (MemberValuePair) value;
					String name = pair.getName().getFullyQualifiedName();
					if (name != null) {
						switch (name) {
						case "value":
							queryExpression = pair.getValue();
							isNative = true;
							break;
						}
					}
				}
			}
		} else if (isQueryAnnotation(annotationHierarchies, a)) {
			for (Object value : a.values()) {
				if (value instanceof MemberValuePair) {
					MemberValuePair pair = (MemberValuePair) value;
					String name = pair.getName().getFullyQualifiedName();
					if (name != null) {
						switch (name) {
						case "value":
							queryExpression = pair.getValue();
							break;
						case "nativeQuery":
							Expression expression = pair.getValue();
							if (expression != null) {
								Object o = expression.resolveConstantExpressionValue();
								if (o instanceof Boolean b) {
									isNative = b.booleanValue();
								}
							}
							break;
						}
					}
				}
			}
		} else if (isNamedQueryAnnotation(annotationHierarchies, a)) {
			for (Object value : a.values()) {
				if (value instanceof MemberValuePair) {
					MemberValuePair pair = (MemberValuePair) value;
					String name = pair.getName().getFullyQualifiedName();
					if (name != null) {
						switch (name) {
						case "query":
							queryExpression = pair.getValue();
							break;
						}
					}
				}
			}
		}
		if (queryExpression != null) {
			EmbeddedLanguageSnippet e = EmbeddedLangAstUtils.extractEmbeddedExpression(queryExpression);
			if (e != null) {
				return new EmbeddedQueryExpression(e, isNative);
			}
		}
		return null;
	}
	
	public static EmbeddedQueryExpression extractQueryExpression(MethodInvocation m) {
		if ("createQuery".equals(m.getName().getIdentifier()) && m.arguments().size() <= 2 && m.arguments().get(0) instanceof Expression queryExpr) {
			IMethodBinding methodBinding = m.resolveMethodBinding();
			if ("jakarta.persistence.EntityManager".equals(methodBinding.getDeclaringClass().getQualifiedName())) {
				if (methodBinding.getParameterTypes().length <= 2 && "java.lang.String".equals(methodBinding.getParameterTypes()[0].getQualifiedName())) {
					EmbeddedLanguageSnippet expression = EmbeddedLangAstUtils.extractEmbeddedExpression(queryExpr);
					return expression == null ? null : new EmbeddedQueryExpression(expression, false);
				}
			}
		}
		return null;
	}
	
	static boolean isQueryAnnotation(AnnotationHierarchies annotationHierarchies, Annotation a) {
		if (Annotations.DATA_JPA_QUERY.equals(a.getTypeName().getFullyQualifiedName()) || QUERY.equals(a.getTypeName().getFullyQualifiedName())) {
			return annotationHierarchies.isAnnotatedWith(a.resolveAnnotationBinding(), Annotations.DATA_JPA_QUERY);
		}
		return false;
	}

	static boolean isNamedQueryAnnotation(AnnotationHierarchies annotationHierarchies, Annotation a) {
		if (NAMED_QUERY.equals(a.getTypeName().getFullyQualifiedName()) || Annotations.JPA_JAKARTA_NAMED_QUERY.equals(a.getTypeName().getFullyQualifiedName())
				|| Annotations.JPA_JAVAX_NAMED_QUERY.equals(a.getTypeName().getFullyQualifiedName())) {
			IAnnotationBinding type = a.resolveAnnotationBinding();
			if (type != null) {
				return annotationHierarchies.isAnnotatedWith(type, Annotations.JPA_JAKARTA_NAMED_QUERY)
						|| annotationHierarchies.isAnnotatedWith(type, Annotations.JPA_JAVAX_NAMED_QUERY);
			}
		}
		return false;
	}
	
	static boolean isQueryJdbcAnnotation(AnnotationHierarchies annotationHierarchies, Annotation a) {
		if (Annotations.DATA_JDBC_QUERY.equals(a.getTypeName().getFullyQualifiedName()) || QUERY.equals(a.getTypeName().getFullyQualifiedName())) {
			return annotationHierarchies.isAnnotatedWith(a.resolveAnnotationBinding(), Annotations.DATA_JDBC_QUERY);
		}
		return false;
	}
	
	static boolean isNativeQueryAnnotation(AnnotationHierarchies annotationHierarchies, Annotation a) {
		if (NATIVE_QUERY.equals(a.getTypeName().getFullyQualifiedName()) || Annotations.DATA_JPA_NATIVE_QUERY.equals(a.getTypeName().getFullyQualifiedName())) {
			IAnnotationBinding type = a.resolveAnnotationBinding();
			if (type != null) {
				return annotationHierarchies.isAnnotatedWith(type, Annotations.DATA_JPA_NATIVE_QUERY);
			}
		}
		return false;
	}

}
