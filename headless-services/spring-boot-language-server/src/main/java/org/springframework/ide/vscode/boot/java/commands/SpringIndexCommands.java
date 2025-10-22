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
package org.springframework.ide.vscode.boot.java.commands;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.stereotypes.IndexBasedStereotypeFactory;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
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

	private static final Logger log = LoggerFactory.getLogger(SpringIndexCommands.class);
	
	private final ModulithService modulithService;
	private final StereotypeCatalogRegistry stereotypeCatalogRegistry;
	private final SourceLinks sourceLinks;
	
	public SpringIndexCommands(SimpleLanguageServer server, SpringMetamodelIndex springIndex, ModulithService modulithService,
			JavaProjectFinder projectFinder, StereotypeCatalogRegistry stereotypeCatalogRegistry, SourceLinks sourceLinks) {

		this.modulithService = modulithService;
		this.stereotypeCatalogRegistry = stereotypeCatalogRegistry;
		this.sourceLinks = sourceLinks;
		
		server.onCommand(SPRING_STRUCTURE_CMD, params -> server.getAsync().invoke(() -> {
			StructureCommandArgs args = StructureCommandArgs.parseFrom(params);

			CachedSpringMetamodelIndex cachedIndex = new CachedSpringMetamodelIndex(springIndex);
			return projectFinder.all().stream()
					.sorted(Comparator.comparing(IJavaProject::getElementName))
					.map(project -> nodeFrom(project, cachedIndex, args.updateMetadata,
							args.selectedGroups == null ? null : args.selectedGroups.get(project.getElementName())))
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
		}));

		server.onCommand(SPRING_STRUCTURE_GROUPS_CMD, params -> server.getAsync().invoke(() -> {
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
					return projectFinder.all().stream().filter(p -> projectName.equals(p.getElementName())).findFirst().map(this::getGroups).orElseThrow();
				}
			}
			return projectFinder.all().stream().map(this::getGroups).toList();
		}));
	}
	
	private Groups getGroups(IJavaProject project) {
		var catalog = stereotypeCatalogRegistry.getCatalogOf(project);
		
		List<Group> groups = catalog.getGroups().stream()
			.map(group -> new Group(group.getIdentifier(), group.getDisplayName()))
			.toList();
		
		return new Groups(project.getElementName(), groups);
	}
	
	private Node nodeFrom(IJavaProject project, CachedSpringMetamodelIndex springIndex, boolean updateMetadata, Collection<String> selectedGroups) {
		log.info("create structural view tree information for project: " + project.getElementName());
		
		if (updateMetadata) {
			stereotypeCatalogRegistry.reset(project);
			log.info("stereotype registry reset for project: " + project.getElementName());
		}
		
		var catalog = stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, project, springIndex);
		
		if (StructureViewUtil.hasSourceDefinedStereotypesEnabled()) {
			factory.registerStereotypeDefinitions();
		}
		
		if (selectedGroups == null) {
			selectedGroups = catalog.getGroups().stream().map(group -> group.getIdentifier()).toList();
		}
		
		if (ModulithService.isModulithDependentProject(project) && StructureViewUtil.hasModulithStructureViewEnabled()) {
			return new ModulithStructureView(catalog, springIndex, sourceLinks, modulithService).createTree(project, factory, selectedGroups);
		}
		else {
			return new JMoleculesStructureView(catalog, springIndex, sourceLinks).createTree(project, factory, selectedGroups);
		}
	}
	
	private static record StructureCommandArgs(boolean updateMetadata, Map<String, Set<String>> selectedGroups) {
		
		public static StructureCommandArgs parseFrom(ExecuteCommandParams params) {
			boolean updateMetadata = false;
			Map<String, Set<String>> selectedGroups = null;
			
			List<Object> arguments = params.getArguments();
			if (arguments != null && arguments.size() == 1) {
				Object object = arguments.get(0);
				if (object instanceof JsonObject) {
					JsonObject paramObject = (JsonObject) object;
					
					JsonElement jsonElement = paramObject.get("updateMetadata");
					updateMetadata = jsonElement != null && jsonElement instanceof JsonPrimitive ? jsonElement.getAsBoolean() : false;
					
					JsonElement groupsElement = paramObject.get("groups");
					if (groupsElement != null) {
						selectedGroups = new Gson().fromJson(groupsElement, new TypeToken<Map<String, Set<String>>>() {});
					}
				}
			}
			
			return new StructureCommandArgs(updateMetadata, selectedGroups);
		}		
	}
	
	private static record Groups (String projectName, List<Group> groups) {}
	private static record Group (String identifier, String displayName) {}

}
