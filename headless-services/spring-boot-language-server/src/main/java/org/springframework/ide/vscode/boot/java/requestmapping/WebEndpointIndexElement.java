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

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;
import org.springframework.ide.vscode.commons.protocol.spring.SymbolElement;

public class WebEndpointIndexElement extends AbstractSpringIndexElement implements SymbolElement {

	private final String path;
	private final String[] httpMethods;
	private final String[] contentTypes;
	private final String[] acceptTypes;
	private final String version;
	private final String symbolLabel;
	private final Range range;
	
	public WebEndpointIndexElement(String path, String[] httpMethods, String[] contentTypes, String[] acceptTypes, String version, Range range, String symbolLabel) {
		this.path = path;
		this.httpMethods = httpMethods;
		this.contentTypes = contentTypes;
		this.acceptTypes = acceptTypes;
		this.version = version;
		this.range = range;
		this.symbolLabel = symbolLabel;
	}
	
	public String getPath() {
		return path;
	}
	
	public String[] getHttpMethods() {
		return httpMethods;
	}
	
	public String[] getContentTypes() {
		return contentTypes;
	}
	
	public String[] getAcceptTypes() {
		return acceptTypes;
	}
	
	public String getVersion() {
		return version;
	}

	@Override
	public DocumentSymbol getDocumentSymbol() {
		DocumentSymbol symbol = new DocumentSymbol();

		symbol.setName(symbolLabel);
		symbol.setKind(SymbolKind.Method);
		symbol.setRange(range);
		symbol.setSelectionRange(range);
		
		return symbol;
	}

}
