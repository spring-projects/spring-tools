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

import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;

public class StereotypeClassElement extends AbstractSpringIndexElement implements StereotypeAnnotatedElement {
	
	private final String type;
	private final Set<String> supertypes;
	private List<String> annotationTypes;
	
	public StereotypeClassElement(String type, Set<String> supertypes, List<String> annotationTypes) {
		this.type = type;
		this.supertypes = supertypes;
		this.annotationTypes = annotationTypes;
	}
	
	public String getType() {
		return type;
	}

	public boolean doesImplement(String fqn) {
		if (type.equals(fqn)) {
			return true;
		}
		
		// check supertypes
		return supertypes.contains(fqn);
	}

	@Override
	public List<String> getAnnotationTypes() {
		return annotationTypes;
	}

}
