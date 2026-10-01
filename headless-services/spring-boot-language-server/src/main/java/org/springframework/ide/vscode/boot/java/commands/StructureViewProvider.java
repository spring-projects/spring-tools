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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.lsp4j.Location;
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeDefinitionLocator;
import org.springframework.ide.vscode.boot.modulith.AppModule;
import org.springframework.ide.vscode.boot.modulith.AppModules;
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
	private final StructureDependencySources dependencySources;

	public StructureViewProvider(SpringMetamodelIndex springIndex, ModulithService modulithService,
			StereotypeCatalogRegistry stereotypeCatalogRegistry, SourceLinks sourceLinks, StructureDependencySources dependencySources) {

		this.springIndex = springIndex;
		this.modulithService = modulithService;
		this.stereotypeCatalogRegistry = stereotypeCatalogRegistry;
		this.sourceLinks = sourceLinks;
		this.dependencySources = dependencySources;
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
	 * Creates the structure tree for a single project, from the live index.
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
		return createTree(project, cachedIndex, updateMetadata, selectedGroups, List.of());
	}

	/**
	 * Same as {@link #createTree(IJavaProject, CachedSpringMetamodelIndex, boolean, Collection)},
	 * with the dependencies the user selected to include in the project's tree: their elements are
	 * built into the tree as if they were the project's own ({@link CompositeStructureElements}),
	 * resolved against a catalog of their own
	 * ({@link StereotypeCatalogRegistry#getCatalogOf(IJavaProject, Collection)}). See
	 * {@code docs/structure-view-dependencies.md}.
	 *
	 * <p>Only the dependencies' types within the project's main application package show up - the
	 * tree is rooted there.
	 *
	 * @param selectedDependencies the selected dependencies, already resolved against what the
	 *        project currently offers - empty for none, which is the project's own tree
	 */
	public Node createTree(IJavaProject project, CachedSpringMetamodelIndex cachedIndex, boolean updateMetadata,
			Collection<String> selectedGroups, List<DependencyDescriptor> selectedDependencies) {
		return createTree(project, cachedIndex, updateMetadata, selectedGroups, selectedDependencies, false);
	}

	/**
	 * @param dependenciesInBackground whether dependencies that are not ready yet - JARs still to be
	 *        scanned - are left out of this tree and prepared in the background rather than waited
	 *        for (see {@link StructureDependencySources}), so the tree is there right away - for the
	 *        IDE's view, which then gets to rebuild it once they are ready
	 */
	public Node createTree(IJavaProject project, CachedSpringMetamodelIndex cachedIndex, boolean updateMetadata,
			Collection<String> selectedGroups, List<DependencyDescriptor> selectedDependencies, boolean dependenciesInBackground) {

		log.info("create structural view tree information for project: " + project.getElementName());

		if (updateMetadata) {
			stereotypeCatalogRegistry.reset(project);
			log.info("stereotype registry reset for project: " + project.getElementName());
		}

		AppModules modules = modulesOf(project);
		List<DependencyDescriptor> dependencies = withModuleDependencies(project, cachedIndex, modules,
				selectedDependencies == null ? List.of() : selectedDependencies);

		if (dependencies.isEmpty()) {
			var catalog = stereotypeCatalogRegistry.getCatalogOf(project);
			return createTree(project, IndexStructureElements.of(project, cachedIndex, catalog), catalog, selectedGroups, updateMetadata);
		}

		var catalog = stereotypeCatalogRegistry.getCatalogOf(project, dependencies.stream().map(DependencyDescriptor::id).toList());

		StructureElements elements = new CompositeStructureElements(
				IndexStructureElements.of(project, cachedIndex, catalog),
				dependencySources.elementsOf(project, withoutProject(dependencies, project), cachedIndex, catalog,
						dependenciesInBackground),
				moduleTypesOf(modules));

		return createTree(project, elements, catalog, selectedGroups, updateMetadata);
	}

	/**
	 * Whether every dependency of the project's tree is ready - selected or providing a Spring
	 * Modulith module - so that a tree built now with them in the background (see
	 * {@link #createTree(IJavaProject, CachedSpringMetamodelIndex, boolean, Collection, List, boolean)})
	 * is complete. A tree that is not must not be compared against a baseline that has them all.
	 */
	public boolean dependenciesReady(IJavaProject project, CachedSpringMetamodelIndex cachedIndex, List<DependencyDescriptor> selectedDependencies) {
		List<DependencyDescriptor> dependencies = withModuleDependencies(project, cachedIndex, modulesOf(project),
				selectedDependencies == null ? List.of() : selectedDependencies);
		return dependencySources.allReady(project, withoutProject(dependencies, project));
	}

	/**
	 * The Spring Modulith modules of the project, when its tree is the Modulith one - {@code null}
	 * otherwise, or while there is no metadata yet.
	 */
	private AppModules modulesOf(IJavaProject project) {
		return isModulithTree(project) ? modulithService.getModulesData(project) : null;
	}

	private static boolean isModulithTree(IJavaProject project) {
		return ModulithService.isModulithDependentProject(project) && StructureViewUtil.hasModulithStructureViewEnabled();
	}

	/**
	 * The given dependencies, plus those that provide a Spring Modulith module of the project: a
	 * dependency with classes in a module's package. Spring Modulith takes a module's classes from
	 * the whole classpath, so the module is there in the project's metadata whether or not the
	 * dependency is selected - and its types have to be there in the project's tree too, or the
	 * module node stays empty.
	 */
	private List<DependencyDescriptor> withModuleDependencies(IJavaProject project, CachedSpringMetamodelIndex cachedIndex, AppModules modules,
			List<DependencyDescriptor> dependencies) {
		if (modules == null) {
			return dependencies;
		}

		List<String> basePackages = modules.stream().map(AppModule::basePackage).toList();
		List<DependencyDescriptor> providingModules = dependencySources.containing(project, basePackages, cachedIndex);

		Map<String, DependencyDescriptor> result = new LinkedHashMap<>();
		dependencies.forEach(dependency -> result.put(dependency.id(), dependency));
		providingModules.forEach(dependency -> result.putIfAbsent(dependency.id(), dependency));
		return List.copyOf(result.values());
	}

	/**
	 * The types of a Spring Modulith module - shown from a JAR whatever their stereotypes, since they
	 * belong to the module (see {@link CompositeStructureElements}).
	 */
	private static Predicate<StereotypeClassElement> moduleTypesOf(AppModules modules) {
		if (modules == null) {
			return type -> false;
		}
		return type -> modules.getModuleForPackage(StructureViewUtil.packageOf(type.getType())).isPresent();
	}

	/**
	 * A project can't include itself - it is already there.
	 */
	private static List<DependencyDescriptor> withoutProject(List<DependencyDescriptor> dependencies, IJavaProject project) {
		return dependencies.stream()
				.filter(dependency -> !project.getElementName().equals(dependency.projectName()))
				.toList();
	}

	/**
	 * Creates the structure tree for a single project from the given elements - the live index
	 * ({@link #createTree(IJavaProject, CachedSpringMetamodelIndex, boolean, Collection)} builds
	 * one of those), or a captured baseline snapshot ({@link SnapshotStructureElements}), diffed
	 * against by rebuilding it into a tree with the same tree-building code and the same
	 * presentation settings the live tree was built with (see
	 * {@code docs/structure-diff-elements.md}) - which is the entire reason this overload, rather
	 * than only the index-specific one above, exists.
	 *
	 * @param updateMetadata whether to re-request modulith metadata first - only ever meaningful,
	 *        and only ever passed as {@code true}, for the live tree; a rebuilt baseline tree is
	 *        always diffed against whatever module metadata is current, so it never needs a fresh
	 *        request of its own
	 * @param selectedGroups identifiers of the groups to structure the tree by, all groups of the
	 *                       project catalog are used when null
	 * @return the root node of the tree, or null if no tree could be created for the project
	 */
	public Node createTree(IJavaProject project, StructureElements elements, Collection<String> selectedGroups, boolean updateMetadata) {
		return createTree(project, elements, stereotypeCatalogRegistry.getCatalogOf(project), selectedGroups, updateMetadata);
	}

	private Node createTree(IJavaProject project, StructureElements elements, AbstractStereotypeCatalog catalog,
			Collection<String> selectedGroups, boolean updateMetadata) {

		if (selectedGroups == null) {
			selectedGroups = catalog.getGroups().stream().map(group -> group.getIdentifier()).toList();
		}

		if (ModulithService.isModulithDependentProject(project) && StructureViewUtil.hasModulithStructureViewEnabled()) {
			return new ModulithStructureView(catalog, sourceLinks, definitionLocator, modulithService).createTree(project, elements, selectedGroups, updateMetadata);
		}
		else {
			return new JMoleculesStructureView(catalog, sourceLinks, definitionLocator).createTree(project, elements, selectedGroups);
		}
	}

	/**
	 * Captures the project's current logical structure as a {@link StructureElementSnapshot},
	 * using a freshly created index cache - see {@code docs/structure-diff-elements.md}. Unlike
	 * {@link #createTree}, needs no Spring Modulith metadata: a snapshot captures every type,
	 * method and member unconditionally, leaving how they get grouped into a tree - by module or
	 * otherwise - entirely to whoever rebuilds one from it later.
	 */
	public StructureElementSnapshot captureSnapshot(IJavaProject project) {
		return captureSnapshot(project, new CachedSpringMetamodelIndex(springIndex));
	}

	/**
	 * Same as {@link #captureSnapshot(IJavaProject)}, but with an index cache that can be shared
	 * across projects.
	 */
	public StructureElementSnapshot captureSnapshot(IJavaProject project, CachedSpringMetamodelIndex cachedIndex) {
		// the dependencies providing a Spring Modulith module are part of the project's tree, so they
		// are part of its baseline too - waited for here, never left out
		AppModules modules = modulesOf(project);
		List<DependencyDescriptor> dependencies = withModuleDependencies(project, cachedIndex, modules, List.of());

		if (dependencies.isEmpty()) {
			var catalog = stereotypeCatalogRegistry.getCatalogOf(project);
			return StructureSnapshotBuilder.capture(IndexStructureElements.of(project, cachedIndex, catalog));
		}

		var catalog = stereotypeCatalogRegistry.getCatalogOf(project, dependencies.stream().map(DependencyDescriptor::id).toList());
		StructureElements elements = new CompositeStructureElements(
				IndexStructureElements.of(project, cachedIndex, catalog),
				dependencySources.elementsOf(project, withoutProject(dependencies, project), cachedIndex, catalog),
				moduleTypesOf(modules));

		return StructureSnapshotBuilder.capture(elements);
	}

	/**
	 * Creates the structure tree for a project, fetching the module metadata and retrying if the
	 * first attempt comes back empty - for Spring Modulith projects the tree cannot be built without
	 * it.
	 *
	 * @throws IllegalStateException if no tree can be built for the project even then
	 */
	public Node createCompleteTree(IJavaProject project) {
		return createCompleteTree(project, List.of());
	}

	/**
	 * Same as {@link #createCompleteTree(IJavaProject)}, with the given dependencies included.
	 */
	public Node createCompleteTree(IJavaProject project, List<DependencyDescriptor> selectedDependencies) {
		Node root = createTree(project, new CachedSpringMetamodelIndex(springIndex), false, null, selectedDependencies);

		if (root == null) {
			root = createTree(project, new CachedSpringMetamodelIndex(springIndex), true, null, selectedDependencies);
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
				node.getAttribute(JsonNodeHandler.JAVA_ELEMENT) instanceof JavaElementReference javaElement ? javaElement : null,
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
	 * @param javaElement the element this node stands for, by binding key, when it has no location
	 *                  - one read from a JAR dependency (may be null)
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
			JavaElementReference javaElement,
			List<StructureNode> children
	) {

		/**
		 * A node that stands for no element read from a JAR.
		 */
		public StructureNode(String nodeId, String text, String icon, String kind, String hover, String contentHash,
				SourceLocation location, SourceLocation reference, List<StructureNode> children) {
			this(nodeId, text, icon, kind, hover, contentHash, location, reference, null, children);
		}
	}

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
