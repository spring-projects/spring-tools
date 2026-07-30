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
package org.springframework.ide.vscode.boot.java;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IProjectBuild;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.ExecuteCommandHandler;
import org.springframework.ide.vscode.commons.languageserver.util.OS;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;

import com.google.gson.JsonPrimitive;

/**
 * Tests that {@link DefaultBuildCommandProvider} only executes build commands for
 * build files that belong to a currently-open project. This guards against a caller
 * pointing the server at an arbitrary {@code mvnw}/{@code gradlew} executable on disk
 * via {@code workspace/executeCommand} (SPRING-TOOLS-51).
 *
 * @author Martin Lippert
 */
@SuppressWarnings("rawtypes")
public class DefaultBuildCommandProviderTest {

	private static final String CMD_EXEC_MAVEN_GOAL = "sts.maven.goal";
	private static final String CMD_EXEC_GRADLE_BUILD = "sts.gradle.build";

	private static final String LEGIT_MAVEN_GOAL = "org.springframework.boot:spring-boot-maven-plugin:process-aot";

	@TempDir
	Path tempDir;

	private JavaProjectFinder projectFinder;
	private ExecuteCommandHandler mavenHandler;
	private ExecuteCommandHandler gradleHandler;

	@BeforeEach
	public void setup() {
		SimpleLanguageServer server = mock(SimpleLanguageServer.class);
		projectFinder = mock(JavaProjectFinder.class);

		new DefaultBuildCommandProvider(server, projectFinder);

		ArgumentCaptor<ExecuteCommandHandler> mavenCaptor = ArgumentCaptor.forClass(ExecuteCommandHandler.class);
		ArgumentCaptor<ExecuteCommandHandler> gradleCaptor = ArgumentCaptor.forClass(ExecuteCommandHandler.class);
		verify(server).onCommand(eq(CMD_EXEC_MAVEN_GOAL), mavenCaptor.capture());
		verify(server).onCommand(eq(CMD_EXEC_GRADLE_BUILD), gradleCaptor.capture());
		mavenHandler = mavenCaptor.getValue();
		gradleHandler = gradleCaptor.getValue();
	}

	@Test
	public void rejectsMavenGoalForPathOutsideOfOpenProjects() throws Exception {
		Path openPom = createBuildFile("open-project", "pom.xml");
		openProject(ProjectBuild.MAVEN_PROJECT_TYPE, openPom);

		// attacker points at a build file that is NOT part of any open project
		Path evilPom = createBuildFile("evil", "pom.xml");

		assertThrows(SecurityException.class,
				() -> mavenHandler.handle(params(evilPom, "compile")).get());
	}

	@Test
	public void rejectsGradleTaskForPathOutsideOfOpenProjects() throws Exception {
		Path openBuild = createBuildFile("open-project", "build.gradle");
		openProject(ProjectBuild.GRADLE_PROJECT_TYPE, openBuild);

		Path evilBuild = createBuildFile("evil", "build.gradle");

		ExecutionException ex = assertThrows(ExecutionException.class,
				() -> gradleHandler.handle(params(evilBuild, "build")).get());
		assertInstanceOf(SecurityException.class, ex.getCause());
	}

	@Test
	public void rejectsMavenGoalWhenNoProjectOfMatchingTypeIsOpen() throws Exception {
		// a gradle project is open, but a maven goal is requested for its build file
		Path gradleBuild = createBuildFile("open-project", "build.gradle");
		openProject(ProjectBuild.GRADLE_PROJECT_TYPE, gradleBuild);

		assertThrows(SecurityException.class,
				() -> mavenHandler.handle(params(gradleBuild, "compile")).get());
	}

	@Test
	public void acceptsLegitMavenGoalForOpenProject() throws Exception {
		Path openPom = createBuildFile("open-project", "pom.xml");
		openProject(ProjectBuild.MAVEN_PROJECT_TYPE, openPom);
		createBuildWrapper(openPom.getParent(), "mvnw");

		// completes normally (the wrapper exits with code 0)
		mavenHandler.handle(params(openPom, LEGIT_MAVEN_GOAL)).get();
	}

	@Test
	public void acceptsLegitGradleTaskForOpenProject() throws Exception {
		Path openBuild = createBuildFile("open-project", "build.gradle");
		openProject(ProjectBuild.GRADLE_PROJECT_TYPE, openBuild);
		createBuildWrapper(openBuild.getParent(), "gradlew");

		gradleHandler.handle(params(openBuild, "processAot")).get();
	}

	// --- helpers ---------------------------------------------------------

	private ExecuteCommandParams params(Path buildFile, String goal) {
		ExecuteCommandParams params = new ExecuteCommandParams();
		params.setArguments(List.of(
				new JsonPrimitive(buildFile.toFile().toString()),
				new JsonPrimitive(goal)));
		return params;
	}

	private void openProject(String type, Path buildFile) {
		IProjectBuild build = mock(IProjectBuild.class);
		when(build.getType()).thenReturn(type);
		when(build.getBuildFile()).thenReturn(buildFile.toUri());

		IJavaProject project = mock(IJavaProject.class);
		when(project.getProjectBuild()).thenReturn(build);

		doReturn(List.of(project)).when(projectFinder).all();
	}

	private Path createBuildFile(String projectDirName, String buildFileName) throws IOException {
		Path projectDir = Files.createDirectories(tempDir.resolve(projectDirName));
		Path buildFile = projectDir.resolve(buildFileName);
		Files.writeString(buildFile, "");
		return buildFile;
	}

	/**
	 * Creates a trivial build wrapper script (mvnw / gradlew) that exits successfully,
	 * so the "accepted" path can run end-to-end without invoking a real build.
	 */
	private void createBuildWrapper(Path projectDir, String baseName) throws IOException {
		if (OS.isWindows()) {
			Path wrapper = projectDir.resolve(baseName + ".cmd");
			Files.writeString(wrapper, "@echo off\r\nexit /b 0\r\n");
		} else {
			Path wrapper = projectDir.resolve(baseName);
			Files.writeString(wrapper, "#!/bin/sh\nexit 0\n");
			wrapper.toFile().setExecutable(true);
		}
	}

}
