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

import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;

public class StereotypePackageElement extends AbstractSpringIndexElement implements StereotypeAnnotatedElement {
	
	private final String packageName;
	private final List<String> annotationTypes;
	
	public StereotypePackageElement(String packageName, List<String> annotationTypes) {
		this.packageName = packageName;
		this.annotationTypes = annotationTypes;
	}
	
	public String getPackageName() {
		return packageName;
	}
	
	public List<String> getAnnotationTypes() {
		return annotationTypes;
	}

}