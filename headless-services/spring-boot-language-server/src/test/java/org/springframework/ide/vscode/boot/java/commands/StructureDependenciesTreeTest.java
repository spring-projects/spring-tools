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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
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
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Step 2 of {@code docs/structure-view-dependencies.md}: the elements of an included workspace
 * project show up in the including project's tree as if they were its own - and a request that
 * includes dependencies carries no change information.
 *
 * <p>The harness's classpaths know no workspace project dependencies (see
 * {@link StructureDependenciesCommandTest}), so the composed trees are built by handing
 * {@link StructureViewProvider} the resolved dependency directly.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class StructureDependenciesTreeTest {

	private static final String STRUCTURE_CMD = "sts/spring-boot/structure";

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private StructureViewProvider structureViewProvider;
	@Autowired private StereotypeCatalogRegistry catalogRegistry;

	private IJavaProject host;
	private IJavaProject dependency;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		host = project("test-stereotypes-support");
		dependency = project("test-stereotypes-dependency");

		indexer.waitOperation().get(15, TimeUnit.SECONDS);
	}

	@Test
	void theTypesOfAnIncludedProjectAppearAsTheProjectsOwn() throws Exception {
		Node tree = tree(host, List.of(workspaceProject(dependency)));

		// no node for the dependency itself - its types sit in the host's package and stereotype nodes
		assertTrue(nodesLabeled(tree, dependency.getElementName()).isEmpty());

		Node application = single(nodesLabeled(tree, "Application (Hexagonal Architecture)"));
		Node controllers = single(nodesLabeled(application, "Controllers (Spring Web)"));

		// the dependency's SampleController gets the stereotype the host's package-info assigns to
		// example.application - the package is split across both projects
		List<Node> sampleControllers = nodesLabeled(controllers, "e.application.SampleController");
		assertEquals(2, sampleControllers.size());
		assertNotEquals(sampleControllers.get(0).getAttribute(JsonNodeHandler.NODE_ID), sampleControllers.get(1).getAttribute(JsonNodeHandler.NODE_ID));

		// request-mapping labels come from the dependency's own index
		Node requestMappings = single(nodesLabeled(application, "Request Mappings (Spring Web)"));
		assertEquals(1, nodesLabeled(requestMappings, "@/dependency-greeting -- GET").size());

		assertEquals(1, nodesLabeled(tree, "e.application.shared.SharedController").size());
		assertEquals(2, nodesLabeled(tree, "@/shared-greeting -- GET").size());
		assertEquals(1, nodesLabeled(tree, "e.application.shared.SharedStereotypeMarkedClass").size());
	}

	/**
	 * The tree's root packages are the top-most packages with types of their own, across the project
	 * and its included dependencies - so a dependency's types outside of the project's packages get
	 * a root package of their own, and the ones inside sit in the project's.
	 */
	@Test
	void typesOutsideOfTheProjectsPackagesGetARootPackageOfTheirOwn() throws Exception {
		Node tree = tree(host, List.of(workspaceProject(dependency)));

		assertEquals(List.of("com.acme.outside", "example"), rootPackageLabels(tree));

		Node outside = tree.getChildren().get(0);
		assertEquals(1, nodesLabeled(outside, "c.a.o.OutsideController").size());
		assertEquals(0, nodesLabeled(outside, "e.application.shared.SharedController").size());
	}

	/**
	 * A single root package gets no node of its own: its types sit right below the application node.
	 */
	@Test
	void withoutDependenciesTheProjectsSingleRootPackageHasNoNodeOfItsOwn() throws Exception {
		Node tree = tree(host, List.of());

		assertEquals(List.of(), rootPackageLabels(tree));
		assertTrue(tree.getChildren().stream()
				.anyMatch(child -> "Application (Hexagonal Architecture)".equals(child.getAttribute(JsonNodeHandler.TEXT))));
		// still abbreviated against that root package: example.MyController makes it example - not
		// example.application, where the application class is
		assertEquals(1, nodesLabeled(tree, "e.application.SampleController").size());
	}

	private static List<String> rootPackageLabels(Node tree) {
		return tree.getChildren().stream()
				.filter(child -> JsonNodeHandler.KIND_PACKAGE.equals(child.getAttribute(JsonNodeHandler.KIND)))
				.map(child -> (String) child.getAttribute(JsonNodeHandler.TEXT))
				.toList();
	}

	@Test
	void withoutASelectionTheTreeIsTheProjectsOwn() throws Exception {
		String own = render(tree(host, List.of()));

		assertFalse(own.contains("SharedController"));
		assertFalse(own.contains("@/dependency-greeting"));
	}

	@Test
	void theDependencysStereotypesAreRegisteredWithACatalogOfTheirOwnNotWithTheProjects() throws Exception {
		List<Node> before = List.of(tree(host, List.of()));
		tree(host, List.of(workspaceProject(dependency)));
		List<Node> after = List.of(tree(host, List.of()));

		assertEquals(comparable(before), comparable(after));

		String sharedStereotype = "example.application.shared.SharedStereotype";
		assertFalse(definesStereotype(catalogRegistry.getCatalogOf(host), sharedStereotype));
		assertTrue(definesStereotype(catalogRegistry.getCatalogOf(host, List.of(workspaceProject(dependency).id())), sharedStereotype));
	}

	@Test
	void projectsIncludingEachOtherTerminate() throws Exception {
		Node hostTree = tree(host, List.of(workspaceProject(dependency)));
		Node dependencyTree = tree(dependency, List.of(workspaceProject(host)));

		assertNotNull(hostTree);
		assertNotNull(dependencyTree);
		// the dependency has no application class - its tree is rooted in the top-most packages with
		// types of their own, with the host's included: com.acme.outside and example
		assertTrue(render(dependencyTree).contains("e.application.MainClass"));
	}

	@Test
	void aProjectDoesNotIncludeItself() throws Exception {
		assertEquals(comparable(List.of(tree(host, List.of()))), comparable(List.of(tree(host, List.of(workspaceProject(host))))));
	}

	@Test
	void aRequestIncludingDependenciesCarriesNoChangeInformationForAnyProject() throws Exception {
		JsonObject diffMode = new JsonObject();
		for (Node root : structureTrees(diffMode)) {
			assertNotNull(root.getAttribute(JsonNodeHandler.HAS_BASELINE), "diff mode keeps its baseline information");
		}

		JsonObject dependencyMode = new JsonObject();
		dependencyMode.add("dependencies", selection(host, dependency));
		JsonObject compareAgainst = new JsonObject();
		compareAgainst.addProperty(host.getElementName(), "some-snapshot");
		dependencyMode.add("compareAgainst", compareAgainst);

		List<Node> roots = structureTrees(dependencyMode);
		assertFalse(roots.isEmpty());

		for (Node root : roots) {
			assertEquals(null, root.getAttribute(JsonNodeHandler.HAS_BASELINE));
			assertEquals(null, root.getAttribute(JsonNodeHandler.COMPARED_AGAINST_CAPTURED_AT));
			assertTrue(allNodes(root).stream().allMatch(node -> node.getAttribute(JsonNodeHandler.CHANGE) == null));
		}
	}

	@Test
	void aChangeInAnIncludedProjectRebuildsTheProjectIncludingIt() throws Exception {
		JsonObject withoutSelection = new JsonObject();
		withoutSelection.add("affectedProjects", names(dependency));
		assertEquals(Set.of(dependency.getElementName()), projectNames(structureTrees(withoutSelection)));

		JsonObject withSelection = new JsonObject();
		withSelection.add("affectedProjects", names(dependency));
		withSelection.add("dependencies", selection(host, dependency));
		assertEquals(Set.of(dependency.getElementName(), host.getElementName()), projectNames(structureTrees(withSelection)));
	}

	private Node tree(IJavaProject project, List<DependencyDescriptor> dependencies) {
		return structureViewProvider.createTree(project, new CachedSpringMetamodelIndex(springIndex), false, null, dependencies);
	}

	@SuppressWarnings("unchecked")
	private List<Node> structureTrees(JsonObject params) throws Exception {
		params.addProperty("updateMetadata", false);
		return (List<Node>) harness.getServer().getWorkspaceService()
				.executeCommand(new ExecuteCommandParams(STRUCTURE_CMD, List.of(params))).get();
	}

	private static JsonObject selection(IJavaProject including, IJavaProject included) {
		JsonArray ids = new JsonArray();
		ids.add(DependencyDescriptor.WORKSPACE_PROJECT_ID_PREFIX + included.getElementName());

		JsonObject selection = new JsonObject();
		selection.add(including.getElementName(), ids);
		return selection;
	}

	private static JsonArray names(IJavaProject... projects) {
		JsonArray names = new JsonArray();
		for (IJavaProject project : projects) {
			names.add(project.getElementName());
		}
		return names;
	}

	private static Set<String> projectNames(List<Node> roots) {
		return roots.stream().map(root -> (String) root.getAttribute(JsonNodeHandler.PROJECT_ID)).collect(Collectors.toSet());
	}

	private static boolean definesStereotype(AbstractStereotypeCatalog catalog, String identifier) {
		return catalog.getDefinitions().stream().anyMatch(definition -> definition.getStereotype().getIdentifier().equals(identifier));
	}

	private static List<Node> nodesLabeled(Node within, String label) {
		return allNodes(within).stream().filter(node -> label.equals(node.getAttribute(JsonNodeHandler.TEXT))).toList();
	}

	private static List<Node> allNodes(Node root) {
		List<Node> result = new ArrayList<>();
		result.add(root);
		root.getChildren().forEach(child -> result.addAll(allNodes(child)));
		return result;
	}

	private static Node single(List<Node> nodes) {
		assertEquals(1, nodes.size(), "expected exactly one node, got: " + nodes.size());
		return nodes.get(0);
	}

	private static List<StructureViewProvider.StructureNode> comparable(List<Node> roots) {
		return roots.stream().map(StructureViewProvider::toStructureNode).toList();
	}

	private static String render(Node tree) {
		return AsciiStructureRenderer.render(StructureViewProvider.toStructureNode(tree));
	}

	private static DependencyDescriptor workspaceProject(IJavaProject project) {
		return DependencyDescriptor.workspaceProject(project.getElementName(), new File(project.getLocationUri()).getAbsolutePath());
	}

	private IJavaProject project(String name) throws Exception {
		File directory = new File(ProjectsHarness.class.getResource("/test-projects/" + name + "/").toURI());
		return projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();
	}

}
