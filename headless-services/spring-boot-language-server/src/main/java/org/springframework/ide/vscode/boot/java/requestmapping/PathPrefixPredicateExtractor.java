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
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;

/**
 * Parses the second argument of {@code PathMatchConfigurer.addPathPrefix(String, Predicate)}
 * from an AST {@link Expression} into a {@link PathPrefixPredicate} tree.
 *
 * <p>Handles the following {@code HandlerTypePredicate} factory methods:</p>
 * <ul>
 *   <li>{@code forAnnotation(Class...)} / {@code forMappedAnnotation(Class...)}</li>
 *   <li>{@code forBasePackage(String...)}</li>
 *   <li>{@code forAssignableType(Class...)}</li>
 * </ul>
 * <p>and the following combinator methods on any predicate:</p>
 * <ul>
 *   <li>{@code .and(Predicate)}</li>
 *   <li>{@code .or(Predicate)}</li>
 *   <li>{@code .negate()}</li>
 * </ul>
 * <p>Lambda expressions are mapped to {@link PathPrefixPredicate.AnyPredicate}.
 * Any unrecognised expression is mapped to {@link PathPrefixPredicate.UnknownPredicate}.</p>
 */
public class PathPrefixPredicateExtractor {

	private PathPrefixPredicateExtractor() {}

	/**
	 * Extracts a {@link PathPrefixPredicate} from {@code expression}.
	 * Never returns {@code null}; returns {@link PathPrefixPredicate.AnyPredicate} when
	 * {@code expression} is {@code null}.
	 */
	public static PathPrefixPredicate extract(Expression expression) {
		if (expression == null) {
			return new PathPrefixPredicate.AnyPredicate();
		}
		if (expression instanceof LambdaExpression) {
			return new PathPrefixPredicate.AnyPredicate();
		}
		if (expression instanceof MethodInvocation mi) {
			return extractFromMethodInvocation(mi);
		}
		return new PathPrefixPredicate.UnknownPredicate();
	}

	@SuppressWarnings("unchecked")
	private static PathPrefixPredicate extractFromMethodInvocation(MethodInvocation mi) {
		String methodName = mi.getName().getIdentifier();

		return switch (methodName) {
			case "and" -> {
				PathPrefixPredicate left = extract(mi.getExpression());
				List<Expression> args = mi.arguments();
				PathPrefixPredicate right = args.isEmpty()
						? new PathPrefixPredicate.UnknownPredicate()
						: extract(args.get(0));
				yield new PathPrefixPredicate.AndPredicate(left, right);
			}
			case "or" -> {
				PathPrefixPredicate left = extract(mi.getExpression());
				List<Expression> args = mi.arguments();
				PathPrefixPredicate right = args.isEmpty()
						? new PathPrefixPredicate.UnknownPredicate()
						: extract(args.get(0));
				yield new PathPrefixPredicate.OrPredicate(left, right);
			}
			case "negate" -> new PathPrefixPredicate.NegatePredicate(extract(mi.getExpression()));
			case "forAnnotation", "forMappedAnnotation" -> {
				if (!isHandlerTypePredicateMethod(mi)) {
					yield new PathPrefixPredicate.UnknownPredicate();
				}
				List<String> annotationTypes = new ArrayList<>();
				for (Expression arg : (List<Expression>) mi.arguments()) {
					String typeName = resolveTypeLiteralName(arg);
					if (typeName != null) {
						annotationTypes.add(typeName);
					}
				}
				yield new PathPrefixPredicate.AnnotationPredicate(annotationTypes);
			}
			case "forBasePackage" -> {
				if (!isHandlerTypePredicateMethod(mi)) {
					yield new PathPrefixPredicate.UnknownPredicate();
				}
				List<String> packages = new ArrayList<>();
				for (Expression arg : (List<Expression>) mi.arguments()) {
					String value = ASTUtils.getExpressionValueAsString(arg, dep -> {});
					if (value != null) {
						packages.add(value);
					}
				}
				yield new PathPrefixPredicate.BasePackagePredicate(packages);
			}
			case "forAssignableType" -> {
				if (!isHandlerTypePredicateMethod(mi)) {
					yield new PathPrefixPredicate.UnknownPredicate();
				}
				List<String> typeNames = new ArrayList<>();
				for (Expression arg : (List<Expression>) mi.arguments()) {
					String typeName = resolveTypeLiteralName(arg);
					if (typeName != null) {
						typeNames.add(typeName);
					}
				}
				yield new PathPrefixPredicate.AssignableTypePredicate(typeNames);
			}
			default -> new PathPrefixPredicate.UnknownPredicate();
		};
	}

	/**
	 * Returns {@code true} if the method invocation's declaring class is
	 * {@code HandlerTypePredicate} (supports both the Spring 7+ location in
	 * {@code org.springframework.web.method} and the Spring 5/6 location in
	 * {@code org.springframework.web.servlet.handler}).
	 */
	private static boolean isHandlerTypePredicateMethod(MethodInvocation mi) {
		IMethodBinding methodBinding = mi.resolveMethodBinding();
		if (methodBinding == null) return false;
		ITypeBinding declaringClass = methodBinding.getDeclaringClass();
		return declaringClass != null
				&& Annotations.HANDLER_TYPE_PREDICATE_TYPES.contains(declaringClass.getQualifiedName());
	}

	/**
	 * Resolves a {@code TypeLiteral} expression (e.g. {@code RestController.class})
	 * to its fully-qualified type name.
	 */
	private static String resolveTypeLiteralName(Expression expression) {
		if (expression instanceof TypeLiteral tl) {
			ITypeBinding binding = tl.getType().resolveBinding();
			if (binding != null) {
				return binding.getQualifiedName();
			}
		}
		return null;
	}
}
