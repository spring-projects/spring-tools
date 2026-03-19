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
package org.springframework.ide.vscode.boot.java.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.jspecify.annotations.Nullable;
import org.springframework.ide.vscode.boot.java.utils.ASTUtils;

/**
 * Resolves the Spring Data domain type from the AST context surrounding a string-based
 * property reference (e.g. {@code Sort.by("firstName")}, {@code Criteria.where("name")}).
 * <p>
 * Domain type resolution uses a <b>two-tier strategy</b>:
 * <ol>
 *   <li><b>Exact resolution</b> ({@link #determineDomainTypeExact}) — walks the parent AST
 *       chain to find an enclosing method invocation that directly reveals the domain type.
 *       Returns at most one type. This is the high-confidence path.</li>
 *   <li><b>Contextual resolution</b> ({@link #determineDomainTypesFromBlock}) — scans all
 *       method invocations in the immediate enclosing {@link Block} to collect candidate
 *       domain types. Returns a list of candidates that the caller can filter by property
 *       name matching.</li>
 * </ol>
 * <p>
 * Both tiers share the same extraction logic ({@link #extractDomainTypeFromInvocation}),
 * which recognizes 5 patterns:
 * <ul>
 *   <li><b>Pattern 1 — Repository method call:</b> extracts the domain type from
 *       the method's return type (unwrapping collections/wrappers like {@code List<T>},
 *       {@code Page<T>}, {@code Optional<T>}, etc.)</li>
 *   <li><b>Pattern 2 — Fluent Template API:</b> walks a fluent chain to find
 *       {@code .query(X.class)} or {@code .update(X.class)}</li>
 *   <li><b>Pattern 3 — Template find/findOne/findAll:</b> finds the {@code Class<T>}
 *       literal argument in calls like {@code template.find(query, Person.class)}</li>
 *   <li><b>Pattern 4 — Aggregation:</b> extracts the class literal from
 *       {@code newAggregation(X.class, ...)}</li>
 *   <li><b>Pattern 5 — Template update with entity:</b> resolves the type of the first
 *       argument in {@code operations.update(entity, options)}</li>
 * </ul>
 * <p>
 * The resolver only identifies the domain type; it does not validate whether a
 * particular property exists on it.
 */
public class SpringDataDomainTypeResolver {

	private static final Set<String> REPOSITORY_FQN_TYPES = Set.of(
			"org.springframework.data.repository.Repository"
	);

	private static final Set<String> TEMPLATE_QUERY_METHOD_NAMES = Set.of(
			"find", "findOne", "findAll", "findById", "findDistinct"
	);

	private static final Set<String> TEMPLATE_FQN_TYPES = Set.of(
			"org.springframework.data.mongodb.core.MongoTemplate",
			"org.springframework.data.mongodb.core.MongoOperations",
			"org.springframework.data.mongodb.core.ReactiveMongoTemplate",
			"org.springframework.data.mongodb.core.ReactiveMongoOperations",
			"org.springframework.data.mongodb.core.FluentMongoOperations",
			"org.springframework.data.cassandra.core.CassandraTemplate",
			"org.springframework.data.cassandra.core.CassandraOperations",
			"org.springframework.data.jdbc.core.JdbcAggregateTemplate",
			"org.springframework.data.jdbc.core.JdbcAggregateOperations",
			"org.springframework.data.r2dbc.core.R2dbcEntityTemplate",
			"org.springframework.data.r2dbc.core.R2dbcEntityOperations"
	);

	private static final Set<String> FLUENT_QUERY_METHOD_NAMES = Set.of("query", "update");

	private static final Set<String> AGGREGATION_FQN_TYPES = Set.of(
			"org.springframework.data.mongodb.core.aggregation.Aggregation"
	);

	// =====================================================================
	// Tier 1: Exact resolution — walk the parent AST chain
	// =====================================================================

	/**
	 * Tier 1 — Exact domain type resolution.
	 * <p>
	 * Walks up the AST parent chain from the given node, testing each ancestor
	 * {@link MethodInvocation} against all 5 extraction patterns. Returns the first
	 * domain type found, or {@code null} if no enclosing invocation reveals one.
	 * <p>
	 * This is the high-confidence path: when it returns a type, we know for certain
	 * this is the domain type the property reference belongs to.
	 */
	public @Nullable ITypeBinding determineDomainTypeExact(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof MethodInvocation invocation) {
				ITypeBinding domainType = extractDomainTypeFromInvocation(invocation);
				if (domainType != null) {
					return domainType;
				}
			}
			current = current.getParent();
		}
		return null;
	}

	// =====================================================================
	// Tier 2: Contextual resolution — scan the enclosing block
	// =====================================================================

	/**
	 * Tier 2 — Contextual domain type resolution.
	 * <p>
	 * When exact resolution fails (the Sort/Criteria call is not directly nested
	 * inside a repository/template invocation), we look at the broader context:
	 * all method invocations in the same enclosing {@link Block}.
	 * <p>
	 * This handles cases like:
	 * <pre>
	 *   Sort sort = Sort.by("firstName");       // &lt;-- no direct parent context
	 *   repository.findAll(sort);                // &lt;-- but this reveals the domain type
	 * </pre>
	 * <p>
	 * Returns all unique domain types found in the block. The caller can then
	 * filter by property name matching to narrow down to a single candidate.
	 */
	public List<ITypeBinding> determineDomainTypesFromBlock(ASTNode node) {
		Block block = findEnclosingBlock(node);
		if (block == null) {
			return List.of();
		}

		Set<String> seenFqns = new LinkedHashSet<>();
		List<ITypeBinding> domainTypes = new ArrayList<>();

		block.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodInvocation invocation) {
				ITypeBinding domainType = extractDomainTypeFromInvocation(invocation);
				if (domainType != null) {
					String fqn = domainType.getQualifiedName();
					if (seenFqns.add(fqn)) {
						domainTypes.add(domainType);
					}
				}
				return true;
			}
		});

		return domainTypes;
	}

	// =====================================================================
	// Shared extraction core — tests a single MethodInvocation
	// =====================================================================

	/**
	 * Shared extraction logic used by both tiers.
	 * <p>
	 * Given a single {@link MethodInvocation}, tests it against all 5 domain type
	 * extraction patterns and returns the domain type if any pattern matches.
	 */
	@Nullable ITypeBinding extractDomainTypeFromInvocation(MethodInvocation invocation) {
		IMethodBinding methodBinding = invocation.resolveMethodBinding();
		if (methodBinding == null) {
			return null;
		}

		ITypeBinding declaringType = methodBinding.getDeclaringClass();
		if (declaringType == null) {
			return null;
		}

		ITypeBinding result;

		// Pattern 1: Repository method call — extract domain type from return type
		result = tryRepositoryCall(methodBinding, declaringType);
		if (result != null) {
			return result;
		}

		// Pattern 2: Fluent Template API — mongoOps.query(X.class).matching(...)
		result = tryFluentApi(invocation);
		if (result != null) {
			return result;
		}

		// Pattern 3: Template find/findOne/findAll — template.find(query, X.class)
		result = tryTemplateFindCall(invocation, declaringType);
		if (result != null) {
			return result;
		}

		// Pattern 4: Aggregation — newAggregation(X.class, ...)
		result = tryAggregation(invocation, declaringType);
		if (result != null) {
			return result;
		}

		// Pattern 5: Template update with entity — operations.update(entity, options)
		return tryTemplateUpdateWithEntity(invocation, declaringType);
	}

	// =====================================================================
	// Pattern 1: Repository method call
	// Receiver implements Repository — domain type is derived from the
	// method's return type, not the repository's generic parameter.
	// E.g. List<OrdersPerCustomer> totalOrdersPerCustomer(Sort sort)
	//      → domain type is OrdersPerCustomer
	// =====================================================================

	private @Nullable ITypeBinding tryRepositoryCall(IMethodBinding methodBinding, ITypeBinding declaringType) {
		if (ASTUtils.findInTypeHierarchy(declaringType, REPOSITORY_FQN_TYPES) == null) {
			return null;
		}
		return unwrapDomainType(methodBinding.getReturnType());
	}

	/**
	 * Unwrap a return type to extract the domain type.
	 * <p>
	 * If the type is a parameterized collection/wrapper (e.g. {@code List<Customer>},
	 * {@code Page<Customer>}, {@code Optional<Customer>}, {@code Flux<Customer>}),
	 * returns the type argument. Otherwise returns the type itself, unless it's a
	 * primitive or {@code java.lang.Object}/{@code void}.
	 */
	private @Nullable ITypeBinding unwrapDomainType(ITypeBinding returnType) {
		if (returnType == null || returnType.isPrimitive()
				|| "void".equals(returnType.getQualifiedName())
				|| "java.lang.Object".equals(returnType.getQualifiedName())) {
			return null;
		}

		if (returnType.isParameterizedType()) {
			ITypeBinding erasure = returnType.getErasure();
			if (isCollectionOrWrapperType(erasure)) {
				ITypeBinding[] typeArgs = returnType.getTypeArguments();
				if (typeArgs.length >= 1 && !typeArgs[0].isWildcardType()) {
					return typeArgs[0];
				}
			}
		}

		return returnType;
	}

	private static final Set<String> WRAPPER_BASE_TYPES = Set.of(
			"java.lang.Iterable",
			"java.util.stream.Stream",
			"java.util.Optional",
			"org.reactivestreams.Publisher"
	);

	private boolean isCollectionOrWrapperType(ITypeBinding erasure) {
		return ASTUtils.isAnyTypeInHierarchy(erasure, WRAPPER_BASE_TYPES);
	}

	// =====================================================================
	// Pattern 2: Fluent Template API
	// Walk the fluent chain to find .query(X.class) or .update(X.class).
	// E.g. mongoOps.query(Customer.class).matching(query(where("name"))).all()
	// =====================================================================

	private @Nullable ITypeBinding tryFluentApi(MethodInvocation invocation) {
		Expression expr = invocation.getExpression();
		if (expr instanceof MethodInvocation methodExpr) {
			return walkFluentChainForDomainType(methodExpr);
		}
		return null;
	}

	private @Nullable ITypeBinding walkFluentChainForDomainType(MethodInvocation invocation) {
		String methodName = invocation.getName().getIdentifier();
		if (FLUENT_QUERY_METHOD_NAMES.contains(methodName)) {
			return extractFirstClassLiteralArgument(invocation);
		}
		Expression expr = invocation.getExpression();
		if (expr instanceof MethodInvocation methodExpr) {
			return walkFluentChainForDomainType(methodExpr);
		}
		return null;
	}

	// =====================================================================
	// Pattern 3: Template find/findOne/findAll with explicit Class parameter
	// E.g. template.find(Query.query(Criteria.where("name")), Person.class)
	// =====================================================================

	private @Nullable ITypeBinding tryTemplateFindCall(MethodInvocation invocation, ITypeBinding declaringType) {
		String methodName = invocation.getName().getIdentifier();
		if (TEMPLATE_QUERY_METHOD_NAMES.contains(methodName) && ASTUtils.isAnyTypeInHierarchy(declaringType, TEMPLATE_FQN_TYPES)) {
			return findClassLiteralInArguments(invocation);
		}
		return null;
	}

	// =====================================================================
	// Pattern 4: Aggregation — newAggregation(X.class, ...)
	// First argument may be a Class literal specifying the input domain type.
	// E.g. newAggregation(Order.class, match(where("id").is(...)))
	// =====================================================================

	private @Nullable ITypeBinding tryAggregation(MethodInvocation invocation, ITypeBinding declaringType) {
		if ("newAggregation".equals(invocation.getName().getIdentifier())
				&& ASTUtils.isAnyTypeInHierarchy(declaringType, AGGREGATION_FQN_TYPES)) {
			return extractFirstClassLiteralArgument(invocation);
		}
		return null;
	}

	// =====================================================================
	// Pattern 5: Template update with entity argument
	// First argument is the entity instance whose type is the domain type.
	// E.g. operations.update(person, UpdateOptions.builder()...build())
	// =====================================================================

	private @Nullable ITypeBinding tryTemplateUpdateWithEntity(MethodInvocation invocation, ITypeBinding declaringType) {
		if (!"update".equals(invocation.getName().getIdentifier())
				|| !ASTUtils.isAnyTypeInHierarchy(declaringType, TEMPLATE_FQN_TYPES)) {
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

	// =====================================================================
	// Helper: find enclosing Block
	// =====================================================================

	private @Nullable Block findEnclosingBlock(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof Block block) {
				return block;
			}
			// Stop at Statement level — don't cross into parent blocks
			if (current instanceof Statement) {
				ASTNode parent = current.getParent();
				if (parent instanceof Block block) {
					return block;
				}
			}
			current = current.getParent();
		}
		return null;
	}

	// =====================================================================
	// Helper: Class literal extraction from arguments
	// =====================================================================

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
