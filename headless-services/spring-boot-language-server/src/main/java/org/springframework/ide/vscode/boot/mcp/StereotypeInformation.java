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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jmolecules.stereotype.api.Stereotypes;
import org.jmolecules.stereotype.catalog.StereotypeDefinition;
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.AsciiStructureRenderer;
import org.springframework.ide.vscode.boot.java.commands.CachedSpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.GitBaselineTracker;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.commands.StructureSnapshotStore;
import org.springframework.ide.vscode.boot.java.commands.StructureSnapshotStore.BaselineHistoryEntry;
import org.springframework.ide.vscode.boot.java.commands.StructureSnapshotStore.StructureSnapshot;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.StructureTreeDiff;
import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider;
import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider.StructureNode;
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
	private final StructureSnapshotStore structureSnapshotStore;
	private final GitBaselineTracker gitBaselineTracker;
	private final SpringSymbolIndex symbolIndex;

	public StereotypeInformation(ProjectLookup projects, SpringMetamodelIndex springIndex,
			StereotypeCatalogRegistry stereotypeCatalogRegistry, StructureViewProvider structureViewProvider,
			StructureSnapshotStore structureSnapshotStore, GitBaselineTracker gitBaselineTracker, SpringSymbolIndex symbolIndex) {
		this.projects = projects;
		this.springIndex = springIndex;
		this.stereotypeCatalogRegistry = stereotypeCatalogRegistry;
		this.structureViewProvider = structureViewProvider;
		this.structureSnapshotStore = structureSnapshotStore;
		this.gitBaselineTracker = gitBaselineTracker;
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
		gitBaselineTracker.syncBaselineWithGit(project);

		return StructureViewProvider.toStructureNode(structureViewProvider.createCompleteTree(project));
	}

	@Tool(description = """
			Captures the current logical structure of the given project as a baseline snapshot, so that a later
			call to getLogicalStructureChanges can show what changed in the logical structure of the project since
			this point in time (e.g. after a refactoring or a series of edits).
			Capturing a new baseline replaces any previously captured baseline for the same project. Usually not
			needed for git-backed projects: those get a baseline automatically on every commit, so that changes
			are always shown against the last commit - call this only to pin a baseline mid-branch, on top of
			uncommitted changes. Note such a manual baseline is replaced automatically by the next commit's.
			Use getProjectList to obtain valid project names.
			""")
	public String captureLogicalStructureBaseline(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)") String projectName)
			throws Exception {

		IJavaProject project = projects.get(projectName);
		symbolIndex.waitOperation().get(10, TimeUnit.SECONDS);

		// no commit information on purpose: a manual capture is normally taken over uncommitted
		// work, so it represents no commit even though one is checked out
		StructureSnapshot snapshot = structureSnapshotStore.captureBaseline(project);
		return "captured logical structure baseline for project '%s' with %d node(s) at %s"
				.formatted(project.getElementName(), snapshot.nodeCount(), snapshot.capturedAt());
	}

	@Tool(description = """
			Removes the captured logical structure baseline for the given project, if any, so getLogicalStructureChanges
			reports "no baseline" again until a new one is captured. Purely a manual undo: for a git-backed project
			with automatic baseline capture enabled, a fresh baseline is captured again as soon as no source changes
			are pending, and otherwise at its next commit.
			Use getProjectList to obtain valid project names.
			""")
	public String clearLogicalStructureBaseline(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)") String projectName)
			throws Exception {

		IJavaProject project = projects.get(projectName);
		boolean hadBaseline = structureSnapshotStore.clearBaseline(project);

		return hadBaseline
				? "cleared the logical structure baseline for project '%s'".formatted(project.getElementName())
				: "project '%s' did not have a logical structure baseline captured".formatted(project.getElementName());
	}

	@Tool(description = """
			Shows what changed in the logical structure of the given project since the baseline captured via
			captureLogicalStructureBaseline (or automatically from the project's git history, if it's git-backed -
			see the note on captureLogicalStructureBaseline), rendered as an ascii-art tree with +/-/~ markers for
			added, removed and modified nodes.
			Returns a plain message instead of a tree when no baseline exists yet for the project, or when nothing
			changed since it.
			Use getProjectList to obtain valid project names.
			""")
	public String getLogicalStructureChanges(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)") String projectName,
			@ToolParam(description = "whether to also show the unchanged parts of the tree instead of collapsing them, defaults to false", required = false) Boolean includeUnchanged)
			throws Exception {

		IJavaProject project = projects.get(projectName);
		symbolIndex.waitOperation().get(10, TimeUnit.SECONDS);

		Optional<StructureTreeDiff> diff = structureSnapshotStore.diffAgainstBaseline(project);

		if (diff.isEmpty()) {
			return "no logical structure baseline captured yet for project '%s' - call captureLogicalStructureBaseline first".formatted(project.getElementName());
		}

		StructureTreeDiff structureTreeDiff = diff.get();
		if (structureTreeDiff.stats().hasChanges()) {
			return AsciiStructureRenderer.render(structureTreeDiff, includeUnchanged != null && includeUnchanged);
		}
		else {
			return "no changes detected in the logical structure of project '%s' since the baseline snapshot"
					.formatted(project.getElementName());
		}
	}

	@Tool(description = """
			Lists the retained logical structure baseline snapshots for the given project, most recent first, each
			with the git commit it was captured at (sha and message) and when it was captured. The most recent entry
			is always the one getLogicalStructureChanges compares against; how many are retained is controlled by
			the boot-java.structure.baseline-history-size setting (10 by default). Useful to see recent history or
			to recognize which commit a highlighted diff is being compared against.
			Use getProjectList to obtain valid project names.
			""")
	public List<BaselineHistoryEntry> getLogicalStructureBaselineHistory(
			@ToolParam(description = "IDE project name from getProjectList().projectName (case-insensitive match)") String projectName)
			throws Exception {

		IJavaProject project = projects.get(projectName);
		return structureSnapshotStore.historyEntriesOf(project);
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
