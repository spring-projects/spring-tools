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
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.jspecify.annotations.Nullable;
import org.springframework.ide.vscode.boot.java.data.AbstractSpringDataDomainTypeResolver;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.quickfix.QuickfixRegistry;

/**
 * Reconciler for <b>Spring Data Cassandra</b> string-based property references.
 * <p>
 * Detects:
 * <ul>
 *   <li>{@code Criteria.where("prop")} — the first string argument</li>
 *   <li>{@code Update.set("prop", value)}, {@code Update.addTo("prop")}, and all other
 *       Cassandra {@code Update} methods that accept a string key — dynamically checked
 *       via {@code TypedPropertyPath} overload detection.</li>
 *   <li>{@code Columns.from("col1", "col2")} — column selection (varargs)</li>
 * </ul>
 */
public class SpringDataCassandraReconciler extends AbstractSpringDataPropertyReferenceReconciler {

	private static final Set<String> CRITERIA_FQN_TYPES = Set.of(
			"org.springframework.data.cassandra.core.query.Criteria"
	);

	private static final Set<String> UPDATE_FQN_TYPES = Set.of(
			"org.springframework.data.cassandra.core.query.Update"
	);

	private static final Set<String> COLUMNS_FQN_TYPES = Set.of(
			"org.springframework.data.cassandra.core.query.Columns"
	);

	private final DomainTypeResolver domainTypeResolver = new DomainTypeResolver();

	public SpringDataCassandraReconciler(QuickfixRegistry quickfixRegistry) {
		super(quickfixRegistry);
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		Version version = SpringProjectUtil.getDependencyVersionByPrefix(project, "spring-data-cassandra");
		return version != null && version.compareTo(new Version(5, 1, 0, null)) >= 0;
	}

	@Override
	protected List<String> getRelevantTypesFqn() {
		return List.of(
				"org.springframework.data.cassandra.core.query.Criteria",
				"org.springframework.data.cassandra.core.query.Update",
				"org.springframework.data.cassandra.core.query.Columns"
		);
	}

	@Override
	protected boolean isPropertyReferenceCall(MethodInvocation node, ITypeBinding declaringType) {
		String erasedFqn = getErasedFqn(declaringType);
		return CRITERIA_FQN_TYPES.contains(erasedFqn) || UPDATE_FQN_TYPES.contains(erasedFqn)
				|| COLUMNS_FQN_TYPES.contains(erasedFqn);
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

		String declaringFqn = getErasedFqn(methodBinding.getDeclaringClass());

		if (COLUMNS_FQN_TYPES.contains(declaringFqn)) {
			return extractAllArgLiterals(args, methodBinding);
		}

		if (args.get(0) instanceof StringLiteral literal) {
			if (hasTypedPropertyPathOverload(methodBinding, 0)) {
				return List.of(literal);
			}
		}
		return List.of();
	}

	private List<StringLiteral> extractAllArgLiterals(List<Expression> args, IMethodBinding methodBinding) {
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

	@Override
	protected AbstractSpringDataDomainTypeResolver getDomainTypeResolver() {
		return domainTypeResolver;
	}

	// =====================================================================
	// Domain type resolver — Cassandra-specific patterns
	// =====================================================================

	/**
	 * Domain type resolver for Spring Data Cassandra. In addition to the common
	 * repository and fluent API patterns, supports template operations with
	 * Class parameters and template update-with-entity calls.
	 */
	private static class DomainTypeResolver extends AbstractSpringDataDomainTypeResolver {

		private static final Set<String> TEMPLATE_FQN_TYPES = Set.of(
				"org.springframework.data.cassandra.core.CassandraTemplate",
				"org.springframework.data.cassandra.core.CassandraOperations",
				"org.springframework.data.cassandra.core.ReactiveCassandraTemplate",
				"org.springframework.data.cassandra.core.ReactiveCassandraOperations"
		);

		private static final List<String> FLUENT_OPERATION_TYPE_PREFIXES = List.of(
				"org.springframework.data.cassandra.core.ExecutableSelectOperation",
				"org.springframework.data.cassandra.core.ExecutableInsertOperation",
				"org.springframework.data.cassandra.core.ExecutableUpdateOperation",
				"org.springframework.data.cassandra.core.ExecutableDeleteOperation",
				"org.springframework.data.cassandra.core.ReactiveSelectOperation",
				"org.springframework.data.cassandra.core.ReactiveInsertOperation",
				"org.springframework.data.cassandra.core.ReactiveUpdateOperation",
				"org.springframework.data.cassandra.core.ReactiveDeleteOperation"
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

			// Pattern 2: Fluent API — guarded by operation type prefix check
			result = tryFluentReceiverType(invocation);
			if (result != null) {
				return result;
			}

			if (!ASTUtils.isAnyTypeInHierarchy(declaringType, TEMPLATE_FQN_TYPES)) {
				return null;
			}

			// Pattern 3: Non-fluent template call with Class<T> parameter
			// e.g. template.selectOne(query, Customer.class)
			result = findClassLiteralInArguments(invocation);
			if (result != null) {
				return result;
			}

			// Pattern 4: Non-fluent template update with entity argument
			// e.g. template.update(person, options) — domain type inferred from entity's type
			return tryUpdateWithEntity(invocation);
		}

		private @Nullable ITypeBinding tryUpdateWithEntity(MethodInvocation invocation) {
			if (!"update".equals(invocation.getName().getIdentifier())) {
				return null;
			}
			@SuppressWarnings("unchecked")
			List<Expression> args = invocation.arguments();
			if (!args.isEmpty()) {
				Expression firstArg = args.get(0);
				if (!(firstArg instanceof TypeLiteral)) {
					ITypeBinding argType = firstArg.resolveTypeBinding();
					if (argType != null && !argType.isPrimitive()
							&& !"java.lang.Object".equals(argType.getQualifiedName())) {
						return argType;
					}
				}
			}
			return null;
		}

	}

}
