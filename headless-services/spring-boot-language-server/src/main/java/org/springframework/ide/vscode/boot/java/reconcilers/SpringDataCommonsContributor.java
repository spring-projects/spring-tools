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

import static org.springframework.ide.vscode.boot.java.reconcilers.SpringDataPropertyReferenceContributor.getErasedFqn;
import static org.springframework.ide.vscode.boot.java.reconcilers.SpringDataPropertyReferenceContributor.hasTypedPropertyPathOverload;

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

/**
 * Contributor for <b>Spring Data Commons</b> string-based property references.
 * Detects {@code Sort.by("prop")} and {@code Sort.Order.asc/desc/by("prop")}.
 */
class SpringDataCommonsContributor implements SpringDataPropertyReferenceContributor {

	private static final Set<String> SORT_FQN_TYPES = Set.of(
			"org.springframework.data.domain.Sort"
	);

	private static final Set<String> SORT_ORDER_FQN_TYPES = Set.of(
			"org.springframework.data.domain.Sort.Order"
	);

	private final DomainTypeResolver domainTypeResolver = new DomainTypeResolver();

	@Override
	public boolean isApplicable(IJavaProject project) {
		Version version = SpringProjectUtil.getDependencyVersionByPrefix(project, "spring-data-commons");
		return version != null && version.compareTo(new Version(4, 1, 0, null)) >= 0;
	}

	@Override
	public List<String> getRelevantTypesFqn() {
		return List.of(
				"org.springframework.data.domain.Sort",
				"org.springframework.data.domain.Sort.Order"
		);
	}

	@Override
	public boolean isPropertyReferenceCall(MethodInvocation node, ITypeBinding declaringType) {
		String erasedFqn = getErasedFqn(declaringType);
		return isSortByCall(node, erasedFqn) || isSortOrderCall(node, erasedFqn);
	}

	@Override
	public List<List<StringLiteral>> extractStringLiteralGroups(MethodInvocation node) {
		IMethodBinding methodBinding = node.resolveMethodBinding();
		if (methodBinding == null) {
			return List.of();
		}

		@SuppressWarnings("unchecked")
		List<Expression> args = node.arguments();

		String declaringFqn = getErasedFqn(methodBinding.getDeclaringClass());

		if (isSortByCall(node, declaringFqn)) {
			List<StringLiteral> group = extractSortByLiterals(args, methodBinding);
			return group.isEmpty() ? List.of() : List.of(group);
		} else if (isSortOrderCall(node, declaringFqn)) {
			List<StringLiteral> group = extractSortOrderLiterals(args, methodBinding);
			return group.isEmpty() ? List.of() : List.of(group);
		}
		return List.of();
	}

	@Override
	public AbstractSpringDataDomainTypeResolver getDomainTypeResolver() {
		return domainTypeResolver;
	}

	@Override
	public Set<String> getFieldAnnotationFqns() {
		return Set.of();
	}

	private boolean isSortByCall(MethodInvocation node, String declaringFqn) {
		return "by".equals(node.getName().getIdentifier()) && SORT_FQN_TYPES.contains(declaringFqn);
	}

	private boolean isSortOrderCall(MethodInvocation node, String declaringFqn) {
		String methodName = node.getName().getIdentifier();
		return ("asc".equals(methodName) || "desc".equals(methodName) || "by".equals(methodName))
				&& SORT_ORDER_FQN_TYPES.contains(declaringFqn);
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
