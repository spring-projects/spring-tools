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

import java.util.List;
import java.util.function.BiConsumer;

import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.jmolecules.stereotype.tooling.HierarchicalNodeHandler;
import org.jmolecules.stereotype.tooling.ProjectTree;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.ApplicationModulesStructureProvider.SimpleApplicationModulesStructureProvider;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.stereotypes.IndexBasedStereotypeFactory;
import org.springframework.ide.vscode.boot.modulith.AppModules;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public class ModulithStructureView {

	private final AbstractStereotypeCatalog catalog;
	private final CachedSpringMetamodelIndex springIndex;
	private final ModulithService modulithService;

	public ModulithStructureView(AbstractStereotypeCatalog catalog, CachedSpringMetamodelIndex springIndex, ModulithService modulithService) {
		this.catalog = catalog;
		this.springIndex = springIndex;
		this.modulithService = modulithService;
	}

	public Node createTree(IJavaProject project, IndexBasedStereotypeFactory factory, List<String> selectedGroups) {

		var adapter = new ModulithStereotypeFactoryAdapter(factory);

		AppModules modulesData = modulithService.getModulesData(project);
		if (modulesData == null) {
			// TODO; logging
			return null;
		}

		ApplicationModules modules = new ApplicationModules(modulesData);

		var labelProvider = new ApplicationModulesLabelProvider(catalog, project, springIndex, modules);
		
		

		// json output
		BiConsumer<Node, NamedInterfaceNode> consumer = (node, c) -> {
			node.withAttribute(HierarchicalNodeHandler.TEXT, labelProvider.getCustomLabel(c))
			.withAttribute(JsonNodeHandler.ICON, c.getIcon());
		};

		// create json nodes to display the structure in a nice way
		var jsonHandler = new JsonNodeHandler<ApplicationModules, NamedInterfaceNode>(labelProvider, consumer, springIndex, catalog);

		// create the project tree and apply all the groupers from the project
		// TODO: in the future, we need to trim this grouper arrays down to what is selected on the UI
		var jsonTree = new ProjectTree<>(adapter, catalog, jsonHandler);

		if ("true".equals(System.getProperty("disable-named-interfaces"))) {
			jsonTree = jsonTree.withStructureProvider(new SimpleApplicationModulesStructureProvider(project, springIndex));
		} else {
			jsonTree = jsonTree.withStructureProvider(new ApplicationModulesNamedInterfacesGroupingProvider(modules, project, springIndex));
		}

		List<String[]> groupers = StructureViewUtil.identifyGroupers(catalog, selectedGroups);
		for (String[] grouper : groupers) {
			jsonTree = jsonTree.withGrouper(grouper);
		}

		jsonTree.process(modules);

		return jsonHandler.getRoot();
	}
}
