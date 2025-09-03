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
package org.springframework.ide.vscode.boot.modulith;

import java.util.Collection;

public class NamedInterface {
	
	private static final String UNNAMED_NAME = "<<UNNAMED>>";

	private final String name;
	private final Collection<String> classes;
	
	public NamedInterface(String name, Collection<String> classes) {
		this.name = name;
		this.classes = classes;
	}
	
	public String getName() {
		return name;
	}

	public boolean isUnnamed() {
		return name.equals(UNNAMED_NAME);
	}

	public boolean isNamed() {
		return !isUnnamed();
	}

	public Collection<String> getClasses() {
		return classes;
	}

}
