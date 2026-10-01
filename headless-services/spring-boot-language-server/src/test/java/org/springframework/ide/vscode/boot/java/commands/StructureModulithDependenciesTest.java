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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarOutputStream;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.stereotypes.JarFixtureBuilder;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.boot.modulith.AppModule;
import org.springframework.ide.vscode.boot.modulith.AppModules;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * GH-2018: Spring Modulith takes a module's classes from the whole classpath, so a module whose
 * classes come from a dependency is there in the project's metadata whether or not the dependency
 * is included in the structure tree.
 *
 * <p>A workspace project providing a module is included automatically: reading it costs nothing,
 * and the module node would stay empty otherwise. A JAR is not: it is only ever scanned once the
 * user selected it (a scan indexes the project's whole classpath), so its module node stays empty
 * until then.
 *
 * <p>The project is a mock whose classpath has {@code spring-modulith-core} on it (which is what
 * makes its tree the Modulith one), a JAR with the classes of one module, another JAR without any,
 * and a workspace project with the classes of another; the module metadata is {@link ModulithService}'s,
 * mocked - the project itself has no types at all.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class StructureModulithDependenciesTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private StereotypeCatalogRegistry catalogRegistry;
	@Autowired private SourceLinks sourceLinks;
	@Autowired private StructureDependencySources dependencySources;
	@Autowired private JarDependencySource jarDependencySource;

	@TempDir
	Path tempDir;

	private IJavaProject project;
	private StructureViewProvider structureViewProvider;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		// the workspace project providing the "shared" module: example.application.shared is its package
		File sharedDirectory = new File(ProjectsHarness.class.getResource("/test-projects/test-stereotypes-dependency/").toURI());
		projectFinder.find(new TextDocumentIdentifier(sharedDirectory.toURI().toString())).get();
		indexer.waitOperation().get(15, TimeUnit.SECONDS);

		File modulith = emptyJar("spring-modulith-core-2.1.1.jar");
		File billing = JarFixtureBuilder.buildJar(tempDir, "billing", Map.of(
				"com.example.billing.Invoice", "package com.example.billing; public record Invoice(String id) {}",
				"com.example.billing.InvoiceSender", "package com.example.billing; public class InvoiceSender {}",
				"com.acme.Unrelated", "package com.acme; public class Unrelated {}"), Map.of());
		File unrelated = JarFixtureBuilder.buildJar(tempDir, "unrelated", Map.of(
				"com.acme.lib.Helper", "package com.acme.lib; public class Helper {}",
				"com.example.billingextras.Other", "package com.example.billingextras; public class Other {}"), Map.of());

		CPE sharedProject = CPE.source(new File(sharedDirectory, "src/main/java"), new File(sharedDirectory, "target/classes"),
				Map.of(CPE.EXTRA_PROJECT_LOCATION, sharedDirectory.getAbsolutePath(), CPE.EXTRA_PROJECT_NAME, "test-stereotypes-dependency"));

		project = project("modulith-app", List.of(CPE.binary(modulith.getAbsolutePath()), CPE.binary(billing.getAbsolutePath()),
				CPE.binary(unrelated.getAbsolutePath()), sharedProject));

		ModulithService modulithService = mock(ModulithService.class);
		when(modulithService.getModulesData(any())).thenReturn(new AppModules(List.of(
				new AppModule("billing", "Billing", "com.example.billing", List.of()),
				new AppModule("shared", "Shared", "example.application.shared", List.of()))));

		structureViewProvider = new StructureViewProvider(springIndex, modulithService, catalogRegistry, sourceLinks, dependencySources);
	}

	/**
	 * The module's classes are in a JAR that was not selected: the module node is there, empty, and
	 * the JAR is not scanned - not even by building the tree waiting for scans.
	 */
	@Test
	void aModuleFromAJarThatIsNotSelectedIsEmptyAndTheJarIsNotScanned() throws Exception {
		Node tree = structureViewProvider.createTree(project, new CachedSpringMetamodelIndex(springIndex), false, null, List.of());

		assertEquals(List.of(), typesBelow(moduleNode(tree, "Billing")));
		assertFalse(jarDependencySource.isReady(offered("billing"), project), "the JAR was scanned although it is not selected");
		assertFalse(jarDependencySource.isReady(offered("unrelated"), project));
	}

	@Test
	void aModuleFromASelectedJarShowsItsTypes() throws Exception {
		Node tree = structureViewProvider.createTree(project, new CachedSpringMetamodelIndex(springIndex), false, null, List.of(offered("billing")));

		Node module = moduleNode(tree, "Billing");
		// plain classes - a JAR's type without a stereotype is shown only when it belongs to a module
		assertEquals(2, typesBelow(module).size(), "types below the module: " + typesBelow(module));
		assertFalse(render(tree).contains("Unrelated"));
		assertTrue(jarDependencySource.isReady(offered("billing"), project));
		assertFalse(jarDependencySource.isReady(offered("unrelated"), project), "a JAR that was not selected was scanned");
	}

	/**
	 * A workspace project needs no scan, so one with classes in a module's package is part of the
	 * tree without being selected.
	 */
	@Test
	void aModuleFromAWorkspaceProjectShowsItsTypesWithoutBeingSelected() throws Exception {
		Node tree = structureViewProvider.createTree(project, new CachedSpringMetamodelIndex(springIndex), false, null, List.of());

		assertTrue(typesBelow(moduleNode(tree, "Shared")).size() > 0, "types below the module:\n" + render(tree));
	}

	@Test
	void noJarIsEverIncludedByAModulesPackage() throws Exception {
		List<String> included = dependencySources
				.containing(project, List.of("com.example.billing", "example.application.shared"), new CachedSpringMetamodelIndex(springIndex))
				.stream().map(DependencyDescriptor::displayName).toList();

		assertEquals(List.of("test-stereotypes-dependency"), included);
		assertEquals(List.of(), dependencySources.containing(project, List.of(""), new CachedSpringMetamodelIndex(springIndex)));
	}

	@Test
	void theBaselineHasTheWorkspaceProjectsTypesButNoJarsUnlessSelected() throws Exception {
		List<String> types = structureViewProvider.captureSnapshot(project).types().stream()
				.map(StructureElementSnapshot.SnapshotType::fqn).toList();

		assertTrue(types.stream().anyMatch(type -> type.startsWith("example.application.shared.")), "captured types: " + types);
		assertFalse(types.contains("com.example.billing.Invoice"), "captured types: " + types);
		assertFalse(jarDependencySource.isReady(offered("billing"), project), "the JAR was scanned although it is not selected");
	}

	private DependencyDescriptor offered(String displayName) {
		return dependencySources.discoverAll(project).stream()
				.filter(dependency -> dependency.displayName().equals(displayName))
				.findFirst()
				.orElseThrow(() -> new AssertionError("not offered: " + displayName));
	}

	private static Node moduleNode(Node tree, String displayName) {
		for (Node child : tree.getChildren()) {
			Object text = child.getAttribute(JsonNodeHandler.TEXT);
			if (JsonNodeHandler.KIND_PACKAGE.equals(child.getAttribute(JsonNodeHandler.KIND)) && text != null
					&& text.toString().startsWith(displayName)) {
				return child;
			}
		}
		throw new AssertionError("no module node '" + displayName + "' in:\n" + render(tree));
	}

	private static List<Node> typesBelow(Node node) {
		List<Node> result = new ArrayList<>();
		for (Node child : node.getChildren()) {
			if (JsonNodeHandler.KIND_TYPE.equals(child.getAttribute(JsonNodeHandler.KIND))) {
				result.add(child);
			}
			result.addAll(typesBelow(child));
		}
		return result;
	}

	private static String render(Node tree) {
		assertNotNull(tree);
		return AsciiStructureRenderer.render(StructureViewProvider.toStructureNode(tree));
	}

	private File emptyJar(String name) throws Exception {
		File jar = tempDir.resolve(name).toFile();
		new JarOutputStream(new FileOutputStream(jar)).close();
		return jar;
	}

	private IJavaProject project(String name, List<CPE> entries) throws Exception {
		IClasspath classpath = mock(IClasspath.class);
		when(classpath.getClasspathEntries()).thenReturn(entries);
		// what makes the project a Spring Modulith one - see ModulithService.isModulithDependentProject
		when(classpath.findBinaryLibraryByPrefix("spring-modulith-core")).thenReturn(Optional.of(entries.get(0)));

		IJavaProject project = mock(IJavaProject.class);
		when(project.getClasspath()).thenReturn(classpath);
		when(project.getElementName()).thenReturn(name);
		when(project.getLocationUri()).thenReturn(URI.create(tempDir.resolve(name).toUri().toString()));
		return project;
	}

}
