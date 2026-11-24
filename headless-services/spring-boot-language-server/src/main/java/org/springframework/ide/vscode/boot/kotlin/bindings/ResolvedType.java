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

import java.util.List;

/**
 * A type, as the Kotlin type resolution resolved it: a class of a Kotlin source file, a class from
 * the classpath, a primitive, an array or a parameterized type. This is the model of the Kotlin
 * side; the indexers see it through the JDT type bindings that adapt it
 * ({@code org.springframework.ide.vscode.boot.kotlin.jdt}).
 * <p>
 * Two resolved types are equal if they stand for the same type.
 *
 * @author Martin Lippert
 */
public interface ResolvedType {

	/** the simple name, as {@code ITypeBinding.getName()} gives it */
	String getName();

	/** the qualified name, as {@code ITypeBinding.getQualifiedName()} gives it */
	String getQualifiedName();

	/** the binary name (nested types separated by '$'), or null if it is not available */
	String getBinaryName();

	/** the binding key, in the syntax that {@code JavaUtils.typeFqNameToBindingKey} produces for erased types */
	String getKey();

	/** the type without type arguments */
	ResolvedType getErasure();

	boolean isParameterizedType();

	/** the type arguments of a parameterized type, empty for all other types */
	default List<ResolvedType> getTypeArguments() {
		return List.of();
	}

	/** the type parameters of a generic type, empty for all other types (a parameterized type has type arguments instead) */
	default List<ResolvedType> getTypeParameters() {
		return List.of();
	}

	/** whether this is a type parameter of a generic type, as it is used in the supertypes and members of the type */
	default boolean isTypeVariable() {
		return false;
	}

	boolean isAnnotation();

	boolean isInterface();

	/** null for interfaces and for java.lang.Object */
	ResolvedType getSuperclass();

	/** the directly implemented or extended interfaces, never containing null */
	List<ResolvedType> getInterfaces();

	/** the annotations the type declaration itself carries */
	List<ResolvedAnnotation> getAnnotations();

}
