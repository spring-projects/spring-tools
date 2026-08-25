/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.mcp;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Location;
import org.jmolecules.stereotype.api.Stereotypes;
import org.jmolecules.stereotype.catalog.StereotypeDefinition;
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.CachedSpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider;
import org.springframework.ide.vscode.boot.java.stereotypes.IndexBasedStereotypeFactory;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.stereotype.Component;

/**
 * @author Martin Lippert
 */
@Component
public class StereotypeInformation {

	private static final Logger logger = LoggerFactory.getLogger(StereotypeInformation.class);

	private final ProjectLookup projects;
	private final SpringMetamodelIndex springIndex;
	private final StereotypeCatalogRegistry stereotypeCatalogRegistry;
	private final StructureViewProvider structureViewProvider;
	private final SpringSymbolIndex symbolIndex;

	public StereotypeInformation(ProjectLookup projects, SpringMetamodelIndex springIndex,
			StereotypeCatalogRegistry stereotypeCatalogRegistry, StructureViewProvider structureViewProvider,
			SpringSymbolIndex symbolIndex) {
		this.projects = projects;
		this.springIndex = springIndex;
		this.stereotypeCatalogRegistry = stereotypeCatalogRegistry;
		this.structureViewProvider = structureViewProvider;
		this.symbolIndex = symbolIndex;
	}

	@Tool(description = """
			This function provides information about all the stereotype definitions that are defined and available in the given project
			""")
	public Set<StereotypeDefinition> getStereotypesList(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)") String projectName)
			throws Exception {

		IJavaProject project = projects.get(projectName);
		AbstractStereotypeCatalog catalog = this.stereotypeCatalogRegistry.getCatalogOf(project);

		return catalog.getDefinitions();
	}

	@Tool(description = """
			This function returns a list of classes or components from the given project and lists the stereotypes that each class or component has.
			This way you can identify, for example, all components from a specific stereotype (e.g. all data repositories, all services, all entities, and so on)
			""")
	public List<ComponentWithStereotypes> getListOfComponentsAndTheirStereotypes(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)") String projectName)
			throws Exception {

		IJavaProject project = projects.get(projectName);
		
		var catalog = stereotypeCatalogRegistry.getCatalogOf(project);
		var cachedIndex = new CachedSpringMetamodelIndex(springIndex);
		var factory = new IndexBasedStereotypeFactory(catalog, project, cachedIndex);

		List<StereotypeClassElement> classNodes = this.springIndex.getNodesOfType(project.getElementName(), StereotypeClassElement.class);
		return classNodes.stream()
			.map(classNode -> createComponent(classNode, factory))
			.filter(component -> component != null)
			.filter(component -> component.stereotypes.size() > 0)
			.toList();
		
	}

	@Tool(description = """
			Find all Spring components by stereotype (Controller, Service, Repository, Component, Entity, etc.).
			Returns all components that have the specified stereotype.
			""")
	public List<ComponentWithStereotypes> findComponentsByStereotype(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)") String projectName,
			@ToolParam(description = "the stereotype name to filter by (e.g., 'Controller', 'Service', 'Repository', 'Entity')") String stereotypeName)
			throws Exception {

		IJavaProject project = projects.get(projectName);
		
		var catalog = stereotypeCatalogRegistry.getCatalogOf(project);
		var cachedIndex = new CachedSpringMetamodelIndex(springIndex);
		var factory = new IndexBasedStereotypeFactory(catalog, project, cachedIndex);

		List<StereotypeClassElement> classNodes = this.springIndex.getNodesOfType(project.getElementName(), StereotypeClassElement.class);
		
		// Filter by stereotype name (case-insensitive partial match)
		String normalizedStereotypeName = stereotypeName.toLowerCase();
		return classNodes.stream()
			.map(classNode -> createComponent(classNode, factory))
			.filter(component -> component != null)
			.filter(component -> component.stereotypes.stream()
					.anyMatch(stereotype -> stereotype.toLowerCase().contains(normalizedStereotypeName)))
			.toList();
	}

	@Tool(description = """
			Returns the logical structure of the given project as a tree, the same structure that the Spring Tools
			logical structure view renders in VSCode and Eclipse.
			The tree groups the components of the project by their stereotypes (and by application modules for Spring Modulith projects),
			so it shows how the application is organized logically instead of by files and folders.
			Each node carries a display label, an icon identifier, a stable node id, the source location of the element it
			represents, and its child nodes, which is everything a client needs to render the tree itself.
			Use getProjectList to obtain valid project names. Use getStereotypesList or getListOfComponentsAndTheirStereotypes
			if you need the flat stereotype information instead of the tree.
			""")
	public StructureNode getLogicalStructure(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)") String projectName)
			throws Exception {

		logger.info("get logical structure for project: {}", projectName);

		IJavaProject project = projects.get(projectName);

		symbolIndex.waitOperation().get(10, TimeUnit.SECONDS);

		Node root = structureViewProvider.createTree(project, false, null);

		if (root == null) {
			// for Spring Modulith projects the tree cannot be created without the module metadata,
			// so try again and let the provider fetch that metadata first
			root = structureViewProvider.createTree(project, true, null);
		}

		if (root == null) {
			throw new Exception("no logical structure available for project with name " + projectName);
		}

		return structureNodeFrom(root);
	}

	/**
	 * A node of the logical structure tree, mirroring the nodes that the language server sends to the
	 * IDE clients via the {@code sts/spring-boot/structure} command.
	 *
	 * @param nodeId    stable identifier of the node within the tree, built from the path of its ancestors
	 * @param text      the label to display for this node
	 * @param icon      identifier of the icon to display for this node (may be null)
	 * @param hover     additional details to show on hover (may be null)
	 * @param location  where the element that this node represents is defined in the source code (may be null)
	 * @param reference where the stereotype of this node is defined, either in source code or in a
	 *                  stereotype catalog file (may be null)
	 * @param children  the child nodes of this node
	 */
	public static record StructureNode(
			String nodeId,
			String text,
			String icon,
			String hover,
			SourceLocation location,
			SourceLocation reference,
			List<StructureNode> children
	) {}

	/**
	 * A range within a source file, with 0-based line and character offsets.
	 */
	public static record SourceLocation(
			String uri,
			int startLine,
			int startColumn,
			int endLine,
			int endColumn
	) {}

	private StructureNode structureNodeFrom(Node node) {
		List<StructureNode> children = node.getChildren().stream()
				.map(this::structureNodeFrom)
				.toList();

		return new StructureNode(
				stringAttribute(node, JsonNodeHandler.NODE_ID),
				stringAttribute(node, JsonNodeHandler.TEXT),
				stringAttribute(node, JsonNodeHandler.ICON),
				stringAttribute(node, JsonNodeHandler.HOVER),
				sourceLocationFrom(node.getAttribute(JsonNodeHandler.LOCATION)),
				sourceLocationFrom(node.getAttribute(JsonNodeHandler.REFERENCE)),
				children);
	}

	private static String stringAttribute(Node node, String key) {
		Object value = node.getAttribute(key);
		return value == null ? null : value.toString();
	}

	private static SourceLocation sourceLocationFrom(Object attribute) {
		if (attribute instanceof Location location && location.getRange() != null) {
			return new SourceLocation(
					location.getUri(),
					location.getRange().getStart().getLine(),
					location.getRange().getStart().getCharacter(),
					location.getRange().getEnd().getLine(),
					location.getRange().getEnd().getCharacter());
		}
		return null;
	}

	public static record ComponentWithStereotypes(String name, List<String> stereotypes) {
	};
	
	private ComponentWithStereotypes createComponent(StereotypeClassElement classElement, IndexBasedStereotypeFactory factory) {
		Stereotypes stereotypes = factory.fromType(classElement);
		
		List<String> stereotypeList = stereotypes.stream()
			.map(stereotype -> stereotype.getDisplayName())
			.toList();
		
		return new ComponentWithStereotypes(classElement.getType(), stereotypeList);
	}

	//
	//
	//


}
