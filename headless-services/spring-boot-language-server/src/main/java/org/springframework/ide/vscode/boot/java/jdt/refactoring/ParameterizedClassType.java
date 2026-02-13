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
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.Type;

/**
 * Represents a parameterized class type like {@code Map<String, List<Integer>>}.
 * <p>
 * Holds a {@link ClassType} (the type erasure) and a list of type arguments,
 * each of which is a {@link JavaType} (and can itself be a {@link ClassType},
 * {@link ParameterizedClassType}, or {@link WildcardType}).
 *
 * @author Alex Boyko
 */
class ParameterizedClassType implements FullyQualifiedType {

	private final ClassType erasure;
	private final List<JavaType> typeArguments;

	/**
	 * Create a new parameterized class name.
	 *
	 * @param erasure       the type erasure (e.g. {@code ClassName("java.util", "Map")})
	 * @param typeArguments the type arguments (must not be empty)
	 */
	public ParameterizedClassType(ClassType erasure, List<JavaType> typeArguments) {
		this.erasure = erasure;
		this.typeArguments = typeArguments != null ? Collections.unmodifiableList(typeArguments) : Collections.emptyList();
	}

	/**
	 * Returns the type erasure (the class name without type arguments).
	 */
	public ClassType getErasure() {
		return erasure;
	}

	/**
	 * Returns the type arguments.
	 */
	public List<JavaType> getTypeArguments() {
		return typeArguments;
	}

	@Override
	public String getSimpleName() {
		return erasure.getSimpleName();
	}

	@Override
	public String getDisplayName() {
		String base = erasure.getDisplayName();
		if (typeArguments.isEmpty()) {
			return base;
		}
		String args = typeArguments.stream()
				.map(JavaType::getDisplayName)
				.collect(Collectors.joining(", "));
		return base + "<" + args + ">";
	}

	@Override
	public String getFullyQualifiedName() {
		String base = erasure.getFullyQualifiedName();
		if (typeArguments.isEmpty()) {
			return base;
		}
		String args = typeArguments.stream()
				.map(Object::toString)
				.collect(Collectors.joining(", "));
		return base + "<" + args + ">";
	}

	@Override
	public List<ClassType> getAllClassNames() {
		List<ClassType> result = new ArrayList<>();
		// Add the erasure itself
		result.add(erasure);
		// Recurse into type arguments
		for (JavaType arg : typeArguments) {
			result.addAll(arg.getAllClassNames());
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Type toType(AST ast) {
		Type baseType = erasure.toType(ast);
		ParameterizedType paramType = ast.newParameterizedType(baseType);
		for (JavaType arg : typeArguments) {
			paramType.typeArguments().add(arg.toType(ast));
		}
		return paramType;
	}

	@Override
	public String toString() {
		return getFullyQualifiedName();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ParameterizedClassType that = (ParameterizedClassType) o;
		return erasure.equals(that.erasure) && typeArguments.equals(that.typeArguments);
	}

	@Override
	public int hashCode() {
		return Objects.hash(erasure, typeArguments);
	}

}
