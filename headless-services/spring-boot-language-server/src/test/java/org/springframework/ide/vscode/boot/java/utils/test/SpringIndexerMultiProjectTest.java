/*******************************************************************************
 * Copyright (c) 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils.test;

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
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.java.utils.SymbolIndexConfig;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.UriUtil;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class SpringIndexerMultiProjectTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private JavaProjectFinder projectFinder;

	private String projectUri1;
	private String projectUri2;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		indexer.configureIndexer(SymbolIndexConfig.builder().scanXml(false).build());

		projectUri1 = UriUtil.toUri(new File(ProjectsHarness.class.getResource("/test-projects/test-annotation-indexing-large-multiproject-1/").toURI())).toString();
		projectFinder.find(new TextDocumentIdentifier(projectUri1)).get();

		projectUri2 = UriUtil.toUri(new File(ProjectsHarness.class.getResource("/test-projects/test-annotation-indexing-large-multiproject-2").toURI())).toString();
		projectFinder.find(new TextDocumentIdentifier(projectUri2)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testQueryingAllSymbols() throws Exception {
        List<? extends WorkspaceSymbol> symbols = indexer.getAllSymbols("");
        assertEquals(220, symbols.size());
    }

    @Test
    void testQueryingAllSymbolsWithNoLimit() throws Exception {
        List<? extends WorkspaceSymbol> symbols = indexer.getAllSymbols("*");
        assertEquals(220, symbols.size());

        int count1 = 0;
        int count2 = 0;

        for (WorkspaceSymbol symbol : symbols) {
            if (symbol.getLocation().getLeft().getUri().startsWith(projectUri1)) {
                count1++;
            } else if (symbol.getLocation().getLeft().getUri().startsWith(projectUri2)) {
                count2++;
            }
        }

        assertEquals(110, count1);
        assertEquals(110, count2);
    }

    @Test
    void testQueryingSymbolsForSpecificProject() throws Exception {
        List<? extends WorkspaceSymbol> symbols = indexer.getAllSymbols("locationPrefix:" + projectUri2);
        assertEquals(110, symbols.size());

        for (WorkspaceSymbol symbol : symbols) {
            assertTrue(symbol.getLocation().getLeft().getUri().startsWith(projectUri2));
        }
    }

    @Test
    void testQueryingSymbolsForSpecificProjectWithNoLimit() throws Exception {
        List<? extends WorkspaceSymbol> symbols = indexer.getAllSymbols("locationPrefix:" + projectUri2 + "?*");
        assertEquals(110, symbols.size());

        for (WorkspaceSymbol symbol : symbols) {
            assertTrue(symbol.getLocation().getLeft().getUri().startsWith(projectUri2));
        }
    }

    @Test
    void testQueryingSymbolsForSpecificProjectWithQuery() throws Exception {
        List<? extends WorkspaceSymbol> symbols = indexer.getAllSymbols("locationPrefix:" + projectUri2 + "?seventhWowSuperBean");
        assertEquals(10, symbols.size());

        for (WorkspaceSymbol symbol : symbols) {
            assertTrue(symbol.getLocation().getLeft().getUri().startsWith(projectUri2));
        }
    }

}
