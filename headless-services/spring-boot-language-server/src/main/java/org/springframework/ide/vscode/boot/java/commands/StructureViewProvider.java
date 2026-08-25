/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
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
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.stereotypes.IndexBasedStereotypeFactory;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeDefinitionLocator;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * Creates the logical structure (structure view) tree for a project, independent of the
 * protocol that the tree is delivered over.
 *
 * <p>The same tree is served to the IDE clients via the {@code sts/spring-boot/structure}
 * LSP command (see {@link SpringIndexCommands}) and to MCP clients via a dedicated MCP tool,
 * so both render the exact same structure.
 *
 * @author Martin Lippert
 */
public class StructureViewProvider {

	private static final Logger log = LoggerFactory.getLogger(StructureViewProvider.class);

	private final SpringMetamodelIndex springIndex;
	private final ModulithService modulithService;
	private final StereotypeCatalogRegistry stereotypeCatalogRegistry;
	private final SourceLinks sourceLinks;
	private final StereotypeDefinitionLocator definitionLocator;

	public StructureViewProvider(SpringMetamodelIndex springIndex, ModulithService modulithService,
			StereotypeCatalogRegistry stereotypeCatalogRegistry, SourceLinks sourceLinks) {

		this.springIndex = springIndex;
		this.modulithService = modulithService;
		this.stereotypeCatalogRegistry = stereotypeCatalogRegistry;
		this.sourceLinks = sourceLinks;
		this.definitionLocator = new StereotypeDefinitionLocator();
	}

	/**
	 * Creates the structure tree for a single project, using a freshly created index cache.
	 *
	 * @see #createTree(IJavaProject, CachedSpringMetamodelIndex, boolean, Collection)
	 */
	public Node createTree(IJavaProject project, boolean updateMetadata, Collection<String> selectedGroups) {
		return createTree(project, new CachedSpringMetamodelIndex(springIndex), updateMetadata, selectedGroups);
	}

	/**
	 * Creates the structure tree for a single project.
	 *
	 * @param project        the project to create the tree for
	 * @param cachedIndex    index cache to read the elements from, can be shared across projects
	 * @param updateMetadata whether to reset the stereotype catalog and re-request modulith metadata first
	 * @param selectedGroups identifiers of the groups to structure the tree by, all groups of the
	 *                       project catalog are used when null
	 * @return the root node of the tree, or null if no tree could be created for the project
	 */
	public Node createTree(IJavaProject project, CachedSpringMetamodelIndex cachedIndex, boolean updateMetadata,
			Collection<String> selectedGroups) {

		log.info("create structural view tree information for project: " + project.getElementName());

		if (updateMetadata) {
			stereotypeCatalogRegistry.reset(project);
			log.info("stereotype registry reset for project: " + project.getElementName());
		}

		var catalog = stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, project, cachedIndex);

		if (StructureViewUtil.hasSourceDefinedStereotypesEnabled()) {
			factory.registerStereotypeDefinitions();
		}

		if (selectedGroups == null) {
			selectedGroups = catalog.getGroups().stream().map(group -> group.getIdentifier()).toList();
		}

		if (ModulithService.isModulithDependentProject(project) && StructureViewUtil.hasModulithStructureViewEnabled()) {
			return new ModulithStructureView(catalog, cachedIndex, sourceLinks, definitionLocator, modulithService).createTree(project, factory, selectedGroups, updateMetadata);
		}
		else {
			return new JMoleculesStructureView(catalog, cachedIndex, sourceLinks, definitionLocator).createTree(project, factory, selectedGroups);
		}
	}

	/**
	 * The groups that the structure tree of the given project can be structured by.
	 */
	public Groups getGroups(IJavaProject project) {
		var catalog = stereotypeCatalogRegistry.getCatalogOf(project);

		List<Group> groups = catalog.getGroups().stream()
			.map(group -> new Group(group.getIdentifier(), group.getDisplayName()))
			.toList();

		return new Groups(project.getElementName(), groups);
	}

	public static record Groups (String projectName, List<Group> groups) {}
	public static record Group (String identifier, String displayName) {}

}
