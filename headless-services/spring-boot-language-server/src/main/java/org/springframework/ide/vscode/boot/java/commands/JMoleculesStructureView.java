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
import org.jmolecules.stereotype.tooling.ProjectTree.TreeConfig;
import org.jmolecules.stereotype.tooling.SimpleLabelProvider;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeDefinitionLocator;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public class JMoleculesStructureView {

	/**
	 * {@link TreeConfig#defaults()}, except that a single root package gets no node of its own: its
	 * types sit right below the application node then, rather than below a package node that is the
	 * application node's only child. With several root packages - types in packages beside each
	 * other, or dependencies outside of the project's packages - each keeps its node.
	 *
	 * <p>Every flag set explicitly: the builder's own defaults are all {@code false}, unlike
	 * {@code defaults()}.
	 */
	private static final TreeConfig TREE_CONFIG = TreeConfig.builder()
			.omitSingleGroupingNodes(false)
			.showNonStereotypedTypes(true)
			.elevateMethodLevelStereotypes(true)
			.skipSinglePackageNode(true)
			.skipApplicationNode(false)
			.build();

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

		// the first level: the application, delivering the root packages of the next one
		StructureApplication application = new StructureApplication(project.getElementName(), elements.rootPackages());

		var labelProvider = new SimpleLabelProvider<>(StructureApplication::name, StereotypePackageElement::getPackageName, StereotypeClassElement::getType,
				(StereotypeMethodElement m, StereotypeClassElement __) -> m.getMethodName(), Object::toString)
				// abbreviated against the root package the type is in or below
				.withTypeLabel(it -> StructureViewUtil.abbreviate(application.rootPackageOf(it), it))
				.withMethodLabel((m, c) -> elements.methodLabel(m, c))
				.withPackageLabel((p) -> StructureViewUtil.getPackageLabel(p))
				.withStereotypeLabel((s) -> StructureViewUtil.getStereotypeLabeler(catalog).apply(s))
				.withApplicationLabel(StructureApplication::name);

		var structureProvider = new ToolsStructureProvider(elements);

		// json output
		BiConsumer<Node, Object> consumer = (node, c) -> {
			node.withAttribute(HierarchicalNodeHandler.TEXT, labelProvider.getCustomLabel(c))
			 .withAttribute(JsonNodeHandler.ICON, "fa-named-interface");
		};

		// create json nodes to display the structure in a nice way
		var jsonHandler = new JsonNodeHandler<StructureApplication, Object>(labelProvider, consumer, elements, sourceLinks, definitionLocator, catalog, project);

		// create the project tree and apply all the groupers from the project
		var jsonTree = new ProjectTree<>(elements.stereotypeFactory(), catalog, jsonHandler)
				.withStructureProvider(structureProvider)
				.withConfig(TREE_CONFIG);

		List<String[]> groupers = StructureViewUtil.identifyGroupers(catalog, selectedGroups);
		for (String[] grouper : groupers) {
			jsonTree = jsonTree.withGrouper(grouper);
		}

		jsonTree.process(application);

		return jsonHandler.getRoot();
	}

}
