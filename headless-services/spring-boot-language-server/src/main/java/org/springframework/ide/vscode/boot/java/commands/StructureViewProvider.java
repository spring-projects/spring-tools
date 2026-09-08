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

import org.eclipse.lsp4j.Location;
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
	 * Creates the structure tree for a project, fetching the module metadata and retrying if the
	 * first attempt comes back empty - for Spring Modulith projects the tree cannot be built without
	 * it.
	 *
	 * @throws IllegalStateException if no tree can be built for the project even then
	 */
	public Node createCompleteTree(IJavaProject project) {
		Node root = createTree(project, false, null);

		if (root == null) {
			root = createTree(project, true, null);
		}

		if (root == null) {
			throw new IllegalStateException("no logical structure available for project with name " + project.getElementName());
		}

		return root;
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

	/**
	 * Same as {@link #toStructureNode(Node)}, but without the source locations.
	 *
	 * <p>For the trees that only ever get diffed: nodes are matched by kind and label and compared
	 * on icon, hover and content hash, so a location is never read - while being roughly a third of
	 * what a persisted baseline costs on disk, rewritten on every capture.
	 */
	public static StructureNode toComparableNode(Node node) {
		if (node == null) {
			return null;
		}

		return new StructureNode(
				stringAttribute(node, JsonNodeHandler.NODE_ID),
				stringAttribute(node, JsonNodeHandler.TEXT),
				stringAttribute(node, JsonNodeHandler.ICON),
				stringAttribute(node, JsonNodeHandler.KIND),
				stringAttribute(node, JsonNodeHandler.HOVER),
				stringAttribute(node, JsonNodeHandler.CONTENT_HASH),
				null,
				null,
				node.getChildren().stream().map(StructureViewProvider::toComparableNode).toList());
	}

	/**
	 * Converts a {@link Node} tree, as built by {@link #createTree}, into a protocol-agnostic
	 * {@link StructureNode} tree - the shape used by MCP tools and by the snapshot/diff machinery,
	 * decoupled from {@link JsonNodeHandler}'s internal attribute map.
	 */
	public static StructureNode toStructureNode(Node node) {
		if (node == null) {
			return null;
		}

		List<StructureNode> children = node.getChildren().stream()
				.map(StructureViewProvider::toStructureNode)
				.toList();

		return new StructureNode(
				stringAttribute(node, JsonNodeHandler.NODE_ID),
				stringAttribute(node, JsonNodeHandler.TEXT),
				stringAttribute(node, JsonNodeHandler.ICON),
				stringAttribute(node, JsonNodeHandler.KIND),
				stringAttribute(node, JsonNodeHandler.HOVER),
				stringAttribute(node, JsonNodeHandler.CONTENT_HASH),
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

	/**
	 * A node of the logical structure tree, mirroring the nodes that the language server sends to the
	 * IDE clients via the {@code sts/spring-boot/structure} command.
	 *
	 * @param nodeId    stable identifier of the node within the tree, built from the path of its ancestors
	 * @param text      the label to display for this node
	 * @param icon      identifier of the icon to display for this node (may be null)
	 * @param kind      discriminates the kind of element this node represents (e.g. "type", "method",
	 *                  "stereotype"), independent of its label or icon
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
			String kind,
			String hover,
			String contentHash,
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

}
