/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.stereotypes;

import java.util.Set;

import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;
import org.springframework.ide.vscode.commons.protocol.spring.CanonicalNameSets;

public abstract class AbstractStereotypeIndexElement extends AbstractSpringIndexElement implements StereotypeAnnotatedElement {

	private Set<String> annotationTypes;

	public AbstractStereotypeIndexElement(Set<String> annotationTypes) {
		this.annotationTypes = CanonicalNameSets.of(annotationTypes);
	}
	
	@Override
	public Set<String> getAnnotationTypes() {
		return annotationTypes;
	}
	
	@Override
	public boolean isAnnotatedWith(String annotationType) {
		return annotationTypes.contains(annotationType);
	}

}
