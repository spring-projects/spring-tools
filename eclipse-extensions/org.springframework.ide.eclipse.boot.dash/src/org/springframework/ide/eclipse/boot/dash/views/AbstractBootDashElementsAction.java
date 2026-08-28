/*******************************************************************************
 * Copyright (c) 2015, 2019 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views;

import java.util.Collection;

import org.eclipse.jface.action.IAction;
import org.eclipse.ui.PlatformUI;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.liveprocess.LiveProcessCommandsExecutor;
import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelection;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel.ElementStateListener;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;

import com.google.common.collect.ImmutableSet;

/**
 * Abstract super class for BootDash actions that operate on selections
 * of elements.
 *
 * @author Kris De Volder
 */
public class AbstractBootDashElementsAction extends AbstractBootDashAction {

	private static final boolean DEBUG = false;//(""+Platform.getLocation()).contains("kdvolder");

	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	final protected MultiSelection<BootDashElement> selection;
	final private ValueListener<ImmutableSet<BootDashElement>> selectionListener;
	final protected BootDashViewModel model;
	private ElementStateListener modelListener;

	public AbstractBootDashElementsAction(Params params) {
		super(params.context, params.style);
		this.model = params.model;
		this.selection = params.selection;
		if (model != null) {
			model.addElementStateListener(modelListener = new ElementStateListener() {
				public void stateChanged(BootDashElement e) {
					debug("action '"+getText()+"' updating for element "+e);
					if (selection.getValue().contains(e) && !PlatformUI.getWorkbench().isClosing()) {
						PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
							public void run() {
								update();
							}
						});
					}
				}

			});
		}
		selection.getElements().addListener(selectionListener = new ValueListener<ImmutableSet<BootDashElement>>() {
			public void gotValue(LiveExpression<ImmutableSet<BootDashElement>> exp, ImmutableSet<BootDashElement> selecteds) {
				update();
			}
		});
		if (params.definitionId != null) {
			this.setActionDefinitionId(params.definitionId);
			params.actions.bindAction(this);
		}
	}

	public void update() {
		updateEnablement();
		updateVisibility();
	}

	/**
	 * Subclass can override to compuet enablement differently.
	 * The default implementation enables if a single element is selected.
	 */
	public void updateEnablement() {
		Collection<BootDashElement> selecteds = getSelectedElements();
		this.setEnabled(selecteds.size() == 1);
	}

	public void updateVisibility() {
		this.setVisible(getSelectedElements().size() > 0);
	}

	public Collection<BootDashElement> getSelectedElements() {
		return selection.getValue();
	}

	protected BootDashElement getSingleSelectedElement() {
		return selection.getSingle();
	}

	public void dispose() {
		if (selectionListener != null) {
			selection.getElements().removeListener(selectionListener);
		}
		if (modelListener != null) {
			model.removeElementStateListener(modelListener);
			modelListener = null;
		}
		super.dispose();
	}

	public static class Params {
		private BootDashActions actions;
		private BootDashViewModel model;
		private MultiSelection<BootDashElement> selection;
		private SimpleDIContext context;
		private int style = IAction.AS_UNSPECIFIED;
		private String definitionId;
		private LiveProcessCommandsExecutor liveProcessCmds;

		public Params(BootDashActions actions) {
			this.actions = actions;
		}
		public Params setModel(BootDashViewModel model) {
			this.model = model;
			return this;
		}
		public MultiSelection<BootDashElement> getSelection() {
			return selection;
		}
		public Params setSelection(MultiSelection<BootDashElement> selection) {
			this.selection = selection;
			return this;
		}
		public SimpleDIContext getContext() {
			return context;
		}
		public Params setContext(SimpleDIContext context) {
			this.context = context;
			return this;
		}
		public Params setStyle(int style) {
			this.style = style;
			return this;
		}
		public Params setDefinitionId(String definitionId) {
			this.definitionId = definitionId;
			return this;
		}
		public LiveProcessCommandsExecutor getLiveProcessCmds() {
			return liveProcessCmds;
		}
		public Params setLiveProcessCmds(LiveProcessCommandsExecutor liveProcessCmds) {
			this.liveProcessCmds = liveProcessCmds;
			return this;
		}
		public BootDashViewModel getModel() {
			return model;
		}
	}
}
