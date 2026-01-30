/*******************************************************************************
 * Copyright (c) 2018, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.TextDocumentInfo;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@SuppressWarnings("deprecation")
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class WebFluxCodeLensProviderTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-webflux-project/").toURI());
		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testRoutesCodeLensesSimpleCase() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/QuoteHandler.java").toUri().toString();
        TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
        TextDocumentInfo openedDoc = harness.openDocument(doc);

        List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);

//        assertEquals(4, codeLenses.size());

        assertTrue(containsCodeLens(codeLenses, "GET /hello - Accept: text/plain", 25, 29, 25, 34));
        assertTrue(containsCodeLens(codeLenses, "POST /echo - Accept: text/plain - Content-Type: text/plain", 30, 29, 30, 33));
        assertTrue(containsCodeLens(codeLenses, "GET /quotes - Accept: application/x-ndjson", 35, 29, 35, 41));
        assertTrue(containsCodeLens(codeLenses, "GET /quotes - Accept: application/json", 41, 29, 41, 40));
    }

    @Test
    void testRoutesCodeLensesNestedRoutes1() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/PersonHandler1.java").toUri().toString();
        TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
        TextDocumentInfo openedDoc = harness.openDocument(doc);

        List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);

        assertEquals(3, codeLenses.size());

        assertTrue(containsCodeLens(codeLenses, "GET /person/{id} - Accept: application/json", 9, 29, 9, 38));
        assertTrue(containsCodeLens(codeLenses, "POST /person/ - Content-Type: application/json", 13, 29, 13, 41));
        assertTrue(containsCodeLens(codeLenses, "GET /person - Accept: application/json", 17, 29, 17, 39));
    }

    @Test
    void testRoutesCodeLensesNestedRoutes2() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/PersonHandler2.java").toUri().toString();
        TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
        TextDocumentInfo openedDoc = harness.openDocument(doc);

        List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);

        assertEquals(3, codeLenses.size());

        assertTrue(containsCodeLens(codeLenses, "GET /person/{id} - Accept: application/json", 9, 29, 9, 38));
        assertTrue(containsCodeLens(codeLenses, "POST / - Accept: application/json - Content-Type: application/pdf,application/json", 13, 29, 13, 41));
        assertTrue(containsCodeLens(codeLenses, "HEAD,GET /person - Accept: application/json,text/plain", 17, 29, 17, 39));
    }

    @Test
    void testRoutesCodeLensesNestedRoutes3() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/PersonHandler3.java").toUri().toString();
        TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
        TextDocumentInfo openedDoc = harness.openDocument(doc);

        List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);

        assertEquals(6, codeLenses.size());
        /*
            assertTrue(containsCodeLens(codeLenses, "GET /person/{id} - Accept: application/json", 9, 29, 9, 38));
            assertTrue(containsCodeLens(codeLenses, "POST / - Accept: application/json - Content-Type: application/json, application/pdf", 13, 29, 13, 41));
            assertTrue(containsCodeLens(codeLenses, "GET, HEAD /person - Accept: text/plain, application/json", 17, 29, 17, 39));
    */
    }

	private boolean containsCodeLens(List<? extends CodeLens> codeLenses, String commandTitle, int startLine, int startPosition, int endLine, int endPosition) {
		for (CodeLens codeLens : codeLenses) {
			Command command = codeLens.getCommand();
			Range range = codeLens.getRange();
			if (command.getTitle().equals(commandTitle)
					&& range.getStart().getLine() == startLine
					&& range.getStart().getCharacter() == startPosition
					&& range.getEnd().getLine() == endLine
					&& range.getEnd().getCharacter() == endPosition) {
				return true;
			}
		}

		return false;
	}

}
