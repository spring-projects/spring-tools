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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.boot.modulith.AppModule;
import org.springframework.ide.vscode.boot.modulith.AppModules;
import org.springframework.ide.vscode.boot.modulith.ModulithMetadataProblem;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;

/**
 * The structure tree of a project whose Spring Modulith metadata is missing or outdated says so,
 * on the project node.
 *
 * @author Martin Lippert
 */
public class StructureModulithMetadataProblemTest {

	private ModulithService modulithService;
	private StructureViewProvider provider;
	private IJavaProject project;

	@BeforeEach
	void setup() throws Exception {
		modulithService = mock(ModulithService.class);
		provider = new StructureViewProvider(mock(SpringMetamodelIndex.class), modulithService, mock(StereotypeCatalogRegistry.class),
				mock(SourceLinks.class), mock(StructureDependencySources.class));

		CPE modulith = CPE.binary("/libs/spring-modulith-core-2.1.1.jar");
		IClasspath classpath = mock(IClasspath.class);
		when(classpath.getClasspathEntries()).thenReturn(List.of(modulith));
		// what makes the project a Spring Modulith one - see ModulithService.isModulithDependentProject
		when(classpath.findBinaryLibraryByPrefix("spring-modulith-core")).thenReturn(Optional.of(modulith));

		project = mock(IJavaProject.class);
		when(project.getClasspath()).thenReturn(classpath);
		when(project.getElementName()).thenReturn("modulith-app");
		when(project.getLocationUri()).thenReturn(URI.create("file:///workspace/modulith-app"));
	}

	@Test
	void withoutMetadataTheProjectNodeSaysWhy() {
		when(modulithService.getProblem(any())).thenReturn(ModulithMetadataProblem.noClassFiles());

		Node root = provider.withMetadataProblem(project, null);

		assertNotNull(root);
		assertEquals("modulith-app", root.getAttributes().get(JsonNodeHandler.PROJECT_ID));
		assertEquals("modulith-app", root.getAttributes().get(JsonNodeHandler.TEXT));
		assertEquals(List.of(), root.getChildren(), "no extra node: the hint belongs to the project node");

		Map<?, ?> problem = (Map<?, ?>) root.getAttributes().get(JsonNodeHandler.PROBLEM);
		assertEquals("project not built", problem.get("label"));
		assertEquals(ModulithMetadataProblem.noClassFiles().message(), problem.get("message"));
	}

	@Test
	void noProblemNoNode() {
		when(modulithService.getProblem(any())).thenReturn(null);
		assertNull(provider.withMetadataProblem(project, null));
	}

	@Test
	void outdatedMetadataMarksTheTree() {
		ModulithMetadataProblem exportFailed = ModulithMetadataProblem.exportFailed(1, "Exception in thread main\njava.lang.ClassNotFoundException: com.acme.Foo\n");
		AppModules modules = new AppModules(List.of(new AppModule("billing", "Billing", "com.example.billing", List.of())))
				.withProblem(exportFailed);
		when(modulithService.getModulesData(any())).thenReturn(modules);
		when(modulithService.getProblem(any())).thenReturn(exportFailed);

		Node tree = new Node(null).withAttribute(JsonNodeHandler.PROJECT_ID, "modulith-app");
		assertSame(tree, provider.withMetadataProblem(project, tree));

		Map<?, ?> problem = (Map<?, ?>) tree.getAttributes().get(JsonNodeHandler.PROBLEM);
		assertEquals("metadata export failed, outdated", problem.get("label"));
		assertEquals("Spring Modulith metadata export failed (exit code 1: java.lang.ClassNotFoundException: com.acme.Foo)", problem.get("message"));
	}

	@Test
	void upToDateMetadataLeavesTheTreeAlone() {
		when(modulithService.getProblem(any())).thenReturn(null);

		Node tree = new Node(null);
		assertSame(tree, provider.withMetadataProblem(project, tree));

		assertFalse(tree.getAttributes().containsKey(JsonNodeHandler.PROBLEM));
	}

	@Test
	void staleStateIsPartOfTheMetadataButNotOfItsIdentity() {
		AppModules fresh = new AppModules(List.of(new AppModule("billing", "Billing", "com.example.billing", List.of())));
		AppModules stale = fresh.withProblem(ModulithMetadataProblem.noClassFiles());

		assertFalse(fresh.isStale());
		assertNull(fresh.getProblem());
		assertTrue(stale.isStale());
		assertEquals(ModulithMetadataProblem.Code.NO_CLASS_FILES, stale.getProblem().code());
		// the same modules: marking them outdated must not look like new metadata to anything comparing them
		assertEquals(fresh, stale);
	}

}
