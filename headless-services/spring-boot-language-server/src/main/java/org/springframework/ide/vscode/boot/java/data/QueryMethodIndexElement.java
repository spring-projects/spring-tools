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
package org.springframework.ide.vscode.boot.java.data;

import java.util.List;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;
import org.springframework.ide.vscode.commons.protocol.spring.SymbolElement;

public class QueryMethodIndexElement extends AbstractSpringIndexElement implements SymbolElement {

	private final String methodName;
	private final String queryString;
	private final Range range;
	private final String contentHash;

	public QueryMethodIndexElement(String methodName, String queryString, Range range, String contentHash) {
		this.methodName = methodName;
		this.queryString = queryString;
		this.range = range;
		this.contentHash = contentHash;
	}

	public String getMethodName() {
		return methodName;
	}

	public String getQueryString() {
		return queryString;
	}

	/**
	 * Hashed over the whole query method declaration, so that a changed query annotation or
	 * signature detail shows up even when the method name stays the same.
	 */
	@Override
	public String getContentHash() {
		return contentHash;
	}
	
	@Override
	public DocumentSymbol getDocumentSymbol() {
		DocumentSymbol symbol = new DocumentSymbol();

		symbol.setName(methodName);
		symbol.setKind(SymbolKind.Method);
		symbol.setRange(range);
		symbol.setSelectionRange(range);
		
		if (queryString != null) {
			DocumentSymbol querySymbol = new DocumentSymbol();
			querySymbol.setName(queryString);
			querySymbol.setKind(SymbolKind.Constant);
			querySymbol.setRange(range);
			querySymbol.setSelectionRange(range);
			
			symbol.setChildren(List.of(querySymbol));
		}
		
		return symbol;
	}

}
