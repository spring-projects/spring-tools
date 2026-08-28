/*******************************************************************************
 * Copyright (c) 2019, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.util;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.springframework.ide.eclipse.boot.dash.views.sections.DynamicSubMenuSupplier;
import org.springsource.ide.eclipse.commons.livexp.core.UIValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

public class MenuUtil {

	/**
	 * Adds a submenu containing a dynamically computed list of actions. The list of actions
	 * is computed on demand when the submenu is about to show.
	 */
	public static void addDynamicSubmenu(IMenuManager parent, DynamicSubMenuSupplier lazyActions) {
		if (lazyActions != null && lazyActions.isVisible()) {
			MenuManager submenu = new MenuManager(lazyActions.getLabel()) {
				public boolean isEnabled() {
					return lazyActions.isEnabled().getValue();
				}

				@Override
				public void fill(Menu parent, int index) {
					super.fill(parent, index);
					try {
						Field f = MenuManager.class.getDeclaredField("menuItem");
						f.setAccessible(true);
						MenuItem mi = (MenuItem) f.get(this);
						if (mi != null) {
							Disposable enablementUpdater = lazyActions.isEnabled().onChange(UIValueListener.from((e, v) -> {
								if (!mi.isDisposed()) {
									mi.setEnabled(e.getValue());
								}
							}));
							mi.addDisposeListener(evt -> enablementUpdater.dispose());
						}
					} catch (Exception e) {
						Log.log(e);
					}
				}
			};
			submenu.setRemoveAllWhenShown(true);
			submenu.addMenuListener(menuAboutToShow -> {
				List<IAction> actions = lazyActions.getActions();
				for (IAction a : actions) {
					menuAboutToShow.add(a);
				}
			});
			submenu.setImageDescriptor(lazyActions.getImageDescriptor());
			parent.add(submenu);
		}
	}

	public static void addDynamicSubmenu(IToolBarManager toolbar, DynamicSubMenuSupplier lazyActions) {
		if (lazyActions != null) {
			String label = lazyActions.getLabel();
			ImageDescriptor imageDescriptor = lazyActions.getImageDescriptor();
			ImageDescriptor imageDescriptorDisabled = lazyActions.getDisabledImageDescriptor();
			Action dropdownAction=new Action(label, SWT.DROP_DOWN){};
			dropdownAction.setImageDescriptor(imageDescriptor);
			dropdownAction.setDisabledImageDescriptor(imageDescriptorDisabled);
			dropdownAction.setMenuCreator(new IMenuCreator() {
				Menu theMenu;

				@Override
				public Menu getMenu(Menu parent) {
					return null;
				}

				@Override
				public Menu getMenu(Control parent) {
					if (theMenu == null) {
						final MenuManager menu = createDynamicPulldownMenuManager(label, imageDescriptor, () -> lazyActions.getActions());
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

			lazyActions.isEnabled().onChange(UIValueListener.from((e, v) -> {
				dropdownAction.setEnabled(e.getValue());
			}));

			toolbar.add(new ToolbarPulldownContributionItem(dropdownAction));
		}
	}

	private static MenuManager createDynamicPulldownMenuManager(String label, ImageDescriptor imageDescriptor, Supplier<List<IAction>> actionSupplier) {
		final MenuManager menu = new MenuManager(label, imageDescriptor, null);
		menu.setRemoveAllWhenShown(true);
		menu.addMenuListener((IMenuManager manager) -> {
			for (IAction a : actionSupplier.get()) {
				menu.add(a);
			}
		});
		return menu;
	}
}
