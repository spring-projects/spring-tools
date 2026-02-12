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

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.Type;

/**
 * Represents a Java primitive type ({@code int}, {@code boolean}, {@code double}, etc.).
 * <p>
 * Primitives do not require imports and have no package or declaring class.
 *
 * @author Alex Boyko
 */
class PrimitiveTypeName implements JavaType {

	private static final Set<String> PRIMITIVE_KEYWORDS = Set.of(
			"boolean", "byte", "char", "short", "int", "long", "float", "double", "void");

	private final String keyword;

	/**
	 * Create a new primitive type name.
	 *
	 * @param keyword the primitive keyword (e.g. {@code "int"}, {@code "boolean"})
	 */
	public PrimitiveTypeName(String keyword) {
		this.keyword = keyword;
	}

	/**
	 * Returns the primitive keyword (e.g. {@code "int"}).
	 */
	public String getKeyword() {
		return keyword;
	}

	@Override
	public List<ClassName> getAllClassNames() {
		return Collections.emptyList();
	}

	@Override
	public Type toType(AST ast) {
		return ast.newPrimitiveType(PrimitiveType.toCode(keyword));
	}

	@Override
	public String toString() {
		return keyword;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		PrimitiveTypeName that = (PrimitiveTypeName) o;
		return keyword.equals(that.keyword);
	}

	@Override
	public int hashCode() {
		return keyword.hashCode();
	}

	/**
	 * Returns whether the given string is a Java primitive keyword.
	 */
	static boolean isPrimitive(String name) {
		return PRIMITIVE_KEYWORDS.contains(name);
	}

}
