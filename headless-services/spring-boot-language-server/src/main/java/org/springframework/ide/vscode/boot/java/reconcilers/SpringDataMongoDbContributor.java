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
 * Contributor for <b>Spring Data MongoDB</b> string-based property references.
 * Detects {@code Criteria.where}, {@code Update.*}, and {@code Field.include/exclude}.
 */
class SpringDataMongoDbContributor implements SpringDataPropertyReferenceContributor {

	private static final Set<String> CRITERIA_FQN_TYPES = Set.of(
			"org.springframework.data.mongodb.core.query.Criteria"
	);

	private static final Set<String> UPDATE_FQN_TYPES = Set.of(
			"org.springframework.data.mongodb.core.query.Update"
	);

	private static final Set<String> FIELD_FQN_TYPES = Set.of(
			"org.springframework.data.mongodb.core.query.Field"
	);

	private final DomainTypeResolver domainTypeResolver = new DomainTypeResolver();

	@Override
	public boolean isApplicable(IJavaProject project) {
		Version version = SpringProjectUtil.getDependencyVersionByPrefix(project, "spring-data-mongodb");
		return version != null && version.compareTo(new Version(5, 1, 0, null)) >= 0;
	}

	@Override
	public List<String> getRelevantTypesFqn() {
		return List.of(
				"org.springframework.data.mongodb.core.query.Criteria",
				"org.springframework.data.mongodb.core.query.Update",
				"org.springframework.data.mongodb.core.query.Field"
		);
	}

	@Override
	public boolean isPropertyReferenceCall(MethodInvocation node, ITypeBinding declaringType) {
		String erasedFqn = getErasedFqn(declaringType);
		return CRITERIA_FQN_TYPES.contains(erasedFqn) || UPDATE_FQN_TYPES.contains(erasedFqn)
				|| FIELD_FQN_TYPES.contains(erasedFqn);
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

		if (CRITERIA_FQN_TYPES.contains(declaringFqn)) {
			List<StringLiteral> group = extractCriteriaLiterals(args, methodBinding);
			return group.isEmpty() ? List.of() : List.of(group);
		} else if (UPDATE_FQN_TYPES.contains(declaringFqn)) {
			List<StringLiteral> group = extractFirstArgLiteral(args, methodBinding);
			return group.isEmpty() ? List.of() : List.of(group);
		} else if (FIELD_FQN_TYPES.contains(declaringFqn)) {
			List<StringLiteral> group = extractAllArgLiterals(args, methodBinding);
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
		return Set.of("org.springframework.data.mongodb.core.mapping.Field");
	}

	private List<StringLiteral> extractCriteriaLiterals(List<Expression> args, IMethodBinding methodBinding) {
		if (args.size() == 1 && args.get(0) instanceof StringLiteral literal) {
			if (hasTypedPropertyPathOverload(methodBinding, 0)) {
				return List.of(literal);
			}
		}
		return List.of();
	}

	private List<StringLiteral> extractFirstArgLiteral(List<Expression> args, IMethodBinding methodBinding) {
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

	private static class DomainTypeResolver extends AbstractSpringDataDomainTypeResolver {

		private static final Set<String> TEMPLATE_FQN_TYPES = Set.of(
				"org.springframework.data.mongodb.core.MongoTemplate",
				"org.springframework.data.mongodb.core.MongoOperations",
				"org.springframework.data.mongodb.core.ReactiveMongoTemplate",
				"org.springframework.data.mongodb.core.ReactiveMongoOperations",
				"org.springframework.data.mongodb.core.FluentMongoOperations"
		);

		private static final Set<String> AGGREGATION_FQN_TYPES = Set.of(
				"org.springframework.data.mongodb.core.aggregation.Aggregation"
		);

		private static final List<String> FLUENT_OPERATION_TYPE_PREFIXES = List.of(
				"org.springframework.data.mongodb.core.ExecutableFindOperation",
				"org.springframework.data.mongodb.core.ExecutableInsertOperation",
				"org.springframework.data.mongodb.core.ExecutableUpdateOperation",
				"org.springframework.data.mongodb.core.ExecutableRemoveOperation",
				"org.springframework.data.mongodb.core.ExecutableAggregationOperation",
				"org.springframework.data.mongodb.core.ExecutableMapReduceOperation",
				"org.springframework.data.mongodb.core.ReactiveFluentMongoOperations",
				"org.springframework.data.mongodb.core.ReactiveFindOperation",
				"org.springframework.data.mongodb.core.ReactiveInsertOperation",
				"org.springframework.data.mongodb.core.ReactiveUpdateOperation",
				"org.springframework.data.mongodb.core.ReactiveRemoveOperation",
				"org.springframework.data.mongodb.core.ReactiveAggregationOperation",
				"org.springframework.data.mongodb.core.ReactiveMapReduceOperation"
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

			return tryAggregation(invocation, declaringType);
		}

		private @Nullable ITypeBinding tryAggregation(MethodInvocation invocation, ITypeBinding declaringType) {
			if ("newAggregation".equals(invocation.getName().getIdentifier())
					&& ASTUtils.isAnyTypeInHierarchy(declaringType, AGGREGATION_FQN_TYPES)) {
				@SuppressWarnings("unchecked")
				List<Expression> args = invocation.arguments();
				if (!args.isEmpty() && args.get(0) instanceof TypeLiteral typeLiteral) {
					return typeLiteral.getType().resolveBinding();
				}
			}
			return null;
		}

	}

}
