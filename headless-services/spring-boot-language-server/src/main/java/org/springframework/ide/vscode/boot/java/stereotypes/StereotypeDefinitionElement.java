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

import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.catalog.StereotypeDefinition.Assignment.Type;
import org.jmolecules.stereotype.support.StringBasedStereotype;
import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;

public class StereotypeDefinitionElement extends AbstractSpringIndexElement {

	private final String type;
	private final int priority;
	private final String displayName;
	private final String[] groups;
	private final Type assignment;
	
	public StereotypeDefinitionElement(String type, int priority, String displayName, String[] groups, Type assignment) {
		this.type = type;
		this.priority = priority;
		this.displayName = displayName;
		this.groups = groups;
		this.assignment = assignment;
	}

	public String getType() {
		return this.type;
	}

	public Type getAssignment() {
		return this.assignment;
	}
	
	public Stereotype createStereotype() {
		var stereotype = StringBasedStereotype.of(type, priority);
		
		// add assigment
		stereotype = stereotype.withDisplayName(displayName);

		// add groups
		if (groups != null) {
			for (String group : groups) {
				stereotype = stereotype.addGroup(group);
			}
		}

		return stereotype;
	}

}
