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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.eclipse.lsp4j.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.IProjectBuild;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.OS;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.java.ProjectBuild;

import com.google.gson.JsonPrimitive;

public class DefaultBuildCommandProvider implements BuildCommandProvider {

	private static final Logger log = LoggerFactory.getLogger(DefaultBuildCommandProvider.class);

	private static final String CMD_EXEC_MAVEN_GOAL = "sts.maven.goal";
	private static final String CMD_EXEC_GRADLE_BUILD = "sts.gradle.build";

	private static final Object MAVEN_LOCK = new Object();

	private final JavaProjectFinder projectFinder;

	public DefaultBuildCommandProvider(SimpleLanguageServer server, JavaProjectFinder projectFinder) {
		this.projectFinder = projectFinder;

		// Execute Maven Goal
		server.onCommand(CMD_EXEC_MAVEN_GOAL, params -> {
			String pomPath = extractString(params.getArguments().get(0));
			String goal = extractString(params.getArguments().get(1));
            Map<String, String> env = params.getArguments().size() > 2 ? extractEnv(params.getArguments().get(2)) : null;
			try {
                Path buildFile = validateOpenProjectBuildFile(pomPath, ProjectBuild.MAVEN_PROJECT_TYPE);
                String[] goals = goal.trim().split("\\s+");
				return executeMaven(buildFile, goals, env);
			} catch (Exception e) {
				return CompletableFuture.failedFuture(e);
			}
		});

		// Execute Gradle Build
		server.onCommand(CMD_EXEC_GRADLE_BUILD, params -> {
			String gradleBuildPath = extractString(params.getArguments().get(0));
			String command = extractString(params.getArguments().get(1));
            Map<String, String> env = params.getArguments().size() > 2 ? extractEnv(params.getArguments().get(2)) : null;
            try {
				Path buildFile = validateOpenProjectBuildFile(gradleBuildPath, ProjectBuild.GRADLE_PROJECT_TYPE);
				String[] tasks = command.trim().split("\\s+");
				return executeGradle(buildFile, tasks, env);
			} catch (Exception e) {
				return CompletableFuture.failedFuture(e);
			}
		});
	}

	/**
	 * Ensures the requested build-file path belongs to a currently-open project of the
	 * expected build type and returns the project's own build-file path to be
	 * used for execution.
	 */
	private Path validateOpenProjectBuildFile(String requestedPath, String expectedBuildType) {
		Path requested = canonicalize(Paths.get(requestedPath));
		for (IJavaProject project : projectFinder.all()) {
			IProjectBuild build = project.getProjectBuild();
			if (build == null || !expectedBuildType.equals(build.getType()) || build.getBuildFile() == null) {
				continue;
			}
			Path projectBuildFile = Paths.get(build.getBuildFile());
			if (canonicalize(projectBuildFile).equals(requested)) {
				return projectBuildFile;
			}
		}
		log.warn("Rejected build command for path outside of any open project: {}", requestedPath);
		throw new SecurityException("Build file does not belong to any open project: " + requestedPath);
	}

	private static Path canonicalize(Path path) {
		try {
			return path.toRealPath();
		} catch (IOException e) {
			return path.toAbsolutePath().normalize();
		}
	}
	
	@Override
	public Command executeMavenGoal(IJavaProject project, String goal) {
		Command cmd = new Command();
		cmd.setCommand(CMD_EXEC_MAVEN_GOAL);
		cmd.setTitle("Execute Maven Goal");
		List<Object> args = new ArrayList<>(3);
        args.add(Paths.get(project.getProjectBuild().getBuildFile()).toFile().toString());
        args.add(goal);
		Map<String, String> env = BuildCommandProvider.buildEnv(project);
		if (!env.isEmpty()) {
			args.add(env);
		}
		cmd.setArguments(args);
		return cmd;
	}
	
	@Override
	public Command executeGradleBuild(IJavaProject project, String command) {
		Command cmd = new Command();
		cmd.setCommand(CMD_EXEC_GRADLE_BUILD);
		cmd.setTitle("Execute Gradle Build");
		List<Object> args = new ArrayList<>(3);
        args.add(Paths.get(project.getProjectBuild().getBuildFile()).toFile().toString());
        args.add(command);
		Map<String, String> env = BuildCommandProvider.buildEnv(project);
		if (env != null && !env.isEmpty()) {
			args.add(env);
		}
		cmd.setArguments(args);
		return cmd;
	}

	private static String extractString(Object o) {
		return o instanceof JsonPrimitive ? ((JsonPrimitive) o).getAsString() : o.toString();
	}

    private static Map<String, String> extractEnv(Object o) {
        Map<String, String> env = new LinkedHashMap<>();
        if (o instanceof JsonObject jo) {
            jo.entrySet().forEach(e -> env.put(e.getKey(), e.getValue().getAsString()));
        }
        return env;
    }

    private CompletableFuture<Void> executeMaven(Path pom, String[] goal, Map<String, String> env) throws IOException {
		synchronized(MAVEN_LOCK) {
			String[] cmd = new String[1 + goal.length];
			Path projectPath = pom.getParent();
			Path mvnw = projectPath.resolve(OS.isWindows() ? "mvnw.cmd" : "mvnw");
			cmd[0] = Files.isRegularFile(mvnw) ? mvnw.toFile().toString() : "mvn";
			System.arraycopy(goal, 0, cmd, 1, goal.length);
			return runProcess(cmd, projectPath, "Failed to execute Maven goal", env);
		}
	}

	private CompletableFuture<Void> executeGradle(Path gradleBuildPath, String[] command, Map<String, String> env) throws IOException {
		String[] cmd = new String[1 + command.length];
		Path projectPath = gradleBuildPath.getParent();
		Path gradlew = projectPath.resolve(OS.isWindows() ? "gradlew.bat" : "gradlew");
		cmd[0] = Files.isRegularFile(gradlew) ? gradlew.toFile().toString() : "gradle";
		System.arraycopy(command, 0, cmd, 1, command.length);
		return runProcess(cmd, projectPath, env, "Failed to execute Gradle build");
	}

	/**
	 * Starts the given command and continuously drains its combined stdout/stderr on a
	 * separate thread so the child never blocks on a full pipe buffer. The captured
	 * output is attached to the resulting exception if the process exits with a
	 * non-zero status.
	 */
	private CompletableFuture<Void> runProcess(String[] cmd, Path workingDir, , Map<String, String> env, String failureMessage) throws IOException {
		Process process = new ProcessBuilder()
				.command(cmd)
				.directory(workingDir.toFile())
				.redirectErrorStream(true)
				.start();
		CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
			try (InputStream in = process.getInputStream()) {
				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				return "";
			}
		});
		return process.onExit().thenCombine(output, (exited, capturedOutput) -> {
			if (exited.exitValue() != 0) {
				throw new CompletionException(failureMessage, new IllegalStateException(
						"Errors running command: %s%n%s".formatted(String.join(" ", cmd), capturedOutput)));
			}
			return null;
		});
	}
}
