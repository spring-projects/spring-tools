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

import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;

import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.jmolecules.stereotype.tooling.HierarchicalNodeHandler;
import org.jmolecules.stereotype.tooling.ProjectTree;
import org.jmolecules.stereotype.tooling.SimpleLabelProvider;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeDefinitionLocator;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public class JMoleculesStructureView {

	private final AbstractStereotypeCatalog catalog;
	private final SourceLinks sourceLinks;
	private final StereotypeDefinitionLocator definitionLocator;

	public JMoleculesStructureView(AbstractStereotypeCatalog catalog, SourceLinks sourceLinks,
			StereotypeDefinitionLocator definitionLocator) {
		this.catalog = catalog;
		this.sourceLinks = sourceLinks;
		this.definitionLocator = definitionLocator;
	}

	public Node createTree(IJavaProject project, StructureElements elements, Collection<String> selectedGroups) {

		StereotypePackageElement mainApplicationPackage = elements.mainApplicationPackage();

		var labelProvider = new SimpleLabelProvider<>(StereotypePackageElement::getPackageName, StereotypePackageElement::getPackageName, StereotypeClassElement::getType,
				(StereotypeMethodElement m, StereotypeClassElement __) -> m.getMethodName(), Object::toString)
				.withTypeLabel(it -> StructureViewUtil.abbreviate(mainApplicationPackage, it))
				.withMethodLabel((m, c) -> elements.methodLabel(m, c))
				.withPackageLabel((p) -> StructureViewUtil.getPackageLabel(p))
				.withStereotypeLabel((s) -> StructureViewUtil.getStereotypeLabeler(catalog).apply(s))
				.withApplicationLabel((p) -> project.getElementName());

		var structureProvider = new ToolsStructureProvider(elements);

		// json output
		BiConsumer<Node, Object> consumer = (node, c) -> {
			node.withAttribute(HierarchicalNodeHandler.TEXT, labelProvider.getCustomLabel(c))
			 .withAttribute(JsonNodeHandler.ICON, "fa-named-interface");
		};

		// create json nodes to display the structure in a nice way
		var jsonHandler = new JsonNodeHandler<StereotypePackageElement, Object>(labelProvider, consumer, elements, sourceLinks, definitionLocator, catalog, project);

		// create the project tree and apply all the groupers from the project
		var jsonTree = new ProjectTree<>(elements.stereotypeFactory(), catalog, jsonHandler)
				.withStructureProvider(structureProvider);

		List<String[]> groupers = StructureViewUtil.identifyGroupers(catalog, selectedGroups);
		for (String[] grouper : groupers) {
			jsonTree = jsonTree.withGrouper(grouper);
		}

		jsonTree.process(mainApplicationPackage);

		return jsonHandler.getRoot();
	}

}
