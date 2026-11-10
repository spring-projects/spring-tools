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
 * An attribute of an annotation together with the value it is set to.
 * <p>
 * The value is, as for JDT's member value pair bindings: a {@link String}, a boxed primitive, a
 * {@link ResolvedType} for a class literal, a {@link ResolvedVariable} for an enum constant or a
 * constant that could not be evaluated, a {@link ResolvedAnnotation} for a nested annotation, or an
 * {@code Object[]} of those for an array. It is null if the value is not known.
 *
 * @author Martin Lippert
 */
public interface ResolvedMemberValuePair {

	String getName();

	Object getValue();

}
