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

import java.util.List;
import java.util.stream.Collectors;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.jandex.TypeVariable;

/**
 * JDT binding keys for classes, methods and fields read from a JAR with Jandex - what
 * {@code STS4LanguageClient.javaLocation} takes to have the IDE's Java tooling find the element, so
 * that a structure tree node from a JAR can open it (see {@code docs/structure-view-dependencies.md},
 * 5.8).
 *
 * <p>The keys are JDT's own, as {@code IBinding.getKey()} renders them for the same elements
 * compiled from source ({@code JarBindingKeysTest} compares the two), since those are what
 * {@code IJavaProject.findElement(String, WorkingCopyOwner)} is built to resolve:
 * <ul>
 * <li>a class: {@code Lp/A$Inner;} - the raw type, even for a generic one;</li>
 * <li>a method: the raw declaring class, the name, its own type
 * parameters, the generic parameter and return types, the thrown exceptions after {@code |}
 * ({@code Lp/A;.typed<T:Ljava/lang/Number;>(TT;)TT;|Ljava/io/IOException;}), and for the
 * constructor of an inner class the enclosing instance as its first parameter. One deliberate
 * difference: a constructor is named after its class ({@code Lp/A;.A(Ljava/lang/String;)V}), where
 * JDT's key has no name at all - which {@code findElement} cannot resolve against a class file,
 * whose constructors are named after the class;</li>
 * <li>a field: {@code Lp/A;.name)Ljava/lang/String;} - except that JDT renders a wildcard in a
 * field's type differently ({@code Ljava/util/Map;{1}-Ljava/lang/Integer;}), which does not matter
 * for the lookup: a field is found by its name.</li>
 * </ul>
 *
 * <p>Not {@code commons.jandex.BindingKeyUtils}: that one differs from JDT for arrays, type
 * variables and constructors, and is left alone since the standalone language server's own
 * Jandex-backed types depend on it as it is.
 *
 * @author Martin Lippert
 */
public class JarBindingKeys {

	public static String of(ClassInfo classInfo) {
		return classKey(classInfo.name());
	}

	public static String of(MethodInfo method) {
		StringBuilder key = new StringBuilder(classKey(method.declaringClass().name()));
		key.append('.');
		key.append(JarStereotypeScanner.sourceName(method));

		List<TypeVariable> typeParameters = method.typeParameters();
		if (!typeParameters.isEmpty()) {
			key.append('<');
			for (TypeVariable typeParameter : typeParameters) {
				key.append(typeParameter.identifier()).append(':');
				if (typeParameter.hasImplicitObjectBound()) {
					// an interface as the only bound: the empty class bound before it, as in a signature
					key.append(':');
				}
				key.append(typeParameter.bounds().stream().map(JarBindingKeys::typeKey).collect(Collectors.joining(":")));
			}
			key.append('>');
		}

		key.append('(');
		if (method.isConstructor() && takesEnclosingInstance(method)) {
			key.append(classKey(method.declaringClass().enclosingClass()));
		}
		method.parameterTypes().forEach(parameter -> key.append(typeKey(parameter)));
		key.append(')');
		key.append(typeKey(method.returnType()));

		method.exceptions().forEach(exception -> key.append('|').append(typeKey(exception)));

		return key.toString();
	}

	public static String of(FieldInfo field) {
		return classKey(field.declaringClass().name()) + "." + field.name() + ")" + typeKey(field.type());
	}

	/**
	 * Whether the constructor takes the enclosing instance - an inner class's does, and JDT's key
	 * lists it, while Jandex's parameter types leave it out. Only the descriptor tells: javac does
	 * not put a nested class's {@code static} into its class file access flags.
	 */
	private static boolean takesEnclosingInstance(MethodInfo constructor) {
		DotName enclosing = constructor.declaringClass().enclosingClass();
		return enclosing != null && constructor.descriptorParametersCount() == constructor.parametersCount() + 1
				&& constructor.descriptorParameterTypes().get(0).name().equals(enclosing);
	}

	private static String classKey(DotName name) {
		return "L" + name.toString().replace('.', '/') + ";";
	}

	private static String typeKey(Type type) {
		switch (type.kind()) {
		case CLASS:
			return classKey(type.name());
		case PARAMETERIZED_TYPE:
			// the erasure's binary name, as for a class - a parameterized owner type
			// (Outer<X>.Inner<Y>) is rendered without its own arguments, which JDT's lookup,
			// matching parameters by their erasure, does not need
			String arguments = type.asParameterizedType().arguments().stream().map(JarBindingKeys::typeKey).collect(Collectors.joining());
			return "L" + type.name().toString().replace('.', '/') + "<" + arguments + ">;";
		case ARRAY:
			return "[".repeat(type.asArrayType().deepDimensions()) + typeKey(type.asArrayType().elementType());
		case PRIMITIVE:
			return JdtStyleTypeNames.binaryName(type);
		case VOID:
			return "V";
		case TYPE_VARIABLE:
			return "T" + type.asTypeVariable().identifier() + ";";
		case TYPE_VARIABLE_REFERENCE:
			return "T" + type.asTypeVariableReference().identifier() + ";";
		case UNRESOLVED_TYPE_VARIABLE:
			return "T" + type.asUnresolvedTypeVariable().identifier() + ";";
		case WILDCARD_TYPE:
			if (type.asWildcardType().superBound() != null) {
				return "-" + typeKey(type.asWildcardType().superBound());
			}
			Type extendsBound = type.asWildcardType().extendsBound();
			return extendsBound == null || extendsBound.name().equals(DotName.OBJECT_NAME) ? "*" : "+" + typeKey(extendsBound);
		default:
			return classKey(type.name());
		}
	}

}
