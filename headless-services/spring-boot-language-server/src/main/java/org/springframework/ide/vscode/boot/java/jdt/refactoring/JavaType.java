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
import java.util.List;

import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Type;

/**
 * Root abstraction for the Java type model used by JDT-based refactorings.
 * <p>
 * Implementations include:
 * <ul>
 *   <li>{@link FullyQualifiedName} — sub-interface for class-like types with a fully qualified name</li>
 *   <li>{@link ClassName} — a non-parameterized class/interface type</li>
 *   <li>{@link ParameterizedClassName} — a parameterized type with type arguments</li>
 *   <li>{@link WildcardName} — a wildcard type ({@code ?}, {@code ? extends X}, {@code ? super X})</li>
 *   <li>{@link PrimitiveTypeName} — a primitive type ({@code int}, {@code boolean}, etc.)</li>
 *   <li>{@link ArrayTypeName} — an array type ({@code String[]}, {@code int[][]}, etc.)</li>
 * </ul>
 * <p>
	 * Instances are created via the {@link #parse(String)} factory method, which accepts
	 * fully qualified type strings using either {@code $} or {@code .} for inner classes.
	 * When {@code .} is used, the parser applies a heuristic based on Java naming conventions
	 * (package segments are lowercase, class names start with uppercase) to detect inner class
	 * boundaries.
 *
 * @author Alex Boyko
 */
public interface JavaType {

	/**
	 * Returns a human-readable display name for this type using short (simple) names.
	 * <p>
	 * Examples:
	 * <ul>
	 *   <li>{@code "int"} for a primitive</li>
	 *   <li>{@code "String"} for a simple class</li>
	 *   <li>{@code "Map.Entry"} for an inner class</li>
	 *   <li>{@code "Map<String, Integer>"} for a parameterized type</li>
	 *   <li>{@code "String[]"} for an array</li>
	 *   <li>{@code "? extends Number"} for a wildcard</li>
	 * </ul>
	 */
	String getDisplayName();

	/**
	 * Returns a flat list of all concrete {@link ClassName} instances referenced in this
	 * type tree. This is used for import management — each class name in the list may
	 * need an import statement.
	 * <p>
	 * Wildcards contribute nothing directly; parameterized types contribute their base
	 * class plus recurse into type arguments. Primitives return an empty list.
	 */
	List<ClassName> getAllClassNames();

	/**
	 * Builds a JDT AST {@link Type} node from this type model.
	 * The generated type uses simple/field names (not fully qualified names),
	 * suitable for use after imports have been added.
	 *
	 * @param ast the AST factory to use for creating nodes
	 * @return the JDT AST {@link Type} node
	 */
	Type toType(AST ast);

	/**
	 * Parse a type string into a {@link JavaType} instance.
	 * <p>
	 * Inner classes may be denoted with {@code $} (e.g. {@code "java.util.Map$Entry"})
	 * or with {@code .} (e.g. {@code "java.util.Map.Entry"}). When {@code .} is used,
	 * the parser applies a heuristic based on Java naming conventions to detect inner
	 * class boundaries: consecutive {@code .}-separated segments that both start with
	 * an uppercase letter are treated as an inner class boundary.
	 * <p>
	 * Supports simple types, inner classes, parameterized types, wildcards,
	 * primitive types, and array types.
	 * <p>
	 * Uses JDT's {@link Signature} utility for parsing the type string into its
	 * structural components.
	 * <p>
	 * Examples of accepted input:
	 * <ul>
	 *   <li>{@code "java.util.Map"}</li>
	 *   <li>{@code "java.util.Map$Entry"} or {@code "java.util.Map.Entry"}</li>
	 *   <li>{@code "java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>"}</li>
	 *   <li>{@code "java.util.List<? extends com.example.Foo>"}</li>
	 *   <li>{@code "int"}, {@code "boolean"}</li>
	 *   <li>{@code "int[]"}, {@code "java.lang.String[][]"}</li>
	 *   <li>{@code "java.util.List<java.lang.String>[]"}</li>
	 * </ul>
	 *
	 * @param typeString the type string
	 * @return the parsed {@link JavaType}
	 */
	static JavaType parse(String typeString) {
		String normalized = NormalizeUtils.normalizeInnerClasses(typeString.trim());
		String sig = Signature.createTypeSignature(normalized, true);
		return parseFromSignature(sig);
	}

	/**
	 * Recursively parse a JDT type signature string into a {@link JavaType}.
	 *
	 * @param sig a JDT type signature (e.g. {@code "Ljava.util.Map;"}, {@code "I"}, {@code "[Ljava.lang.String;"})
	 * @return the parsed {@link JavaType}
	 */
	private static JavaType parseFromSignature(String sig) {
		int kind = Signature.getTypeSignatureKind(sig);

		switch (kind) {
			case Signature.BASE_TYPE_SIGNATURE:
				return new PrimitiveTypeName(Signature.toString(sig));

			case Signature.ARRAY_TYPE_SIGNATURE:
				int dimensions = Signature.getArrayCount(sig);
				String elementSig = Signature.getElementType(sig);
				JavaType componentType = parseFromSignature(elementSig);
				return new ArrayTypeName(componentType, dimensions);

			case Signature.CLASS_TYPE_SIGNATURE:
				return parseClassSignature(sig);

			case Signature.WILDCARD_TYPE_SIGNATURE:
				return parseWildcardSignature(sig);

			default:
				throw new IllegalArgumentException("Unsupported type signature kind: " + kind + " for signature: " + sig);
		}
	}

	/**
	 * Parse a class type signature into a {@link ClassName} or {@link ParameterizedClassName}.
	 * <p>
	 * Uses {@link Signature#getTypeErasure(String)} to extract the raw class signature,
	 * then {@link Signature#getSignatureQualifier(String)} and
	 * {@link Signature#getSignatureSimpleName(String)} to decompose it into package and
	 * class name chain. For {@code $}-separated inner class input, the simple name is
	 * returned as a {@code .}-separated chain (e.g. {@code "Map.Entry"}).
	 */
	private static JavaType parseClassSignature(String sig) {
		// Extract the erasure (raw class without type arguments)
		String erasureSig = Signature.getTypeErasure(sig);

		// Decompose into package and class chain
		String packageName = Signature.getSignatureQualifier(erasureSig);
		String classChain = Signature.getSignatureSimpleName(erasureSig);

		// Build the ClassName chain by splitting on '.' (inner classes)
		ClassName className = buildClassNameFromChain(packageName, classChain);

		// Extract type arguments
		String[] typeArgSigs = Signature.getTypeArguments(sig);
		if (typeArgSigs.length > 0) {
			List<JavaType> parsedArgs = new ArrayList<>(typeArgSigs.length);
			for (String argSig : typeArgSigs) {
				parsedArgs.add(parseFromSignature(argSig));
			}
			return new ParameterizedClassName(className, parsedArgs);
		}

		return className;
	}

	/**
	 * Build a {@link ClassName} from a package name and a class chain string.
	 * <p>
	 * The class chain may contain {@code .} separators for inner classes
	 * (e.g. {@code "Map.Entry"} or {@code "Outer.Middle.Inner"}).
	 *
	 * @param packageName the package name (e.g. {@code "java.util"}), or empty string
	 * @param classChain  the class name chain (e.g. {@code "Map"}, {@code "Map.Entry"})
	 * @return the constructed {@link ClassName}
	 */
	private static ClassName buildClassNameFromChain(String packageName, String classChain) {
		String[] parts = classChain.split("\\.");
		ClassName current = new ClassName(packageName, parts[0]);
		for (int i = 1; i < parts.length; i++) {
			current = new ClassName(current, parts[i]);
		}
		return current;
	}

	/**
	 * Parse a wildcard type signature into a {@link WildcardName}.
	 * <p>
	 * Wildcard signatures use:
	 * <ul>
	 *   <li>{@code *} — unbounded wildcard ({@code ?})</li>
	 *   <li>{@code +<sig>} — upper-bounded wildcard ({@code ? extends X})</li>
	 *   <li>{@code -<sig>} — lower-bounded wildcard ({@code ? super X})</li>
	 * </ul>
	 */
	private static WildcardName parseWildcardSignature(String sig) {
		char first = sig.charAt(0);
		if (first == Signature.C_STAR) {
			// Unbounded wildcard: ?
			return new WildcardName(null, true);
		} else if (first == Signature.C_EXTENDS) {
			// Upper-bounded: ? extends X
			String boundSig = sig.substring(1);
			JavaType bound = parseFromSignature(boundSig);
			return new WildcardName(bound, true);
		} else if (first == Signature.C_SUPER) {
			// Lower-bounded: ? super X
			String boundSig = sig.substring(1);
			JavaType bound = parseFromSignature(boundSig);
			return new WildcardName(bound, false);
		}
		throw new IllegalArgumentException("Invalid wildcard signature: " + sig);
	}

}
