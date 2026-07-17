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
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.util.OS;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class DefaultBuildCommandProvider implements BuildCommandProvider {
	
	private static final String CMD_EXEC_MAVEN_GOAL = "sts.maven.goal";
	private static final String CMD_EXEC_GRADLE_BUILD = "sts.gradle.build";
	
	private static final Object MAVEN_LOCK = new Object(); 
	
	public DefaultBuildCommandProvider(SimpleLanguageServer server) {
		
		// Execute Maven Goal
		server.onCommand(CMD_EXEC_MAVEN_GOAL, params -> {
			String pomPath = extractString(params.getArguments().get(0));
			String goal = extractString(params.getArguments().get(1));
			Map<String, String> env = params.getArguments().size() > 2 ? extractEnv(params.getArguments().get(2)) : null;
			return CompletableFuture.runAsync(() -> {
				try {
					executeMaven(Paths.get(pomPath), goal.trim().split("\\s+"), env).get();
				} catch (Exception e) {
					throw new CompletionException(e);
				}
			});
		});
		
		// Execute Gradle Build
		server.onCommand(CMD_EXEC_GRADLE_BUILD, params -> {
			String gradleBuildPath = extractString(params.getArguments().get(0));
			String command = extractString(params.getArguments().get(1));
			Map<String, String> env = params.getArguments().size() > 2 ? extractEnv(params.getArguments().get(2)) : null;
			return CompletableFuture.runAsync(() -> {
				try {
					executeGradle(Paths.get(gradleBuildPath), command.trim().split("\\s+"), env).get();
				} catch (Exception e) {
					throw new CompletionException(e);
				}
			});
		});
	}
	
	@Override
	public Command executeMavenGoal(IJavaProject project, String goal) {
		Command cmd = new Command();
		cmd.setCommand(CMD_EXEC_MAVEN_GOAL);
		cmd.setTitle("Execute Maven Goal");
		List<Object> args = new ArrayList<>(List.of(
				Paths.get(project.getProjectBuild().getBuildFile()).toFile().toString(), goal));
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
		List<Object> args = new ArrayList<>(List.of(
				Paths.get(project.getProjectBuild().getBuildFile()).toFile().toString(), command));
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

	private CompletableFuture<Void> executeMaven(Path pom, String[] goal, Map<String, String> env) {
		synchronized(MAVEN_LOCK) {
			Path projectPath = pom.getParent();
			Path mvnw = projectPath.resolve(OS.isWindows() ? "mvnw.cmd" : "mvnw");
			List<String> cmdList = new ArrayList<>();
			cmdList.add(Files.isRegularFile(mvnw) ? mvnw.toFile().toString() : "mvn");
			cmdList.addAll(Arrays.asList(goal));
			try {
				ProcessBuilder pb = new ProcessBuilder(cmdList);
				pb.directory(projectPath.toFile());
				if (env != null && !env.isEmpty()) {
					pb.environment().putAll(env);
				}
				return pb.start().onExit().thenAccept(process -> {
					if (process.exitValue() != 0) {
						throw new CompletionException("Failed to execute Maven goal", new IllegalStateException("Errors running maven command: %s".formatted(String.join(" ", cmdList))));
					}
				});
			} catch (IOException e) {
				throw new CompletionException(e);
			}
		}
	}

	private CompletableFuture<Void> executeGradle(Path gradleBuildPath, String[] command, Map<String, String> env) {
		Path projectPath = gradleBuildPath.getParent();
		Path gradlew = projectPath.resolve(OS.isWindows() ? "gradlew.cmd" : "gradlew");
		List<String> cmdList = new ArrayList<>();
		cmdList.add(Files.isRegularFile(gradlew) ? gradlew.toFile().toString() : "gradle");
		cmdList.addAll(Arrays.asList(command));
		try {
			ProcessBuilder pb = new ProcessBuilder(cmdList);
			pb.directory(projectPath.toFile());
			if (env != null && !env.isEmpty()) {
				pb.environment().putAll(env);
			}
			return pb.start().onExit().thenAccept(process -> {
				if (process.exitValue() != 0) {
					throw new CompletionException("Failed to execute Gradle build", new IllegalStateException("Errors running gradle command: %s".formatted(String.join(" ", cmdList))));
				}
			});
		} catch (IOException e) {
			throw new CompletionException(e);
		}
	}
}
