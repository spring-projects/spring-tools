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
	protected List<List<StringLiteral>> extractStringLiteralGroups(MethodInvocation node) {
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
				return List.of(List.of(literal));
			}
		}
		return List.of();
	}

	@Override
	protected AbstractSpringDataDomainTypeResolver getDomainTypeResolver() {
		return domainTypeResolver;
	}

	@Override
	protected Set<String> getFieldAnnotationFqns() {
		return Set.of("org.springframework.data.relational.core.mapping.Column");
	}

	// =====================================================================
	// Domain type resolver — Relational-specific patterns
	// =====================================================================

	/**
	 * Domain type resolver for Spring Data Relational (JDBC + R2DBC).
	 * In addition to the common repository and fluent API patterns (R2DBC only),
	 * extracts the domain type from {@code Class<T>} arguments in template operations.
	 */
	private static class DomainTypeResolver extends AbstractSpringDataDomainTypeResolver {

		private static final Set<String> TEMPLATE_FQN_TYPES = Set.of(
				"org.springframework.data.jdbc.core.JdbcAggregateTemplate",
				"org.springframework.data.jdbc.core.JdbcAggregateOperations",
				"org.springframework.data.r2dbc.core.R2dbcEntityTemplate",
				"org.springframework.data.r2dbc.core.R2dbcEntityOperations"
		);

		private static final List<String> FLUENT_OPERATION_TYPE_PREFIXES = List.of(
				"org.springframework.data.r2dbc.core.ReactiveSelectOperation",
				"org.springframework.data.r2dbc.core.ReactiveInsertOperation",
				"org.springframework.data.r2dbc.core.ReactiveUpdateOperation",
				"org.springframework.data.r2dbc.core.ReactiveDeleteOperation"
		);

		@Override
		protected List<String> getFluentOperationTypePrefixes() {
			return FLUENT_OPERATION_TYPE_PREFIXES;
		}

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

			// Pattern 2: Fluent API — R2DBC only, guarded by operation type prefix check
			result = tryFluentReceiverType(invocation);
			if (result != null) {
				return result;
			}

			// Pattern 3: Non-fluent template call with Class<T> parameter
			// e.g. template.select(query, Customer.class) or template.findAll(Customer.class)
			if (ASTUtils.isAnyTypeInHierarchy(declaringType, TEMPLATE_FQN_TYPES)) {
				return findClassLiteralInArguments(invocation);
			}

			return null;
		}

	}

}
