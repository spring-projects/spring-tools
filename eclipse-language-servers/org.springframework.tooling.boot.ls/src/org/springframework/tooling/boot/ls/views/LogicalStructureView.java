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
package org.springframework.tooling.boot.ls.views;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
	
	private TreeViewer treeViewer;
	
	final private StructureClient structureClient = new StructureClient();
	
	final private GroupingRepository groupingRepository = new GroupingRepository();
	
	private Consumer<Set<String>> indexStateListener = projectNames -> fetchStructure(projectNames, false);
	
	void fetchStructure(Set<String> affectedProjects, boolean updateMetadata) {
		structureClient.fetchStructure(new StructureParameter(updateMetadata, affectedProjects, getGroupings()))
				.thenAccept(nodes -> {
					UI.getDisplay().asyncExec(() -> {
						Object[] expanded = treeViewer.getExpandedElements();
						if (affectedProjects == null || affectedProjects.isEmpty()) {
							treeViewer.setInput(nodes);
						} else {
							@SuppressWarnings("unchecked")
							List<StereotypeNode> oldNodes = (List<StereotypeNode>) treeViewer.getInput();
							if (oldNodes == null) {
								oldNodes = Collections.emptyList();
							}
							List<StereotypeNode> newNodes = new ArrayList<>(oldNodes.size() + nodes.size());
							Map<String, Optional<StereotypeNode>> nodesMap = affectedProjects.stream().collect(Collectors.toMap(e -> e, e -> nodes.stream().filter(n -> e.equals(n.getProjectId())).findFirst()));
							for (StereotypeNode n : oldNodes) {
								String projectName = n.getProjectId();
								if (nodesMap.containsKey(projectName)) {
									nodesMap.remove(projectName).ifPresent(newNodes::add);
								} else {
									newNodes.add(n);
								}
							}
							nodesMap.values().stream().filter(opt -> opt.isPresent()).map(opt -> opt.get()).forEach(newNodes::add);
							treeViewer.setInput(newNodes);
						}
						
						treeViewer.setExpandedElements(expanded);
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
		treeViewer.setContentProvider(new StructureTreeContentProvider());
		
		// Set up label provider
		treeViewer.setLabelProvider(new StructureTreeLabelProvider());
		
		// Set initial input - placeholder data
		treeViewer.setInput(Collections.emptyList());
		
		fetchStructure(null, false);
		
		final SpringIndexState springIndexState = LanguageServerCommonsActivator.getInstance().getSpringIndexState();
		
		springIndexState.addStateChangedListener(indexStateListener);
		
		treeViewer.getControl().addDisposeListener(e -> springIndexState.removeStateChangedListener(indexStateListener));
		
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
				}
			}
		});
		
		// Make the viewer available for selection
		getSite().setSelectionProvider(treeViewer);
		
		initActions(getViewSite().getActionBars());
	}

	private void initActions(IActionBars actionBars) {
		actionBars.getToolBarManager().add(new GroupingAction(this));
		actionBars.getToolBarManager().add(new RefreshAction(this));
		actionBars.getToolBarManager().add(new ExpandCollapseAction(this, true));
		actionBars.getToolBarManager().add(new ExpandCollapseAction(this, false));
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

