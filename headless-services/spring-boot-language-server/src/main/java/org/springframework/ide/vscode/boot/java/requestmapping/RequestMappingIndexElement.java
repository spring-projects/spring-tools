/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping;

import org.eclipse.lsp4j.Range;

public class RequestMappingIndexElement extends WebEndpointIndexElement {
	
	private String methodSignature;
	private final String contentHash;

	public RequestMappingIndexElement(String path, String[] httpMethods, String[] contentTypes, String[] acceptTypes, String version, Range range, String symbolLabel, String methodSignature, String contentHash) {
		super(path, httpMethods, contentTypes, acceptTypes, version, range, symbolLabel);
		this.methodSignature = methodSignature;
		this.contentHash = contentHash;
	}

	public String getMethodSignature() {
		return methodSignature;
	}

	/**
	 * Hashed over the whole mapping method, not just the annotation that this element's range
	 * points at, so that changes to the method body show up as changes of the mapping.
	 */
	@Override
	public String getContentHash() {
		return contentHash;
	}

}
