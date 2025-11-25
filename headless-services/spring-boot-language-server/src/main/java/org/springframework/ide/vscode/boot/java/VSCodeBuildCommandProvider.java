/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
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

public class VSCodeBuildCommandProvider implements BuildCommandProvider {

	@Override
	public Command executeMavenGoal(IJavaProject project, String goal) {
		Command cmd = new Command();
		cmd.setCommand("maven.goal.custom");
		cmd.setTitle("Execute Maven Goal");
		cmd.setArguments(List.of(Paths.get(project.getProjectBuild().getBuildFile()).toFile().toString(), goal));
		return cmd;
	}

	@Override
	public Command executeGradleBuild(IJavaProject project, String command) {
		Command cmd = new Command();
		cmd.setCommand("gradle.runBuild");
		cmd.setTitle("Execute Gradle Build");
		cmd.setArguments(List.of(Paths.get(project.getProjectBuild().getBuildFile()).toFile().toString(), command));
		return cmd;
	}

}
