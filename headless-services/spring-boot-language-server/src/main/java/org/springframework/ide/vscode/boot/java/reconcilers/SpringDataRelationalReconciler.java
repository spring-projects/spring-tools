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

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.jspecify.annotations.Nullable;
import org.springframework.ide.vscode.boot.java.data.AbstractSpringDataDomainTypeResolver;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;

/**
 * Reconciler for <b>Spring Data Relational</b> (JDBC/R2DBC) string-based property references.
 * <p>
 * Detects:
 * <ul>
 *   <li>{@code Criteria.where("prop")} — the first string argument</li>
 *   <li>{@code Update.update("prop", value)}, {@code Update.set("prop", value)} — the first string argument</li>
 * </ul>
 * <p>
 * Only flags string literals for which a {@code TypedPropertyPath} overload exists
 * on the declaring class, determined dynamically at reconcile time.
 */
public class SpringDataRelationalReconciler extends AbstractSpringDataPropertyReferenceReconciler {

	private static final Set<String> CRITERIA_FQN_TYPES = Set.of(
			"org.springframework.data.relational.core.query.Criteria"
	);

	private static final Set<String> UPDATE_FQN_TYPES = Set.of(
			"org.springframework.data.relational.core.query.Update"
	);

	private final DomainTypeResolver domainTypeResolver = new DomainTypeResolver();

	public SpringDataRelationalReconciler(QuickfixRegistry quickfixRegistry) {
		super(quickfixRegistry);
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		Version version = SpringProjectUtil.getDependencyVersionByPrefix(project, "spring-data-relational");
		return version != null && version.compareTo(new Version(4, 1, 0, null)) >= 0;
	}

	@Override
	protected List<String> getRelevantTypesFqn() {
		return List.of(
				"org.springframework.data.relational.core.query.Criteria",
				"org.springframework.data.relational.core.query.Update"
		);
	}

	@Override
	protected boolean isPropertyReferenceCall(MethodInvocation node, ITypeBinding declaringType) {
		String erasedFqn = getErasedFqn(declaringType);
		return CRITERIA_FQN_TYPES.contains(erasedFqn) || UPDATE_FQN_TYPES.contains(erasedFqn);
	}

	@Override
	protected List<StringLiteral> extractStringLiterals(MethodInvocation node) {
		IMethodBinding methodBinding = node.resolveMethodBinding();
		if (methodBinding == null) {
			return List.of();
		}

		@SuppressWarnings("unchecked")
		List<Expression> args = node.arguments();
		if (args.isEmpty()) {
			return List.of();
		}

		if (args.get(0) instanceof StringLiteral literal) {
			if (hasTypedPropertyPathOverload(methodBinding, 0)) {
				return List.of(literal);
			}
		}
		return List.of();
	}

	@Override
	protected AbstractSpringDataDomainTypeResolver getDomainTypeResolver() {
		return domainTypeResolver;
	}

	// =====================================================================
	// Domain type resolver — Relational-specific patterns
	// =====================================================================

	/**
	 * Domain type resolver for Spring Data Relational (JDBC + R2DBC).
	 * In addition to the common repository pattern, extracts the domain type
	 * from {@code Class<T>} arguments in JDBC/R2DBC template operations.
	 */
	private static class DomainTypeResolver extends AbstractSpringDataDomainTypeResolver {

		private static final Set<String> TEMPLATE_FQN_TYPES = Set.of(
				"org.springframework.data.jdbc.core.JdbcAggregateTemplate",
				"org.springframework.data.jdbc.core.JdbcAggregateOperations",
				"org.springframework.data.r2dbc.core.R2dbcEntityTemplate",
				"org.springframework.data.r2dbc.core.R2dbcEntityOperations"
		);

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

			ITypeBinding result;

			// Pattern 1: Repository method call
			result = tryRepositoryCall(methodBinding, declaringType);
			if (result != null) {
				return result;
			}

			// Pattern 2: Template operation with Class<T> parameter
			if (ASTUtils.isAnyTypeInHierarchy(declaringType, TEMPLATE_FQN_TYPES)) {
				return findClassLiteralInArguments(invocation);
			}

			return null;
		}

		private @Nullable ITypeBinding findClassLiteralInArguments(MethodInvocation invocation) {
			@SuppressWarnings("unchecked")
			List<Expression> args = invocation.arguments();
			for (Expression arg : args) {
				if (arg instanceof TypeLiteral typeLiteral) {
					ITypeBinding binding = typeLiteral.getType().resolveBinding();
					if (binding != null && !"java.lang.Object".equals(binding.getQualifiedName())) {
						return binding;
					}
				}
			}
			return null;
		}

	}

}
