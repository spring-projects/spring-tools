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
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
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
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * GH-2018: Spring Modulith takes a module's classes from the whole classpath, so a module whose
 * classes come from a dependency is there in the project's metadata whether or not the dependency
 * is selected for the structure tree - and the tree has to show the module's types too, rather than
 * an empty module node.
 *
 * <p>The project is a mock whose classpath has {@code spring-modulith-core} on it (which is what
 * makes its tree the Modulith one) and a JAR with the module's classes, and the module metadata is
 * {@link ModulithService}'s, mocked - the project itself has no types at all.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class StructureModulithDependenciesTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private StereotypeCatalogRegistry catalogRegistry;
	@Autowired private SourceLinks sourceLinks;
	@Autowired private StructureDependencySources dependencySources;

	@TempDir
	Path tempDir;

	private IJavaProject project;
	private StructureViewProvider structureViewProvider;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		File modulith = emptyJar("spring-modulith-core-2.1.1.jar");
		File billing = JarFixtureBuilder.buildJar(tempDir, "billing", Map.of(
				"com.example.billing.Invoice", "package com.example.billing; public record Invoice(String id) {}",
				"com.example.billing.InvoiceSender", "package com.example.billing; public class InvoiceSender {}",
				"com.acme.Unrelated", "package com.acme; public class Unrelated {}"), Map.of());

		project = project("modulith-app", modulith, billing);

		ModulithService modulithService = mock(ModulithService.class);
		when(modulithService.getModulesData(any())).thenReturn(new AppModules(List.of(
				new AppModule("billing", "Billing", "com.example.billing", List.of()))));

		structureViewProvider = new StructureViewProvider(springIndex, modulithService, catalogRegistry, sourceLinks, dependencySources);
	}

	@Test
	void aModuleFromAJarThatIsNotSelectedShowsItsTypes() throws Exception {
		Node tree = structureViewProvider.createTree(project, new CachedSpringMetamodelIndex(springIndex), false, null, List.of());

		Node module = moduleNode(tree, "Billing");
		// plain classes - a JAR's type without a stereotype is shown only when it belongs to a module
		assertEquals(2, typesBelow(module).size(), "types below the module: " + typesBelow(module));
		assertFalse(render(tree).contains("Unrelated"));
	}

	@Test
	void theModulesTypesAreInTheBaselineToo() throws Exception {
		StructureElementSnapshot snapshot = structureViewProvider.captureSnapshot(project);

		List<String> types = snapshot.types().stream().map(StructureElementSnapshot.SnapshotType::fqn).toList();
		assertTrue(types.contains("com.example.billing.Invoice"), "captured types: " + types);
		assertTrue(types.contains("com.example.billing.InvoiceSender"), "captured types: " + types);
	}

	@Test
	void aTreeIsOnlyCompleteOnceTheModulesJarIsScanned() throws Exception {
		CachedSpringMetamodelIndex cachedIndex = new CachedSpringMetamodelIndex(springIndex);
		assertFalse(structureViewProvider.dependenciesReady(project, cachedIndex, List.of()));

		structureViewProvider.createTree(project, cachedIndex, false, null, List.of());

		assertTrue(structureViewProvider.dependenciesReady(project, cachedIndex, List.of()));
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

	private IJavaProject project(String name, File... jars) throws Exception {
		List<CPE> entries = List.of(jars).stream().map(jar -> CPE.binary(jar.getAbsolutePath())).toList();

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
