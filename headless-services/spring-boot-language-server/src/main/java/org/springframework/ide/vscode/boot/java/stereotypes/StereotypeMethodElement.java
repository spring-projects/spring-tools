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

import org.eclipse.lsp4j.Location;

public class StereotypeMethodElement extends AbstractStereotypeIndexElement {
	
	private final String methodName;
	private final String methodLabel;
	private final String methodSignature;
	
	private final Location location;
	private final String contentHash;

	public StereotypeMethodElement(String methodName, String methodLabel, String methodSignature, Location location, Set<String> annotationTypes, String contentHash) {
		super(annotationTypes);
		this.methodName = methodName;
		this.methodLabel = methodLabel;
		this.methodSignature = methodSignature;
		this.location = location;
		this.contentHash = contentHash;
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

	/**
	 * A hash of the source text of the whole method declaration, so that changes to the method that
	 * leave its label unchanged (an edited body, most notably) can still be detected. May be null
	 * for elements restored from an older index cache.
	 */
	public String getContentHash() {
		return contentHash;
	}

}
