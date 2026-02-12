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

/**
 * A {@link JavaType} that represents a class or interface with a fully qualified name.
 * <p>
 * Implementations include:
 * <ul>
 *   <li>{@link ClassType} — a non-parameterized class/interface type</li>
 *   <li>{@link ParameterizedClassType} — a parameterized type with type arguments</li>
 * </ul>
 *
 * @author Alex Boyko
 */
interface FullyQualifiedType extends JavaType {

	/**
	 * Returns the full source-style type string using {@code .} for inner classes.
	 * <p>
	 * Examples:
	 * <ul>
	 *   <li>{@code "java.util.Map"}</li>
	 *   <li>{@code "java.util.Map.Entry"}</li>
	 *   <li>{@code "java.util.Map<java.lang.String, java.util.List<java.lang.Integer>>"}</li>
	 * </ul>
	 */
	String getFullyQualifiedName();

	/**
	 * Returns the simple name of the primary type.
	 * <p>
	 * For class types, this is the last segment (e.g. {@code "Map"}, {@code "Entry"}).
	 */
	String getSimpleName();

}
