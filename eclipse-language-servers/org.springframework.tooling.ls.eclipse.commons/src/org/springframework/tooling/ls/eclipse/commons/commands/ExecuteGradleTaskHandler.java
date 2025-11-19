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
package org.springframework.tooling.ls.eclipse.commons.commands;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.configuration.BuildConfiguration;
import org.eclipse.buildship.core.internal.launch.GradleRunConfigurationAttributes;
import org.eclipse.buildship.core.internal.util.variable.ExpressionUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.command.LSPCommandHandler;
import org.eclipse.lsp4j.Command;

import com.google.common.base.Optional;
import com.google.gson.Gson;

@SuppressWarnings("restriction")
public class ExecuteGradleTaskHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Command cmd = new Gson().fromJson(event.getParameter(LSPCommandHandler.LSP_COMMAND_PARAMETER_ID), Command.class);
		if (cmd != null) {
			try {
				String projectPath = (String) cmd.getArguments().get(0);
				String task = (String) cmd.getArguments().get(1);
				System.out.println("Project: '%s', task: '%s'".formatted(projectPath, task));

				IResource projectFolder = LSPEclipseUtils.findResourceFor(Paths.get(projectPath).toUri());
				File workingFolder = projectFolder.getLocation().toFile();
		        GradleRunConfigurationAttributes configurationAttributes = getRunConfigurationAttributes(workingFolder, workingFolder, Arrays.asList(task.split("\\s+")));

		        // create/reuse a launch configuration for the given attributes
		        ILaunchConfiguration launchConfig = CorePlugin.gradleLaunchConfigurationManager().getOrCreateRunConfiguration(configurationAttributes);

				DebugUITools.launch(launchConfig, ILaunchManager.RUN_MODE);
			} catch (Exception e) {
				throw new ExecutionException("Failed to execute Maven Goal command", e);
			}
		}
		throw new ExecutionException("Maven Goal Execution command is invalid");
	}

    private static GradleRunConfigurationAttributes getRunConfigurationAttributes(File rootDir, File workingDir, List<String> tasks) {
        BuildConfiguration buildConfig = CorePlugin.configurationManager().loadBuildConfiguration(rootDir);
        return new GradleRunConfigurationAttributes(tasks,
                                                    projectDirectoryExpression(workingDir),
                                                    buildConfig.getGradleDistribution().toString(),
                                                    gradleUserHomeExpression(buildConfig.getGradleUserHome()),
                                                    javaHomeExpression(buildConfig.getJavaHome()),
                                                    buildConfig.getJvmArguments(),
                                                    buildConfig.getArguments(),
                                                    buildConfig.isShowExecutionsView(),
                                                    buildConfig.isShowExecutionsView(),
                                                    buildConfig.isOverrideWorkspaceSettings(),
                                                    buildConfig.isOfflineMode(),
                                                    buildConfig.isBuildScansEnabled());
    }

    private static String projectDirectoryExpression(File rootProjectDir) {
        // return the directory as an expression if the project is part of the workspace, otherwise
        // return the absolute path of the project directory available on the Eclipse project model
        Optional<IProject> project = CorePlugin.workspaceOperations().findProjectByLocation(rootProjectDir);
        if (project.isPresent()) {
            return ExpressionUtils.encodeWorkspaceLocation(project.get());
        } else {
            return rootProjectDir.getAbsolutePath();
        }
    }

    private static String gradleUserHomeExpression(File gradleUserHome) {
        return gradleUserHome == null ? "" : gradleUserHome.getAbsolutePath();
    }

    private static String javaHomeExpression(File javaHome) {
        return javaHome == null ? "" : javaHome.getAbsolutePath();
    }
}
