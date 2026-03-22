/*******************************************************************************
 * Copyright (c) 2017, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.cron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.InlayHint;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.TextDocumentInfo;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Udayani V
 */
@SuppressWarnings("deprecation")
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class CronExpressionsInlayHintsProviderTest {

	@Autowired
	private BootLanguageServerHarness harness;
	@Autowired
	private JavaProjectFinder projectFinder;
	@Autowired
	private SpringSymbolIndex indexer;
	private SimpleLanguageServer server;
	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-annotations/").toURI());
		String projectDir = directory.toURI().toString();
		server = mock(SimpleLanguageServer.class);

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

	@Test
	public void test_cronExpresionInlayHints() throws Exception {

		String docUri = directory.toPath().resolve("src/main/java/org/test/CronScheduler.java").toUri().toString();
		TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
		TextDocumentInfo openedDoc = harness.openDocument(doc);

		List<InlayHint> inlayHints = harness.getInlayHints(openedDoc);

		assertEquals(9, inlayHints.size());

		assertTrue(containsInlayHints(inlayHints.get(0), 10, 33, "every hour"));
		assertTrue(containsInlayHints(inlayHints.get(1), 15, 33, "every minute at second 01"));
		assertTrue(containsInlayHints(inlayHints.get(2), 20, 34, "every minute at second 10"));
		assertTrue(containsInlayHints(inlayHints.get(3), 25, 36, "every hour between 8 and 10"));
		assertTrue(containsInlayHints(inlayHints.get(4), 30, 36, "at 6 and 19 hours"));
		assertTrue(containsInlayHints(inlayHints.get(5), 35, 39, "every 30 minutes every hour between 8 and 10"));
		assertTrue(containsInlayHints(inlayHints.get(6), 40, 42,
				"every hour between 9 and 17 every day between Monday and Friday"));
		assertTrue(containsInlayHints(inlayHints.get(7), 45, 35, "every hour every day between Monday and Friday"));
		assertTrue(
				containsInlayHints(inlayHints.get(8), 51, 9, "every second between 0 and 59 at 10 minute at 13 hour"));
	}

	@Test
	public void test_noInlayHintsForInvalidCronExp() throws Exception {

		String docUri = directory.toPath().resolve("src/main/java/org/test/Scheduler.java").toUri().toString();
		TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
		TextDocumentInfo openedDoc = harness.openDocument(doc);

		List<InlayHint> inlayHints = harness.getInlayHints(openedDoc);

		assertEquals(0, inlayHints.size());
	}

	private boolean containsInlayHints(InlayHint inlayHint, int line, int character, String label) {
		Position pos = inlayHint.getPosition();
		String inlayText = inlayHint.getLabel().getLeft();
		if (pos.getLine() == line && pos.getCharacter() == character && inlayText.equals(label)
				&& inlayText.equals(label)) {
			return true;
		}
		return false;
	}
}