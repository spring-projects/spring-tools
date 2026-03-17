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

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the Spring Data domain type from AST context surrounding a string-based property reference.
 * <p>
 * Given an AST node (typically a {@code Sort.by(...)}, {@code Criteria.where(...)}, etc.), this class
 * walks up the AST to determine the domain type from the enclosing context:
 * <ul>
 *   <li>Repository method call — extracts {@code T} from {@code Repository<T, ID>} type hierarchy</li>
 *   <li>Fluent Template API — walks {@code .query(X.class)} / {@code .update(X.class)} chain</li>
 *   <li>Template find/findOne/findAll — finds {@code Class<T>} literal in arguments</li>
 *   <li>Aggregation {@code newAggregation(X.class, ...)} — extracts class literal</li>
 *   <li>Template {@code update(entity, options)} — resolves entity argument type</li>
 * </ul>
 */
public class SpringDataDomainTypeResolver {

	private static final String REPOSITORY_FQN = "org.springframework.data.repository.Repository";

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

	public @Nullable ITypeBinding determineDomainType(ASTNode node) {
		ITypeBinding domainType;

		domainType = findDomainTypeFromRepositoryMethodCall(node);
		if (domainType != null) {
			return domainType;
		}

		domainType = findDomainTypeFromFluentTemplateApi(node);
		if (domainType != null) {
			return domainType;
		}

		domainType = findDomainTypeFromTemplateFindCall(node);
		if (domainType != null) {
			return domainType;
		}

		domainType = findDomainTypeFromAggregation(node);
		if (domainType != null) {
			return domainType;
		}

		return findDomainTypeFromTemplateUpdateWithEntity(node);
	}

	/**
	 * Scenario 1: The expression is ultimately an argument to a repository method call.
	 * Walks up the parent chain to find a MethodInvocation whose receiver type implements Repository&lt;T, ID&gt;.
	 */
	private @Nullable ITypeBinding findDomainTypeFromRepositoryMethodCall(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof MethodInvocation invocation) {
				IMethodBinding methodBinding = invocation.resolveMethodBinding();
				if (methodBinding != null) {
					ITypeBinding declaringType = methodBinding.getDeclaringClass();
					if (declaringType != null) {
						ITypeBinding repoType = findRepositoryType(declaringType);
						if (repoType != null) {
							ITypeBinding[] typeArgs = repoType.getTypeArguments();
							if (typeArgs.length >= 1) {
								return typeArgs[0];
							}
						}
					}
				}
			}
			current = current.getParent();
		}
		return null;
	}

	/**
	 * Scenario 2: Fluent Template API chain like operations.query(X.class).matching(where(...)).
	 * Walks up the method invocation chain looking for query(Class) or update(Class).
	 */
	private @Nullable ITypeBinding findDomainTypeFromFluentTemplateApi(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof MethodInvocation invocation) {
				Expression expr = invocation.getExpression();
				if (expr instanceof MethodInvocation methodExpr) {
					ITypeBinding result = walkFluentChainForDomainType(methodExpr);
					if (result != null) {
						return result;
					}
				}
			}
			current = current.getParent();
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

	/**
	 * Scenario 3: Template find/findOne/findAll with explicit Class parameter.
	 * E.g. template.find(query, Person.class)
	 */
	private @Nullable ITypeBinding findDomainTypeFromTemplateFindCall(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof MethodInvocation invocation) {
				String methodName = invocation.getName().getIdentifier();
				if (TEMPLATE_QUERY_METHOD_NAMES.contains(methodName)) {
					IMethodBinding methodBinding = invocation.resolveMethodBinding();
					if (methodBinding != null) {
						ITypeBinding declaringType = methodBinding.getDeclaringClass();
						if (declaringType != null && isTemplateOrOperationsType(declaringType)) {
							return findClassLiteralInArguments(invocation);
						}
					}
				}
			}
			current = current.getParent();
		}
		return null;
	}

	/**
	 * Scenario 4: Aggregation newAggregation(X.class, ...).
	 * The first argument to newAggregation may be a Class literal.
	 */
	private @Nullable ITypeBinding findDomainTypeFromAggregation(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof MethodInvocation invocation) {
				String methodName = invocation.getName().getIdentifier();
				if ("newAggregation".equals(methodName)) {
					IMethodBinding methodBinding = invocation.resolveMethodBinding();
					if (methodBinding != null) {
						String declaringFqn = getErasedFqn(methodBinding.getDeclaringClass());
						if (AGGREGATION_FQN_TYPES.contains(declaringFqn)) {
							return extractFirstClassLiteralArgument(invocation);
						}
					}
				}
			}
			current = current.getParent();
		}
		return null;
	}

	/**
	 * Scenario 5: operations.update(entity, options) where entity is the first arg.
	 * The type of the entity argument is the domain type.
	 */
	private @Nullable ITypeBinding findDomainTypeFromTemplateUpdateWithEntity(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof MethodInvocation invocation) {
				String methodName = invocation.getName().getIdentifier();
				if ("update".equals(methodName)) {
					IMethodBinding methodBinding = invocation.resolveMethodBinding();
					if (methodBinding != null) {
						ITypeBinding declaringType = methodBinding.getDeclaringClass();
						if (declaringType != null && isTemplateOrOperationsType(declaringType)) {
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
						}
					}
				}
			}
			current = current.getParent();
		}
		return null;
	}

	private @Nullable ITypeBinding findRepositoryType(ITypeBinding type) {
		if (type == null) {
			return null;
		}
		String erasedFqn = getErasedFqn(type);
		if (REPOSITORY_FQN.equals(erasedFqn) && type.isParameterizedType()) {
			return type;
		}
		for (ITypeBinding iface : type.getInterfaces()) {
			ITypeBinding result = findRepositoryType(iface);
			if (result != null) {
				return result;
			}
		}
		ITypeBinding superclass = type.getSuperclass();
		if (superclass != null) {
			return findRepositoryType(superclass);
		}
		return null;
	}

	private boolean isTemplateOrOperationsType(ITypeBinding type) {
		if (type == null) {
			return false;
		}
		String fqn = getErasedFqn(type);
		if (TEMPLATE_FQN_TYPES.contains(fqn)) {
			return true;
		}
		for (ITypeBinding iface : type.getInterfaces()) {
			if (isTemplateOrOperationsType(iface)) {
				return true;
			}
		}
		ITypeBinding superclass = type.getSuperclass();
		if (superclass != null) {
			return isTemplateOrOperationsType(superclass);
		}
		return false;
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

	private String getErasedFqn(ITypeBinding type) {
		if (type.isParameterizedType()) {
			return type.getErasure().getQualifiedName();
		}
		return type.getQualifiedName();
	}

}
