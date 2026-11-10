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
 * A method (or constructor), as resolved for the source code that is being indexed.
 *
 * @author Martin Lippert
 */
public interface ResolvedMethod {

	String getName();

	ResolvedType getDeclaringClass();

	List<ResolvedType> getParameterTypes();

	ResolvedType getReturnType();

}
