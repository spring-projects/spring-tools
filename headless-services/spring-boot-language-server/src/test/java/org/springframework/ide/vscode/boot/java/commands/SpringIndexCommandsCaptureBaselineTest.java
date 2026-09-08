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
import static org.junit.jupiter.api.Assertions.assertNull;
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

import com.google.gson.Gson;
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
	private static final String BASELINE_HISTORY_CMD = "sts/spring-boot/structure/baselineHistory";
	private static final String STRUCTURE_CMD = "sts/spring-boot/structure";

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private StereotypeInformation stereotypeInformation;
	@Autowired private StructureSnapshotStore structureSnapshotStore;

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
		assertTrue(changed.values().contains("modified"), "expected something to be reported as modified, but got: " + changed);
	}

	@Test
	void changingAMethodMarksThatMethodButNotItsEnclosingClass() throws Exception {
		captureBaseline(project.getElementName());

		// a body-only edit of an annotated method: nothing about the class itself changed
		String controllerUri = new File(directory, "src/main/java/example/application/SampleController.java").toURI().toString();
		String originalContent = FileUtils.readFileToString(new File(new URI(controllerUri)), Charset.defaultCharset());
		String newContent = originalContent.replace("return \"hello!!!\";", "return \"hello, world!!!\";");
		assertNotEquals(originalContent, newContent, "test setup problem: replacement did not match the file content");

		indexer.updateDocument(controllerUri, newContent, "test triggered").get(5, TimeUnit.SECONDS);

		List<Node> roots = structureTrees();

		// the method the edit happened in is the one that gets highlighted - both where it shows up
		// as a method of its own and as a member of its controller (it is labelled by its route)
		assertEquals("modified", findNode(roots, JsonNodeHandler.KIND_METHOD, "/greeting").getAttribute(JsonNodeHandler.CHANGE));
		assertEquals("modified", findNode(roots, JsonNodeHandler.KIND_MEMBER, "/greeting").getAttribute(JsonNodeHandler.CHANGE));

		// ... while its class, and everything above it, only carry the change without being
		// highlighted, so one edit lights up one row instead of the whole path down to it
		Node type = findNode(roots, JsonNodeHandler.KIND_TYPE, "SampleController");
		assertEquals("containsChanges", type.getAttribute(JsonNodeHandler.CHANGE),
				"the enclosing class must not be highlighted for a change inside one of its methods");
		assertEquals("containsChanges", rootOf(project.getElementName()).getAttribute(JsonNodeHandler.CHANGE));
	}

	@Test
	void changingAMappingsRouteMarksTheNewMappingButNotItsController() throws Exception {
		captureBaseline(project.getElementName());

		// the route is part of the node's label, so this reads as the old mapping removed and a new
		// one added - the added node tells the story, the controller stays out of it
		String controllerUri = new File(directory, "src/main/java/example/application/SampleController.java").toURI().toString();
		String originalContent = FileUtils.readFileToString(new File(new URI(controllerUri)), Charset.defaultCharset());
		String newContent = originalContent.replace("@GetMapping(\"/greeting\")", "@GetMapping(\"/hello\")");
		assertNotEquals(originalContent, newContent, "test setup problem: replacement did not match the file content");

		indexer.updateDocument(controllerUri, newContent, "test triggered").get(5, TimeUnit.SECONDS);

		List<Node> roots = structureTrees();

		assertEquals("added", findNode(roots, JsonNodeHandler.KIND_MEMBER, "/hello").getAttribute(JsonNodeHandler.CHANGE));
		assertEquals("containsChanges", findNode(roots, JsonNodeHandler.KIND_TYPE, "SampleController").getAttribute(JsonNodeHandler.CHANGE),
				"a renamed route must not light up its controller as well");
	}

	@Test
	void deletingAMappingMarksTheControllerItWasRemovedFrom() throws Exception {
		captureBaseline(project.getElementName());

		// nothing replaces it, so the controller is the only place the deletion can show up at all
		String controllerUri = new File(directory, "src/main/java/example/application/SampleController.java").toURI().toString();
		String originalContent = FileUtils.readFileToString(new File(new URI(controllerUri)), Charset.defaultCharset());
		String newContent = originalContent.replace(
				"\t@GetMapping(\"/greeting\")\n\tpublic String sayHello() {\n\t\treturn \"hello!!!\";\n\t}", "");
		assertNotEquals(originalContent, newContent, "test setup problem: replacement did not match the file content");

		indexer.updateDocument(controllerUri, newContent, "test triggered").get(5, TimeUnit.SECONDS);

		assertEquals("modified", findNode(structureTrees(), JsonNodeHandler.KIND_TYPE, "SampleController").getAttribute(JsonNodeHandler.CHANGE));
	}

	@Test
	void changingSomethingWithoutANodeOfItsOwnStillMarksTheClass() throws Exception {
		captureBaseline(project.getElementName());

		// a field has no node in the tree, so the class is the only place its change can show up -
		// leaving out the annotated methods must not make the class blind to everything else
		String controllerUri = new File(directory, "src/main/java/example/application/SampleController.java").toURI().toString();
		String originalContent = FileUtils.readFileToString(new File(new URI(controllerUri)), Charset.defaultCharset());
		String newContent = originalContent.replace("public class SampleController {",
				"public class SampleController {\n\n\tprivate int counter;\n");
		assertNotEquals(originalContent, newContent, "test setup problem: replacement did not match the file content");

		indexer.updateDocument(controllerUri, newContent, "test triggered").get(5, TimeUnit.SECONDS);

		Node type = findNode(structureTrees(), JsonNodeHandler.KIND_TYPE, "SampleController");
		assertEquals("modified", type.getAttribute(JsonNodeHandler.CHANGE));
	}

	@Test
	void structureTreeComparesAgainstTheRequestedHistoricalBaseline() throws Exception {
		// captured directly with distinct known shas, bypassing git sha resolution (disabled in this
		// test harness - see GitBaselineTracker's isEnabled()) to simulate two commits' worth of
		// retained history
		String olderKey = structureSnapshotStore.captureBaseline(project, "sha1", "before adding goodbye")
				.capturedAt().toString();

		String controllerUri = new File(directory, "src/main/java/example/application/SampleController.java").toURI().toString();
		String originalContent = FileUtils.readFileToString(new File(new URI(controllerUri)), Charset.defaultCharset());
		String newContent = originalContent.replace(
				"\tpublic String sayHello() {\n\t\treturn \"hello!!!\";\n\t}",
				"\tpublic String sayHello() {\n\t\treturn \"hello!!!\";\n\t}\n\n\t@GetMapping(\"/goodbye\")\n\tpublic String sayGoodbye() {\n\t\treturn \"goodbye!!!\";\n\t}");
		assertNotEquals(originalContent, newContent, "test setup problem: replacement did not match the file content");
		indexer.updateDocument(controllerUri, newContent, "test triggered").get(5, TimeUnit.SECONDS);

		structureSnapshotStore.captureBaseline(project, "sha2", "after adding goodbye");

		assertTrue(changedNodesOf(structureTrees(null)).isEmpty(),
				"expected no changes when comparing against the newest baseline (the default)");

		Map<String, String> changed = changedNodesOf(structureTrees(Map.of(project.getElementName(), olderKey)));
		assertFalse(changed.isEmpty(), "expected changes when comparing against an older, explicitly requested baseline");
		assertTrue(changed.values().contains("added"), "expected the new mapping to show up as added, but got: " + changed);
		assertTrue(changed.keySet().stream().anyMatch(nodeId -> nodeId.contains("/goodbye")));
	}

	@Test
	void structureTreeFallsBackToTheNewestBaselineForAnUnknownSnapshotKey() throws Exception {
		structureSnapshotStore.captureBaseline(project, "sha1", "first commit");

		Map<String, String> unknown = Map.of(project.getElementName(), "2020-01-01T00:00:00Z");

		assertTrue(changedNodesOf(structureTrees(unknown)).isEmpty(),
				"expected no changes: falling back to the (only, and current) newest baseline");
		assertEquals("sha1", rootOf(project.getElementName(), unknown).getAttribute(JsonNodeHandler.COMPARED_AGAINST_SHA));
	}

	@Test
	void rootNodeReportsWhichBaselineItIsComparedAgainst() throws Exception {
		String olderKey = structureSnapshotStore.captureBaseline(project, "sha1", "first message").capturedAt().toString();
		String newestKey = structureSnapshotStore.captureBaseline(project, "sha2", "second message").capturedAt().toString();

		Node defaultRoot = rootOf(project.getElementName(), null);
		assertEquals("sha2", defaultRoot.getAttribute(JsonNodeHandler.COMPARED_AGAINST_SHA));
		assertEquals("second message", defaultRoot.getAttribute(JsonNodeHandler.COMPARED_AGAINST_MESSAGE));
		assertEquals(newestKey, defaultRoot.getAttribute(JsonNodeHandler.COMPARED_AGAINST_CAPTURED_AT));

		Node pinnedRoot = rootOf(project.getElementName(), Map.of(project.getElementName(), olderKey));
		assertEquals("sha1", pinnedRoot.getAttribute(JsonNodeHandler.COMPARED_AGAINST_SHA));
		assertEquals("first message", pinnedRoot.getAttribute(JsonNodeHandler.COMPARED_AGAINST_MESSAGE));
		assertEquals(olderKey, pinnedRoot.getAttribute(JsonNodeHandler.COMPARED_AGAINST_CAPTURED_AT));
	}

	@Test
	void manuallyCapturedSnapshotsCarryNoCommitInformation() throws Exception {
		captureBaseline(project.getElementName());

		List<StructureSnapshotStore.BaselineHistoryEntry> history = baselineHistory(project.getElementName());

		assertEquals(1, history.size());
		assertNull(history.get(0).commitSha(), "a manual snapshot represents no commit");
		assertNull(history.get(0).commitMessage(), "a manual snapshot represents no commit");
		assertNotNull(history.get(0).capturedAt(), "but it still says when it was taken");

		// ... and the tree reports it the same way: no commit, just the capture time
		Node root = rootOf(project.getElementName(), null);
		assertNull(root.getAttribute(JsonNodeHandler.COMPARED_AGAINST_SHA));
		assertEquals(history.get(0).capturedAt(), root.getAttribute(JsonNodeHandler.COMPARED_AGAINST_CAPTURED_AT));
	}

	@Test
	void baselineHistoryCommandReturnsTheRetainedSnapshotsNewestFirst() throws Exception {
		structureSnapshotStore.captureBaseline(project, "sha1", "first message");
		structureSnapshotStore.captureBaseline(project, "sha2", "second message");

		List<StructureSnapshotStore.BaselineHistoryEntry> history = baselineHistory(project.getElementName());

		assertEquals(2, history.size());
		assertEquals("sha2", history.get(0).commitSha());
		assertEquals("second message", history.get(0).commitMessage());
		assertEquals("sha1", history.get(1).commitSha());
	}

	/**
	 * Command results are serialized with Gson on their way to the IDE clients, and Gson cannot
	 * reflect over {@code java.time} types - a field like {@link java.time.Instant} in a result DTO
	 * only blows up once a real client asks for it, never in a test that inspects the returned Java
	 * objects. So serialize what these commands actually return, exactly as the JSON-RPC layer does.
	 */
	@Test
	void baselineCommandResultsAreGsonSerializable() throws Exception {
		structureSnapshotStore.captureBaseline(project, "sha1", "first message");

		assertNotNull(new Gson().toJson(baselineHistory(project.getElementName())));
		assertNotNull(new Gson().toJson(captureBaseline(project.getElementName())));
		assertNotNull(new Gson().toJson(clearBaseline(project.getElementName())));
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
		return rootOf(projectName, null);
	}

	private Node rootOf(String projectName, Map<String, String> compareAgainst) throws Exception {
		return structureTrees(compareAgainst).stream()
				.filter(root -> projectName.equals(root.getAttribute(JsonNodeHandler.PROJECT_ID)))
				.findFirst()
				.orElseThrow();
	}

	/**
	 * The one node of the given kind whose label contains the given text, searched depth first.
	 */
	private static Node findNode(List<Node> roots, String kind, String labelPart) {
		for (Node root : roots) {
			Node found = findNode(root, kind, labelPart);
			if (found != null) {
				return found;
			}
		}
		throw new AssertionError("no " + kind + " node with a label containing '" + labelPart + "' in the structure tree");
	}

	private static Node findNode(Node node, String kind, String labelPart) {
		Object label = node.getAttribute(JsonNodeHandler.TEXT);
		if (kind.equals(node.getAttribute(JsonNodeHandler.KIND)) && label != null && label.toString().contains(labelPart)) {
			return node;
		}

		for (Node child : node.getChildren()) {
			Node found = findNode(child, kind, labelPart);
			if (found != null) {
				return found;
			}
		}
		return null;
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

	private List<Node> structureTrees() throws Exception {
		return structureTrees(null);
	}

	@SuppressWarnings("unchecked")
	private List<Node> structureTrees(Map<String, String> compareAgainst) throws Exception {
		JsonObject params = new JsonObject();
		params.addProperty("updateMetadata", false);
		if (compareAgainst != null) {
			JsonObject compareAgainstJson = new JsonObject();
			compareAgainst.forEach(compareAgainstJson::addProperty);
			params.add("compareAgainst", compareAgainstJson);
		}

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

	@SuppressWarnings("unchecked")
	private List<StructureSnapshotStore.BaselineHistoryEntry> baselineHistory(String projectName) throws InterruptedException, ExecutionException, java.util.concurrent.TimeoutException {
		CompletableFuture<Object> future = (CompletableFuture<Object>) harness.getServer().getWorkspaceService()
				.executeCommand(new ExecuteCommandParams(BASELINE_HISTORY_CMD, List.of(projectName)));
		return (List<StructureSnapshotStore.BaselineHistoryEntry>) future.get(5, TimeUnit.SECONDS);
	}

}
