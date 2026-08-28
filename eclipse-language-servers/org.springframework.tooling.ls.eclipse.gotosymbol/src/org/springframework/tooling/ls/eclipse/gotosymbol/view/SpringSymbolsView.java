/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.gotosymbol.view;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.springframework.tooling.ls.eclipse.gotosymbol.GotoSymbolPlugin;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.GotoSymbolDialogModel;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.GotoSymbolSection;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.InFileSymbolsProvider;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.InProjectSymbolsProvider;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.InWorkspaceSymbolsProvider;
import org.springframework.tooling.ls.eclipse.gotosymbol.dialogs.SelectionTracker;
import org.springsource.ide.eclipse.commons.livexp.ui.ChooseOneSectionCombo;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.SimpleLabelProvider;
import org.springsource.ide.eclipse.commons.livexp.ui.ViewPartWithSections;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.common.collect.ImmutableList;

public class SpringSymbolsView extends ViewPartWithSections {

	private static String VIEW_TYPE_ID = SpringSymbolsView.class.getName();

	/**
	 * Adds scroll support to the whole view. You probably want to disable this
	 * if view is broken into pieces that have their own scrollbars
	 */
	private static final boolean ENABLE_SCROLLING = false;

	private SpringSymbolsViewModel model;

	Action newViewAction = new Action("New View", GotoSymbolPlugin.getImageDescriptor("icons/add_target.png")) {
		@Override
		public void run() {
			try {
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				if (page != null) {
					page.showView(VIEW_TYPE_ID, UUID.randomUUID().toString(), IWorkbenchPage.VIEW_ACTIVATE);
				}
			} catch (Exception e) {
				Log.log(e);
			}
		}
	};
	
	Action refreshAction = new Action("Refresh", GotoSymbolPlugin.getImageDescriptor("icons/refresh.png")) {
		@Override
		public void run() {
			model.refreshButton.increment();
		}
	};

	public SpringSymbolsView() {
		super(ENABLE_SCROLLING);
	}

	@SuppressWarnings("resource")
	@Override
	protected List<IPageSection> createSections() throws CoreException {
		List<IPageSection> sections = new ArrayList<>();
		sections.add(
			new ChooseOneSectionCombo<>(this, "Scope", 
				model.gotoSymbols.currentSymbolsProvider, 
				ImmutableList.copyOf(model.gotoSymbols.getSymbolsProviders())
			)
			.setLabelProvider(new SimpleLabelProvider() {
				@Override
				public String getText(Object element) {
					if (element instanceof InWorkspaceSymbolsProvider) {
						return "Workspace";
					} else if (element instanceof InProjectSymbolsProvider) {
						return "Project";
					} else if (element instanceof InFileSymbolsProvider) {
						return "File";
					}
					return super.getText(element);
				}
			})
		);
		sections.add(new GotoSymbolSection(this, model.gotoSymbols).enableStatusLine(false));
		return sections;
	}

	/**
	 * This is a callback that will allow us to create the viewer and initialize
	 * it.
	 */
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		contributeToActionBars();
	}
	
	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}

	/**
	 * Fills the pull-down menu for this view (accessible from the toolbar)
	 */
	private void fillLocalPullDown(IMenuManager manager) {
//		manager.add(a);
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(newViewAction);
		manager.add(refreshAction);
	}
	
	@Override
	public void dispose() {
		if (lastView()) {
			IWorkbenchWindow[] workbenchWindows = PlatformUI.getWorkbench().getWorkbenchWindows();
			// PT 173267278 - When closing the last symbols view, ensure all selection
			// trackers are
			// disposed. This will indirectly shut down the active boot LS, via LSP4E
			// (see org.eclipse.lsp4e.LanguageServerWrapper), if there are no more
			// references
			// to IDocuments connected to that boot LS,
			// either from open editors, or the selection trackers.
			SelectionTracker.disposeAll(workbenchWindows);
		}
		super.dispose();
	}

	private boolean lastView() {
		IWorkbenchWindow[] workbenchWindows = PlatformUI.getWorkbench().getWorkbenchWindows();
		// This will check if there is still another view present aside from the current
		// one being closed
		if (workbenchWindows != null) {
			for (IWorkbenchWindow wbw : workbenchWindows) {
				IWorkbenchPage[] pages = wbw.getPages();
				if (pages != null) {
					for (IWorkbenchPage page : pages) {
						IViewReference[] references = page.getViewReferences();
						if (references != null) {
							for (IViewReference reference : references) {
								if (VIEW_TYPE_ID.equals(reference.getId())) {
									// there is still one other view left, so the current one being disposed is not
									// the last one
									return false;
								}
							}
						}
					}
				}
			}
		}

		return true;
	}

	@Override
	public void init(IViewSite site) throws PartInitException {
		super.init(site);
		model = new SpringSymbolsViewModel(getSite().getWorkbenchWindow());
		model.gotoSymbols.setOkHandler(GotoSymbolDialogModel.OPEN_IN_EDITOR_OK_HANDLER);
	}
	
}
