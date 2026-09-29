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
package org.springframework.ide.vscode.boot.java.stereotypes;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.jandex.DotName;
import org.jboss.jandex.Type;
import org.jboss.jandex.WildcardType;

/**
 * Renders Jandex {@link Type}s the way JDT's {@code ITypeBinding} renders the same types -
 * {@code getName()}, {@code getQualifiedName()} and {@code getBinaryName()} - because the labels
 * of structure tree nodes are built from those on the AST side, and a JAR-scanned node has to read
 * the same. The expected strings in {@code JdtStyleTypeNamesTest} were taken from JDT itself.
 *
 * <p>A {@code $} in a class name is taken to separate a nested class from its enclosing class, as
 * it does for every class javac produces from source.
 *
 * @author Martin Lippert
 */
public class JdtStyleTypeNames {

	private static final Map<String, String> PRIMITIVE_DESCRIPTORS = Map.of(
			"boolean", "Z", "byte", "B", "char", "C", "short", "S", "int", "I", "long", "J", "float", "F", "double", "D");

	/**
	 * {@code ITypeBinding.getName()}: simple names throughout - {@code Map<String,Integer>},
	 * {@code List<? extends Number>}, {@code String[][]}, {@code Inner} for a nested class.
	 */
	public static String name(Type type) {
		return switch (type.kind()) {
			case CLASS -> simpleName(type.name());
			case PARAMETERIZED_TYPE -> simpleName(type.name()) + arguments(type, JdtStyleTypeNames::name);
			case ARRAY -> name(type.asArrayType().elementType()) + "[]".repeat(type.asArrayType().deepDimensions());
			case WILDCARD_TYPE -> wildcard(type.asWildcardType(), JdtStyleTypeNames::name);
			case TYPE_VARIABLE -> type.asTypeVariable().identifier();
			case UNRESOLVED_TYPE_VARIABLE -> type.asUnresolvedTypeVariable().identifier();
			case TYPE_VARIABLE_REFERENCE -> type.asTypeVariableReference().identifier();
			default -> type.name().toString(); // primitives, void
		};
	}

	/**
	 * {@code ITypeBinding.getQualifiedName()}: {@code java.util.Map<java.lang.String,java.lang.Integer>},
	 * {@code p.A.Inner} (dots, not {@code $}, for a nested class), {@code java.lang.String[][]}.
	 */
	public static String qualifiedName(Type type) {
		return switch (type.kind()) {
			case CLASS -> qualifiedName(type.name());
			case PARAMETERIZED_TYPE -> qualifiedName(type.name()) + arguments(type, JdtStyleTypeNames::qualifiedName);
			case ARRAY -> qualifiedName(type.asArrayType().elementType()) + "[]".repeat(type.asArrayType().deepDimensions());
			case WILDCARD_TYPE -> wildcard(type.asWildcardType(), JdtStyleTypeNames::qualifiedName);
			default -> name(type);
		};
	}

	/**
	 * {@code ITypeBinding.getBinaryName()}: the erasure, {@code p.A$Inner} for a nested class, and
	 * descriptors for primitives and arrays ({@code I}, {@code [I}, {@code [[Ljava.lang.String;}).
	 * JDT renders a type variable's binary name as the declaring method's descriptor plus the
	 * variable; that is not reproduced - a type variable renders as its erasure here, which only
	 * matters for method signatures, and those are only ever compared among JAR-scanned elements.
	 */
	public static String binaryName(Type type) {
		return switch (type.kind()) {
			case CLASS, PARAMETERIZED_TYPE -> type.name().toString();
			case PRIMITIVE -> PRIMITIVE_DESCRIPTORS.get(type.name().toString());
			case VOID -> "V";
			case ARRAY -> "[".repeat(type.asArrayType().deepDimensions()) + descriptor(type.asArrayType().elementType());
			case TYPE_VARIABLE -> type.asTypeVariable().bounds().isEmpty() ? "java.lang.Object" : binaryName(type.asTypeVariable().bounds().get(0));
			default -> "java.lang.Object";
		};
	}

	/**
	 * A class's simple name, as JDT's {@code getName()} gives it for a class binding: no package,
	 * and only the innermost name of a nested class.
	 */
	public static String simpleName(DotName name) {
		String withoutPackage = name.withoutPackagePrefix();
		return withoutPackage.substring(withoutPackage.lastIndexOf('$') + 1);
	}

	/**
	 * A class's name as JDT's {@code getQualifiedName()} gives it for a class binding: dots, also
	 * between a nested class and its enclosing one.
	 */
	public static String qualifiedName(DotName name) {
		return name.toString().replace('$', '.');
	}

	private static String descriptor(Type element) {
		return element.kind() == Type.Kind.PRIMITIVE ? binaryName(element) : "L" + binaryName(element) + ";";
	}

	private static String arguments(Type type, Function<Type, String> render) {
		return type.asParameterizedType().arguments().stream().map(render).collect(Collectors.joining(",", "<", ">"));
	}

	private static String wildcard(WildcardType wildcard, Function<Type, String> render) {
		if (wildcard.superBound() != null) {
			return "? super " + render.apply(wildcard.superBound());
		}
		Type upper = wildcard.extendsBound();
		return upper == null || DotName.OBJECT_NAME.equals(upper.name()) ? "?" : "? extends " + render.apply(upper);
	}

}
