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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.commands.SpringIndexCommands.Dependencies;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.JsonObject;

/**
 * Step 1 of {@code docs/structure-view-dependencies.md}: the dependencies a project offers can be
 * asked for, and a structure request can carry a selection. What a selection does to the tree is
 * {@link StructureDependenciesTreeTest}'s.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class StructureDependenciesCommandTest {

	private static final String DEPENDENCIES_CMD = "sts/spring-boot/structure/dependencies";

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private StructureDependencySources dependencySources;
	@Autowired private JarDependencySource jarDependencySource;

	private IJavaProject project;
	private StructureTreeTestFixture tree;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		File directory = new File(ProjectsHarness.class.getResource("/test-projects/test-stereotypes-support/").toURI());
		project = projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();
		tree = new StructureTreeTestFixture(harness, indexer, directory);

		indexer.waitOperation().get(15, TimeUnit.SECONDS);
	}

	@Test
	void dependenciesCommandAnswersForASingleProject() throws Exception {
		Dependencies dependencies = (Dependencies) harness.getServer().getWorkspaceService()
				.executeCommand(new ExecuteCommandParams(DEPENDENCIES_CMD, List.of(project.getElementName()))).get();

		assertEquals(project.getElementName(), dependencies.projectName());
		// the harness builds classpaths with MavenProjectClasspath, which does no workspace
		// resolution - so there is never a workspace project dependency to offer here
		assertTrue(dependencies.dependencies().stream().noneMatch(d -> d.kind() == DependencyDescriptor.Kind.WORKSPACE_PROJECT));
	}

	@Test
	void jarsAreOfferedWithTheirMavenCoordinates() throws Exception {
		Dependencies dependencies = (Dependencies) harness.getServer().getWorkspaceService()
				.executeCommand(new ExecuteCommandParams(DEPENDENCIES_CMD, List.of(project.getElementName()))).get();

		DependencyDescriptor springWeb = dependencies.dependencies().stream()
				.filter(d -> d.id().equals("gav:org.springframework:spring-web"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("spring-web not offered, got: " + dependencies.dependencies()));

		assertEquals(DependencyDescriptor.Kind.JAR, springWeb.kind());
		assertEquals("spring-web", springWeb.displayName());
		assertEquals("org.springframework", springWeb.groupId());
		assertEquals("spring-web", springWeb.artifactId());
		assertTrue(springWeb.version() != null && !springWeb.version().isBlank());
		assertTrue(springWeb.location().endsWith(".jar"));

		// the JRE never shows up as a dependency to include
		assertTrue(dependencies.dependencies().stream().allMatch(d -> d.id().startsWith("gav:") || d.id().startsWith("jar:")));
		assertTrue(dependencies.dependencies().stream().noneMatch(d -> d.location().contains("/jmods/") || d.location().endsWith("rt.jar")));
	}

	@SuppressWarnings("unchecked")
	@Test
	void dependenciesCommandAnswersForAllProjectsWithoutAnArgument() throws Exception {
		List<Dependencies> all = (List<Dependencies>) harness.getServer().getWorkspaceService()
				.executeCommand(new ExecuteCommandParams(DEPENDENCIES_CMD, List.of())).get();

		assertTrue(all.stream().anyMatch(d -> d.projectName().equals(project.getElementName())));
	}

	@Test
	void theKindOfADependencyIsSentByName() throws Exception {
		Object result = harness.getServer().getWorkspaceService()
				.executeCommand(new ExecuteCommandParams(DEPENDENCIES_CMD, List.of(project.getElementName()))).get();

		// the Gson the language server actually sends command results with - LSP4J's own enum
		// adapter would otherwise write the kind as a number
		JsonObject json = new MessageJsonHandler(Map.of()).getGson().toJsonTree(result).getAsJsonObject();

		JsonObject first = json.getAsJsonArray("dependencies").get(0).getAsJsonObject();
		assertEquals("JAR", first.get("kind").getAsString());
	}

	@Test
	void aSelectionThatResolvesToNothingLeavesTheTreeAsItIs() throws Exception {
		List<Node> without = tree.structureTrees(null, null, null);
		List<Node> with = tree.structureTrees(null, null,
				Map.of(project.getElementName(), List.of("project:some-dependency", "gav:com.example:lib")));

		assertEquals(comparable(without), comparable(with));
	}

	/**
	 * A JAR still to be scanned keeps no tree waiting: the request answers right away, the JAR is
	 * scanned in the background, and the client is told once the project's tree can be built with
	 * it - the same way it is told about any other index update.
	 */
	@Test
	void aJarStillToBeScannedIsScannedInTheBackgroundAndTheClientToldOnceItIsReady() throws Exception {
		String projectName = project.getElementName();
		DependencyDescriptor springWeb = dependencySources.resolve(project, List.of("gav:org.springframework:spring-web")).get(0);
		int updatesBefore = harness.getIndexUpdatedCount();

		List<Node> trees = tree.structureTrees(null, null, Map.of(projectName, List.of(springWeb.id())));
		assertTrue(trees.stream().anyMatch(root -> projectName.equals(root.getAttribute(JsonNodeHandler.PROJECT_ID))));

		long deadline = System.currentTimeMillis() + 60_000;
		while (!jarDependencySource.isReady(springWeb, project) || !indexUpdatedSince(updatesBefore, projectName)) {
			assertTrue(System.currentTimeMillis() < deadline, "the JAR was not scanned in the background, or the client not told");
			Thread.sleep(100);
		}
	}

	private boolean indexUpdatedSince(int updatesBefore, String projectName) {
		for (int i = updatesBefore; i < harness.getIndexUpdatedCount(); i++) {
			if (harness.getIndexUpdatedDetails(i).getAffectedProjects().contains(projectName)) {
				return true;
			}
		}
		return false;
	}

	private List<StructureViewProvider.StructureNode> comparable(List<Node> roots) {
		return roots.stream().map(StructureViewProvider::toStructureNode).toList();
	}

}
