/*******************************************************************************
 * Copyright (c) 2020 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model.remote;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchesListener2;
import org.springframework.ide.eclipse.boot.dash.model.RunState;

import com.google.common.collect.ImmutableSet;

/**
 * Monitor launch termination and when a relevant launch is terminated, starts a polling loop to
 * refresh related docker container.
 */
public class DebugLaunchTerminationListener implements ILaunchesListener2 {

	private static final boolean DEBUG = false;

	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	private static final long GRACE_PERIOD = 10_000;
	private static final long INTERVAL = 200;

	List<Pair<ILaunch, GenericRemoteAppElement>> watched = new ArrayList<>();

	/**
	 * THe 'deadline' is the moment in time at at which point we stop the polling loop that
	 * periodically refreshes elements belonging to watched terminated launches.
	 * <p>
	 * The deadline is reset whenever something 'interesting' happens. This allows for
	 * some lag bewteen the triggering event (a launch was terminated) and the moment
	 * when we will actually be able to observe this reflected in the state of
	 * docker container.
	 */
	long deadline;

	synchronized public void add(ILaunch l, GenericRemoteAppElement e) {
		watched.add(Pair.of(l,e));
	}

	public DebugLaunchTerminationListener() {
		DebugPlugin.getDefault().getLaunchManager().addLaunchListener(this);
	}

	@Override
	public void launchesAdded(ILaunch[] arg0) {
		//Don't care
		debug("launches added");
	}

	@Override
	public void launchesChanged(ILaunch[] arg0) {
		debug("launches changed");
		//Don't care
	}

	@Override
	public void launchesRemoved(ILaunch[] arg0) {
		debug("launches removed");
		//Don't care ??
	}

	@Override
	public void launchesTerminated(ILaunch[] _terminateds) {
		debug("launches terminated");
		if (isInteresting(_terminateds)) {
			scheduleRefresh();
		}
	}

	Job refresh = new Job("Refresh DockerContainers States") {
		{
			setSystem(true);
		}
		@Override
		protected IStatus run(IProgressMonitor arg0) {
			debug("Refreshing docker containers...");
			List<GenericRemoteAppElement> toRefresh = new ArrayList<>();
			synchronized (refreshables) {
				Iterator<GenericRemoteAppElement> iter = refreshables.iterator();
				while (iter.hasNext()) {
					GenericRemoteAppElement refreshable = iter.next();
					debug(refreshable+" = "+refreshable.getRunState());
					if (refreshable.isDisposed()) {
						debug("skip refresh: was disposed: "+refreshable);
						iter.remove();
					} else if (RunState.INACTIVE.equals(refreshable.getRunState())) {
						debug("skip refresh: is already INACTIVE "+refreshable);
						iter.remove();
					} else {
						debug("will refresh "+refreshable);
						toRefresh.add(refreshable);
					}
				}
				long timeleft = deadline - System.currentTimeMillis();
				if (!refreshables.isEmpty() && timeleft > 0) {
					debug("trigger another round of refreshes");
					schedule(INTERVAL);
				} else {
					debug("ending refresh with "+refreshables.size() +" refreshables left");
					refreshables.clear();
				}
			}
			for (GenericRemoteAppElement e : toRefresh) {
				Object _parent = e.getParent();
				if (_parent instanceof GenericRemoteAppElement) {
					GenericRemoteAppElement parent = (GenericRemoteAppElement) _parent;
					parent.getChildren().refresh();
				}
			}
			return Status.OK_STATUS;
		}
	};
	private Set<GenericRemoteAppElement> refreshables = new HashSet<>();

	private void scheduleRefresh() {
		deadline = System.currentTimeMillis() + GRACE_PERIOD;
		refresh.schedule();
	}

	private boolean isInteresting(ILaunch[] _terminateds) {
		synchronized (refreshables) {
			boolean interesting = false;
			ImmutableSet<ILaunch> terminateds = ImmutableSet.copyOf(_terminateds);
			Iterator<Pair<ILaunch, GenericRemoteAppElement>> iter = watched.iterator();
			while (iter.hasNext()) {
				Pair<ILaunch, GenericRemoteAppElement> w = iter.next();
				if (terminateds.contains(w.getKey())) {
					refreshables.add(w.getValue());
					iter.remove();
					interesting = true;
				}
			}
			return interesting;
		}
	}
}