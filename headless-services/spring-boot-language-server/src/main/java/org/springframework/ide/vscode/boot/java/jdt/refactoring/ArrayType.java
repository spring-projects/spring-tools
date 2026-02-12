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
import java.util.Objects;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Type;

/**
 * Represents a Java array type (e.g. {@code String[]}, {@code int[][]}).
 * <p>
 * Wraps a component {@link JavaType} (which can be any type: class, primitive,
 * parameterized, etc.) and a dimension count.
 *
 * @author Alex Boyko
 */
class ArrayType implements JavaType {

	private final JavaType componentType;
	private final int dimensions;

	/**
	 * Create a new array type name.
	 *
	 * @param componentType the component (element) type
	 * @param dimensions    the number of array dimensions (must be &gt;= 1)
	 */
	public ArrayType(JavaType componentType, int dimensions) {
		this.componentType = componentType;
		this.dimensions = dimensions;
	}

	/**
	 * Returns the component (element) type.
	 */
	public JavaType getComponentType() {
		return componentType;
	}

	/**
	 * Returns the number of array dimensions.
	 */
	public int getDimensions() {
		return dimensions;
	}

	@Override
	public String getDisplayName() {
		return componentType.getDisplayName() + "[]".repeat(dimensions);
	}

	@Override
	public List<ClassType> getAllClassNames() {
		return componentType.getAllClassNames();
	}

	@Override
	public Type toType(AST ast) {
		return ast.newArrayType(componentType.toType(ast), dimensions);
	}

	@Override
	public String toString() {
		return componentType + "[]".repeat(dimensions);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ArrayType that = (ArrayType) o;
		return dimensions == that.dimensions && componentType.equals(that.componentType);
	}

	@Override
	public int hashCode() {
		return Objects.hash(componentType, dimensions);
	}

}
