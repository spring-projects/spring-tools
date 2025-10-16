/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
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

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
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
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.java.commands.WorkspaceBootExecutableProjects.ExecutableProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class WorkspaceBootExecutableProjectsTest {
	
	private static final String PROJECT_NAME = "test-spring-find-executable-projects";
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex symbolIndex;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		harness.setGavSupplier(ProjectsHarness.GAV_SUPPLIER);
	}
	
	@SuppressWarnings("unchecked")
	@Test 
	void singleProject() throws Exception {
		MavenJavaProject project = ProjectsHarness.INSTANCE.mavenProject(PROJECT_NAME);
		
		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(project.getProjectBuild().getBuildFile().toASCIIString())).get();
		
		CompletableFuture<Void> initProject = symbolIndex.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
		
		List<WorkspaceBootExecutableProjects.ExecutableProject> res = (List<WorkspaceBootExecutableProjects.ExecutableProject>) harness.getServer().getWorkspaceService().executeCommand(new ExecuteCommandParams(WorkspaceBootExecutableProjects.CMD, Collections.emptyList())).get();
		assertNotNull(res);
		assertEquals(1,  res.size());
		ExecutableProject execProject = res.get(0);
		assertEquals(PROJECT_NAME, execProject.name());
		assertEquals("org.test.MainClass", execProject.mainClass());
		assertEquals(project.getLocationUri().toASCIIString(), execProject.uri());
		assertEquals("com.example:test-spring-find-executable-projects:0.0.1-SNAPSHOT", execProject.gav());
		assertEquals(100, execProject.classpath().size());
		
		assertTrue(execProject.classpath().stream().map(path -> Path.of(path)).anyMatch(p -> p.endsWith("target/classes")));
		assertFalse(execProject.classpath().stream().map(path -> Path.of(path)).anyMatch(p -> p.endsWith("target/test-classes")));
	}
	
	@SuppressWarnings("unchecked")
	@Test
	void multipleProjects() throws Exception {
		MavenJavaProject project1 = ProjectsHarness.INSTANCE.mavenProject(PROJECT_NAME);
		MavenJavaProject project2 = ProjectsHarness.INSTANCE.mavenProject("test-spring-data-symbols");
		ProjectsHarness.INSTANCE.mavenProject("test-annotation-indexing-non-boot-project");
		ProjectsHarness.INSTANCE.mavenProject("test-xml-validations");
		
		
		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(project1.getProjectBuild().getBuildFile().toASCIIString())).get();
		projectFinder.find(new TextDocumentIdentifier(project2.getProjectBuild().getBuildFile().toASCIIString())).get();
		
		CompletableFuture<Void> initProject = symbolIndex.waitOperation();
		initProject.get(10, TimeUnit.SECONDS);
		
		List<WorkspaceBootExecutableProjects.ExecutableProject> res = (List<WorkspaceBootExecutableProjects.ExecutableProject>) harness.getServer().getWorkspaceService().executeCommand(new ExecuteCommandParams(WorkspaceBootExecutableProjects.CMD, Collections.emptyList())).get();
		assertNotNull(res);
		assertEquals(2,  res.size());
		
		ExecutableProject execProject1 = res.stream().filter(p -> PROJECT_NAME.equals(p.name())).findFirst().orElseThrow();
		assertEquals(PROJECT_NAME, execProject1.name());
		assertEquals("org.test.MainClass", execProject1.mainClass());
		assertEquals(project1.getLocationUri().toASCIIString(), execProject1.uri());
		assertEquals("com.example:test-spring-find-executable-projects:0.0.1-SNAPSHOT", execProject1.gav());
		assertEquals(100, execProject1.classpath().size());
		assertTrue(execProject1.classpath().stream().map(path -> Path.of(path)).anyMatch(p -> p.endsWith("target/classes")));
		assertFalse(execProject1.classpath().stream().map(path -> Path.of(path)).anyMatch(p -> p.endsWith("target/test-classes")));
		
		ExecutableProject execProject2 = res.stream().filter(p -> "test-spring-data-symbols".equals(p.name())).findFirst().orElseThrow();
		assertEquals("test-spring-data-symbols", execProject2.name());
		assertEquals("org.test.Application", execProject2.mainClass());
		assertEquals(project2.getLocationUri().toASCIIString(), execProject2.uri());
		assertEquals("com.example:test-spring-data-symbols:0.0.1-SNAPSHOT", execProject2.gav());
		assertEquals(79, execProject2.classpath().size());
		assertTrue(execProject2.classpath().stream().map(path -> Path.of(path)).anyMatch(p -> p.endsWith("target/classes")));
		assertFalse(execProject2.classpath().stream().map(path -> Path.of(path)).anyMatch(p -> p.endsWith("target/test-classes")));
		
	}

}
