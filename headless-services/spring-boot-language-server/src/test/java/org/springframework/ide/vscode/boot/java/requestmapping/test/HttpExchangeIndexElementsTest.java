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
package org.springframework.ide.vscode.boot.java.requestmapping.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.requestmapping.HttpExchangeIndexElement;
import org.springframework.ide.vscode.boot.java.utils.test.SpringIndexerTest;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.DocumentElement;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class HttpExchangeIndexElementsTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-httpexchange-indexing/").toURI());
		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testSimpleHttpExchangeSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/com/example/httpexchange/demo/HttpExchangeExample.java").toUri().toString();
        
        List<? extends WorkspaceSymbol> symbols = indexer.getSymbols(docUri);
        assertEquals(1, symbols.size());
        assertTrue(SpringIndexerTest.containsSymbol(symbols, "@/stores -- GET", docUri, 8, 1, 8, 24));
    }

    @Test
    void testSimpleHttpExchangeIndexElements() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/com/example/httpexchange/demo/HttpExchangeExample.java").toUri().toString();

        Bean[] beans = springIndex.getBeansOfDocument(docUri);
        assertEquals(0, beans.length);

        DocumentElement document = springIndex.getDocument(docUri);
        List<HttpExchangeIndexElement> mappingElements = SpringMetamodelIndex.getNodesOfType(HttpExchangeIndexElement.class, List.of(document));

        assertEquals(1, mappingElements.size());
        HttpExchangeIndexElement mappingElement = mappingElements.get(0);

        assertEquals("/stores", mappingElement.getPath());
        assertArrayEquals(new String[] {"GET"}, mappingElement.getHttpMethods());

        assertEquals("@/stores -- GET", mappingElement.getDocumentSymbol().getName());
    }

    @Test
    void testeHttpExchangeSymbolWithClassLevelAnnotation() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/com/example/httpexchange/demo/HttpExchangeExampleWithClassLevelAnnotation.java").toUri().toString();
        
        List<? extends WorkspaceSymbol> symbols = indexer.getSymbols(docUri);
        assertEquals(1, symbols.size());
        assertTrue(SpringIndexerTest.containsSymbol(symbols, "@/stores/all -- GET", docUri, 10, 1, 10, 21));
    }

    @Test
    void testHttpExchangeIndexElementsWithClassLevelAnnotation() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/com/example/httpexchange/demo/HttpExchangeExampleWithClassLevelAnnotation.java").toUri().toString();

        Bean[] beans = springIndex.getBeansOfDocument(docUri);
        assertEquals(0, beans.length);

        DocumentElement document = springIndex.getDocument(docUri);
        List<HttpExchangeIndexElement> mappingElements = SpringMetamodelIndex.getNodesOfType(HttpExchangeIndexElement.class, List.of(document));

        assertEquals(1, mappingElements.size());
        HttpExchangeIndexElement mappingElement = mappingElements.get(0);

        assertEquals("/stores/all", mappingElement.getPath());
        assertArrayEquals(new String[] {"GET"}, mappingElement.getHttpMethods());

        assertEquals("@/stores/all -- GET", mappingElement.getDocumentSymbol().getName());
    }
}
