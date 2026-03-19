/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.jspecify.annotations.Nullable;
import org.springframework.ide.vscode.boot.java.data.AbstractSpringDataDomainTypeResolver;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;

/**
 * Reconciler for <b>Spring Data Commons</b> string-based property references.
 * <p>
 * Detects:
 * <ul>
 *   <li>{@code Sort.by("prop1", "prop2")} — all string arguments</li>
 *   <li>{@code Sort.Order.asc("prop")}, {@code Sort.Order.desc("prop")},
 *       {@code Sort.Order.by("prop")} — the first string argument</li>
 * </ul>
 * <p>
 * Only flags string literals for which a {@code TypedPropertyPath} overload exists
 * on the declaring class.
 */
public class SpringDataCommonsReconciler extends AbstractSpringDataPropertyReferenceReconciler {

	private static final Set<String> SORT_FQN_TYPES = Set.of(
			"org.springframework.data.domain.Sort"
	);

	private static final Set<String> SORT_ORDER_FQN_TYPES = Set.of(
			"org.springframework.data.domain.Sort.Order"
	);

	private final DomainTypeResolver domainTypeResolver = new DomainTypeResolver();

	public SpringDataCommonsReconciler(QuickfixRegistry quickfixRegistry) {
		super(quickfixRegistry);
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		Version version = SpringProjectUtil.getDependencyVersionByPrefix(project, "spring-data-commons");
		return version != null && version.compareTo(new Version(4, 1, 0, null)) >= 0;
	}

	@Override
	protected List<String> getRelevantTypesFqn() {
		return List.of(
				"org.springframework.data.domain.Sort",
				"org.springframework.data.domain.Sort.Order"
		);
	}

	@Override
	protected boolean isPropertyReferenceCall(MethodInvocation node, String erasedFqn) {
		return isSortByCall(node, erasedFqn) || isSortOrderCall(node, erasedFqn);
	}

	@Override
	protected List<StringLiteral> extractStringLiterals(MethodInvocation node) {
		IMethodBinding methodBinding = node.resolveMethodBinding();
		if (methodBinding == null) {
			return List.of();
		}

		@SuppressWarnings("unchecked")
		List<Expression> args = node.arguments();

		String declaringFqn = getErasedFqn(methodBinding.getDeclaringClass());

		if (isSortByCall(node, declaringFqn)) {
			return extractSortByLiterals(args, methodBinding);
		} else if (isSortOrderCall(node, declaringFqn)) {
			return extractSortOrderLiterals(args, methodBinding);
		}
		return List.of();
	}

	@Override
	protected AbstractSpringDataDomainTypeResolver getDomainTypeResolver() {
		return domainTypeResolver;
	}

	// =====================================================================
	// Sort.by detection
	// =====================================================================

	private boolean isSortByCall(MethodInvocation node, String declaringFqn) {
		return "by".equals(node.getName().getIdentifier()) && SORT_FQN_TYPES.contains(declaringFqn);
	}

	private List<StringLiteral> extractSortByLiterals(List<Expression> args, IMethodBinding methodBinding) {
		List<StringLiteral> result = new ArrayList<>();
		for (int i = 0; i < args.size(); i++) {
			if (args.get(i) instanceof StringLiteral literal) {
				if (hasTypedPropertyPathOverload(methodBinding, i)) {
					result.add(literal);
				}
			}
		}
		return result;
	}

	// =====================================================================
	// Sort.Order.asc/desc/by detection
	// =====================================================================

	private boolean isSortOrderCall(MethodInvocation node, String declaringFqn) {
		String methodName = node.getName().getIdentifier();
		return ("asc".equals(methodName) || "desc".equals(methodName) || "by".equals(methodName))
				&& SORT_ORDER_FQN_TYPES.contains(declaringFqn);
	}

	private List<StringLiteral> extractSortOrderLiterals(List<Expression> args, IMethodBinding methodBinding) {
		for (int i = 0; i < args.size(); i++) {
			if (args.get(i) instanceof StringLiteral literal) {
				if (hasTypedPropertyPathOverload(methodBinding, i)) {
					return List.of(literal);
				}
			}
		}
		return List.of();
	}

	// =====================================================================
	// Domain type resolver — Commons only uses the repository pattern
	// =====================================================================

	/**
	 * Domain type resolver for Spring Data Commons. Only supports the repository
	 * method call pattern since Commons types ({@code Sort}, {@code Sort.Order})
	 * are used across all modules and the domain type comes from the repository
	 * call that passes the Sort as a parameter.
	 */
	private static class DomainTypeResolver extends AbstractSpringDataDomainTypeResolver {

		@Override
		protected @Nullable ITypeBinding extractDomainTypeFromInvocation(MethodInvocation invocation) {
			IMethodBinding methodBinding = invocation.resolveMethodBinding();
			if (methodBinding == null) {
				return null;
			}

			ITypeBinding declaringType = methodBinding.getDeclaringClass();
			if (declaringType == null) {
				return null;
			}

			return tryRepositoryCall(methodBinding, declaringType);
		}

	}

}
