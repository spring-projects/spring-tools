/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.mcp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider.SourceLocation;
import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider.StructureNode;
import org.springframework.ide.vscode.boot.mcp.StereotypeInformation;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Tests for the MCP tools around stereotypes and the logical structure of a project.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class StereotypeInformationTest {

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
	void logicalStructureRootRepresentsTheProject() throws Exception {
		StructureNode root = stereotypeInformation.getLogicalStructure(project.getElementName());

		assertEquals(project.getElementName(), root.text());
		assertNotNull(root.icon());
		assertNotNull(root.nodeId());
		assertFalse(root.children().isEmpty(), "the structure tree root doesn't have any children");
	}

	@Test
	void logicalStructureIsLookedUpCaseInsensitively() throws Exception {
		StructureNode root = stereotypeInformation.getLogicalStructure(project.getElementName().toUpperCase());

		assertEquals(project.getElementName(), root.text());
	}

	@Test
	void logicalStructureNodesCarryLabelsAndUniqueNodeIds() throws Exception {
		StructureNode root = stereotypeInformation.getLogicalStructure(project.getElementName());

		List<StructureNode> nodes = flatten(root);
		List<String> nodeIds = new ArrayList<>();

		for (StructureNode node : nodes) {
			assertNotNull(node.text(), "node without a label found");
			assertNotNull(node.nodeId(), "node without a node id found");
			assertFalse(nodeIds.contains(node.nodeId()), "duplicate node id found: " + node.nodeId());
			nodeIds.add(node.nodeId());
		}

		// child node ids are prefixed with the id of their parent
		for (StructureNode child : root.children()) {
			assertTrue(child.nodeId().startsWith(root.nodeId() + "/"),
					"node id " + child.nodeId() + " isn't nested within " + root.nodeId());
		}
	}

	@Test
	void logicalStructureContainsTypesWithTheirSourceLocations() throws Exception {
		StructureNode root = stereotypeInformation.getLogicalStructure(project.getElementName());

		List<SourceLocation> locations = flatten(root).stream()
				.map(StructureNode::location)
				.filter(location -> location != null)
				.toList();

		assertFalse(locations.isEmpty(), "no node with a source location found");

		for (SourceLocation location : locations) {
			assertNotNull(location.uri());
			assertTrue(location.startLine() >= 0);
			assertTrue(location.endLine() >= location.startLine());
		}
	}

	@Test
	void logicalStructureOfUnknownProjectFails() throws Exception {
		assertThrows(Exception.class, () -> stereotypeInformation.getLogicalStructure("no-such-project"));
	}

	@Test
	void changesWithoutACapturedBaselineReturnAHelpfulMessage() throws Exception {
		String changes = stereotypeInformation.getLogicalStructureChanges(project.getElementName(), null);

		assertTrue(changes.contains("captureLogicalStructureBaseline"),
				"expected a hint to capture a baseline first but got: " + changes);
	}

	@Test
	void baselineWithoutLaterChangesReportsNoChanges() throws Exception {
		stereotypeInformation.captureLogicalStructureBaseline(project.getElementName());

		String changes = stereotypeInformation.getLogicalStructureChanges(project.getElementName(), null);

		assertTrue(changes.contains("no changes detected"), "expected a no-changes message but got: " + changes);
	}

	@Test
	void addingARequestMappingMethodShowsUpAsAddedInTheDiffAgainstTheBaseline() throws Exception {
		stereotypeInformation.captureLogicalStructureBaseline(project.getElementName());

		String controllerUri = directory.toPath().resolve("src/main/java/example/application/SampleController.java").toUri().toString();
		String originalContent = FileUtils.readFileToString(new File(new URI(controllerUri)), Charset.defaultCharset());
		String newContent = originalContent.replace(
				"\tpublic String sayHello() {\n\t\treturn \"hello!!!\";\n\t}",
				"\tpublic String sayHello() {\n\t\treturn \"hello!!!\";\n\t}\n\n\t@GetMapping(\"/goodbye\")\n\tpublic String sayGoodbye() {\n\t\treturn \"goodbye!!!\";\n\t}");
		assertNotEquals(originalContent, newContent, "test setup problem: replacement did not match the file content");

		CompletableFuture<Void> updateFuture = indexer.updateDocument(controllerUri, newContent, "test triggered");
		updateFuture.get(5, TimeUnit.SECONDS);

		String changes = stereotypeInformation.getLogicalStructureChanges(project.getElementName(), null);

		assertTrue(changes.contains("+") && changes.contains("/goodbye"),
				"expected the new mapping to show up as added but got:\n" + changes);
	}

	private static List<StructureNode> flatten(StructureNode node) {
		List<StructureNode> result = new ArrayList<>();
		result.add(node);
		node.children().forEach(child -> result.addAll(flatten(child)));
		return result;
	}

}
