/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
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
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TextBlock;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;

public class JdtQueryVisitorUtils {
	
	private static final String QUERY = "Query";
	private static final String NAMED_QUERY = "NamedQuery";	

	
	public record EmbeddedExpression(Expression expression, String text, int offset) {};
	
	public record EmbeddedQueryExpression(EmbeddedExpression query, boolean isNative) {};
	
	public static EmbeddedQueryExpression extractQueryExpression(SingleMemberAnnotation a) {
		if (isQueryAnnotation(a)) {
			EmbeddedExpression expression = extractEmbeddedExpression(a.getValue());
			return expression == null ? null : new EmbeddedQueryExpression(expression, false);
		}
		return null;
	}
	
	public static EmbeddedQueryExpression extractQueryExpression(NormalAnnotation a) {
		Expression queryExpression = null;
		boolean isNative = false;
		if (isQueryAnnotation(a)) {
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
		} else if (isNamedQueryAnnotation(a)) {
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
			EmbeddedExpression e = extractEmbeddedExpression(queryExpression);
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
					EmbeddedExpression expression = extractEmbeddedExpression(queryExpr);
					return expression == null ? null : new EmbeddedQueryExpression(expression, false);
				}
			}
		}
		return null;
	}
	
	public static EmbeddedExpression extractEmbeddedExpression(Expression valueExp) {
		String text = null;
		int offset = 0;
		if (valueExp instanceof StringLiteral sl) {
			text = sl.getEscapedValue();
			text = text.substring(1, text.length() - 1);
			offset = sl.getStartPosition() + 1; // +1 to skip over opening "
		} else if (valueExp instanceof TextBlock tb) {
			text = tb.getEscapedValue();
			text = text.substring(3, text.length() - 3);
			offset = tb.getStartPosition() + 3; // +3 to skip over opening """ 
		}
		return text == null ? null : new EmbeddedExpression(valueExp, text, offset);
	}

	
	static boolean isQueryAnnotation(Annotation a) {
		if (Annotations.DATA_QUERY.equals(a.getTypeName().getFullyQualifiedName()) || QUERY.equals(a.getTypeName().getFullyQualifiedName())) {
			ITypeBinding type = a.resolveTypeBinding();
			if (type != null) {
				return AnnotationHierarchies.hasTransitiveSuperAnnotationType(type, Annotations.DATA_QUERY);
			}
		}
		return false;
	}

	static boolean isNamedQueryAnnotation(Annotation a) {
		if (NAMED_QUERY.equals(a.getTypeName().getFullyQualifiedName()) || Annotations.JPA_JAKARTA_NAMED_QUERY.equals(a.getTypeName().getFullyQualifiedName())
				|| Annotations.JPA_JAVAX_NAMED_QUERY.equals(a.getTypeName().getFullyQualifiedName())) {
			ITypeBinding type = a.resolveTypeBinding();
			if (type != null) {
				return AnnotationHierarchies.hasTransitiveSuperAnnotationType(type, Annotations.JPA_JAKARTA_NAMED_QUERY)
						|| AnnotationHierarchies.hasTransitiveSuperAnnotationType(type, Annotations.JPA_JAVAX_NAMED_QUERY);
			}
		}
		return false;
	}

}
