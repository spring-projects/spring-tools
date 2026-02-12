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

import java.util.List;

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
 * fully qualified type strings using {@code $} for inner classes
 * (as returned by {@code Class.getName()}).
 *
 * @author Alex Boyko
 */
interface JavaType {

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
	 * Inner classes are denoted with {@code $} (e.g. {@code "java.util.Map$Entry"}).
	 * Supports simple types, inner classes, parameterized types, wildcards,
	 * primitive types, and array types.
	 * <p>
	 * Examples of accepted input:
	 * <ul>
	 *   <li>{@code "java.util.Map"}</li>
	 *   <li>{@code "java.util.Map$Entry"}</li>
	 *   <li>{@code "java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>"}</li>
	 *   <li>{@code "java.util.List<? extends com.example.Foo>"}</li>
	 *   <li>{@code "int"}, {@code "boolean"}</li>
	 *   <li>{@code "int[]"}, {@code "java.lang.String[][]"}</li>
	 *   <li>{@code "java.util.List<java.lang.String>[]"}</li>
	 * </ul>
	 *
	 * @param typeString the type string (using {@code $} for inner classes)
	 * @return the parsed {@link JavaType}
	 */
	static JavaType parse(String typeString) {
		typeString = typeString.trim();

		// Handle wildcards: ?, ? extends X, ? super X
		if (typeString.startsWith("?")) {
			return parseWildcard(typeString);
		}

		// Handle array types: strip trailing [] pairs, count dimensions
		int dimensions = 0;
		String remaining = typeString;
		while (remaining.endsWith("[]")) {
			dimensions++;
			remaining = remaining.substring(0, remaining.length() - 2);
		}
		if (dimensions > 0) {
			JavaType componentType = parse(remaining);
			return new ArrayTypeName(componentType, dimensions);
		}

		// Handle primitive types
		if (PrimitiveTypeName.isPrimitive(typeString)) {
			return new PrimitiveTypeName(typeString);
		}

		// Extract the erased class name (before any '<')
		int angleBracket = typeString.indexOf('<');
		String erasedFqn = angleBracket >= 0 ? typeString.substring(0, angleBracket) : typeString;

		// Build the ClassName using $ for inner class detection
		ClassName className = buildClassName(erasedFqn);

		// Parse type arguments if present
		if (angleBracket >= 0) {
			List<String> argStrings = splitTypeArguments(typeString, angleBracket);
			List<JavaType> parsedArgs = new java.util.ArrayList<>(argStrings.size());
			for (String arg : argStrings) {
				parsedArgs.add(parse(arg));
			}
			return new ParameterizedClassName(className, parsedArgs);
		}

		return className;
	}

	/**
	 * Split the top-level type arguments from a type string.
	 * <p>
	 * Given {@code "Map<String, List<Integer>>"} with {@code angleBracketIdx} pointing
	 * to the first {@code <}, returns {@code ["String", "List<Integer>"]}.
	 */
	private static List<String> splitTypeArguments(String typeString, int angleBracketIdx) {
		// Content between the outermost < and >
		String argsContent = typeString.substring(angleBracketIdx + 1, typeString.length() - 1);

		List<String> args = new java.util.ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < argsContent.length(); i++) {
			char c = argsContent.charAt(i);
			if (c == '<') {
				depth++;
			} else if (c == '>') {
				depth--;
			} else if (c == ',' && depth == 0) {
				args.add(argsContent.substring(start, i).trim());
				start = i + 1;
			}
		}
		args.add(argsContent.substring(start).trim());
		return args;
	}

	/**
	 * Build a ClassName from a fully qualified name string that uses {@code $} for inner classes.
	 * <p>
	 * E.g. {@code "java.util.Map$Entry"} builds
	 * {@code ClassName("java.util", "Map")} as the declaring class,
	 * then {@code ClassName(declaringMap, "Entry")}.
	 */
	private static ClassName buildClassName(String fqn) {
		// Find the last dot before any $ to separate package from class
		int dollarIdx = fqn.indexOf('$');
		String beforeDollar = dollarIdx >= 0 ? fqn.substring(0, dollarIdx) : fqn;

		int lastDot = beforeDollar.lastIndexOf('.');
		String packageName;
		String classPart;
		if (lastDot >= 0) {
			packageName = fqn.substring(0, lastDot);
			classPart = fqn.substring(lastDot + 1);
		} else {
			packageName = "";
			classPart = fqn;
		}

		// Split class part on $ for inner classes
		String[] parts = classPart.split("\\$");
		ClassName current = new ClassName(packageName, parts[0]);
		for (int i = 1; i < parts.length; i++) {
			current = new ClassName(current, parts[i]);
		}
		return current;
	}

	/**
	 * Parse a wildcard from a source-style string like "?", "? extends Foo", "? super Bar".
	 */
	private static WildcardName parseWildcard(String typeString) {
		String rest = typeString.substring(1).trim();
		if (rest.startsWith("extends ")) {
			JavaType bound = parse(rest.substring("extends ".length()).trim());
			return new WildcardName(bound, true);
		} else if (rest.startsWith("super ")) {
			JavaType bound = parse(rest.substring("super ".length()).trim());
			return new WildcardName(bound, false);
		}
		return new WildcardName(null, true);
	}

}
