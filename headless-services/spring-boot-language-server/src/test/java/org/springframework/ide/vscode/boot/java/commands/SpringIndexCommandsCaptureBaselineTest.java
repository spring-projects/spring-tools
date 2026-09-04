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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
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
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.commands.SpringIndexCommands.CaptureBaselineResult;
import org.springframework.ide.vscode.boot.java.commands.SpringIndexCommands.ClearBaselineResult;
import org.springframework.ide.vscode.boot.mcp.StereotypeInformation;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.JsonObject;

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
	private static final String CLEAR_BASELINE_CMD = "sts/spring-boot/structure/clearBaseline";
	private static final String STRUCTURE_CMD = "sts/spring-boot/structure";

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private StereotypeInformation stereotypeInformation;

	private File directory;
	private IJavaProject project;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-stereotypes-support/").toURI());
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

		String changes = stereotypeInformation.getLogicalStructureChanges(project.getElementName(), null);

		assertTrue(changes.contains("no changes detected"),
				"expected the MCP tool to see the baseline captured via the LSP command, but got: " + changes);
	}

	@Test
	void capturingABaselineForAnUnknownProjectFails() {
		ExecutionException exception = assertThrows(ExecutionException.class, () -> captureBaseline("no-such-project"));
		assertTrue(exception.getCause().getMessage().contains("no-such-project"));
	}

	@Test
	void structureTreeCarriesNoChangeMarkersWithoutABaseline() throws Exception {
		List<Node> roots = structureTrees();

		assertTrue(changedNodesOf(roots).isEmpty(),
				"expected no change markers before a baseline was captured, but got: " + changedNodesOf(roots));
	}

	@Test
	void structureTreeMarksNodesThatChangedSinceTheBaseline() throws Exception {
		captureBaseline(project.getElementName());

		// right after capturing, current == baseline, so nothing is marked
		assertTrue(changedNodesOf(structureTrees()).isEmpty(), "expected no change markers directly after capturing a baseline");

		String controllerUri = new File(directory, "src/main/java/example/application/SampleController.java").toURI().toString();
		String originalContent = FileUtils.readFileToString(new File(new URI(controllerUri)), Charset.defaultCharset());
		String newContent = originalContent.replace(
				"\tpublic String sayHello() {\n\t\treturn \"hello!!!\";\n\t}",
				"\tpublic String sayHello() {\n\t\treturn \"hello!!!\";\n\t}\n\n\t@GetMapping(\"/goodbye\")\n\tpublic String sayGoodbye() {\n\t\treturn \"goodbye!!!\";\n\t}");
		assertNotEquals(originalContent, newContent, "test setup problem: replacement did not match the file content");

		indexer.updateDocument(controllerUri, newContent, "test triggered").get(5, TimeUnit.SECONDS);

		Map<String, String> changed = changedNodesOf(structureTrees());

		assertFalse(changed.isEmpty(), "expected change markers in the structure tree after changing a controller");
		assertTrue(changed.values().contains("added"), "expected at least one node marked as added, but got: " + changed);
		assertTrue(changed.keySet().stream().anyMatch(nodeId -> nodeId.contains("/goodbye")),
				"expected the new mapping to be marked as changed, but got: " + changed.keySet());
	}

	@Test
	void structureTreeMarksNodesWhoseSourceChangedWithoutChangingTheTree() throws Exception {
		captureBaseline(project.getElementName());

		// change the body of the mapping method only: same route, same signature, same label, so
		// every node in the tree still looks exactly as it did - only the source behind it changed
		String controllerUri = new File(directory, "src/main/java/example/application/SampleController.java").toURI().toString();
		String originalContent = FileUtils.readFileToString(new File(new URI(controllerUri)), Charset.defaultCharset());
		String newContent = originalContent.replace("return \"hello!!!\";", "return \"hello, world!!!\";");
		assertNotEquals(originalContent, newContent, "test setup problem: replacement did not match the file content");

		indexer.updateDocument(controllerUri, newContent, "test triggered").get(5, TimeUnit.SECONDS);

		Map<String, String> changed = changedNodesOf(structureTrees());

		assertFalse(changed.isEmpty(), "expected the changed method body to be marked in the structure tree");
		assertFalse(changed.values().contains("added"), "expected no node to be reported as added, but got: " + changed);
		assertTrue(changed.keySet().stream().anyMatch(nodeId -> nodeId.contains("SampleController")),
				"expected the controller and its members to be marked as changed, but got: " + changed.keySet());
	}

	@Test
	void rootNodeReportsNoBaselineBeforeOneIsCaptured() throws Exception {
		Node root = rootOf(project.getElementName());

		assertEquals(Boolean.FALSE, root.getAttribute(JsonNodeHandler.HAS_BASELINE));
	}

	@Test
	void rootNodeReportsABaselineEvenWhenNothingChangedSinceIt() throws Exception {
		captureBaseline(project.getElementName());

		Node root = rootOf(project.getElementName());

		// hasBaseline must not be confused with "something changed" - it says a baseline exists,
		// which is what the client needs to tell "no baseline" apart from "baseline, no changes yet"
		assertEquals(Boolean.TRUE, root.getAttribute(JsonNodeHandler.HAS_BASELINE));
		assertTrue(changedNodesOf(structureTrees()).isEmpty());
	}

	@Test
	void clearingAnUncapturedBaselineReportsItHadNone() throws Exception {
		ClearBaselineResult result = clearBaseline(project.getElementName());

		assertEquals(project.getElementName(), result.projectName());
		assertFalse(result.hadBaseline());
	}

	@Test
	void clearingARemovesItSoTheTreeGoesBackToNoBaseline() throws Exception {
		captureBaseline(project.getElementName());
		assertEquals(Boolean.TRUE, rootOf(project.getElementName()).getAttribute(JsonNodeHandler.HAS_BASELINE));

		ClearBaselineResult result = clearBaseline(project.getElementName());

		assertTrue(result.hadBaseline());
		assertEquals(Boolean.FALSE, rootOf(project.getElementName()).getAttribute(JsonNodeHandler.HAS_BASELINE));
	}

	@Test
	void clearingIsVisibleToTheMcpTools() throws Exception {
		captureBaseline(project.getElementName());
		clearBaseline(project.getElementName());

		String changes = stereotypeInformation.getLogicalStructureChanges(project.getElementName(), null);

		assertTrue(changes.contains("captureLogicalStructureBaseline"),
				"expected the MCP tool to see the baseline cleared via the LSP command, but got: " + changes);
	}

	private Node rootOf(String projectName) throws Exception {
		return structureTrees().stream()
				.filter(root -> projectName.equals(root.getAttribute(JsonNodeHandler.PROJECT_ID)))
				.findFirst()
				.orElseThrow();
	}

	/**
	 * The node ids of all nodes carrying a change marker, mapped to that marker.
	 */
	private static Map<String, String> changedNodesOf(List<Node> roots) {
		Map<String, String> changed = new LinkedHashMap<>();
		roots.forEach(root -> collectChanges(root, changed));
		return changed;
	}

	private static void collectChanges(Node node, Map<String, String> changed) {
		Object change = node.getAttribute(JsonNodeHandler.CHANGE);
		if (change != null) {
			changed.put(String.valueOf(node.getAttribute(JsonNodeHandler.NODE_ID)), change.toString());
		}
		node.getChildren().forEach(child -> collectChanges(child, changed));
	}

	@SuppressWarnings("unchecked")
	private List<Node> structureTrees() throws Exception {
		JsonObject params = new JsonObject();
		params.addProperty("updateMetadata", false);

		return (List<Node>) harness.getServer().getWorkspaceService()
				.executeCommand(new ExecuteCommandParams(STRUCTURE_CMD, List.of(params))).get();
	}

	@SuppressWarnings("unchecked")
	private CaptureBaselineResult captureBaseline(String projectName) throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
		CompletableFuture<Object> future = (CompletableFuture<Object>) harness.getServer().getWorkspaceService()
				.executeCommand(new ExecuteCommandParams(CAPTURE_BASELINE_CMD, List.of(projectName)));
		return (CaptureBaselineResult) future.get(5, TimeUnit.SECONDS);
	}

	@SuppressWarnings("unchecked")
	private ClearBaselineResult clearBaseline(String projectName) throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
		CompletableFuture<Object> future = (CompletableFuture<Object>) harness.getServer().getWorkspaceService()
				.executeCommand(new ExecuteCommandParams(CLEAR_BASELINE_CMD, List.of(projectName)));
		return (ClearBaselineResult) future.get(5, TimeUnit.SECONDS);
	}

}
