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

public class RequestMappingIndexElement extends AbstractSpringIndexElement implements SymbolElement {

	private final String path;
	private final String[] httpMethods;
	private final String[] contentTypes;
	private final String[] acceptTypes;
	private final String symbolLabel;
	private final Range range;
	
	public RequestMappingIndexElement(String path, String[] httpMethods, String[] contentTypes, String[] acceptTypes, Range range, String symbolLabel) {
		this.path = path;
		this.httpMethods = httpMethods;
		this.contentTypes = contentTypes;
		this.acceptTypes = acceptTypes;
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
