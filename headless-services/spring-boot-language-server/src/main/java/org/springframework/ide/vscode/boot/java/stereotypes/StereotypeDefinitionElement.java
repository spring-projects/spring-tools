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
package org.springframework.ide.vscode.boot.java.stereotypes;

import org.jmolecules.stereotype.catalog.StereotypeDefinition.Assignment.Type;
import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;

public class StereotypeDefinitionElement extends AbstractSpringIndexElement {

	private final String type;
	private final Type assignment;
	
	public StereotypeDefinitionElement(String type, Type assignment) {
		this.type = type;
		this.assignment = assignment;
	}

	public String getType() {
		return this.type;
	}

	public Type getAssignment() {
		return this.assignment;
	}
}
