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

import org.eclipse.lsp4j.Location;
import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;

public class StereotypeMethodElement extends AbstractSpringIndexElement implements StereotypeAnnotatedElement {
	
	private final String methodName;
	private final String methodLabel;
	private final String methodSignature;
	
	private final Location location;
	private List<String> annotationTypes;
	
	public StereotypeMethodElement(String methodName, String methodLabel, String methodSignature, Location location, List<String> annotationTypes) {
		this.methodName = methodName;
		this.methodLabel = methodLabel;
		this.methodSignature = methodSignature;
		this.location = location;
		this.annotationTypes = annotationTypes;
	}
	
	public String getMethodName() {
		return methodName;
	}
	
	public String getMethodLabel() {
		return methodLabel;
	}
	
	public String getMethodSignature() {
		return methodSignature;
	}

	public Location getLocation() {
		return location;
	}
	
	public List<String> getAnnotationTypes() {
		return annotationTypes;
	}

}
