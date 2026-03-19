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
 * Reconciler for <b>Spring Data MongoDB</b> string-based property references.
 * <p>
 * Detects:
 * <ul>
 *   <li>{@code Criteria.where("prop")} — the first string argument</li>
 *   <li>{@code Update.set("prop", value)}, {@code Update.unset("prop")}, and all other
 *       MongoDB {@code Update} methods that accept a string key — the first string argument</li>
 *   <li>{@code Field.include("prop")}, {@code Field.exclude("prop")} — projection field
 *       references (single and varargs)</li>
 * </ul>
 * <p>
 * Only flags string literals for which a {@code TypedPropertyPath} overload exists
 * on the declaring class, determined dynamically at reconcile time.
 */
public class SpringDataMongoDbReconciler extends AbstractSpringDataPropertyReferenceReconciler {

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

	public SpringDataMongoDbReconciler(QuickfixRegistry quickfixRegistry) {
		super(quickfixRegistry);
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		Version version = SpringProjectUtil.getDependencyVersionByPrefix(project, "spring-data-mongodb");
		return version != null && version.compareTo(new Version(5, 1, 0, null)) >= 0;
	}

	@Override
	protected List<String> getRelevantTypesFqn() {
		return List.of(
				"org.springframework.data.mongodb.core.query.Criteria",
				"org.springframework.data.mongodb.core.query.Update",
				"org.springframework.data.mongodb.core.query.Field"
		);
	}

	@Override
	protected boolean isPropertyReferenceCall(MethodInvocation node, String erasedFqn) {
		return CRITERIA_FQN_TYPES.contains(erasedFqn) || UPDATE_FQN_TYPES.contains(erasedFqn)
				|| FIELD_FQN_TYPES.contains(erasedFqn);
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

		if (CRITERIA_FQN_TYPES.contains(declaringFqn)) {
			return extractCriteriaLiterals(args, methodBinding);
		} else if (UPDATE_FQN_TYPES.contains(declaringFqn)) {
			return extractFirstArgLiteral(args, methodBinding);
		} else if (FIELD_FQN_TYPES.contains(declaringFqn)) {
			return extractAllArgLiterals(args, methodBinding);
		}

		return List.of();
	}

	@Override
	protected AbstractSpringDataDomainTypeResolver getDomainTypeResolver() {
		return domainTypeResolver;
	}

	// =====================================================================
	// Criteria.where detection — first argument is the property name
	// =====================================================================

	private List<StringLiteral> extractCriteriaLiterals(List<Expression> args, IMethodBinding methodBinding) {
		if (args.size() == 1 && args.get(0) instanceof StringLiteral literal) {
			if (hasTypedPropertyPathOverload(methodBinding, 0)) {
				return List.of(literal);
			}
		}
		return List.of();
	}

	// =====================================================================
	// Update/Field detection — first arg (Update) or all args (Field)
	// Dynamic: we check if a TypedPropertyPath overload exists.
	// =====================================================================

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

	// =====================================================================
	// Domain type resolver — MongoDB-specific patterns
	// =====================================================================

	/**
	 * Domain type resolver for Spring Data MongoDB. In addition to the common
	 * repository pattern, supports fluent API receiver type extraction,
	 * template find calls with Class parameters, and aggregation.
	 */
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

		private static final Set<String> TEMPLATE_QUERY_METHOD_NAMES = Set.of(
				"find", "findOne", "findAll", "findById", "findDistinct"
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

			// Pattern 2: Fluent API — receiver expression type argument
			result = tryFluentReceiverType(invocation);
			if (result != null) {
				return result;
			}

			// Pattern 3: Template find/findOne/findAll — template.find(query, X.class)
			result = tryTemplateFindCall(invocation, declaringType);
			if (result != null) {
				return result;
			}

			// Pattern 4: Aggregation — newAggregation(X.class, ...)
			return tryAggregation(invocation, declaringType);
		}

		// Pattern 2: Fluent API — receiver expression type
		// The fluent chain threads the domain type T through generics:
		//   mongoOps.query(Customer.class)  -> ExecutableFind<Customer>
		//   .as(Jedi.class)                 -> FindWithProjection<Jedi>  (narrowed)
		//   .matching(query(where("name"))) -> receiver type has <Jedi>

		private @Nullable ITypeBinding tryFluentReceiverType(MethodInvocation invocation) {
			Expression receiver = invocation.getExpression();
			if (receiver == null) {
				return null;
			}
			ITypeBinding receiverType = receiver.resolveTypeBinding();
			if (receiverType == null || !receiverType.isParameterizedType()) {
				return null;
			}
			ITypeBinding[] typeArgs = receiverType.getTypeArguments();
			if (typeArgs.length == 1 && !typeArgs[0].isWildcardType()
					&& !typeArgs[0].isPrimitive()
					&& !"java.lang.Object".equals(typeArgs[0].getQualifiedName())) {
				return typeArgs[0];
			}
			return null;
		}

		// Pattern 3: Template find/findOne/findAll with explicit Class parameter

		private @Nullable ITypeBinding tryTemplateFindCall(MethodInvocation invocation, ITypeBinding declaringType) {
			String methodName = invocation.getName().getIdentifier();
			if (TEMPLATE_QUERY_METHOD_NAMES.contains(methodName) && ASTUtils.isAnyTypeInHierarchy(declaringType, TEMPLATE_FQN_TYPES)) {
				return findClassLiteralInArguments(invocation);
			}
			return null;
		}

		// Pattern 4: Aggregation — newAggregation(X.class, ...)

		private @Nullable ITypeBinding tryAggregation(MethodInvocation invocation, ITypeBinding declaringType) {
			if ("newAggregation".equals(invocation.getName().getIdentifier())
					&& ASTUtils.isAnyTypeInHierarchy(declaringType, AGGREGATION_FQN_TYPES)) {
				return extractFirstClassLiteralArgument(invocation);
			}
			return null;
		}

		private @Nullable ITypeBinding extractFirstClassLiteralArgument(MethodInvocation invocation) {
			@SuppressWarnings("unchecked")
			List<Expression> args = invocation.arguments();
			if (!args.isEmpty() && args.get(0) instanceof TypeLiteral typeLiteral) {
				return typeLiteral.getType().resolveBinding();
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
