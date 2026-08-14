/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
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
import java.util.List;
import java.util.Optional;
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
import org.springframework.ide.vscode.boot.mcp.ProjectInformation;
import org.springframework.ide.vscode.boot.mcp.ProjectInformation.Library;
import org.springframework.ide.vscode.boot.mcp.ProjectInformation.Project;
import org.springframework.ide.vscode.boot.mcp.ProjectLookup;
import org.springframework.ide.vscode.commons.Version;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Tests for ProjectInformation
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class ProjectInformationTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	private ProjectInformation projectInformation;
	private File directory;
	private IJavaProject project;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-request-mapping-symbols/").toURI());
		String projectDir = directory.toURI().toString();

		// trigger project creation
		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);

		// Create the MCP tools instance
		projectInformation = new ProjectInformation(new ProjectLookup(projectFinder));
	}

	@Test
	void testGetProjectList() throws Exception {
		List<Project> projects = projectInformation.getProjectList();

		assertNotNull(projects);
		assertTrue(projects.size() > 0);

		Project testProject = projects.stream()
				.filter(p -> p.projectName().equalsIgnoreCase(project.getElementName()))
				.findFirst()
				.orElse(null);

		assertNotNull(testProject);
		assertTrue(testProject.isSpringBootProject());
		assertNotNull(testProject.javaVersion());
	}

	@Test
	void testGetSpringBootVersion() throws Exception {
		Version bootVersion = projectInformation.getSpringBootVersion(project.getElementName());

		assertNotNull(bootVersion);
		assertEquals(3, bootVersion.getMajor());
		assertEquals(5, bootVersion.getMinor());
		assertEquals(6, bootVersion.getPatch());
		assertEquals("3.5.6", bootVersion.toString());
	}

	@Test
	void testGetJavaVersion() throws Exception {
		String javaVersion = projectInformation.getJavaVersion(project.getElementName());

		assertNotNull(javaVersion);
		assertFalse(javaVersion.isBlank());
	}

	@Test
	void testGetResolvedProjectClasspath() throws Exception {
		List<Library> classpath = projectInformation.getResolvedProjectClasspath(project.getElementName());

		assertNotNull(classpath);
		assertTrue(classpath.size() > 0);

		Library springBoot = classpath.stream()
				.filter(lib -> lib.name() != null && lib.name().contains("spring-boot-3"))
				.findFirst()
				.orElse(null);

		assertNotNull(springBoot);
		assertEquals("3.5.6", springBoot.version());

		assertTrue(classpath.stream().anyMatch(lib -> lib.name().contains("spring-boot-starter-web")));
		assertTrue(classpath.stream().anyMatch(lib -> lib.name().contains("spring-boot-starter-actuator")));
		
		Optional<Library> snakeyamlLib = classpath.stream().filter(lib -> lib.name().contains("snakeyaml")).findAny();
		assertTrue(snakeyamlLib.isPresent());
		
		assertEquals("2.4", snakeyamlLib.get().version());
	}

	@Test
	void testGetProjectThrowsForUnknownProject() {
		Exception exception = assertThrows(Exception.class, () -> {
			projectInformation.getSpringBootVersion("does-not-exist-project");
		});

		assertTrue(exception.getMessage().contains("not found"));
	}
}
