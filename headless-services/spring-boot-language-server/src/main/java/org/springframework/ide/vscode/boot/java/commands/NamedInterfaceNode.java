/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.commands;

import org.springframework.ide.vscode.boot.modulith.NamedInterface;

public class NamedInterfaceNode {

	public static final NamedInterfaceNode INTERNAL = new NamedInterfaceNode(null);
	
	private final NamedInterface namedInterface;

	public NamedInterfaceNode(NamedInterface namedInterface) {
		this.namedInterface = namedInterface;
	}

	@Override
	public String toString() {
		if (namedInterface == null) {
			return "Internal";
		}
		return namedInterface.isUnnamed() ? "API" : namedInterface.getName();
	}

}
