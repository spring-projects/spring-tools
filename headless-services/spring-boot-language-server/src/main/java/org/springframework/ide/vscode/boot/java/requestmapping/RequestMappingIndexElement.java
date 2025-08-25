/*******************************************************************************
 * Copyright (c) 2025 Broadcom
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

	public RequestMappingIndexElement(String path, String[] httpMethods, String[] contentTypes, String[] acceptTypes, Range range, String symbolLabel, String methodSignature) {
		super(path, httpMethods, contentTypes, acceptTypes, range, symbolLabel);
		this.methodSignature = methodSignature;
	}
	
	public String getMethodSignature() {
		return methodSignature;
	}

}
