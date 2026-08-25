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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
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

	private final StructureViewProvider structureViewProvider;

	private final Executor messageWorkerThreadPool;

	public SpringIndexCommands(SimpleLanguageServer server, SpringMetamodelIndex springIndex,
			JavaProjectFinder projectFinder, StructureViewProvider structureViewProvider) {

		this.structureViewProvider = structureViewProvider;
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
						.map(project -> structureViewProvider.createTree(project, cachedIndex, args.updateMetadata,
								args.selectedGroups == null ? null : args.selectedGroups.get(project.getElementName())))
						.filter(Objects::nonNull)
						.collect(Collectors.toList());
			}, messageWorkerThreadPool);
		});
		
		server.onCommand(SPRING_STRUCTURE_GROUPS_CMD, params -> {
			return CompletableFuture.supplyAsync(() -> {
				if (params.getArguments().size() == 1) {
					Object o = params.getArguments().get(0);
					String name = null;
					if (o instanceof JsonElement) {
						name = ((JsonElement) o).getAsString();
					} else if (o instanceof String) {
						name = (String) o;
					}
					if (name != null) {
						final String projectName = name;
						return projectFinder.all().stream().filter(p -> projectName.equals(p.getElementName())).findFirst().map(structureViewProvider::getGroups).orElseThrow();
					}
				}
				return projectFinder.all().stream().map(structureViewProvider::getGroups).toList();

			}, messageWorkerThreadPool);
		});
	}

	private static record StructureCommandArgs(boolean updateMetadata, List<String> affectedProjects, Map<String, Set<String>> selectedGroups) {
		
		public static StructureCommandArgs parseFrom(ExecuteCommandParams params) {
			boolean updateMetadata = false;
			Map<String, Set<String>> selectedGroups = null;
			List<String> affectedProjects = null;
			
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
				}
			}
			
			return new StructureCommandArgs(updateMetadata, affectedProjects, selectedGroups);
		}
	}

}
