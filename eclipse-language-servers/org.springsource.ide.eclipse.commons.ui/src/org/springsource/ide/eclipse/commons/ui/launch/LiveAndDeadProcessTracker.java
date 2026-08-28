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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IProcess;

/**
 * A variation of LiveProcessTracker that retains launch configurations even
 * after all the associated processes have been terminated. (Thus allowing to
 * relaunch.
 *
 * @author Kris De Volder
 */
public class LiveAndDeadProcessTracker extends LaunchList {

	/*
	 * The code in this class seems more complicated than it should be. The main source of
	 * complication is dealing with cases where tracked launch configs have been renamed
	 * or deleted. Ideally we would update the lists based on a ILaunchConfigurationListener
	 * any time this happens. Unfortunately there's no 'renamed' event. Instead we
	 * get disconnected 'delete' and 'create' events.
	 *
	 * What we do instead is check the elements returned by accessor methods 'getLast' and
	 * 'getLaunches' on every call for 'broken' elements. Any 'broken' elements are dealt with either
	 * by 'repairing' them if we can determine what it was renamed to, or deleting them
	 * if we can not.
	 *
	 * Further complications are due to the fact that LinkedHashMap has no 'getLast' method.
	 * We keep the 'last' element in a separate field. Keeping this field in sync
	 * with the actual elements in the map requires some effort.
	 */

	public class ProcessItem extends Item {

		private final IProcess process; //This is so we can recover the configuration
		                          // for processes after the conf has been renamed.
		                          // Rename = delete + create.

		public ProcessItem(ILaunchConfiguration conf, String mode, IProcess process) {
			super(conf, mode);
			this.process = process;
		}

		public ProcessItem setConf(ILaunchConfiguration newConf) {
			return new ProcessItem(newConf, mode, process);
		}

	}

	private static final boolean DEBUG = false;// (""+Platform.getLocation()).contains("kdvolder");

	private static LiveAndDeadProcessTracker instance;
	public synchronized static LaunchList getInstance() {
		if (instance == null) {
			instance = new LiveAndDeadProcessTracker();
		}
		return instance;
	}

	private final LinkedHashMap<String, ProcessItem> configs = new LinkedHashMap<String, ProcessItem>();
	private ProcessItem last = null;

	public LiveAndDeadProcessTracker() {
		//DebugPlugin.getDefault().getLaunchManager().addLaunchConfigurationListener(this);
	}

	@Override
	protected void processTerminated(IProcess process) {
		//nothing... we keep the dead ones too!
	}

	@Override
	protected void processCreated(IProcess process) {
		synchronized (process) {
			ILaunch l = process.getLaunch();
			if (l != null) {
				ILaunchConfiguration c = l.getLaunchConfiguration();
				if (c != null) {
					last = new ProcessItem(c, l.getLaunchMode(), process);
					configs.remove(last.getName()); //so the element moves to the end being now the 'most' recent.
					configs.put(last.getName(), last);
				}
			}
		}
		fireChangeEvent();
	}

	private boolean exists(ILaunchConfiguration conf) {
		//Assume working copyies exist as long as we have a reference to them.
		return conf.exists() || conf.isWorkingCopy();
	}


	@Override
	public synchronized Item getLast() {
		if (last != null) {
			if (exists(last.conf)) {
				return last;
			}
			//might be conf delete or renamed
			last = findRenamed(last);
			if (last == null) {
				//last was deleted... find the 'previous last'.
				// It's a shame we have to iterate the whole collection to find the last one...
				// but there's no other way with a LinkedHashMap
				for (Item newLast : getLaunches()) {
					last = (ProcessItem)newLast;
				}
			}
		}
		return last;
	}

	@Override
	public synchronized Collection<Item> getLaunches() {
		//debug(">>> getLaunches");

		ArrayList<String> names = new ArrayList<String>(configs.keySet());
		for (String name : names) {
//			debug("name (key)  = "+name);
			ProcessItem item = configs.get(name);
//			debug("name (item) = "+item.getName());
			ILaunchConfiguration conf = item.conf;
			if (exists(conf)) {
//				debug("exists: "+conf.getName());
				continue;
			}
			// could be renamed or deleted
			ProcessItem renamedItem = findRenamed(item);
			if (renamedItem != null) {
//				debug("renamed: "+name+" -> "+renamedItem.getName());
				configs.remove(item.getName());
				configs.put(renamedItem.getName(), renamedItem);
				continue;
			}
			//config no longer valid probably deleted (or renamed, but couldn't figure out the new name).
//			debug("deleted: "+name);
			configs.remove(item.getName());
		}
		ArrayList<Item> items = new ArrayList<LaunchList.Item>(configs.values());
//		debug("<<< getLaunches # "+items.size());
		return items;
	}

	/**
	 * Try to 'fix' a renamed element. This only works if we can recover the
	 * new config via its association with the process. This association may
	 * no longer exist if the launch was deregistered from the debugger already.
	 * So this is a 'best effort' implementation.
	 *
	 * @return Fixed element or null if a corresponding renamed config couldn't
	 *      be found.
	 */
	private ProcessItem findRenamed(ProcessItem item) {
		ILaunch l = item.process.getLaunch();
		if (l != null) {
			ILaunchConfiguration renamedConf = l.getLaunchConfiguration();
			if (renamedConf != null && exists(renamedConf)) {
				return item.setConf(renamedConf);
			}
		}
		return null;
	}

	private void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

}
