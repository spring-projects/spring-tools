/*******************************************************************************
 * Copyright (c) 2015, 2023 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.views.properties.IPropertySheetPage;
import org.eclipse.ui.views.properties.tabbed.ITabbedPropertySheetPageContributor;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.liveprocess.LiveProcessCommandsExecutor;
import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelection;
import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelectionSource;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.dash.util.MenuUtil;
import org.springframework.ide.eclipse.boot.dash.util.ToolbarPulldownContributionItem;
import org.springframework.ide.eclipse.boot.dash.views.sections.BootDashUnifiedTreeSection;
import org.springframework.ide.eclipse.boot.dash.views.sections.TagSearchSection;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.ViewPartWithSections;

import com.google.common.collect.ImmutableSet;

/**
 * @author Kris De Volder
 */
public class BootDashTreeView extends ViewPartWithSections implements ITabbedPropertySheetPageContributor, ISelectionProvider {

	/**
	 * The ID of the view as specified by the extension.
	 */
	public static final String ID = "org.springframework.ide.eclipse.boot.dash.views.BootDashView";

	/**
	 * Adds scroll support to the whole view. You probably want to disable this
	 * if view is broken into pieces that have their own scrollbars
	 */
	private static final boolean ENABLE_SCROLLING = false;

	private BootDashViewModel model = BootDashActivator.getDefault().getModel();

	// private Action refreshAction;
	// private Action doubleClickAction;

	private BootDashActions actions;

	private MultiSelection<Object> selection = null; // lazy init

	private List<ISelectionChangedListener> selectionListeners = new ArrayList<>();

	private BootDashUnifiedTreeSection treeSection;

	/*
	 * The content provider class is responsible for providing objects to the
	 * view. It can wrap existing objects in adapters or simply return objects
	 * as-is. These objects may be sensitive to the current input of the view,
	 * or ignore it and always show the same content (like Task List, for
	 * example).
	 */

	/**
	 * The constructor.
	 */
	public BootDashTreeView() {
		super(ENABLE_SCROLLING);
	}

	@Override
	public void dispose() {
		super.dispose();
		if (actions != null) {
			actions.dispose();
		}
	}

	/**
	 * This is a callback that will allow us to create the viewer and initialize
	 * it.
	 */
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);

		getSite().setSelectionProvider(this);
		getSite().registerContextMenu(treeSection.getMenuMgr(), this);

		// Create the help context id for the viewer's control
		// PlatformUI.getWorkbench().getHelpSystem().setHelp(tv.getControl(),
		// "org.springframework.ide.eclipse.boot.dash.viewer");
		actions = new BootDashActions(model, getRawSelection().filter(BootDashElement.class), context(), LiveProcessCommandsExecutor.getDefault());
		// hookContextMenu();
		// hookDoubleClickAction();
		contributeToActionBars();
	}

	private SimpleDIContext context() {
		return model.getContext().injections;
	}

	public synchronized MultiSelection<Object> getRawSelection() {
		if (this.selection == null) {
			MultiSelection<Object> selection = MultiSelection.empty(Object.class);
			for (IPageSection section : getSections()) {
				if (section instanceof MultiSelectionSource) {
					MultiSelectionSource source = (MultiSelectionSource) section;
					MultiSelection<Object> subSelection = source.getSelection().cast(Object.class);
					selection = MultiSelection.union(selection, subSelection);
				}
			}
			this.selection = selection;
			selection.getElements().addListener(new ValueListener<ImmutableSet<Object>>() {
				@Override
				public void gotValue(LiveExpression<ImmutableSet<Object>> exp, ImmutableSet<Object> value) {
					ISelection selection = getSelection();
					for (ISelectionChangedListener selectionListener : selectionListeners) {
						selectionListener.selectionChanged(new SelectionChangedEvent(BootDashTreeView.this, selection));
					}
				}
			});
		}
		return this.selection;
	}

	public List<BootDashElement> getSelectedElements() {
		ArrayList<BootDashElement> elements = new ArrayList<>();
		for (Object e : getRawSelection().getValue()) {
			if (e instanceof BootDashElement) {
				elements.add((BootDashElement) e);
			}
		}
		return Collections.unmodifiableList(elements);
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
		for (RunStateAction a : actions.getRunStateActions()) {
			manager.add(a);
			//'addVisible' would be nice but doesm't work here. Probaly because the pulldown is only
			// populated once rather than every time it shows:
			//			BootDashUnifiedTreeSection.addVisible(manager, a);
		}
		manager.add(actions.getOpenBrowserAction());
		manager.add(actions.getOpenConsoleAction());
		manager.add(actions.getLinkWithConsoleAction());
		manager.add(actions.getOpenInPackageExplorerAction());
		manager.add(actions.getOpenConfigAction());
		manager.add(actions.getShowPropertiesViewAction());

		MenuUtil.addDynamicSubmenu(manager, actions.getLiveDataConnectionManagement());


		manager.add(new Separator());
		addAddRunTargetMenuActions(manager);

		manager.add(new Separator());
		manager.add(actions.getToggleFiltersDialogAction());

		manager.add(new Separator());
		manager.add(actions.getOpenBootDashPreferencesAction());

		// manager.add(refreshAction);
		// manager.add(new Separator());
		// manager.add(action2);
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		for (RunStateAction a : actions.getRunStateActions()) {
			if (a.showInToolbar()) {
				manager.add(a);
			}
		}
		manager.add(actions.getOpenBrowserAction());
		manager.add(actions.getOpenConsoleAction());
		manager.add(actions.getLinkWithConsoleAction());
		manager.add(actions.getOpenConfigAction());
		manager.add(actions.getShowPropertiesViewAction());
		MenuUtil.addDynamicSubmenu(manager, actions.getLiveDataConnectionManagement());
		manager.add(actions.getToggleFiltersDialogAction());

// This ought to work, but it doesn't.
//		manager.add(createAddRunTargetMenuManager());
// Must write specific code to create toolbar pull-down button / menu:
		createAddRunTargetPulldown(manager);
		// manager.add(refreshAction);
		// manager.add(action2);
	}

	private void addAddRunTargetMenuActions(IMenuManager manager) {
		if (actions.getAddRunTargetActions().length == 1) {
			//Special case. Creationg a pulldown for just one item isn't very logical.
			AddRunTargetAction action = actions.getAddRunTargetActions()[0];
			manager.add(action);
		} else {
			MenuManager menu = createAddRunTargetMenuManager();
			manager.add(menu);
		}

	}

	private MenuManager createAddRunTargetMenuManager() {
		final MenuManager menu = new MenuManager("Add Run Target...", BootDashActivator.getImageDescriptor("icons/add_target.png"), null);
		for (AddRunTargetAction a : actions.getAddRunTargetActions()) {
			menu.add(a);
		}
		return menu;
	}


	public void createAddRunTargetPulldown(IToolBarManager toolbar) {
		if (actions.getAddRunTargetActions().length == 1) {
			//Special case. Creationg a pulldown for just one item isn't very logical.
			AddRunTargetAction action = actions.getAddRunTargetActions()[0];
			toolbar.add(action);
		} else {
			Action dropdownAction=new Action("Create Target",SWT.DROP_DOWN){};
			dropdownAction.setImageDescriptor(BootDashActivator.getImageDescriptor("icons/add_target.png"));
			dropdownAction.setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/add_target_disabled.png"));
			dropdownAction.setMenuCreator(new IMenuCreator() {
				Menu theMenu;

				@Override
				public Menu getMenu(Menu parent) {
					return null;
				}

				@Override
				public Menu getMenu(Control parent) {
					if (theMenu == null) {
						final MenuManager menu = createAddRunTargetMenuManager();
						theMenu = menu.createContextMenu(parent);
						theMenu.addDisposeListener(new DisposeListener() {
							public void widgetDisposed(DisposeEvent e) {
								menu.dispose();
							}
						});
					}
					return theMenu;
				}

				@Override
				public void dispose() {
				}
			});

			toolbar.add(new ToolbarPulldownContributionItem(dropdownAction));
		}
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		if (page != null) {
			page.setFocus();
		}
	}

	public UserInteractions ui() {
		return context().getBean(UserInteractions.class);
	}

	public BootDashActions getActions() {
		return actions;
	}

	@Override
	public Shell getShell() {
		return getSite().getShell();
	}

	@Override
	protected List<IPageSection> createSections() throws CoreException {
		List<IPageSection> sections = new ArrayList<>();
		sections.add(new TagSearchSection(BootDashTreeView.this, model.getFilterBox().getText(), model));
		sections.add(treeSection = new BootDashUnifiedTreeSection(this, model, context()));

		return sections;
	}

	@Override
	public String getContributorId() {
		return "org.springframework.ide.eclipse.boot.dash.propertyContributor";
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public Object getAdapter(Class adapter) {
		if (adapter == IPropertySheetPage.class) {
			return new TabbedPropertySheetPage(this);
		}
		return super.getAdapter(adapter);
	}

	@Override
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		selectionListeners.add(listener);
	}

	@Override
	public ISelection getSelection() {
		return new StructuredSelection(getRawSelection().getValue().toArray());
	}

	@Override
	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		selectionListeners.remove(listener);
	}

	@Override
	public void setSelection(ISelection selection) {
		//This method isn't implemented. Probably this is okay, nobody needs to set our selection.
		// If the need arises to do this in the future, then the 'setSelection' needs to be distributed to
		// our subsections so each subsection can select whichever elements in the selection apply to them.
	}
}
