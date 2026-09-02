/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.java.commands.SpringIndexCommands.CaptureBaselineResult;
import org.springframework.ide.vscode.boot.mcp.StereotypeInformation;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Tests for the {@code sts/spring-boot/structure/captureBaseline} LSP command, the entry point the
 * VSCode extension uses to capture a logical structure baseline from the "Logical Structure" tree
 * view, without going through an MCP tool.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class SpringIndexCommandsCaptureBaselineTest {

	private static final String CAPTURE_BASELINE_CMD = "sts/spring-boot/structure/captureBaseline";

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private StereotypeInformation stereotypeInformation;

	private IJavaProject project;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		File directory = new File(ProjectsHarness.class.getResource("/test-projects/test-stereotypes-support/").toURI());
		project = projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

	@Test
	void capturesABaselineWithNodeCountAndTimestamp() throws Exception {
		CaptureBaselineResult result = captureBaseline(project.getElementName());

		assertEquals(project.getElementName(), result.projectName());
		assertTrue(result.nodeCount() > 0, "expected at least one node in the captured baseline");
		assertNotNull(result.capturedAt());
	}

	@Test
	void baselineCapturedThroughTheLspCommandIsVisibleToTheMcpTools() throws Exception {
		captureBaseline(project.getElementName());

		String changes = stereotypeInformation.getLogicalStructureChanges(project.getElementName(), "baseline", null);

		assertTrue(changes.contains("no changes detected"),
				"expected the MCP tool to see the baseline captured via the LSP command, but got: " + changes);
	}

	@Test
	void capturingABaselineForAnUnknownProjectFails() {
		ExecutionException exception = assertThrows(ExecutionException.class, () -> captureBaseline("no-such-project"));
		assertTrue(exception.getCause().getMessage().contains("no-such-project"));
	}

	@SuppressWarnings("unchecked")
	private CaptureBaselineResult captureBaseline(String projectName) throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
		CompletableFuture<Object> future = (CompletableFuture<Object>) harness.getServer().getWorkspaceService()
				.executeCommand(new ExecuteCommandParams(CAPTURE_BASELINE_CMD, List.of(projectName)));
		return (CaptureBaselineResult) future.get(5, TimeUnit.SECONDS);
	}

}
