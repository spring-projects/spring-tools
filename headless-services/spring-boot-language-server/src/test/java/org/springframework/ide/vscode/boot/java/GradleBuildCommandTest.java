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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.util.Map;

import org.eclipse.lsp4j.Command;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IProjectBuild;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.java.Jre;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;

/**
 * The command that runs a Gradle build, per client (the build is what refreshes the AOT metadata of a Gradle project). The arguments are the build file, the command line of the build
 * (tasks and options) and the environment to run it with.
 *
 * @author Alex Boyko
 */
public class GradleBuildCommandTest {

	@TempDir
	Path tempDir;

	private IJavaProject project() {
		IJavaProject project = mock(IJavaProject.class);
		IClasspath classpath = mock(IClasspath.class);
		when(classpath.getJre()).thenReturn(new Jre("21", "/jdks/jdk-21"));
		when(project.getClasspath()).thenReturn(classpath);
		URI buildFile = tempDir.resolve("build.gradle").toUri();
		when(project.getProjectBuild()).thenReturn(IProjectBuild.create(ProjectBuild.GRADLE_PROJECT_TYPE, buildFile));
		return project;
	}

	private void assertCommand(Command cmd, String commandId, int argCount) {
		assertEquals(commandId, cmd.getCommand());
		assertEquals(argCount, cmd.getArguments().size());
		assertEquals(tempDir.resolve("build.gradle").toFile().toString(), cmd.getArguments().get(0));
		assertEquals("classes aotClasses", cmd.getArguments().get(1));
		assertEquals(Map.of("JAVA_HOME", "/jdks/jdk-21"), cmd.getArguments().get(2));
	}

	@Test
	void vscodeAndEclipseRunTheBuildWithTheirGradleSupport() {
		// vscode-gradle: gradle.runBuild(buildFile, command, env); Eclipse: handled by the Buildship launch of the Eclipse client
		Command cmd = new VSCodeBuildCommandProvider().executeGradleBuild(project(), "classes aotClasses");
		assertCommand(cmd, "gradle.runBuild", 4);
		// the client reloads the project after the build: the generated folders become source folders
		assertEquals(Map.of("refreshJavaProject", true), cmd.getArguments().get(3));
	}

	@Test
	void otherClientsLetTheLanguageServerRunTheBuild() {
		assertCommand(new DefaultBuildCommandProvider(mock(SimpleLanguageServer.class), mock(JavaProjectFinder.class))
				.executeGradleBuild(project(), "classes aotClasses"), "sts.gradle.build", 3);
	}

}
