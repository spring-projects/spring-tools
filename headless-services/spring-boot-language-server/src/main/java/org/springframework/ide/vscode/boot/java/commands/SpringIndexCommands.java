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
package org.springframework.ide.vscode.boot.java.commands;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.commands.StructureSnapshotStore.StructureSnapshot;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;

public class SpringIndexCommands {

	private static final String SPRING_STRUCTURE_CMD = "sts/spring-boot/structure";
	private static final String SPRING_STRUCTURE_GROUPS_CMD = "sts/spring-boot/structure/groups";
	private static final String SPRING_STRUCTURE_CAPTURE_BASELINE_CMD = "sts/spring-boot/structure/captureBaseline";
	private static final String SPRING_STRUCTURE_CLEAR_BASELINE_CMD = "sts/spring-boot/structure/clearBaseline";
	private static final String SPRING_STRUCTURE_BASELINE_HISTORY_CMD = "sts/spring-boot/structure/baselineHistory";

	private final StructureViewProvider structureViewProvider;
	private final StructureSnapshotStore structureSnapshotStore;
	private final GitBaselineTracker gitBaselineTracker;

	private final Executor messageWorkerThreadPool;

	public SpringIndexCommands(SimpleLanguageServer server, SpringMetamodelIndex springIndex,
			JavaProjectFinder projectFinder, StructureViewProvider structureViewProvider,
			StructureSnapshotStore structureSnapshotStore, GitBaselineTracker gitBaselineTracker) {

		this.structureViewProvider = structureViewProvider;
		this.structureSnapshotStore = structureSnapshotStore;
		this.gitBaselineTracker = gitBaselineTracker;
		this.messageWorkerThreadPool = Executors.newCachedThreadPool();

		server.onCommand(SPRING_STRUCTURE_CMD, params -> {
			return CompletableFuture.supplyAsync(() -> {
				StructureCommandArgs args = StructureCommandArgs.parseFrom(params);

				CachedSpringMetamodelIndex cachedIndex = new CachedSpringMetamodelIndex(springIndex);
				
				Stream<? extends IJavaProject> projects = projectFinder.all().stream();
				if (args.affectedProjects != null && args.affectedProjects.size() > 0) {
					projects = projects.filter(project -> args.affectedProjects.contains(project.getElementName()));
				}

				return projects
						.parallel()
						.map(project -> createAnnotatedTree(project, cachedIndex, args))
						.filter(Objects::nonNull)
						.collect(Collectors.toList());
			}, messageWorkerThreadPool);
		});
		
		server.onCommand(SPRING_STRUCTURE_GROUPS_CMD, params -> {
			return CompletableFuture.supplyAsync(() -> {
				Optional<String> projectName = singleStringArg(params);
				if (projectName.isPresent()) {
					return projectFinder.all().stream().filter(p -> projectName.get().equals(p.getElementName())).findFirst().map(structureViewProvider::getGroups).orElseThrow();
				}
				return projectFinder.all().stream().map(structureViewProvider::getGroups).toList();

			}, messageWorkerThreadPool);
		});

		server.onCommand(SPRING_STRUCTURE_CAPTURE_BASELINE_CMD, params -> {
			return CompletableFuture.supplyAsync(() -> {
				IJavaProject project = resolveProject(params, SPRING_STRUCTURE_CAPTURE_BASELINE_CMD, projectFinder);

				// no commit information on purpose: a manual capture is normally taken over
				// uncommitted work, so it represents no commit even though one is checked out
				StructureSnapshot snapshot = structureSnapshotStore.captureBaseline(project);
				return new CaptureBaselineResult(project.getElementName(), snapshot.nodeCount(), snapshot.capturedAt().toString());
			}, messageWorkerThreadPool);
		});

		server.onCommand(SPRING_STRUCTURE_CLEAR_BASELINE_CMD, params -> {
			return CompletableFuture.supplyAsync(() -> {
				IJavaProject project = resolveProject(params, SPRING_STRUCTURE_CLEAR_BASELINE_CMD, projectFinder);

				boolean hadBaseline = structureSnapshotStore.clearBaseline(project);
				return new ClearBaselineResult(project.getElementName(), hadBaseline);
			}, messageWorkerThreadPool);
		});

		server.onCommand(SPRING_STRUCTURE_BASELINE_HISTORY_CMD, params -> {
			return CompletableFuture.supplyAsync(() -> {
				IJavaProject project = resolveProject(params, SPRING_STRUCTURE_BASELINE_HISTORY_CMD, projectFinder);
				return structureSnapshotStore.historyEntriesOf(project);
			}, messageWorkerThreadPool);
		});
	}

	/**
	 * Builds the structure tree of a project and, if a baseline was captured for that project,
	 * marks the nodes that changed since then, so clients can highlight them in their tree.
	 *
	 * <p>Before building the tree, gives the project a baseline for free if it's git-backed and
	 * doesn't have one yet (or its git HEAD moved since its last baseline) - see
	 * {@link GitBaselineTracker#syncBaselineWithGit}.
	 */
	private Node createAnnotatedTree(IJavaProject project, CachedSpringMetamodelIndex cachedIndex, StructureCommandArgs args) {
		gitBaselineTracker.syncBaselineWithGit(project);

		Node tree = structureViewProvider.createTree(project, cachedIndex, args.updateMetadata,
				args.selectedGroups == null ? null : args.selectedGroups.get(project.getElementName()));

		if (tree != null) {
			String snapshotKey = args.compareAgainst == null ? null : args.compareAgainst.get(project.getElementName());

			tree.withAttribute(JsonNodeHandler.HAS_BASELINE, structureSnapshotStore.hasBaseline(project));

			StructureSnapshot comparedAgainst = structureSnapshotStore.annotateWithChangesSinceBaseline(project, tree, snapshotKey);
			if (comparedAgainst != null) {
				// sha and message stay null for a manually captured snapshot - the capture time is
				// all there is to identify it by
				tree.withAttribute(JsonNodeHandler.COMPARED_AGAINST_SHA, comparedAgainst.commitSha());
				tree.withAttribute(JsonNodeHandler.COMPARED_AGAINST_MESSAGE, comparedAgainst.commitMessage());
				tree.withAttribute(JsonNodeHandler.COMPARED_AGAINST_CAPTURED_AT,
						comparedAgainst.capturedAt() == null ? null : comparedAgainst.capturedAt().toString());
			}
		}

		return tree;
	}

	/**
	 * Resolves the single project name argument of a structure command (see {@link #singleStringArg})
	 * to the matching project, failing with a message identifying which command and project name
	 * were involved.
	 */
	private static IJavaProject resolveProject(ExecuteCommandParams params, String commandId, JavaProjectFinder projectFinder) {
		String projectName = singleStringArg(params)
				.orElseThrow(() -> new IllegalArgumentException(commandId + " requires a project name argument"));

		return projectFinder.all().stream()
				.filter(p -> projectName.equals(p.getElementName()))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("no project found with name " + projectName));
	}

	/**
	 * Several structure commands take a single project name as their only argument, sent either as
	 * a plain JSON string or (depending on the JSON-RPC client) as a {@link JsonElement}.
	 */
	private static Optional<String> singleStringArg(ExecuteCommandParams params) {
		List<Object> arguments = params.getArguments();
		if (arguments == null || arguments.size() != 1) {
			return Optional.empty();
		}

		Object argument = arguments.get(0);
		if (argument instanceof JsonElement jsonElement && jsonElement.isJsonPrimitive()) {
			return Optional.of(jsonElement.getAsString());
		}
		else if (argument instanceof String string) {
			return Optional.of(string);
		}
		return Optional.empty();
	}

	public static record CaptureBaselineResult(String projectName, int nodeCount, String capturedAt) {}

	public static record ClearBaselineResult(String projectName, boolean hadBaseline) {}

	private static record StructureCommandArgs(boolean updateMetadata, List<String> affectedProjects, Map<String, Set<String>> selectedGroups,
			Map<String, String> compareAgainst) {

		public static StructureCommandArgs parseFrom(ExecuteCommandParams params) {
			boolean updateMetadata = false;
			Map<String, Set<String>> selectedGroups = null;
			List<String> affectedProjects = null;
			Map<String, String> compareAgainst = null;

			List<Object> arguments = params.getArguments();
			if (arguments != null && arguments.size() == 1) {
				Object object = arguments.get(0);
				if (object instanceof JsonObject) {
					JsonObject paramObject = (JsonObject) object;

					JsonElement jsonElement = paramObject.get("updateMetadata");
					updateMetadata = jsonElement != null && jsonElement instanceof JsonPrimitive ? jsonElement.getAsBoolean() : false;

					JsonElement affectedProjectsElement = paramObject.get("affectedProjects");
					if (affectedProjectsElement != null) {
						affectedProjects = new Gson().fromJson(affectedProjectsElement, new TypeToken<List<String>>(){}.getType());
					}

					JsonElement groupsElement = paramObject.get("groups");
					if (groupsElement != null) {
						selectedGroups = new Gson().fromJson(groupsElement, new TypeToken<Map<String, Set<String>>>() {});
					}

					JsonElement compareAgainstElement = paramObject.get("compareAgainst");
					if (compareAgainstElement != null) {
						compareAgainst = new Gson().fromJson(compareAgainstElement, new TypeToken<Map<String, String>>() {});
					}
				}
			}

			return new StructureCommandArgs(updateMetadata, affectedProjects, selectedGroups, compareAgainst);
		}
	}

}
