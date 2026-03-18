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
package org.springframework.ide.vscode.boot.java.jdt.refactoring;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

/**
 * A self-contained JDT-based refactoring that replaces string-based Spring Data
 * property references with type-safe method references or {@code PropertyPath} chains.
 * <p>
 * Single-segment example — replaces:
 * <pre>
 *   Sort.by("firstName")
 * </pre>
 * with:
 * <pre>
 *   Sort.by(Customer::getFirstName)
 * </pre>
 * <p>
 * Multi-segment example — replaces:
 * <pre>
 *   where("address.country")
 * </pre>
 * with:
 * <pre>
 *   where(PropertyPath.of(Person::getAddress).then(Address::getCountry))
 * </pre>
 * and adds imports for the domain types and {@code PropertyPath} as needed.
 * <p>
 * The constructor accepts only serializable {@link PropertyReferenceDescriptor}
 * records. The live AST and {@link ASTRewrite} are provided to {@link #apply}.
 * Multiple descriptors can be passed to batch-replace several literals in one rewrite.
 */
public class TypeSafePropertyReferenceRefactoring implements JdtRefactoring {

	/**
	 * A single segment in a property path, pairing a domain type with the accessor
	 * method name to use in a method reference.
	 * <p>
	 * For example, the dotted path {@code "address.country"} on domain type {@code Person}
	 * is represented as two segments:
	 * <ol>
	 *   <li>{@code PropertySegment("com.example.Person", "getAddress")}</li>
	 *   <li>{@code PropertySegment("com.example.Address", "getCountry")}</li>
	 * </ol>
	 * For records, the method name is the accessor name (e.g., {@code "firstName"})
	 * rather than a getter (e.g., {@code "getFirstName"}).
	 *
	 * @param domainTypeFqn  fully qualified name of the type that owns this property
	 *                       (e.g. {@code "com.example.Person"})
	 * @param methodName     the accessor/getter method name on that type
	 *                       (e.g. {@code "getAddress"} for classes, {@code "firstName"} for records)
	 */
	public static record PropertySegment(String domainTypeFqn, String methodName) {

		public PropertySegment {
			if (domainTypeFqn == null || domainTypeFqn.isBlank()) {
				throw new IllegalArgumentException("domainTypeFqn must not be null or blank");
			}
			if (methodName == null || methodName.isBlank()) {
				throw new IllegalArgumentException("methodName must not be null or blank");
			}
		}

	}

	/**
	 * Serializable descriptor for a single string-based property reference that should
	 * be replaced with a type-safe method reference (or {@code PropertyPath} chain).
	 * <p>
	 * Contains only plain data (offset, property segments) so it can be transported
	 * across LSP boundaries or stored in a code action command payload.
	 * <p>
	 * Each {@link PropertySegment} in the chain pairs a domain type FQN with the
	 * accessor method name on that type. For a simple property like {@code "firstName"}
	 * on {@code Customer}, the chain has one segment with method name {@code "getFirstName"}
	 * (or {@code "firstName"} for records). For a dotted path like
	 * {@code "address.country"} on {@code Person}, the chain has two segments:
	 * {@code [Person/getAddress, Address/getCountry]}.
	 *
	 * @param offset          start offset of the {@code StringLiteral} AST node (including
	 *                        the opening quote) in the compilation unit source
	 * @param propertyChain   ordered list of property segments; must have at least one element
	 */
	public static record PropertyReferenceDescriptor(
			int offset,
			List<PropertySegment> propertyChain
	) {

		public PropertyReferenceDescriptor {
			if (propertyChain == null || propertyChain.isEmpty()) {
				throw new IllegalArgumentException("propertyChain must not be null or empty");
			}
			propertyChain = List.copyOf(propertyChain);
		}

	}

	static final String PROPERTY_PATH_FQN = "org.springframework.data.core.PropertyPath";
	static final String PROPERTY_PATH_SIMPLE = "PropertyPath";

	private final PropertyReferenceDescriptor[] descriptors;

	/**
	 * @param descriptors one or more descriptors identifying string literals to replace
	 */
	public TypeSafePropertyReferenceRefactoring(PropertyReferenceDescriptor... descriptors) {
		if (descriptors == null || descriptors.length == 0) {
			throw new IllegalArgumentException("At least one descriptor is required");
		}
		this.descriptors = descriptors;
	}

	/**
	 * Record all replacements and import additions into an existing {@link ASTRewrite}.
	 * <p>
	 * This is the primary API when multiple refactorings need to share a single
	 * rewrite against the same compilation unit.
	 *
	 * @param rewrite the shared {@link ASTRewrite} to record changes into
	 * @param cu      the parsed {@link CompilationUnit} (must be the same instance
	 *                that the rewrite was created from)
	 */
	@Override
	public void apply(ASTRewrite rewrite, CompilationUnit cu) {
		AST ast = cu.getAST();

		// Collect all needed import FQNs across all descriptors first,
		// then add them in sorted order so ListRewrite insertions don't interleave.
		Set<String> importFqns = new LinkedHashSet<>();

		for (PropertyReferenceDescriptor descriptor : descriptors) {
			StringLiteral literal = findStringLiteralAtOffset(cu, descriptor.offset());
			if (literal == null) {
				continue;
			}

			List<PropertySegment> chain = descriptor.propertyChain();

			if (chain.size() == 1) {
				// Single segment: replace with DomainType::getProperty
				ASTNode replacement = buildMethodReference(ast, chain.get(0));
				rewrite.replace(literal, replacement, null);
				importFqns.add(chain.get(0).domainTypeFqn());
			}
			else {
				// Multi-segment: replace with PropertyPath.of(T1::getP1).then(T2::getP2)...
				ASTNode replacement = buildPropertyPathChain(ast, chain);
				rewrite.replace(literal, replacement, null);
				importFqns.add(PROPERTY_PATH_FQN);
				for (PropertySegment segment : chain) {
					importFqns.add(segment.domainTypeFqn());
				}
			}
		}

		List<String> sortedFqns = new ArrayList<>(importFqns);
		sortedFqns.sort(String::compareTo);
		for (String fqn : sortedFqns) {
			ClassType classType = classTypeFromFqn(fqn);
			JdtRefactorUtils.addImport(rewrite, ast, cu, classType);
		}
	}

	/**
	 * Build {@code DomainType::methodName} for a single segment.
	 */
	private static ExpressionMethodReference buildMethodReference(AST ast, PropertySegment segment) {
		ExpressionMethodReference ref = ast.newExpressionMethodReference();
		ref.setExpression(ast.newSimpleName(extractSimpleName(segment.domainTypeFqn())));
		ref.setName(ast.newSimpleName(segment.methodName()));
		return ref;
	}

	/**
	 * Build {@code PropertyPath.of(T1::getP1).then(T2::getP2).then(T3::getP3)...}
	 * for a multi-segment property chain.
	 */
	@SuppressWarnings("unchecked")
	private static ASTNode buildPropertyPathChain(AST ast, List<PropertySegment> chain) {
		// Start: PropertyPath.of(T1::getP1)
		MethodInvocation ofCall = ast.newMethodInvocation();
		ofCall.setExpression(ast.newSimpleName(PROPERTY_PATH_SIMPLE));
		ofCall.setName(ast.newSimpleName("of"));
		ofCall.arguments().add(buildMethodReference(ast, chain.get(0)));

		// Chain: .then(T2::getP2).then(T3::getP3)...
		MethodInvocation current = ofCall;
		for (int i = 1; i < chain.size(); i++) {
			MethodInvocation thenCall = ast.newMethodInvocation();
			thenCall.setExpression(current);
			thenCall.setName(ast.newSimpleName("then"));
			thenCall.arguments().add(buildMethodReference(ast, chain.get(i)));
			current = thenCall;
		}

		return current;
	}

	private static StringLiteral findStringLiteralAtOffset(CompilationUnit cu, int offset) {
		StringLiteral[] result = new StringLiteral[1];
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(StringLiteral node) {
				if (node.getStartPosition() == offset) {
					result[0] = node;
				}
				return false;
			}
		});
		return result[0];
	}

	private static String extractSimpleName(String fqn) {
		int lastDot = fqn.lastIndexOf('.');
		return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
	}

	private static String extractPackageName(String fqn) {
		int lastDot = fqn.lastIndexOf('.');
		return lastDot >= 0 ? fqn.substring(0, lastDot) : "";
	}

	private static ClassType classTypeFromFqn(String fqn) {
		return new ClassType(extractPackageName(fqn), extractSimpleName(fqn));
	}

}
