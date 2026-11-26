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
package org.springframework.ide.vscode.boot.java;

import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.Command;
import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * Build commands that the client runs. The arguments are the build file, the goals or the command line of the build,
 * and the environment to run it with.
 */
public class ClientCommandBuildCommandProvider implements BuildCommandProvider {

	private final String mavenGoalCommand;

	private final String gradleBuildCommand;

	private ClientCommandBuildCommandProvider(String mavenGoalCommand, String gradleBuildCommand) {
		this.mavenGoalCommand = mavenGoalCommand;
		this.gradleBuildCommand = gradleBuildCommand;
	}

	/**
	 * The build commands registered by the Spring Boot extension for VS Code, which Theia runs too.
	 */
	public static ClientCommandBuildCommandProvider forVSCode() {
		return new ClientCommandBuildCommandProvider("vscode-spring-boot.maven.goal", "vscode-spring-boot.gradle.build");
	}

	/**
	 * The commands of Maven for Java and Gradle for Java, which the Eclipse client handles under the same IDs.
	 */
	public static ClientCommandBuildCommandProvider forEclipse() {
		return new ClientCommandBuildCommandProvider("maven.goal.custom", "gradle.runBuild");
	}

	@Override
	public Command executeMavenGoal(IJavaProject project, String goal) {
		Command cmd = new Command();
		cmd.setCommand(mavenGoalCommand);
		cmd.setTitle("Execute Maven Goal");
		cmd.setArguments(List.of(
				Paths.get(project.getProjectBuild().getBuildFile()).toFile().toString(),
				goal,
				BuildCommandProvider.buildEnv(project)
		));
		return cmd;
	}

	@Override
	public Command executeGradleBuild(IJavaProject project, String command) {
		Command cmd = new Command();
		cmd.setCommand(gradleBuildCommand);
		cmd.setTitle("Execute Gradle Build");
		cmd.setArguments(List.of(
				Paths.get(project.getProjectBuild().getBuildFile()).toFile().toString(),
				command,
				BuildCommandProvider.buildEnv(project)
		));
		return cmd;
	}

}
