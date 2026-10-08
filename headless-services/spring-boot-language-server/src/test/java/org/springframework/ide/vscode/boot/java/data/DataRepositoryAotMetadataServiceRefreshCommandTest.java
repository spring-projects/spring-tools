/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.Command;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ide.vscode.boot.java.BuildCommandProvider;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IProjectBuild;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;

/**
 * "Refresh AOT metadata" command for Maven and Gradle projects.
 *
 * @author Alex Boyko
 */
public class DataRepositoryAotMetadataServiceRefreshCommandTest {

	@TempDir
	Path projectDir;

	private BuildCommandProvider buildCmds;
	private DataRepositoryAotMetadataService service;

	@BeforeEach
	void setup() {
		buildCmds = mock(BuildCommandProvider.class);
		when(buildCmds.executeGradleBuild(any(), anyString())).thenReturn(new Command("Execute Gradle Build", "gradle.runBuild"));
		when(buildCmds.executeMavenGoal(any(), anyString())).thenReturn(new Command("Execute Maven Goal", "maven.goal.custom"));
		service = new DataRepositoryAotMetadataService(null, null, buildCmds);
	}

	private IJavaProject project(String buildType, String buildFile, String bootJar, File outputFolder) throws Exception {
		return project(buildType, buildFile, null, bootJar, outputFolder);
	}

	private IJavaProject project(String buildType, String buildFile, Set<String> tasks, String bootJar, File outputFolder) throws Exception {
		IJavaProject project = mock(IJavaProject.class);
		IClasspath classpath = mock(IClasspath.class);
		when(project.getClasspath()).thenReturn(classpath);
		when(project.getProjectBuild()).thenReturn(IProjectBuild.create(buildType, projectDir.resolve(buildFile).toUri(), tasks));
		when(classpath.findBinaryLibraryByName("spring-boot")).thenReturn(bootJar == null ? Optional.empty() : Optional.of(CPE.binary("/repo/" + bootJar)));
		CPE source = CPE.source(projectDir.resolve("src/main/java").toFile(), outputFolder);
		source.setOwn(true);
		when(classpath.getClasspathEntries()).thenReturn(List.of(source));
		return project;
	}

	private IJavaProject gradleProject(String bootJar, File outputFolder) throws Exception {
		return project(ProjectBuild.GRADLE_PROJECT_TYPE, "build.gradle", bootJar, outputFolder);
	}

	private IJavaProject mavenProject(File outputFolder) throws Exception {
		return project(ProjectBuild.MAVEN_PROJECT_TYPE, "pom.xml", "spring-boot-4.0.0.jar", outputFolder);
	}

	private String mavenGoal(IJavaProject project) {
		ArgumentCaptor<String> goal = ArgumentCaptor.forClass(String.class);
		verify(buildCmds).executeMavenGoal(any(), goal.capture());
		return goal.getValue();
	}

	private String gradleCommand(IJavaProject project) {
		ArgumentCaptor<String> command = ArgumentCaptor.forClass(String.class);
		verify(buildCmds).executeGradleBuild(any(), command.capture());
		return command.getValue();
	}

	@Test
	void compiledProjectRunsAotClasses() throws Exception {
		Path classes = Files.createDirectories(projectDir.resolve("build/classes/java/main"));
		Files.createFile(classes.resolve("App.class"));
		IJavaProject project = gradleProject("spring-boot-4.0.0.jar", classes.toFile());

		assertTrue(service.regenerateMetadataCommand(project).isPresent());

		assertEquals("aotClasses", gradleCommand(project));
	}

	@Test
	void notCompiledProjectCompilesFirst() throws Exception {
		IJavaProject project = gradleProject("spring-boot-4.0.0.jar", projectDir.resolve("build/classes/java/main").toFile());

		assertTrue(service.regenerateMetadataCommand(project).isPresent());

		assertEquals("classes aotClasses", gradleCommand(project));
	}

	@Test
	void projectWithoutProcessAotTaskHasNoCommand() throws Exception {
		IJavaProject project = project(ProjectBuild.GRADLE_PROJECT_TYPE, "build.gradle", Set.of("build", "bootJar"),
				"spring-boot-4.0.0.jar", projectDir.toFile());

		assertFalse(service.regenerateMetadataCommand(project).isPresent());
		verify(buildCmds, never()).executeGradleBuild(any(), anyString());
	}

	@Test
	void mavenCompiledProjectRunsProcessAotGoal() throws Exception {
		Path classes = Files.createDirectories(projectDir.resolve("target/classes"));
		Files.createFile(classes.resolve("App.class"));
		IJavaProject project = mavenProject(classes.toFile());

		assertTrue(service.regenerateMetadataCommand(project).isPresent());

		assertEquals("org.springframework.boot:spring-boot-maven-plugin:process-aot", mavenGoal(project));
	}

	@Test
	void mavenNotCompiledProjectCompilesFirst() throws Exception {
		IJavaProject project = mavenProject(projectDir.resolve("target/classes").toFile());

		assertTrue(service.regenerateMetadataCommand(project).isPresent());

		assertEquals("compile org.springframework.boot:spring-boot-maven-plugin:process-aot", mavenGoal(project));
	}

	@Test
	void unknownBuildTypeHasNoCommand() throws Exception {
		IJavaProject project = project("other", "build.xyz", "spring-boot-4.0.0.jar", projectDir.toFile());

		assertFalse(service.regenerateMetadataCommand(project).isPresent());
	}

	@Test
	void clientWithoutSupportGetsNoCommand() throws Exception {
		when(buildCmds.executeGradleBuild(any(), anyString())).thenReturn(null);
		IJavaProject project = gradleProject("spring-boot-4.0.0.jar", projectDir.toFile());

		assertFalse(service.regenerateMetadataCommand(project).isPresent());
	}

}
