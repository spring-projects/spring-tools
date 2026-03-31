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

/**
 * Contributor for <b>Spring Data Relational</b> (JDBC/R2DBC) string-based property references.
 * Detects {@code Criteria.where("prop")} and {@code Update.update/set("prop", value)}.
 */
class SpringDataRelationalContributor implements SpringDataPropertyReferenceContributor {

	private static final Set<String> CRITERIA_FQN_TYPES = Set.of(
			"org.springframework.data.relational.core.query.Criteria"
	);

	private static final Set<String> UPDATE_FQN_TYPES = Set.of(
			"org.springframework.data.relational.core.query.Update"
	);

	private final DomainTypeResolver domainTypeResolver = new DomainTypeResolver();

	@Override
	public boolean isApplicable(IJavaProject project) {
		Version version = SpringProjectUtil.getDependencyVersionByPrefix(project, "spring-data-relational");
		return version != null && version.compareTo(new Version(4, 1, 0, null)) >= 0;
	}

	@Override
	public List<String> getRelevantTypesFqn() {
		return List.of(
				"org.springframework.data.relational.core.query.Criteria",
				"org.springframework.data.relational.core.query.Update"
		);
	}

	@Override
	public boolean isPropertyReferenceCall(MethodInvocation node, ITypeBinding declaringType) {
		String erasedFqn = getErasedFqn(declaringType);
		return CRITERIA_FQN_TYPES.contains(erasedFqn) || UPDATE_FQN_TYPES.contains(erasedFqn);
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
		return Set.of("org.springframework.data.relational.core.mapping.Column");
	}

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

			result = tryRepositoryCall(methodBinding, declaringType);
			if (result != null) {
				return result;
			}

			result = tryFluentReceiverType(invocation);
			if (result != null) {
				return result;
			}

			if (ASTUtils.isAnyTypeInHierarchy(declaringType, TEMPLATE_FQN_TYPES)) {
				return findClassLiteralInArguments(invocation);
			}

			return null;
		}

	}

}
