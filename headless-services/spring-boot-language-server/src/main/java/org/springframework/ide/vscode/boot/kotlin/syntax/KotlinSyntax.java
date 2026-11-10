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
package org.springframework.ide.vscode.boot.kotlin.syntax;

import java.util.List;

/**
 * What the Kotlin indexing needs to know about the syntax of a Kotlin source file: packages,
 * imports, classes with their annotations and supertypes, functions, and where all of that is in
 * the source text. It is the contract between whatever parses Kotlin ({@link KotlinPsiReader}
 * today) and the rest of the Kotlin support, which keeps the parser replaceable.
 * <p>
 * Names are kept the way they are written in the source: nothing in here is resolved. All
 * ranges are offsets into the original source text.
 *
 * @author Martin Lippert
 */
public final class KotlinSyntax {

	private KotlinSyntax() {
	}

	/** a part of the source text */
	public record Range(int start, int length) {
		public int end() {
			return start + length;
		}
	}

	/**
	 * A type reference as written, like {@code List<String>?}.
	 *
	 * @param name the name as written, qualified or not, without type arguments and nullability marker
	 */
	public record TypeRef(String name, boolean nullable, List<TypeRef> arguments, Range range) {
	}

	/** the value of an annotation argument */
	public sealed interface Value permits StringValue, NumberValue, BooleanValue, ClassValue, ArrayValue, ReferenceValue, ConcatValue, UnsupportedValue {
	}

	/** a string literal without templates */
	public record StringValue(String value) implements Value {
	}

	/** a number literal, with the token as written */
	public record NumberValue(String token) implements Value {
	}

	public record BooleanValue(boolean value) implements Value {
	}

	/** a class literal, like {@code Foo::class}, with the type as written */
	public record ClassValue(String typeName) implements Value {
	}

	/**
	 * An array, written as {@code [a, b]} or {@code arrayOf(a, b)}.
	 *
	 * @param ranges where the values are in the source, in the same order. Empty if that is not known.
	 */
	public record ArrayValue(List<Value> values, List<Range> ranges) implements Value {

		public ArrayValue(List<Value> values) {
			this(values, List.of());
		}

		/** @return null if the position of the value is not known */
		public Range rangeOf(int index) {
			return index < ranges.size() ? ranges.get(index) : null;
		}

		// arrays are the same if their values are, wherever they are written
		@Override
		public boolean equals(Object obj) {
			return obj instanceof ArrayValue other && values.equals(other.values);
		}

		@Override
		public int hashCode() {
			return values.hashCode();
		}
	}

	/** a reference to a constant or an enum entry, with the dotted name as written */
	public record ReferenceValue(String name) implements Value {
	}

	/**
	 * Values that are put together: {@code "a" + B}, or a string with templates, {@code "a${B}c"}.
	 * Every part is a value of its own.
	 */
	public record ConcatValue(List<Value> parts) implements Value {
	}

	/** an expression that is not understood (yet), with its source text */
	public record UnsupportedValue(String text) implements Value {
	}

	/**
	 * @param name the name of the argument, null if it is passed by position
	 * @param range the whole argument, including the name
	 * @param valueRange just the value
	 */
	public record Argument(String name, Value value, Range range, Range valueRange) {
	}

	/**
	 * An annotation use.
	 *
	 * @param typeName the annotation type as written
	 * @param useSiteTarget the use-site target ({@code field}, {@code get}, ...), null if there is none
	 * @param range the whole annotation, including the '@'
	 */
	public record AnnotationUse(String typeName, String useSiteTarget, List<Argument> arguments, Range range, Range typeNameRange) {
	}

	public record Parameter(String name, TypeRef type) {
	}

	/**
	 * A {@code const val}.
	 *
	 * @param value the value it is set to, an {@link UnsupportedValue} if it is no value that is understood
	 */
	public record Constant(String name, Value value) {
	}

	/**
	 * @param returnType null if the function has no explicit return type
	 */
	public record Function(String name, Range range, Range nameRange, List<AnnotationUse> annotations, List<Parameter> parameters,
			TypeRef returnType) {
	}

	public enum ClassKind {
		CLASS, INTERFACE, ANNOTATION, ENUM, OBJECT, COMPANION
	}

	/**
	 * A class, interface, object, ... with its members.
	 *
	 * @param name the name, for a companion object without a name that is {@code Companion}
	 * @param range the whole declaration, including annotations and the body
	 * @param nameRange where the name is - the whole declaration for a companion object without a name
	 * @param supertypes the supertypes in the order they are declared, classes and interfaces mixed
	 * @param constants the {@code const val}s declared in the body of the class
	 * @param nestedClasses the classes declared in the body of the class
	 */
	public record ClassDeclaration(ClassKind kind, String name, Range range, Range nameRange, List<AnnotationUse> annotations,
			List<TypeRef> supertypes, List<Function> functions, List<Constant> constants, List<ClassDeclaration> nestedClasses) {
	}

	/**
	 * @param name the imported name, for a star import the package or class the star is applied to
	 * @param alias the alias of the import, null if there is none
	 */
	public record Import(String name, String alias, boolean allUnder, Range range) {
	}

	/**
	 * A parsed Kotlin source file.
	 *
	 * @param packageName the package, empty for the default package
	 * @param packageRange the package directive, null if there is none
	 * @param packageNameRange the name in the package directive, null if there is none
	 * @param constants the {@code const val}s on the top level of the file
	 * @param length the length of the source text
	 */
	public record File(String packageName, Range packageRange, Range packageNameRange, List<Import> imports, List<ClassDeclaration> classes,
			List<Constant> constants, int length) {
	}

}
