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
package org.springframework.ide.vscode.boot.index.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class SpringMetamodelIndexerFactoriesTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-indexing/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

	@Test
	void testSymbolForAotProcessorFromFactoriesFile() {
		String docUri = directory.toPath().resolve("src/main/resources/META-INF/spring/aot.factories").toUri().toString();

		List<? extends WorkspaceSymbol> symbols = indexer.getWorkspaceSymbolsFromSymbolIndex(docUri);

		assertEquals(1, symbols.size());
		WorkspaceSymbol symbol = symbols.get(0);
		
		assertEquals("@+ 'registeredViaFactoriesBeanRegistrationAotProcessor' (aot.factories) org.test.aot.RegisteredViaFactoriesBeanRegistrationAotProcessor", symbol.getName());
		assertEquals(docUri, symbol.getLocation().getLeft().getUri());
	}
	
	@Test
	void testIndexElementsForAotProcessorFromFactoriesFile() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "registeredViaFactoriesBeanRegistrationAotProcessor");

		assertEquals(1, beans.length);
		assertEquals("registeredViaFactoriesBeanRegistrationAotProcessor", beans[0].getName());
		assertEquals("org.test.aot.RegisteredViaFactoriesBeanRegistrationAotProcessor", beans[0].getType());
		
		assertFalse(beans[0].isConfiguration());
	}
	
}
