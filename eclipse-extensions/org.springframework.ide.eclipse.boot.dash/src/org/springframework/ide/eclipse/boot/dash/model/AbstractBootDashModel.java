/*******************************************************************************
 * Copyright (c) 2015, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model;

import java.util.Comparator;

import org.eclipse.core.runtime.ListenerList;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.views.BootDashModelConsoleManager;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStoreApi;
import org.springsource.ide.eclipse.commons.livexp.core.AbstractDisposable;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSets;
import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;

public abstract class AbstractBootDashModel extends AbstractDisposable implements BootDashModel {

	private static final boolean DEBUG = false;
//			(""+Platform.getLocation()).contains("bamboo") ||
//			(""+Platform.getLocation()).contains("kdvolder")
//			;
	private final BootDashViewModel parent;
	private final RunTarget target;

	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	public AbstractBootDashModel(RunTarget target, BootDashViewModel parent) {
		super();
		this.target = target;
		this.parent = parent;
	}

	public RunTarget getRunTarget() {
		return this.target;
	}

	ListenerList elementStateListeners = new ListenerList();

	@Override
	public void notifyElementChanged(BootDashElement element, Object reason) {
		debug("notifyElementChanged("+element.getName() + ", "+reason);
		if (element != null) {
			for (Object l : elementStateListeners.getListeners()) {
				((BootDashModel.ElementStateListener) l).stateChanged(element);
			}
		}
	}

	protected LiveExpression<RefreshState> createRefreshState() {
		return LiveExpression.constant(RefreshState.READY);
	}

	ListenerList modelStateListeners = new ListenerList();

	@Override
	public final void notifyModelStateChanged() {
		for (Object l : modelStateListeners.getListeners()) {
			((BootDashModel.ModelStateListener) l).stateChanged(this);
		}
	}

	@Override
	public Comparator<BootDashElement> getElementComparator() {
		return null;
	}

	abstract public ObservableSet<BootDashElement> getElements();

	abstract public BootDashModelConsoleManager getElementConsoleManager();

	/**
	 * Trigger manual model refresh.
	 */
	@Override
	abstract public void refresh(UserInteractions ui);

	public void addElementStateListener(BootDashModel.ElementStateListener l) {
		elementStateListeners.add(l);
	}

	public void removeElementStateListener(BootDashModel.ElementStateListener l) {
		elementStateListeners.remove(l);
	}

	public void addModelStateListener(BootDashModel.ModelStateListener l) {
		modelStateListeners.add(l);
	}

	public void removeModelStateListener(BootDashModel.ModelStateListener l) {
		modelStateListeners.remove(l);
	}

	public BootDashViewModel getViewModel() {
		return parent;
	}

	@Override
	public String getDisplayName() {
		return getRunTarget().getDisplayName();
	}

	@Override
	public String getNameTemplate() {
		return getRunTarget().getNameTemplate();
	}

	@Override
	public void setNameTemplate(String template) throws Exception {
		getRunTarget().setNameTemplate(template);
	}

	@Override
	public boolean hasCustomNameTemplate() {
		return getRunTarget().hasCustomNameTemplate();
	}

	public ObservableSet<ButtonModel> getButtons() {
		return LiveSets.emptySet(ButtonModel.class);
	}

	@Override
	public final void performDoubleClickAction(UserInteractions ui) {
		getRunTarget().performDoubleClickAction(ui);
	}

	protected final SimpleDIContext injections() {
		return getViewModel().getContext().injections;
	}

	public final UserInteractions ui() {
		return injections().getBean(UserInteractions.class);
	}

	protected final PropertyStoreApi getPersistentProperties() {
		return target.getPersistentProperties();
	}
}