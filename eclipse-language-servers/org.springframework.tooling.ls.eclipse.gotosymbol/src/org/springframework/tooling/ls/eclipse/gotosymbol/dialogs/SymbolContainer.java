/*******************************************************************************
 * Copyright (c) 2022 VMware Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Martin Lippert (VMware Inc.) - initial implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.gotosymbol.dialogs;

import java.util.List;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.WorkspaceSymbol;

public class SymbolContainer {
	
	private SymbolInformation symbolInformation;
	private DocumentSymbol documentSymbol;
	private WorkspaceSymbol workspaceSymbol;
	
	private List<SymbolContainer> children = List.of();
	
	public static SymbolContainer fromSymbolInformation(SymbolInformation symbolInformation) {
		return new SymbolContainer(symbolInformation);
	}
	
	public static SymbolContainer fromDocumentSymbol(DocumentSymbol documentSymbol) {
		return new SymbolContainer(documentSymbol);
	}
	
	public static SymbolContainer fromWorkspaceSymbol(WorkspaceSymbol workspaceSymbol) {
		return new SymbolContainer(workspaceSymbol);
	}
	
	private SymbolContainer(SymbolInformation symbolInformation) {
		this.symbolInformation = symbolInformation;
	}
	
	private SymbolContainer(DocumentSymbol documentSymbol) {
		this.documentSymbol = documentSymbol;
		if (documentSymbol.getChildren() == null) {
			this.children = List.of();
		} else {
			this.children = documentSymbol.getChildren().stream().map(SymbolContainer::new).toList();
		}
	}
	
	private SymbolContainer(WorkspaceSymbol workspaceSymbol) {
		this.workspaceSymbol = workspaceSymbol;
	}

	public boolean isSymbolInformation() {
		return symbolInformation != null;
	}
	
	public boolean isDocumentSymbol() {
		return documentSymbol != null;
	}
	
	public boolean isWorkspaceSymbol() {
		return workspaceSymbol != null;
	}
	
	public SymbolInformation getSymbolInformation() {
		return symbolInformation;
	}
	
	public DocumentSymbol getDocumentSymbol() {
		return documentSymbol;
	}
	
	public WorkspaceSymbol getWorkspaceSymbol() {
		return workspaceSymbol;
	}
	
	/**
	 * convenience method to get the name from the symbol, independent of which type of symbol it is
	 */
	public String getName() {
		
		if (isSymbolInformation()) {
			return symbolInformation.getName();
		}
		else if (isDocumentSymbol()) {
			return documentSymbol.getName();
		}
		else if (isWorkspaceSymbol()) {
			return workspaceSymbol.getName();
		}
		
		return null;
	}

	/**
	 * convenience method to get the underlying LSP object for the symbol
	 */
	public Object get() {
		if (symbolInformation != null) {
			return symbolInformation;
		}
		else if (documentSymbol != null) { 
			return documentSymbol;
		}
		else {
			return workspaceSymbol;
		}
	}
	
	public List<SymbolContainer> getChildren() {
		return children;
	}
	
}
