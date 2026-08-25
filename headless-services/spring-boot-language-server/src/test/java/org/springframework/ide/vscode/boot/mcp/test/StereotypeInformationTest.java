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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.mcp.StereotypeInformation;
import org.springframework.ide.vscode.boot.mcp.StereotypeInformation.SourceLocation;
import org.springframework.ide.vscode.boot.mcp.StereotypeInformation.StructureNode;
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

	private static List<StructureNode> flatten(StructureNode node) {
		List<StructureNode> result = new ArrayList<>();
		result.add(node);
		node.children().forEach(child -> result.addAll(flatten(child)));
		return result;
	}

}
