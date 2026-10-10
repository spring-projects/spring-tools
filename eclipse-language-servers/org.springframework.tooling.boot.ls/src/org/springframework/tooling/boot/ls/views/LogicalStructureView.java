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
package org.springframework.tooling.boot.ls.views;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.lsp4j.Location;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.part.ViewPart;
import org.springframework.tooling.boot.ls.views.StructureClient.Groups;
import org.springframework.tooling.boot.ls.views.StructureClient.StructureParameter;
import org.springframework.tooling.ls.eclipse.commons.LanguageServerCommonsActivator;
import org.springframework.tooling.ls.eclipse.commons.SpringIndexState;


/**
 * Traditional Eclipse view for displaying logical structure as a tree.
 * 
 * @author Alex Boyko
 */
public class LogicalStructureView extends ViewPart {

	public static final String ID = "org.springframework.tooling.boot.ls.views.LogicalStructureView";
	
	/**
	 * Upper bound on how many changed nodes are revealed after a refresh. A huge set of changes (a
	 * baseline captured before a large refactoring, for example) would otherwise turn into a long
	 * series of expansions.
	 */
	private static final int MAX_REVEALED_NODES = 50;
	
	private TreeViewer treeViewer;
	
	final private StructureClient structureClient = new StructureClient();
	
	final private GroupingRepository groupingRepository = new GroupingRepository();
	
	final private StructureTreeMerger structureMerger = new StructureTreeMerger();
	
	final private StructureViewState viewState = new StructureViewState();
	
	final private BaselineCommands baselineCommands = new BaselineCommands(this, structureClient, viewState);
	
	final private DependencyCommands dependencyCommands = new DependencyCommands(this, structureClient, viewState);
	
	private final List<StateToggleAction> toggleActions = new ArrayList<>();
	
	// what the latest request asked for, to tell whether the trees at hand carry what is shown now
	private boolean requestedChanges;
	private boolean requestedDependencies;
	
	private boolean diffEnabledTold;
	
	private String lastRevealedChanges;
	
	private final Runnable viewStateListener = () -> runInUI(this::viewStateChanged);
	
	private Consumer<Set<String>> indexStateListener = projectNames -> fetchStructure(projectNames, false);
	
	void fetchStructure(Set<String> affectedProjects, boolean updateMetadata) {
		int requestNumber = structureMerger.startRequest();
		StructureParameter parameter = new StructureParameter(updateMetadata, affectedProjects, getGroupings(),
				viewState.getCompareAgainstForRequest(), viewState.isShowingChanges(), viewState.getDependenciesForRequest());
		requestedChanges = parameter.changes();
		requestedDependencies = viewState.isIncludeDependencies();
		structureClient.fetchStructure(parameter)
				.thenAccept(nodes -> {
					UI.getDisplay().asyncExec(() -> {
						if (treeViewer.getControl().isDisposed()) {
							return;
						}
						Object[] expanded = treeViewer.getExpandedElements();
						treeViewer.setInput(structureMerger.merge(requestNumber, affectedProjects, nodes));
						treeViewer.setExpandedElements(expanded);
						if (!diffEnabledTold && viewState.isShowingChanges()) {
							// changes were shown already when the view opened, which the language server is told
							// once it answers a request
							tellDiffEnabled();
						}
						revealChangedNodes();
					});
				});
	}
	
	CompletableFuture<List<Groups>> fetchGroups() {
		return structureClient.fetchGroups();
	}

	@SuppressWarnings("restriction")
	@Override
	public void createPartControl(Composite parent) {
		treeViewer = new TreeViewer(parent, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL);
		
		// Set up content provider
		treeViewer.setContentProvider(new StructureTreeContentProvider(viewState::isHideUnchanged));
		
		// Set up label provider
		StructureTreeLabelProvider labelProvider = new StructureTreeLabelProvider(viewState::isHighlightChanges);
		treeViewer.setLabelProvider(new DelegatingStyledCellLabelProvider(labelProvider) {
			@Override
			public String getToolTipText(Object element) {
				return labelProvider.getToolTipText(element);
			}
		});
		ColumnViewerToolTipSupport.enableFor(treeViewer);
		
		// Set initial input - placeholder data
		treeViewer.setInput(Collections.emptyList());
		
		fetchStructure(null, false);
		
		final SpringIndexState springIndexState = LanguageServerCommonsActivator.getInstance().getSpringIndexState();
		
		springIndexState.addStateChangedListener(indexStateListener);
		
		viewState.addListener(viewStateListener);
		
		treeViewer.getControl().addDisposeListener(e -> {
			springIndexState.removeStateChangedListener(indexStateListener);
			viewState.removeListener(viewStateListener);
		});
		
		treeViewer.addDoubleClickListener(e -> {
			Object o = ((IStructuredSelection) e.getSelection()).getFirstElement();
			if (o instanceof StereotypeNode) {
				StereotypeNode n = (StereotypeNode) o;
				Location l = n.location();
				if (l == null) {
					l = n.reference();
				}
				if (l != null) {
					LSPEclipseUtils.openInEditor(l);
				} else if (n.javaElement() != null) {
					// a node read from a JAR dependency
					JavaElementOpener.open(getSite().getShell(), n.text(), n.javaElement());
				}
			}
		});
		
		// Make the viewer available for selection
		getSite().setSelectionProvider(treeViewer);
		
		MenuManager menuManager = new MenuManager("#PopupMenu");
		menuManager.setRemoveAllWhenShown(true);
		menuManager.addMenuListener(this::fillContextMenu);
		treeViewer.getControl().setMenu(menuManager.createContextMenu(treeViewer.getControl()));
		getSite().registerContextMenu(menuManager, treeViewer);
		
		initActions(getViewSite().getActionBars());
	}

	private void initActions(IActionBars actionBars) {
		actionBars.getToolBarManager().add(new GroupingAction(this));
		actionBars.getToolBarManager().add(new RefreshAction(this));
		actionBars.getToolBarManager().add(new ExpandCollapseAction(this, true));
		actionBars.getToolBarManager().add(new ExpandCollapseAction(this, false));
		
		StateToggleAction highlightChanges = new StateToggleAction("Highlight Changes",
				"Highlight the nodes that changed since the baseline captured for their project",
				viewState::isHighlightChanges, this::setHighlightChanges);
		StateToggleAction hideUnchanged = new StateToggleAction("Hide Unchanged Nodes",
				"Show only the nodes that changed since the baseline captured for their project, and the path leading to them",
				viewState::isHideUnchanged, this::setHideUnchanged);
		StateToggleAction includeDependencies = new StateToggleAction("Include Dependencies",
				"Include the elements of the dependencies selected for a project in its tree. Showing changes is off then.",
				viewState::isIncludeDependencies, viewState::setIncludeDependencies);
		toggleActions.add(highlightChanges);
		toggleActions.add(hideUnchanged);
		toggleActions.add(includeDependencies);
		actionBars.getMenuManager().add(includeDependencies);
		actionBars.getMenuManager().add(highlightChanges);
		actionBars.getMenuManager().add(hideUnchanged);
	}

	private void fillContextMenu(IMenuManager menu) {
		Object selected = ((IStructuredSelection) treeViewer.getSelection()).getFirstElement();
		if (!(selected instanceof StereotypeNode node)) {
			return;
		}

		if (node.isProject()) {
			String projectName = node.getProjectId();
			menu.add(new Action("Select Dependencies...") {
				@Override
				public void run() {
					dependencyCommands.select(projectName);
				}
			});

			// no baselines, and no changes to show, for a tree with dependencies included
			if (!viewState.isIncludeDependencies()) {
				menu.add(new Separator());
				menu.add(new Action("Capture Logical Structure Baseline") {
					@Override
					public void run() {
						baselineCommands.capture(projectName);
					}
				});
				menu.add(new Action("Clear Logical Structure Baseline") {
					@Override
					public void run() {
						baselineCommands.clear(projectName);
					}
				});
				menu.add(new Action("Select Baseline to Compare Against...") {
					@Override
					public void run() {
						baselineCommands.select(projectName);
					}
				});
			}
		}
		// deliberately keyed off the actual change state rather than the highlighting toggle: the
		// changes are there to look at either way
		if (!viewState.isIncludeDependencies() && node.change() != null && node.location() != null) {
			menu.add(new ShowChangesAction(getSite().getShell(), node.location()));
		}
	}

	private void setHighlightChanges(boolean on) {
		viewState.setHighlightChanges(on);
		if (on) {
			tellDiffEnabled();
		}
	}

	private void setHideUnchanged(boolean on) {
		viewState.setHideUnchanged(on);
		if (on) {
			tellDiffEnabled();
		}
	}

	/**
	 * Tells the language server that changes are shown - when the user turns that on, and once per
	 * session when it was on already at startup, as soon as the language server answered a request.
	 * The server decides whether to ask anything.
	 */
	private void tellDiffEnabled() {
		diffEnabledTold = true;
		structureClient.diffEnabled();
	}

	/**
	 * Something of the view state changed: the trees are either fetched again, when the ones at hand
	 * do not carry what is shown now, or just shown differently.
	 */
	private void viewStateChanged() {
		if (treeViewer.getControl().isDisposed()) {
			return;
		}
		toggleActions.forEach(StateToggleAction::update);
		lastRevealedChanges = null;

		// a tree with or without dependencies is a different tree, and so is one with or without the
		// information about the changes - which is only asked for when it is shown
		if (viewState.isIncludeDependencies() != requestedDependencies || (viewState.isShowingChanges() && !requestedChanges)) {
			fetchStructure(null, false);
		} else {
			treeViewer.refresh();
			revealChangedNodes();
		}
	}

	/**
	 * Expands the tree just far enough to show the nodes that changed since the baseline, leaving
	 * unchanged branches as the user left them.
	 */
	private void revealChangedNodes() {
		if (!viewState.isShowingChanges() || !getSite().getPage().isPartVisible(this)) {
			// there is no visual cue that would explain an auto-expand, or nobody to see it
			return;
		}

		List<StereotypeNode> changed = StereotypeNode.deepestChangedNodes(structureMerger.rootElements());

		// Only expand when the set of changes itself is different from what was expanded last time, so
		// that a branch the user collapsed on purpose does not spring open again on every unrelated
		// index update.
		String changeKey = String.join("\n", changed.stream().map(StereotypeNode::id).toList());
		if (changeKey.equals(lastRevealedChanges)) {
			return;
		}
		lastRevealedChanges = changeKey;

		for (StereotypeNode node : changed.subList(0, Math.min(changed.size(), MAX_REVEALED_NODES))) {
			List<StereotypeNode> ancestors = new ArrayList<>();
			for (StereotypeNode ancestor = node.parent(); ancestor != null; ancestor = ancestor.parent()) {
				ancestors.add(0, ancestor);
			}
			// the node itself does not need expanding, only its ancestors do
			ancestors.forEach(ancestor -> treeViewer.setExpandedState(ancestor, true));
		}
	}

	void runInUI(Runnable runnable) {
		UI.getDisplay().asyncExec(() -> {
			if (!treeViewer.getControl().isDisposed()) {
				runnable.run();
			}
		});
	}

	void setStatusMessage(String message) {
		getViewSite().getActionBars().getStatusLineManager().setMessage(message);
	}

	@Override
	public void setFocus() {
		treeViewer.getControl().setFocus();
	}

	void setGroupings(Map<String, List<String>> groupings) {
		groupingRepository.saveWorkspaceGroupings(groupings);
	}
	
	Map<String, List<String>> getGroupings() {
		return groupingRepository.getWorkspaceGroupings();
	}
	
	void expandAll() {
		treeViewer.expandAll();
	}
	
	void collapseAll() {
		treeViewer.collapseAll();
	}

}

