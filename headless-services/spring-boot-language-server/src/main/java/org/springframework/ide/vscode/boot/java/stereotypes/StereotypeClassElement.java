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

import java.util.List;
import java.util.Set;

import org.eclipse.lsp4j.Location;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;

public class StereotypeClassElement extends AbstractStereotypeIndexElement {
	
	private final String type;
	private final Location location;
	private final Set<String> supertypes;
	
	public StereotypeClassElement(String type, Location location, Set<String> supertypes, Set<String> annotationTypes) {
		super(annotationTypes);
		this.type = type;
		this.location = location;
		this.supertypes = supertypes;
	}
	
	public String getType() {
		return type;
	}
	
	public Location getLocation() {
		return location;
	}

	public boolean doesImplement(String fqn) {
		if (type.equals(fqn)) {
			return true;
		}
		
		// check supertypes
		return supertypes.contains(fqn);
	}

	public List<StereotypeMethodElement> getMethods() {
		return SpringMetamodelIndex.getNodesOfType(StereotypeMethodElement.class, List.of(this));
	}

}
