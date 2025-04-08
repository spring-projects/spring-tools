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
package org.springframework.ide.vscode.boot.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.springframework.ide.vscode.commons.protocol.spring.DocumentElement;
import org.springframework.ide.vscode.commons.protocol.spring.ProjectElement;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.protocol.spring.SymbolElement;

public class SpringIndexToSymbolsConverter {

	public static List<DocumentSymbol> createDocumentSymbols(List<SpringIndexElement> indexElements) {
		List<DocumentSymbol> result = new ArrayList<>();
		
		for (SpringIndexElement indexElement : indexElements) {
			result.addAll(createSymbol(indexElement));
		}
		
		return result;
	}
	
	public static List<WorkspaceSymbol> createWorkspaceSymbols(Collection<ProjectElement> projects,
			Predicate<DocumentElement> documentPredicate, Predicate<DocumentSymbol> symbolPredicate) {

		return projects.stream()
			.flatMap(project -> project.getChildren().stream())
			.filter(node -> node instanceof DocumentElement)
			.map(node -> (DocumentElement) node)
			.filter(documentPredicate)
			.flatMap(document -> createWorkspaceSymbols(document, symbolPredicate).stream())
			.toList();
	}
	
	private static List<DocumentSymbol> createSymbol(SpringIndexElement indexElement) {
		
		List<DocumentSymbol> subTreeSymbols = new ArrayList<>();
		List<SpringIndexElement> children = indexElement.getChildren();

		if (children != null && children.size() > 0) {
			for (SpringIndexElement child : children) {
				List<DocumentSymbol> childSymbols = createSymbol(child);
				if (childSymbols != null) {
					subTreeSymbols.addAll(childSymbols);
				}
			}
		}
		
		if (indexElement instanceof SymbolElement symbolElement) {
			DocumentSymbol documentSymbol = symbolElement.getDocumentSymbol();
			if (subTreeSymbols.size() > 0) {
				documentSymbol.setChildren(subTreeSymbols);
			}
			
			return List.of(documentSymbol);
		}
		else {
//			symbol = new DocumentSymbol(indexElement.toString(), SymbolKind.String,
//					new Range(new Position(), new Position()),
//					new Range(new Position(), new Position()));
			return subTreeSymbols;
		}
	}
	
	private static List<WorkspaceSymbol> createWorkspaceSymbols(DocumentElement document, Predicate<DocumentSymbol> symbolPredicate) {
		return SpringMetamodelIndex.getNodesOfType(SymbolElement.class, List.of(document)).stream()
			.map(symbolElement -> symbolElement.getDocumentSymbol())
			.filter(symbolPredicate)
			.map(documentSymbol -> createWorkspaceSymbol(documentSymbol, document.getDocURI()))
			.toList();
	}

	private static WorkspaceSymbol createWorkspaceSymbol(DocumentSymbol documentSymbol, String docURI) {
		WorkspaceSymbol workspaceSymbol = new WorkspaceSymbol();
		
		workspaceSymbol.setName(documentSymbol.getName());
		workspaceSymbol.setKind(documentSymbol.getKind());
		workspaceSymbol.setTags(documentSymbol.getTags());
		
		Location location = new Location(docURI, documentSymbol.getRange());
		workspaceSymbol.setLocation(Either.forLeft(location));

		return workspaceSymbol;
	}

}
