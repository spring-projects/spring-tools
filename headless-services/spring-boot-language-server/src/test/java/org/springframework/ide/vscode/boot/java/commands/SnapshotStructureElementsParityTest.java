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
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.StructureTreeDiff;
import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider.StructureNode;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.stereotypes.IndexBasedStereotypeFactory;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeDefinitionLocator;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * The central invariant "R3" of {@code docs/structure-diff-elements.md} depends on: diffing a tree
 * rebuilt from a {@link StructureElementSnapshot} taken of an unchanged project against the tree
 * built straight from the live index must report no changes at all - otherwise diffing against a
 * rebuilt baseline could never come out clean for a project nobody touched. Checked for both
 * {@link JMoleculesStructureView} and {@link ModulithStructureView}.
 *
 * <p>Deliberately not a structural {@code equals} of the two {@code toComparableNode} trees: their
 * {@code nodeId}s legitimately differ in format, harmlessly, since a node id embeds its source
 * location ({@code JsonNodeHandler.assignNodeId}) and a snapshot-rebuilt element has none. That is
 * fine - {@code nodeId} plays no part in matching two trees during a diff
 * ({@code StructureTreeDiffer.indexChildren} matches by {@code kind + text} alone) - so diffing,
 * exactly as production code does, is both the more accurate check of this invariant and immune to
 * that difference.
 *
 * <p>Deliberately duplicates a few lines of {@link StructureViewProvider#createTree} (building the
 * catalog, the {@link IndexBasedStereotypeFactory} and an {@link IndexStructureElements}) rather
 * than calling it: this test needs the intermediate {@link StructureElements} to capture a snapshot
 * from, which {@code createTree} builds internally and does not hand back.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class SnapshotStructureElementsParityTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private StereotypeCatalogRegistry stereotypeCatalogRegistry;
	@Autowired private SourceLinks sourceLinks;
	@Autowired private ModulithService modulithService;

	private final StereotypeDefinitionLocator definitionLocator = new StereotypeDefinitionLocator();

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
	}

	@Test
	void rebuiltTreeEqualsTheLiveTree_jMolecules() throws Exception {
		File directory = new File(ProjectsHarness.class.getResource("/test-projects/test-stereotypes-support/").toURI());
		IJavaProject project = projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();
		indexer.waitOperation().get(15, TimeUnit.SECONDS);

		var catalog = stereotypeCatalogRegistry.getCatalogOf(project);
		var cachedIndex = new CachedSpringMetamodelIndex(springIndex);
		var factory = new IndexBasedStereotypeFactory(catalog, project, cachedIndex);
		factory.registerStereotypeDefinitions();
		List<String> selectedGroups = catalog.getGroups().stream().map(g -> g.getIdentifier()).toList();

		StructureElements indexElements = new IndexStructureElements(project, cachedIndex, factory);
		Node liveTree = new JMoleculesStructureView(catalog, sourceLinks, definitionLocator).createTree(project, indexElements, selectedGroups);
		assertNotNull(liveTree);

		StructureElementSnapshot snapshot = StructureSnapshotBuilder.capture(indexElements);
		StructureElements snapshotElements = new SnapshotStructureElements(snapshot);
		Node rebuiltTree = new JMoleculesStructureView(catalog, sourceLinks, definitionLocator).createTree(project, snapshotElements, selectedGroups);
		assertNotNull(rebuiltTree);

		StructureNode liveComparable = StructureViewProvider.toComparableNode(liveTree);
		StructureNode rebuiltComparable = StructureViewProvider.toComparableNode(rebuiltTree);

		StructureTreeDiff diff = StructureTreeDiffer.diff(project.getElementName(), Instant.now(), Instant.now(), rebuiltComparable, liveComparable);
		assertFalse(diff.stats().hasChanges(), "expected no changes diffing an unchanged project's tree against itself, but got: " + diff.stats());
	}

	@Test
	void rebuiltTreeEqualsTheLiveTree_modulith() throws Exception {
		// lands the project under a temp folder rather than target/test-classes: Modulith filters
		// out anything under target/test-classes, same reasoning as ModulithStructureTreeTest
		MavenJavaProject project = ProjectsHarness.INSTANCE.mavenProject("spring-modulith-example-full", p -> {});
		harness.useProject(project);
		harness.getProjectFinder().find(new TextDocumentIdentifier(project.getLocationUri().toASCIIString())).get();
		indexer.waitOperation().get(15, TimeUnit.SECONDS);
		assertEquals(Boolean.TRUE, modulithService.requestMetadata(project, Duration.ZERO).get());
		indexer.waitOperation().get(15, TimeUnit.SECONDS);

		var catalog = stereotypeCatalogRegistry.getCatalogOf(project);
		var cachedIndex = new CachedSpringMetamodelIndex(springIndex);
		var factory = new IndexBasedStereotypeFactory(catalog, project, cachedIndex);
		factory.registerStereotypeDefinitions();
		List<String> selectedGroups = catalog.getGroups().stream().map(g -> g.getIdentifier()).toList();

		StructureElements indexElements = new IndexStructureElements(project, cachedIndex, factory);
		Node liveTree = new ModulithStructureView(catalog, sourceLinks, definitionLocator, modulithService).createTree(project, indexElements, selectedGroups, false);
		assertNotNull(liveTree);

		StructureElementSnapshot snapshot = StructureSnapshotBuilder.capture(indexElements);
		StructureElements snapshotElements = new SnapshotStructureElements(snapshot);
		Node rebuiltTree = new ModulithStructureView(catalog, sourceLinks, definitionLocator, modulithService).createTree(project, snapshotElements, selectedGroups, false);
		assertNotNull(rebuiltTree);

		StructureNode liveComparable = StructureViewProvider.toComparableNode(liveTree);
		StructureNode rebuiltComparable = StructureViewProvider.toComparableNode(rebuiltTree);

		StructureTreeDiff diff = StructureTreeDiffer.diff(project.getElementName(), Instant.now(), Instant.now(), rebuiltComparable, liveComparable);
		assertFalse(diff.stats().hasChanges(), "expected no changes diffing an unchanged project's tree against itself, but got: " + diff.stats());
	}

}
