/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.handlers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.java.handlers.CopilotCodeLensProvider;
import org.springframework.ide.vscode.boot.java.handlers.QueryType;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.TextDocumentInfo;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.JsonPrimitive;

/**
 * Tests for RouterFunctionCodeLensProvider
 * 
 * @author Martin Lippert
 */
@SuppressWarnings("deprecation")
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class RouterFunctionCodeLensProviderTest {

	@Autowired
	private BootLanguageServerHarness harness;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-webflux-project/").toURI());
		
		// Enable copilot code lenses using the BootLanguageServerHarness
		// This will trigger the command that CopilotCodeLensProvider registered
		harness.getServer().getWorkspaceService().executeCommand(
			new ExecuteCommandParams(
				CopilotCodeLensProvider.CMD_ENABLE_COPILOT_FEATURES,
				Collections.singletonList(new JsonPrimitive(true))
			)
		).get();
	}

	@Test
	public void testShowCodeLensForStaticImportStyleWebFlux() throws Exception {
		setCommandParamsHandler(true);

		String docUri = directory.toPath().resolve("src/main/java/org/test/QuoteRouter.java").toUri().toString();
		TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
		TextDocumentInfo openedDoc = harness.openDocument(doc);

		List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);

		// QuoteRouter has two methods:
		// 1. route() - uses static import style (RouterFunctions.route() with parameters)
		// 2. differentStyle() - uses builder pattern (RouterFunctions.route().GET().build())
		// Only the first method should show a code lens
		assertEquals(1, codeLenses.size());
		
		// Check that it's the 'route' method (not 'differentStyle')
		CodeLens codeLens = codeLenses.get(0);
		assertTrue(containsCodeLensWithTitle(codeLens, QueryType.ROUTER_CONVERSION.getTitle()));
		
		// Verify the prompt contains the method name and line number (not the full method body)
		Command cmd = codeLens.getCommand();
		String prompt = cmd.getArguments().get(0).toString();
		assertTrue(prompt.contains("method route at line 22"));
		// Verify it does NOT contain the full method implementation
		assertFalse(prompt.contains("RouterFunctions.route(GET"));
	}

	@Test
	public void testNoCodeLensForBuilderPattern() throws Exception {
		setCommandParamsHandler(true);

		String docUri = directory.toPath().resolve("src/main/java/org/test/QuoteRouter.java").toUri().toString();
		TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
		TextDocumentInfo openedDoc = harness.openDocument(doc);

		List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);

		// Only 1 code lens should appear (for the static import style method)
		// The builder pattern method should not have a code lens
		assertEquals(1, codeLenses.size());
		
		// Verify it's not for the 'differentStyle' method by checking the range
		CodeLens codeLens = codeLenses.get(0);
		Range range = codeLens.getRange();
		
		// The 'route' method starts at line 21 (0-indexed: line 21)
		// The 'differentStyle' method starts at line 30 (0-indexed: line 30)
		assertTrue(range.getStart().getLine() < 30, "Code lens should be for 'route' method, not 'differentStyle'");
	}

	@Test
	public void testShowCodeLensForNestedStaticImportStyle() throws Exception {
		setCommandParamsHandler(true);

		String docUri = directory.toPath().resolve("src/main/java/org/test/NestedRouter1.java").toUri().toString();
		TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
		TextDocumentInfo openedDoc = harness.openDocument(doc);

		List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);

		// NestedRouter1 uses static import style with nest() function
		assertEquals(1, codeLenses.size());
		
		CodeLens codeLens = codeLenses.get(0);
		assertTrue(containsCodeLensWithTitle(codeLens, QueryType.ROUTER_CONVERSION.getTitle()));
	}

	@Test
	public void testNoCodeLensesWhenDisabled() throws Exception {
		setCommandParamsHandler(false);

		String docUri = directory.toPath().resolve("src/main/java/org/test/QuoteRouter.java").toUri().toString();
		TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
		TextDocumentInfo openedDoc = harness.openDocument(doc);

		List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);

		// When disabled, no code lenses should appear
		assertEquals(0, codeLenses.size());
	}

	private void setCommandParamsHandler(boolean value) throws InterruptedException, ExecutionException {
		// Execute the command through the harness to set the code lens visibility
		harness.getServer().getWorkspaceService().executeCommand(
			new ExecuteCommandParams(
				CopilotCodeLensProvider.CMD_ENABLE_COPILOT_FEATURES,
				Collections.singletonList(new JsonPrimitive(value))
			)
		).get();
	}

	private boolean containsCodeLensWithTitle(CodeLens codeLens, String commandTitle) {
		Command command = codeLens.getCommand();
		return command != null && command.getTitle().equals(commandTitle);
	}
}

