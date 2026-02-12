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
import java.util.Objects;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.WildcardType;

/**
 * Represents a wildcard type: {@code ?}, {@code ? extends Foo}, or {@code ? super Bar}.
 * <p>
 * Wildcards only appear as type arguments inside a {@link ParameterizedClassName}.
 *
 * @author Alex Boyko
 */
class WildcardName implements JavaType {

	private final JavaType bound;
	private final boolean upperBound;

	/**
	 * Create a new wildcard name.
	 *
	 * @param bound      the bound type, or {@code null} for an unbounded wildcard ({@code ?})
	 * @param upperBound {@code true} for {@code ? extends X}, {@code false} for {@code ? super X}.
	 *                   Ignored if {@code bound} is {@code null}.
	 */
	public WildcardName(JavaType bound, boolean upperBound) {
		this.bound = bound;
		this.upperBound = upperBound;
	}

	/**
	 * Returns the bound type, or {@code null} for an unbounded wildcard.
	 */
	public JavaType getBound() {
		return bound;
	}

	/**
	 * Returns {@code true} if this is an upper-bounded wildcard ({@code ? extends X}),
	 * {@code false} if lower-bounded ({@code ? super X}).
	 */
	public boolean isUpperBound() {
		return upperBound;
	}

	@Override
	public String getDisplayName() {
		if (bound == null) {
			return "?";
		}
		return "? " + (upperBound ? "extends" : "super") + " " + bound.getDisplayName();
	}

	@Override
	public List<ClassName> getAllClassNames() {
		if (bound != null) {
			return bound.getAllClassNames();
		}
		return Collections.emptyList();
	}

	@Override
	public Type toType(AST ast) {
		WildcardType wildcard = ast.newWildcardType();
		if (bound != null) {
			wildcard.setBound(bound.toType(ast), upperBound);
		}
		return wildcard;
	}

	@Override
	public String toString() {
		if (bound == null) {
			return "?";
		}
		return "? " + (upperBound ? "extends" : "super") + " " + bound;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		WildcardName that = (WildcardName) o;
		return upperBound == that.upperBound && Objects.equals(bound, that.bound);
	}

	@Override
	public int hashCode() {
		return Objects.hash(bound, upperBound);
	}

}
