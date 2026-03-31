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
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.jspecify.annotations.Nullable;
import org.springframework.ide.vscode.boot.java.data.AbstractSpringDataDomainTypeResolver;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;

/**
 * Contributor for <b>Spring Data Cassandra</b> string-based property references.
 * Detects {@code Criteria.where}, {@code Update.*}, and {@code Columns.from}.
 */
class SpringDataCassandraContributor implements SpringDataPropertyReferenceContributor {

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

	@Override
	public boolean isApplicable(IJavaProject project) {
		Version version = SpringProjectUtil.getDependencyVersionByPrefix(project, "spring-data-cassandra");
		return version != null && version.compareTo(new Version(5, 1, 0, null)) >= 0;
	}

	@Override
	public List<String> getRelevantTypesFqn() {
		return List.of(
				"org.springframework.data.cassandra.core.query.Criteria",
				"org.springframework.data.cassandra.core.query.Update",
				"org.springframework.data.cassandra.core.query.Columns"
		);
	}

	@Override
	public boolean isPropertyReferenceCall(MethodInvocation node, ITypeBinding declaringType) {
		String erasedFqn = getErasedFqn(declaringType);
		return CRITERIA_FQN_TYPES.contains(erasedFqn) || UPDATE_FQN_TYPES.contains(erasedFqn)
				|| COLUMNS_FQN_TYPES.contains(erasedFqn);
	}

	@Override
	public List<List<StringLiteral>> extractStringLiteralGroups(MethodInvocation node) {
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
			List<StringLiteral> group = extractAllArgLiterals(args, methodBinding);
			return group.isEmpty() ? List.of() : List.of(group);
		}

		if (args.get(0) instanceof StringLiteral literal) {
			if (hasTypedPropertyPathOverload(methodBinding, 0)) {
				return List.of(List.of(literal));
			}
		}
		return List.of();
	}

	@Override
	public AbstractSpringDataDomainTypeResolver getDomainTypeResolver() {
		return domainTypeResolver;
	}

	@Override
	public Set<String> getFieldAnnotationFqns() {
		return Set.of("org.springframework.data.cassandra.core.mapping.Column");
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

			result = tryRepositoryCall(methodBinding, declaringType);
			if (result != null) {
				return result;
			}

			result = tryFluentReceiverType(invocation);
			if (result != null) {
				return result;
			}

			if (!ASTUtils.isAnyTypeInHierarchy(declaringType, TEMPLATE_FQN_TYPES)) {
				return null;
			}

			result = findClassLiteralInArguments(invocation);
			if (result != null) {
				return result;
			}

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
