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
package org.springframework.ide.vscode.boot.java.beans;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;
import org.springframework.ide.vscode.commons.protocol.spring.SymbolElement;

/**
 * @author Martin Lippert
 */
public class ConfigPropertyIndexElement extends AbstractSpringIndexElement implements SymbolElement {
	
	private final String name;
	private final String type;
	private final Range range;
	private final String contentHash;

	public ConfigPropertyIndexElement(String name, String type, Range range, String contentHash) {
		this.name = name;
		this.type = type;
		this.range = range;
		this.contentHash = contentHash;
	}

	/**
	 * Hashed over the whole field or record component declaration, so that a changed initializer
	 * or annotation shows up even when name and type stay the same.
	 */
	@Override
	public String getContentHash() {
		return contentHash;
	}


	public String getName() {
		return name;
	}
	
	public String getType() {
		return type;
	}
	
	public Range getRange() {
		return range;
	}

	@Override
	public DocumentSymbol getDocumentSymbol() {
		DocumentSymbol symbol = new DocumentSymbol();
		
		symbol.setName(name + " (" + getShortTypeName() + ")");
		symbol.setKind(SymbolKind.Property);
		symbol.setRange(range);
		symbol.setSelectionRange(range);
		
		return symbol;
	}

	private String getShortTypeName() {
		if (type != null) {
			int i = type.lastIndexOf(".");
			if (i >= 0) {
				return type.substring(i + 1);
			}
			else {
				return type;
			}
		}
		
		return "";
	}

}
