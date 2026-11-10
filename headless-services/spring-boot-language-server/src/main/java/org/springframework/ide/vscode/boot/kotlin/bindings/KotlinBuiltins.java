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
package org.springframework.ide.vscode.boot.kotlin.bindings;

import java.util.Map;

/**
 * How the types Kotlin has built in, or defines as aliases of Java types, look on the JVM. Kotlin
 * sources refer to {@code String} or {@code Int}, while the JVM only knows {@code java.lang.String}
 * and {@code int}.
 *
 * @author Martin Lippert
 */
final class KotlinBuiltins {

	private KotlinBuiltins() {
	}

	/** the types that are primitives on the JVM, with the primitive and the boxed type */
	record Primitive(String primitive, String boxed) {
	}

	private static final Map<String, Primitive> PRIMITIVES = Map.of(
			"kotlin.Int", new Primitive("int", "java.lang.Integer"),
			"kotlin.Long", new Primitive("long", "java.lang.Long"),
			"kotlin.Short", new Primitive("short", "java.lang.Short"),
			"kotlin.Byte", new Primitive("byte", "java.lang.Byte"),
			"kotlin.Char", new Primitive("char", "java.lang.Character"),
			"kotlin.Boolean", new Primitive("boolean", "java.lang.Boolean"),
			"kotlin.Float", new Primitive("float", "java.lang.Float"),
			"kotlin.Double", new Primitive("double", "java.lang.Double"));

	/** the arrays of primitives, with the primitive of the elements */
	private static final Map<String, String> PRIMITIVE_ARRAYS = Map.of(
			"kotlin.IntArray", "int",
			"kotlin.LongArray", "long",
			"kotlin.ShortArray", "short",
			"kotlin.ByteArray", "byte",
			"kotlin.CharArray", "char",
			"kotlin.BooleanArray", "boolean",
			"kotlin.FloatArray", "float",
			"kotlin.DoubleArray", "double");

	/** the Kotlin types that are mapped to Java classes */
	private static final Map<String, String> CLASSES = Map.ofEntries(
			Map.entry("kotlin.Any", "java.lang.Object"),
			Map.entry("kotlin.String", "java.lang.String"),
			Map.entry("kotlin.CharSequence", "java.lang.CharSequence"),
			Map.entry("kotlin.Number", "java.lang.Number"),
			Map.entry("kotlin.Comparable", "java.lang.Comparable"),
			Map.entry("kotlin.Throwable", "java.lang.Throwable"),
			Map.entry("kotlin.Enum", "java.lang.Enum"),
			Map.entry("kotlin.Annotation", "java.lang.annotation.Annotation"),
			Map.entry("kotlin.Cloneable", "java.lang.Cloneable"),
			Map.entry("kotlin.Nothing", "java.lang.Void"),
			Map.entry("kotlin.collections.Iterable", "java.lang.Iterable"),
			Map.entry("kotlin.collections.MutableIterable", "java.lang.Iterable"),
			Map.entry("kotlin.collections.Iterator", "java.util.Iterator"),
			Map.entry("kotlin.collections.MutableIterator", "java.util.Iterator"),
			Map.entry("kotlin.collections.Collection", "java.util.Collection"),
			Map.entry("kotlin.collections.MutableCollection", "java.util.Collection"),
			Map.entry("kotlin.collections.List", "java.util.List"),
			Map.entry("kotlin.collections.MutableList", "java.util.List"),
			Map.entry("kotlin.collections.Set", "java.util.Set"),
			Map.entry("kotlin.collections.MutableSet", "java.util.Set"),
			Map.entry("kotlin.collections.Map", "java.util.Map"),
			Map.entry("kotlin.collections.MutableMap", "java.util.Map"),
			Map.entry("kotlin.collections.Map.Entry", "java.util.Map$Entry"),
			Map.entry("kotlin.collections.MutableMap.MutableEntry", "java.util.Map$Entry"),
			// the aliases the Kotlin standard library declares for Java classes
			Map.entry("kotlin.collections.ArrayList", "java.util.ArrayList"),
			Map.entry("kotlin.collections.LinkedHashMap", "java.util.LinkedHashMap"),
			Map.entry("kotlin.collections.HashMap", "java.util.HashMap"),
			Map.entry("kotlin.collections.LinkedHashSet", "java.util.LinkedHashSet"),
			Map.entry("kotlin.collections.HashSet", "java.util.HashSet"),
			Map.entry("kotlin.Exception", "java.lang.Exception"),
			Map.entry("kotlin.RuntimeException", "java.lang.RuntimeException"),
			Map.entry("kotlin.Error", "java.lang.Error"),
			Map.entry("kotlin.IllegalArgumentException", "java.lang.IllegalArgumentException"),
			Map.entry("kotlin.IllegalStateException", "java.lang.IllegalStateException"),
			Map.entry("kotlin.UnsupportedOperationException", "java.lang.UnsupportedOperationException"),
			Map.entry("kotlin.NoSuchElementException", "java.util.NoSuchElementException"));

	/** the packages whose members can be used in every Kotlin file without an import, in lookup order */
	static final String[] DEFAULT_IMPORTS = {
			"kotlin", "kotlin.annotation", "kotlin.collections", "kotlin.comparisons", "kotlin.io", "kotlin.ranges",
			"kotlin.sequences", "kotlin.text", "kotlin.jvm", "java.lang" };

	static final String ARRAY = "kotlin.Array";
	static final String UNIT = "kotlin.Unit";

	static boolean isBuiltin(String kotlinName) {
		return PRIMITIVES.containsKey(kotlinName) || PRIMITIVE_ARRAYS.containsKey(kotlinName) || CLASSES.containsKey(kotlinName)
				|| ARRAY.equals(kotlinName) || UNIT.equals(kotlinName);
	}

	static Primitive primitive(String kotlinName) {
		return PRIMITIVES.get(kotlinName);
	}

	/** the primitive of the elements if the name is the one of an array of primitives, null otherwise */
	static String primitiveArrayElement(String kotlinName) {
		return PRIMITIVE_ARRAYS.get(kotlinName);
	}

	/** the JVM name of the Java class the type is mapped to, null if it is none that is mapped */
	static String javaClass(String kotlinName) {
		return CLASSES.get(kotlinName);
	}

}
