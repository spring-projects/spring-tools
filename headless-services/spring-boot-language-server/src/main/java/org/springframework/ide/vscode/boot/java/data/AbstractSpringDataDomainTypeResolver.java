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
 * Abstract base for Spring Data domain type resolvers.
 * <p>
 * Provides the two-tier resolution strategy shared by all Spring Data modules:
 * <ol>
 *   <li><b>Exact resolution</b> ({@link #determineDomainTypeExact}) — walks the parent AST
 *       chain to find an enclosing method invocation that directly reveals the domain type.</li>
 *   <li><b>Contextual resolution</b> ({@link #determineDomainTypesFromBlock}) — scans all
 *       method invocations in the immediate enclosing {@link Block} to collect candidate
 *       domain types.</li>
 * </ol>
 * <p>
 * Concrete subclasses implement {@link #extractDomainTypeFromInvocation} with module-specific
 * patterns (e.g. MongoDB fluent API, aggregation, Cassandra template, etc.). The base class
 * provides the common <b>Pattern 1 — Repository method call</b> via {@link #tryRepositoryCall}.
 */
public abstract class AbstractSpringDataDomainTypeResolver {

	private static final Set<String> REPOSITORY_FQN_TYPES = Set.of(
			"org.springframework.data.repository.Repository"
	);

	private static final Set<String> WRAPPER_BASE_TYPES = Set.of(
			"java.lang.Iterable",
			"java.util.stream.Stream",
			"java.util.Optional",
			"org.reactivestreams.Publisher"
	);

	/**
	 * Tier 1 — Exact domain type resolution.
	 * <p>
	 * Walks up the AST parent chain from the given node, testing each ancestor
	 * {@link MethodInvocation} against all extraction patterns. Returns the first
	 * domain type found, or {@code null} if no enclosing invocation reveals one.
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

	/**
	 * Tier 2 — Contextual domain type resolution.
	 * <p>
	 * When exact resolution fails, scans all method invocations in the same
	 * enclosing {@link Block} to collect candidate domain types.
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

	/**
	 * Module-specific extraction logic. Given a single {@link MethodInvocation},
	 * tests it against module-specific domain type extraction patterns and returns
	 * the domain type if any pattern matches.
	 */
	protected abstract @Nullable ITypeBinding extractDomainTypeFromInvocation(MethodInvocation invocation);

	// =====================================================================
	// Shared Pattern: Repository method call
	// =====================================================================

	/**
	 * Pattern 1 — Repository method call.
	 * Extracts the domain type from the method's return type when the declaring
	 * type implements {@code Repository}.
	 */
	protected @Nullable ITypeBinding tryRepositoryCall(IMethodBinding methodBinding, ITypeBinding declaringType) {
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
	protected @Nullable ITypeBinding unwrapDomainType(ITypeBinding returnType) {
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

	protected boolean isCollectionOrWrapperType(ITypeBinding erasure) {
		return ASTUtils.isAnyTypeInHierarchy(erasure, WRAPPER_BASE_TYPES);
	}

	// =====================================================================
	// Shared Pattern: Fluent API receiver type
	// =====================================================================

	/**
	 * Returns FQN prefixes of the fluent operation types for this module.
	 * The receiver type's erased FQN must start with one of these prefixes
	 * for the fluent chain domain type extraction to apply.
	 * <p>
	 * For example, MongoDB returns prefixes like
	 * {@code "org.springframework.data.mongodb.core.ExecutableFindOperation"} which
	 * also matches inner types like {@code ExecutableFindOperation.FindWithQuery}.
	 * <p>
	 * Return an empty list if this module has no fluent API (e.g., JDBC).
	 */
	protected List<String> getFluentOperationTypePrefixes() {
		return List.of();
	}

	/**
	 * Extracts the domain type from the receiver expression's type argument
	 * in a fluent API chain. Only considers receivers whose erased type FQN
	 * starts with one of the prefixes from {@link #getFluentOperationTypePrefixes()}.
	 * <p>
	 * Example: {@code mongoOps.query(Customer.class).matching(where("name"))} —
	 * the receiver of {@code matching()} has type {@code FindWithQuery<Customer>},
	 * so the domain type is {@code Customer}.
	 */
	protected @Nullable ITypeBinding tryFluentReceiverType(MethodInvocation invocation) {
		List<String> prefixes = getFluentOperationTypePrefixes();
		if (prefixes.isEmpty()) {
			return null;
		}

		Expression receiver = invocation.getExpression();
		if (receiver == null) {
			return null;
		}
		ITypeBinding receiverType = receiver.resolveTypeBinding();
		if (receiverType == null || !receiverType.isParameterizedType()) {
			return null;
		}

		String receiverFqn = receiverType.getErasure().getQualifiedName();
		boolean isFluentType = false;
		for (String prefix : prefixes) {
			if (receiverFqn.startsWith(prefix)) {
				isFluentType = true;
				break;
			}
		}
		if (!isFluentType) {
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

	// =====================================================================
	// Shared Pattern: Class<T> literal in arguments
	// =====================================================================

	/**
	 * Scans method arguments for a {@code Class<T>} literal (e.g., {@code Customer.class})
	 * and returns the type binding. Skips {@code Object.class}.
	 */
	protected @Nullable ITypeBinding findClassLiteralInArguments(MethodInvocation invocation) {
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

	// =====================================================================
	// Helper: find enclosing Block
	// =====================================================================

	private @Nullable Block findEnclosingBlock(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof Block block) {
				return block;
			}
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

}
