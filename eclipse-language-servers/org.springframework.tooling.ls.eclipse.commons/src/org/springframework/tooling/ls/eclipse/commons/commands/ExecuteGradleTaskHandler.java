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
package org.springframework.tooling.ls.eclipse.commons.commands;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.tooling.model.eclipse.EclipseProject;
import org.eclipse.buildship.core.GradleCore;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.configuration.BuildConfiguration;
import org.eclipse.buildship.core.internal.launch.GradleLaunchConfigurationManager;
import org.eclipse.buildship.core.internal.launch.GradleRunConfigurationAttributes;
import org.eclipse.buildship.core.internal.util.gradle.HierarchicalElementUtils;
import org.eclipse.buildship.core.internal.util.variable.ExpressionUtils;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.command.LSPCommandHandler;
import org.eclipse.lsp4j.Command;
import org.eclipse.swt.widgets.Display;
import org.springframework.tooling.ls.eclipse.commons.LanguageServerCommonsActivator;

import com.google.common.base.Optional;
import com.google.gson.Gson;

@SuppressWarnings("restriction")
public class ExecuteGradleTaskHandler extends AbstractHandler {

	// Gradle command line options that are followed by a value
	private static final Set<String> OPTIONS_WITH_VALUE = Set.of("-I", "--init-script", "-c", "--settings-file", "-b",
			"--build-file", "-g", "--gradle-user-home", "-p", "--project-dir", "--project-cache-dir", "-x",
			"--exclude-task");

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Command cmd = new Gson().fromJson(event.getParameter(LSPCommandHandler.LSP_COMMAND_PARAMETER_ID),
				Command.class);
		if (cmd != null && cmd.getArguments() != null && cmd.getArguments().size() >= 2) {
			if (Display.getCurrent() != null) {
				// Do not block the UI thread: the launch only returns when the build is done
				Job job = new Job("Gradle build") {
					@Override
					protected IStatus run(IProgressMonitor monitor) {
						try {
							runBuild(cmd);
							return Status.OK_STATUS;
						} catch (ExecutionException e) {
							return Status.error(e.getMessage(), e);
						}
					}
				};
				job.setUser(true);
				job.schedule();
			} else {
				// Returns when the build has finished, so the caller (e.g. another job) can run something after it
				runBuild(cmd);
			}
			return null;
		} else {
			throw new ExecutionException("Gradle build command is invalid");
		}
	}

	private void runBuild(Command cmd) throws ExecutionException {
		try {
			String buildGradlePath = (String) cmd.getArguments().get(0);
			GradleCommandLine commandLine = GradleCommandLine.parse((String) cmd.getArguments().get(1));
			String javaHome = cmd.getArguments().size() > 2 ? javaHome(cmd.getArguments().get(2)) : null;
			boolean refreshProject = cmd.getArguments().size() > 3 && refreshProject(cmd.getArguments().get(3));

			IResource buildGradle = LSPEclipseUtils.findResourceFor(Paths.get(buildGradlePath).toUri());
			IProject project = buildGradle.getProject();
			EclipseProject gradleProject = GradleCore.getWorkspace().getBuild(project).map(build -> {
				try {
					return build.withConnection(conn -> conn.getModel(EclipseProject.class),
							new NullProgressMonitor());
				} catch (Exception e) {
					throw new RuntimeException(
							"Failed to get Gradle EclipseProject model for project: " + project.getName(), e);
				}
			}).get();

			File rootDir = HierarchicalElementUtils.getRoot(gradleProject).getProjectDirectory();
			File workingDir = gradleProject.getProjectDirectory();
			GradleRunConfigurationAttributes configurationAttributes = getRunConfigurationAttributes(rootDir,
					workingDir, commandLine, javaHome);

			// Buildship saves the launch configuration and reuses a saved one with the same attributes
			GradleLaunchConfigurationManager launchConfigurations = CorePlugin.gradleLaunchConfigurationManager();
			boolean existed = launchConfigurations.getRunConfiguration(configurationAttributes).isPresent();
			ILaunchConfiguration launchConfig = launchConfigurations.getOrCreateRunConfiguration(configurationAttributes);
			try {
				// Buildship's launch delegate only returns once the build has finished
				launchConfig.launch(ILaunchManager.RUN_MODE, new NullProgressMonitor());
			} finally {
				if (refreshProject) {
					// The build writes files (e.g. the AOT metadata) the workspace does not know about yet.
					// Synchronize the project, which refreshes the workspace and updates the source folders.
					synchronize(project);
				}
				if (!existed) {
					// Created for this build only, the build can use files that are gone after it (an init
					// script). Do not leave it around, but never delete a configuration of the user.
					deleteLaunchConfiguration(launchConfig);
				}
			}
		} catch (Exception e) {
			throw new ExecutionException("Failed to execute Gradle build command", e);
		}
	}

	private static void synchronize(IProject project) {
		try {
			GradleCore.getWorkspace().getBuild(project)
					.ifPresent(build -> build.synchronize(new NullProgressMonitor()));
		} catch (Exception e) {
			LanguageServerCommonsActivator.logError(e, "Failed to synchronize project " + project.getName());
		}
	}

	private static void deleteLaunchConfiguration(ILaunchConfiguration launchConfig) {
		try {
			launchConfig.delete();
		} catch (CoreException e) {
			LanguageServerCommonsActivator.logError(e, "Failed to delete the launch configuration " + launchConfig.getName());
		}
	}

	/**
	 * Gradle command line split into the tasks and the options (such as <code>-I &lt;init script&gt;</code>) that
	 * have to be passed to Gradle as arguments and not as tasks.
	 */
	record GradleCommandLine(List<String> tasks, List<String> options) {

		static GradleCommandLine parse(String command) {
			List<String> tasks = new ArrayList<>();
			List<String> options = new ArrayList<>();
			String[] tokens = command.trim().split("\\s+");
			for (int i = 0; i < tokens.length; i++) {
				String token = tokens[i];
				if (token.isEmpty()) {
					continue;
				}
				if (token.startsWith("-")) {
					options.add(token);
					if (OPTIONS_WITH_VALUE.contains(token) && i + 1 < tokens.length) {
						options.add(tokens[++i]);
					}
				} else {
					tasks.add(token);
				}
			}
			return new GradleCommandLine(tasks, options);
		}
	}

	// Same options as the `gradle.runBuild` command of vscode-gradle: { "refreshJavaProject": true }
	private static boolean refreshProject(Object options) {
		return options instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("refreshJavaProject"));
	}

	private static String javaHome(Object env) {
		if (env instanceof Map<?, ?> map && map.get("JAVA_HOME") instanceof String javaHome && !javaHome.isBlank()) {
			return javaHome;
		}
		return null;
	}

	private static GradleRunConfigurationAttributes getRunConfigurationAttributes(File rootDir, File workingDir,
			GradleCommandLine commandLine, String javaHome) {
		BuildConfiguration buildConfig = CorePlugin.configurationManager().loadBuildConfiguration(rootDir);
		List<String> arguments = new ArrayList<>(buildConfig.getArguments());
		arguments.addAll(commandLine.options());
		// The Java home of the project (if known) wins over the one in the build configuration
		File javaHomeDir = javaHome != null ? new File(javaHome) : buildConfig.getJavaHome();
		return new GradleRunConfigurationAttributes(commandLine.tasks(), projectDirectoryExpression(workingDir),
				buildConfig.getGradleDistribution().toString(),
				gradleUserHomeExpression(buildConfig.getGradleUserHome()),
				javaHomeExpression(javaHomeDir), buildConfig.getJvmArguments(),
				arguments, buildConfig.isShowExecutionsView(), buildConfig.isShowConsoleView(),
				// Buildship only applies the arguments and the Java home of the launch configuration if it
				// overrides the build settings of the project. All other settings are taken from the build
				// configuration above, so this does not change them.
				true, buildConfig.isOfflineMode(), buildConfig.isBuildScansEnabled());
	}

	private static String projectDirectoryExpression(File rootProjectDir) {
		// return the directory as an expression if the project is part of the
		// workspace, otherwise
		// return the absolute path of the project directory available on the Eclipse
		// project model
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
