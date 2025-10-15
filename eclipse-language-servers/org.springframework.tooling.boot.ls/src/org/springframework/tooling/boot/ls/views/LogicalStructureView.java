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

import java.util.Collections;
import java.util.function.Consumer;

import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.lsp4e.ui.UI;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;
import org.springframework.tooling.boot.ls.BootLanguageServerPlugin;
import org.springframework.tooling.boot.ls.BootLsState;


/**
 * Traditional Eclipse view for displaying logical structure as a tree.
 * 
 * @author Alex Boyko
 */
public class LogicalStructureView extends ViewPart {

	public static final String ID = "org.springframework.tooling.boot.ls.views.LogicalStructureView";
	
	private TreeViewer treeViewer;
	
	final private StructureClient client = new StructureClient();
	
	private Consumer<BootLsState> lsStateListener = state -> {
		if (state.isIndexed()) {
			fetchStructure(false);
		} else {
			UI.getDisplay().asyncExec(() -> treeViewer.setInput(null));
		}
	};
	
	void fetchStructure(boolean updateMetadata) {
		client.fetch(updateMetadata).thenAccept(nodes -> {
			UI.getDisplay().asyncExec(() -> {
				Object[] expanded = treeViewer.getExpandedElements();
				treeViewer.setInput(nodes);
				treeViewer.setExpandedElements(expanded);
			});
		});
	}

	@Override
	public void createPartControl(Composite parent) {
		treeViewer = new TreeViewer(parent, SWT.SINGLE | SWT.H_SCROLL | SWT.V_SCROLL);
		
		// Set up content provider
		treeViewer.setContentProvider(new StructureTreeContentProvider());
		
		// Set up label provider
		treeViewer.setLabelProvider(new StructureTreeLabelProvider());
		
		// Set initial input - placeholder data
		treeViewer.setInput(Collections.emptyList());
		
		BootLsState lsState = BootLanguageServerPlugin.getDefault().getLsState();
		
		if (lsState.isIndexed()) {
			client.fetch(false).thenAccept(nodes -> {
				UI.getDisplay().asyncExec(() -> {
					treeViewer.setInput(nodes);
				});
			});
		}
		
		lsState.addStateChangedListener(lsStateListener);
		
		treeViewer.getControl().addDisposeListener(e -> lsState.removeStateChangedListener(lsStateListener));
		
		// Make the viewer available for selection
		getSite().setSelectionProvider(treeViewer);
		
		initActions();
	}

	private void initActions() {
		getViewSite().getActionBars().getToolBarManager().add(new RefreshAction(this));
	}

	@Override
	public void setFocus() {
		treeViewer.getControl().setFocus();
	}
}

