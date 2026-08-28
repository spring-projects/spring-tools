/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.AssertionFailedError;

import org.eclipse.core.internal.events.NotificationManager;
import org.eclipse.core.internal.events.ResourceChangeListenerList;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.debug.core.DebugPlugin;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.springsource.ide.eclipse.commons.frameworks.test.util.ACondition;

/**
 * JUnit 4 'Rule' that checks whether a test registered some
 * listeners but didn't deregister them. This is often an indication
 * of resource/memory leak.
 *
 * @author Kris De Volder
 */
@SuppressWarnings("restriction")
public class ListenerLeakDetector implements TestRule {
	private Set<Object> startingListeners;

	public Statement apply(final Statement base, Description description) {
		return new Statement() {
			@Override
			public void evaluate() throws Throwable {
				start();
				base.evaluate();
				verify();
			}
		};
	}

	protected void start() throws Exception {
		startingListeners = getListeners();
	}

	@SuppressWarnings("unchecked")
	protected Set<Object> getListeners() throws Exception {
		Set<Object> listeners = new HashSet<>();
		listeners.addAll(getDebugListeners());
		listeners.addAll(getWorkspaceListeners());
		return listeners;
	}

	protected List<Object> getDebugListeners() throws Exception {
		DebugPlugin plugin = DebugPlugin.getDefault();
		Field f = DebugPlugin.class.getDeclaredField("fEventListeners");
		f.setAccessible(true);
		ListenerList list = (ListenerList)f.get(plugin);
		List<Object> listeners = new ArrayList<>();
		for (Object l : list.getListeners()) {
			if (isInteresting(l)) {
				listeners.add(l);
			}
		}
		return listeners;
	}

	protected List<Object> getWorkspaceListeners() throws Exception {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		NotificationManager notMan = (NotificationManager) getField(workspace, "notificationManager");
		ResourceChangeListenerList listenerList = (ResourceChangeListenerList)getField(notMan, "listeners");
		Object[] entries = listenerList.getListeners(); //Watch out, these entries aren't the actual
														// listeners yet.
		if (entries != null) {
			List<Object> listeners = new ArrayList<>(entries.length);
			for (int i = 0; i < entries.length; i++) {
				Object l = getField(entries[i], "listener");
				if (isInteresting(l)) {
					listeners.add(l);
				}
			}
			return listeners;
		}
		return Collections.emptyList();
	}

	/**
	 * Eclipse is one noisy bugger when it comes to adding workspace listeners. We really don't care
	 * about the listener classes we don't own/control for this leak detection... so...
	 */
	protected boolean isInteresting(Object l) {
		String classname = l.getClass().getName();
		return classname.startsWith("org.springframework.ide.eclipse.boot") && !isIgnoredListenerClassName(classname);
	}

	private boolean isIgnoredListenerClassName(String classname) {
		//Phill's plugin attaches listeners when console ui kicks in. Ignore those, they are only disposed if someone closes / disposes the
		// ui associated with the console.
		return classname.startsWith("org.springframework.ide.eclipse.boot.restart.RestartConsolePageParticipant");
	}

	private Object getField(Object self, String name) throws Exception {
		Field f = self.getClass().getDeclaredField(name);
		f.setAccessible(true);
		return f.get(self);
	}

	protected void verify() throws Throwable {
		ACondition.waitFor("listeners removed", 2000, () -> {
			Set<Object> endingListeners = getListeners();
			for (Object l : endingListeners) {
				if (!startingListeners.contains(l)) {
					throw new AssertionFailedError("Leaked listener: "+l+" of class "+l.getClass().getName());
				}
			}
		});
	}
}