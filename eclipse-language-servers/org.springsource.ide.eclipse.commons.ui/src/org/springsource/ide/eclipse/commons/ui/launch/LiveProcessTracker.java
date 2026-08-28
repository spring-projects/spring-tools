/*******************************************************************************
 * Copyright (c) 2013 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.ui.launch;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IProcess;

/**
 * An instance of this class tracks running processes by attaching to the eclipse debugger.
 * processes are forgotten as soon as they terminate.
 *
 * @author Kris De Volder
 */
public class LiveProcessTracker extends LaunchList {

	public interface Listener {
		void changed();
	}

	private static LiveProcessTracker instance;

	private final LinkedList<IProcess> processes = new LinkedList<IProcess>();

	/**
	 * Gets an instance of process tracker that may be shared. (There's no need to create multiple instances
	 * since they are all just tracking the same set of active processes).
	 */
	public synchronized static LiveProcessTracker getInstance() {
		if (instance == null) {
			instance = new LiveProcessTracker();
		}
		return instance;
	}

	private LiveProcessTracker() {
		super();
	}

//	private void dispose() {
//		if (debugListener != null) {
//			DebugPlugin.getDefault().removeDebugEventListener(debugListener);
//			debugListener = null;
//		}
//	}


	@Override
	protected void processTerminated(IProcess process) {
		boolean changed;
		synchronized (this) {
			changed = processes.remove(process);
		}
		if (changed) {
			fireChangeEvent();
		}
	}

	@Override
	protected void processCreated(IProcess process) {
		boolean changed = false;
		synchronized (this) {
			if (!processes.contains(process)) {
				processes.add(process);
				changed = true;
			}
		}
		if (changed) {
			fireChangeEvent();
		}
	}

	@Override
	public synchronized Item getLast() {
		if (!processes.isEmpty()) {
			ILaunch l = processes.getLast().getLaunch();
			ILaunchConfiguration c = l.getLaunchConfiguration();
			if (l != null && c != null) {
				return new Item(c, l.getLaunchMode());
			}
		}
		return null;
	}

	@Override
	public synchronized Collection<Item> getLaunches() {
		LinkedHashMap<String, Item> launches = new LinkedHashMap<String, LaunchList.Item>();
		for (IProcess p : processes) {
			ILaunch l = p.getLaunch();
			if (l != null) {
				ILaunchConfiguration c = l.getLaunchConfiguration();
				if (c != null) {
					launches.put(c.getName(), new Item(c, l.getLaunchMode()));
				}
			}
		}
		return launches.values();
	}

}
