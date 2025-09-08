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

import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.stereotypes.IndexBasedStereotypeFactory;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

import com.google.gson.JsonPrimitive;

public class SpringIndexCommands {
	
	private static final String SPRING_STRUCTURE_CMD = "sts/spring-boot/structure";
	private static final Logger log = LoggerFactory.getLogger(SpringIndexCommands.class);
	
	private final SpringMetamodelIndex springIndex;
	private final ModulithService modulithService;
	private final StereotypeCatalogRegistry stereotypeCatalogRegistry;
	
	public SpringIndexCommands(SimpleLanguageServer server, SpringMetamodelIndex springIndex, ModulithService modulithService, JavaProjectFinder projectFinder, StereotypeCatalogRegistry stereotypeCatalogRegistry) {
		this.springIndex = springIndex;
		this.modulithService = modulithService;
		this.stereotypeCatalogRegistry = stereotypeCatalogRegistry;
		
		server.onCommand(SPRING_STRUCTURE_CMD, params -> server.getAsync().invoke(() -> {
			boolean updateMetadata =
					params.getArguments() != null
					&& params.getArguments().size() == 1
					&& params.getArguments().get(0) instanceof JsonPrimitive
					? ((JsonPrimitive) params.getArguments().get(0)).getAsBoolean() : false;

			return projectFinder.all().stream()
					.map(project -> nodeFrom(project, updateMetadata))
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
		}));
	}
	
	private Node nodeFrom(IJavaProject project, boolean updateMetadata) {
		log.info("create structural view tree information for project: " + project.getElementName());
		
		if (updateMetadata) {
			stereotypeCatalogRegistry.reset(project);
			log.info("stereotype registry reset for project: " + project.getElementName());
		}
		
		var catalog = stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, springIndex);
		
		if (System.getProperty("enable-source-defined-stereotypes") != null) {
			factory.registerStereotypeDefinitions();
		}
		
		if (ModulithService.isModulithDependentProject(project) && System.getProperty("disable-modulith-structure-view") == null) {
			return new ModulithStructureView(catalog, springIndex, modulithService).createTree(project, factory);
		}
		else {
			return new JMoleculesStructureView(catalog, springIndex).createTree(project, factory);
		}
	}

}
