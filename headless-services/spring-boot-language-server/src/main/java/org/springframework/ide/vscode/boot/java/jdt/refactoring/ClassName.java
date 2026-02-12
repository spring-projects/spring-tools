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
 * Represents a non-parameterized class or interface type.
 * <p>
 * A {@code ClassName} has:
 * <ul>
 *   <li>A simple name (e.g. {@code "Entry"})</li>
 *   <li>Either a package name (for top-level types) or a declaring class (for inner types)</li>
 * </ul>
 * <p>
 * Two constructor forms:
 * <ul>
 *   <li>{@link #ClassName(String, String)} — top-level class with explicit package</li>
 *   <li>{@link #ClassName(ClassName, String)} — inner class whose package is implied by the declaring class</li>
 * </ul>
 *
 * @author Alex Boyko
 */
class ClassName implements FullyQualifiedName {

	private final String packageName;
	private final String simpleName;
	private final ClassName declaringClass;

	/**
	 * Create a top-level class name with an explicit package.
	 *
	 * @param packageName the package name (e.g. {@code "java.util"}), empty string for default package
	 * @param simpleName  the simple class name (e.g. {@code "Map"})
	 */
	public ClassName(String packageName, String simpleName) {
		this.packageName = packageName != null ? packageName : "";
		this.simpleName = simpleName;
		this.declaringClass = null;
	}

	/**
	 * Create an inner class name whose package is implied by the declaring class.
	 *
	 * @param declaringClass the declaring (outer) class (must not be {@code null})
	 * @param simpleName     the simple class name (e.g. {@code "Entry"})
	 */
	public ClassName(ClassName declaringClass, String simpleName) {
		this.declaringClass = declaringClass;
		this.simpleName = simpleName;
		this.packageName = declaringClass.getPackageName();
	}

	/**
	 * Returns the package name (e.g. {@code "java.util"}).
	 * For inner classes, this is the package of the outermost declaring class.
	 */
	public String getPackageName() {
		return packageName;
	}

	@Override
	public String getSimpleName() {
		return simpleName;
	}

	/**
	 * Returns the declaring (outer) class, or {@code null} for top-level types.
	 */
	public ClassName getDeclaringClass() {
		return declaringClass;
	}

	/**
	 * Returns the type name to use in source code after imports have been added.
	 * <p>
	 * For top-level types, this is the same as {@link #getSimpleName()}.
	 * For inner types, this includes the declaring class chain
	 * (e.g. {@code "Map.Entry"}, {@code "Outer.Middle.Inner"}).
	 */
	public String getFieldTypeName() {
		if (declaringClass != null) {
			return declaringClass.getFieldTypeName() + "." + simpleName;
		}
		return simpleName;
	}

	@Override
	public String getFullyQualifiedName() {
		String fieldTypeName = getFieldTypeName();
		if (packageName.isEmpty()) {
			return fieldTypeName;
		}
		return packageName + "." + fieldTypeName;
	}

	@Override
	public List<ClassName> getAllClassNames() {
		return List.of(this);
	}

	@Override
	public Type toType(AST ast) {
		if (declaringClass != null) {
			return ast.newQualifiedType(declaringClass.toType(ast), ast.newSimpleName(simpleName));
		}
		return ast.newSimpleType(ast.newName(simpleName));
	}

	@Override
	public String toString() {
		return getFullyQualifiedName();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ClassName that = (ClassName) o;
		return getFullyQualifiedName().equals(that.getFullyQualifiedName());
	}

	@Override
	public int hashCode() {
		return getFullyQualifiedName().hashCode();
	}

}
