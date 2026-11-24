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

/**
 * A variable, as resolved for the source code that is being indexed: an enum constant or a
 * constant that is referred to by name, the field of a property, or a parameter.
 *
 * @author Martin Lippert
 */
public interface ResolvedVariable {

	String getName();

	/** the type the field is declared in, null if that is not known (and for parameters) */
	ResolvedType getDeclaringClass();

	/** the type of the variable, null if it is not known (for enum constants and constants that are referred to) */
	default ResolvedType getType() {
		return null;
	}

	default boolean isParameter() {
		return false;
	}

}
