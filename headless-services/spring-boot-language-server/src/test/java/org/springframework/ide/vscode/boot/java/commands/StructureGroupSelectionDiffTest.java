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

import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Characterizes a bug fixed by {@code docs/structure-diff-elements.md}'s "R4" step: a baseline used
 * to be captured with every stereotype group, but the live tree it got diffed against was built
 * with whatever groups the client currently had selected
 * ({@code SpringIndexCommands.createAnnotatedTree}). Deselecting a group therefore used to make
 * that group's nodes look removed (they only existed in the baseline) and the types beneath them
 * look added under whatever they got grouped as instead - purely from the group selection, with no
 * code change at all.
 *
 * <p>This is the characterization test called for by "R0" in
 * {@code docs/structure-diff-elements.md}. It failed against the tree-snapshot implementation the
 * bug was in, and started passing once "R4" (element-based snapshots, diffed by rebuilding the
 * baseline tree with the live tree's own group selection - {@code StructureSnapshotStore}) landed.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class StructureGroupSelectionDiffTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	private IJavaProject project;
	private StructureTreeTestFixture tree;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		File directory = new File(ProjectsHarness.class.getResource("/test-projects/test-stereotypes-support/").toURI());
		project = projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();
		tree = new StructureTreeTestFixture(harness, indexer, directory);

		indexer.waitOperation().get(15, TimeUnit.SECONDS);

		harness.getServer().getWorkspaceService().executeCommand(
				new ExecuteCommandParams("sts/spring-boot/structure/captureBaseline", List.of(project.getElementName()))).get();
	}

	/**
	 * Deselecting any one group, with no source change at all, must not mark anything in the tree
	 * as changed - it merely restructures how the same, unchanged elements are grouped.
	 */
	@Test
	void deselectingAGroupWithNoCodeChangeMarksNothingAsChanged() throws Exception {
		List<String> allGroups = StructureTreeTestFixture.groupIdentifiersOf(harness, project.getElementName());

		for (String dropped : allGroups) {
			Set<String> selected = new HashSet<>(allGroups);
			selected.remove(dropped);

			List<Node> roots = tree.structureTrees(null, Map.of(project.getElementName(), selected));
			Node root = roots.stream().filter(n -> project.getElementName().equals(n.getAttribute(JsonNodeHandler.PROJECT_ID))).findFirst().orElseThrow();

			Node changed = findAnyChangeAttribute(root);
			assertNull(changed, () -> "deselecting group '" + dropped + "' must not report any change, but found one on node '"
					+ changed.getAttribute(JsonNodeHandler.TEXT) + "' (" + changed.getAttribute(JsonNodeHandler.CHANGE) + ")");
		}
	}

	private static Node findAnyChangeAttribute(Node node) {
		if (node.getAttribute(JsonNodeHandler.CHANGE) != null) {
			return node;
		}
		for (Node child : node.getChildren()) {
			Node found = findAnyChangeAttribute(child);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

}
