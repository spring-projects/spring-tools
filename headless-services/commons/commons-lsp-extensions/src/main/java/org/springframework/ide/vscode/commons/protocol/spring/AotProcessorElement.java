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
package org.springframework.ide.vscode.commons.protocol.spring;

public class AotProcessorElement extends AbstractSpringIndexElement {

	private final String type;
	private final String docUri;
	
	public AotProcessorElement(String type, String docUri) {
		this.type = type;
		this.docUri = docUri;
	}
	
	public String getType() {
		return type;
	}
	
	public String getDocUri() {
		return docUri;
	}

}
