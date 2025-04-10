/*******************************************************************************
 * Copyright (c) 2018, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class DataRepositorySymbolProviderTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-data-symbols/").toURI());
		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testSimpleRepositorySymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/CustomerRepository.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = indexer.getSymbols(docUri);
        assertEquals(1, symbols.size());
        assertTrue(containsSymbol(symbols, "@+ 'customerRepository' (Customer) Repository<Customer,Long>", docUri, 6, 17, 6, 35));
    }

    @Test
    void testNoRepositorySymbolForNoRepositoryAnnotation() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/CustomerRepositoryParentInterface.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = indexer.getSymbols(docUri);
        assertEquals(2, symbols.size());

        assertTrue(containsSymbol(symbols, "@NoRepositoryBean", docUri, 8, 0, 8, 17));
        assertTrue(containsSymbol(symbols, "@Query(\"PARENT REPO INTERFACE QUERY STATEMENT\")", docUri, 11, 1, 11, 48));
    }

    @Test
    void testDocumentSymbolsForRepository() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/CustomerRepository.java").toUri().toString();
        List<? extends DocumentSymbol> symbols = indexer.getDocumentSymbolsFromMetamodelIndex(docUri);
        assertEquals(1, symbols.size());
        assertTrue(containsDocumentSymbol(symbols, "@+ 'customerRepository' (Customer) Repository<Customer,Long>", docUri, 6, 17, 6, 35));
        
        DocumentSymbol documentSymbol = symbols.get(0);
        List<DocumentSymbol> children = documentSymbol.getChildren();
        DocumentSymbol childSymbol = children.get(0);
        assertEquals("findByLastName(String) : List<Customer>", childSymbol.getName());
        
        assertEquals(1, children.size());
    }

    @Test
    void testNestedDocumentSymbolsForRepositoryWithQuery() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/CustomerRepositoryWithQuery.java").toUri().toString();
        List<? extends DocumentSymbol> symbols = indexer.getDocumentSymbolsFromMetamodelIndex(docUri);
        assertEquals(1, symbols.size());
        assertTrue(containsDocumentSymbol(symbols, "@+ 'customerRepositoryWithQuery' (Customer) Repository<Customer,Long>", docUri, 7, 17, 7, 44));
        
        DocumentSymbol documentSymbol = symbols.get(0);
        List<DocumentSymbol> children = documentSymbol.getChildren();
        DocumentSymbol queryMethodSymbol = children.get(0);
        assertEquals("findPetTypes() : List<Object>", queryMethodSymbol.getName());
        assertEquals(1, children.size());
        
        List<DocumentSymbol> queryChildren = queryMethodSymbol.getChildren();
        DocumentSymbol queryStringSymbol = queryChildren.get(0);
        assertEquals("SELECT ptype FROM PetType ptype ORDER BY ptype.name", queryStringSymbol.getName());
        assertEquals(SymbolKind.Constant, queryStringSymbol.getKind());
    }

	private boolean containsSymbol(List<? extends WorkspaceSymbol> symbols, String name, String uri, int startLine, int startCHaracter, int endLine, int endCharacter) {
		for (Iterator<? extends WorkspaceSymbol> iterator = symbols.iterator(); iterator.hasNext();) {
			WorkspaceSymbol symbol = iterator.next();

			if (symbol.getName().equals(name)
					&& symbol.getLocation().getLeft().getUri().equals(uri)
					&& symbol.getLocation().getLeft().getRange().getStart().getLine() == startLine
					&& symbol.getLocation().getLeft().getRange().getStart().getCharacter() == startCHaracter
					&& symbol.getLocation().getLeft().getRange().getEnd().getLine() == endLine
					&& symbol.getLocation().getLeft().getRange().getEnd().getCharacter() == endCharacter) {
				return true;
			}
 		}

		return false;
	}

	private boolean containsDocumentSymbol(List<? extends DocumentSymbol> symbols, String name, String uri, int startLine, int startCHaracter, int endLine, int endCharacter) {
		for (Iterator<? extends DocumentSymbol> iterator = symbols.iterator(); iterator.hasNext();) {
			DocumentSymbol symbol = iterator.next();

			if (symbol.getName().equals(name)
					&& symbol.getRange().getStart().getLine() == startLine
					&& symbol.getRange().getStart().getCharacter() == startCHaracter
					&& symbol.getRange().getEnd().getLine() == endLine
					&& symbol.getRange().getEnd().getCharacter() == endCharacter) {
				return true;
			}
 		}

		return false;
	}

}
