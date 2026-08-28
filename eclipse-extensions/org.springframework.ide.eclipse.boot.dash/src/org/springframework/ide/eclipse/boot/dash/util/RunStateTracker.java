/*******************************************************************************
 * Copyright (c) 2015, 2017 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;
import org.springframework.ide.eclipse.boot.dash.model.RunState;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.cli.CloudCliServiceLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.util.BootLaunchUtils;
import org.springframework.ide.eclipse.boot.util.Log;
import org.springframework.ide.eclipse.boot.util.ProcessListenerAdapter;
import org.springframework.ide.eclipse.boot.util.ProcessTracker;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

/**
 * Generalization of OwnerRunStateTracker. An instance of this class tracks active processes
 * in the eclipse DebugUI and maitains a 'RunState' that is associated indirectly with some
 * object these processes belong to.
 * <p>
 * This is an abstract class as clients need to implement the 'getOwner' method to define
 * what enitity a given launch configuration belongs to.
 *
 * @author Kris De Volder
 */
public abstract class RunStateTracker<T> extends ProcessListenerAdapter implements Disposable {

	//// public API ///////////////////////////////////////////////////////////////////

	public interface RunStateListener<T> {
		void stateChanged(T owner);
	}

	public RunStateTracker() {
		activeStates = new HashMap<>();
		processTracker = new ProcessTracker(this);
		updateOwnerStatesAndFireEvents();
	}

	public synchronized RunState getState(final T owner) {
		return getState(activeStates, owner);
	}

	public void addListener(RunStateListener<T> listener) {
		this.listeners.add(listener);
	}

	public void removeListener(RunStateListener<T> listener) {
		this.listeners.remove(listener);
	}

	///////////////////////// stuff below is implementation cruft ////////////////////

	private static final boolean DEBUG = (""+Platform.getLocation()).contains("kdvolder");

	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	private Map<T, RunState> activeStates = null;
	private Map<ILaunch, ReadyStateMonitor> readyStateTrackers = null;
	private ProcessTracker processTracker = null;
	private ListenerList listeners = new ListenerList(); // listeners that are interested in us (i.e. clients)

	private static <T> RunState getState(Map<T, RunState> states, T p) {
		if (states != null) {
			RunState state = states.get(p);
			if (state != null) {
				return state;
			}
		}
		return RunState.INACTIVE;
	}

	private Map<T, RunState> getCurrentActiveStates() {
		Map<T, RunState> states = new HashMap<>();
		for (ILaunch l : launchManager().getLaunches()) {
			if (!l.isTerminated() && isInteresting(l)) {
				T p = getOwner(l);
				RunState s1 = getState(states, p);
				RunState s2 = getActiveState(l);
				states.put(p, s1.merge(s2));
			}
		}
		return states;
	}

	protected abstract T getOwner(ILaunch l);

	protected boolean isInteresting(ILaunch l) {
		return BootLaunchUtils.isBootLaunch(l)
				|| CloudCliServiceLaunchConfigurationDelegate.isLocalCloudServiceLaunch(l.getLaunchConfiguration());
	}

	/**
	 * Assuming that l is an active launch, determine its RunState.
	 */
	private RunState getActiveState(ILaunch l) {
		boolean isReady = getReadyState(l).getValue();
		if (isReady) {
			return BootLaunchUtils.isDebugging(l)
					? RunState.DEBUGGING
					: RunState.RUNNING;
		}
		return RunState.STARTING;
	}

	private synchronized LiveExpression<Boolean> getReadyState(ILaunch l) {
		if (readyStateTrackers == null) {
			readyStateTrackers = new HashMap<>();
		}
		ReadyStateMonitor tracker = readyStateTrackers.get(l);
		if (tracker == null) {
			readyStateTrackers.put(l, tracker = createReadyStateTracker(l));
			tracker.getReady().addListener(readyStateListener);
//		} else {
//			debug("getReadyState["+l+"] "+BootLaunchUtils.getProject(l)+" FROM CACHE");
		}
		return tracker.getReady();
	}

	private ValueListener<Boolean> readyStateListener = new ValueListener<Boolean>() {
		public void gotValue(LiveExpression<Boolean> exp, Boolean value) {
			if (value) {
				//ready state tracker detected a launch just entered the 'ready' state
				updateOwnerStatesAndFireEvents();
			}
		}
	};
	private boolean updateInProgress;

	protected final ReadyStateMonitor createReadyStateTracker(ILaunch l) {
		try {
			Boolean canUseLifeCycle=null, cliCanUseLifeCycle=null, isSingleProcessServiceLaunch = null;
			if ((canUseLifeCycle = BootLaunchConfigurationDelegate.canUseLifeCycle(l)) || (cliCanUseLifeCycle = CloudCliServiceLaunchConfigurationDelegate.canUseLifeCycle(l))) {
				SpringApplicationReadyStateMonitor readyStateMonitor = new SpringApplicationReadyStateMonitor(l);
				readyStateMonitor.startPolling();
				debug("createReadyStateTracker"+"["+l+"] "+BootLaunchUtils.getProject(l)+" OK!");
				return readyStateMonitor;
			} else if (isSingleProcessServiceLaunch  = CloudCliServiceLaunchConfigurationDelegate.isSingleProcessServiceConfig(l.getLaunchConfiguration())) {
				String serviceId = l.getLaunchConfiguration().getAttribute(CloudCliServiceLaunchConfigurationDelegate.ATTR_CLOUD_SERVICE_ID, (String) null);
				if (serviceId != null) {
					CloudCliServiceReadyStateMonitor readyStateMonitor = new CloudCliServiceReadyStateMonitor(l, serviceId);
					readyStateMonitor.startPolling();
					return readyStateMonitor;
				}
			}
			debug("createReadyStateTracker"+"["+l+"] "+BootLaunchUtils.getProject(l)+" NOT APPLICABLE");
			debug("  canUseLifeCycle="+canUseLifeCycle+"\n  cliCanUseLifeCycle="+cliCanUseLifeCycle+"\n  isSingleProcessServiceLaunch="+isSingleProcessServiceLaunch);
		} catch (Exception e) {
			debug("createReadyStateTracker"+"["+l+"] "+BootLaunchUtils.getProject(l)+" FAILED: "+ExceptionUtil.getMessage(e));
			Log.log(e);
		}
		return DummyReadyStateMonitor.create();
	}

	private synchronized void cleanupReadyStateTrackers() {
		if (readyStateTrackers != null) {
			Iterator<Entry<ILaunch, ReadyStateMonitor>> iter = readyStateTrackers.entrySet().iterator();
			while(iter.hasNext()) {
				Entry<ILaunch, ReadyStateMonitor> entry = iter.next();
				ILaunch l = entry.getKey();
				if (l.isTerminated()) {
					ReadyStateMonitor tracker = entry.getValue();
					iter.remove();
					tracker.dispose();
				}
			}
		}
	}

	protected ILaunchManager launchManager() {
		return DebugPlugin.getDefault().getLaunchManager();
	}

	public void dispose() {
		if (processTracker != null) {
			processTracker.dispose();
			processTracker = null;
		}
	}

	private void updateOwnerStatesAndFireEvents() {
		//Note that updateOwnerStates is synchronized, but this method is not.
		// Important not to keep locks while firing events.
		Set<T> affected = updateOwnerStates();
		for (Object _l : listeners.getListeners()) {
			@SuppressWarnings("unchecked")
			RunStateListener<T> listener = (RunStateListener<T>) _l;
			for (T p : affected) {
				listener.stateChanged(p);
			}
		}
	}

	private synchronized Set<T> updateOwnerStates() {
		if (updateInProgress) {
			//Avoid bug caused by reentrance from same thread.
			//This bug causes double update events for INAVTIVE -> RUNNING for owners that
			// don't have ready state tracking and so immediately enter the ready state upon
			// creation.
			return Collections.emptySet();
		} else {
			updateInProgress = true;
			try {
				Map<T, RunState> oldStates = activeStates;
				activeStates = getCurrentActiveStates();

				// Compute set of owners who's state has changed
				Set<T> affectedOwners = new HashSet<>(keySet(oldStates));
				affectedOwners.addAll(keySet(activeStates));
				Iterator<T> iter = affectedOwners.iterator();
				while (iter.hasNext()) {
					T p = iter.next();
					RunState oldState = getState(oldStates, p);
					RunState newState = getState(activeStates, p);
					if (oldState.equals(newState)) {
						iter.remove();
					} else {
						debug(p+": "+ oldState +" => " + newState);
					}
				}
				return affectedOwners;
			} finally {
				updateInProgress = false;
			}
		}
	}

	/**
	 * Null-safe 'keySet' fetcher for map.
	 */
	private <K,V> Set<K> keySet(Map<K, V> map) {
		if (map == null) {
			return Collections.emptySet();
		}
		return map.keySet();
	}

	@Override
	public void processTerminated(ProcessTracker tracker, IProcess process) {
		updateOwnerStatesAndFireEvents();
		cleanupReadyStateTrackers();
	}

	@Override
	public void processCreated(ProcessTracker tracker, IProcess process) {
		updateOwnerStatesAndFireEvents();
	}
}
