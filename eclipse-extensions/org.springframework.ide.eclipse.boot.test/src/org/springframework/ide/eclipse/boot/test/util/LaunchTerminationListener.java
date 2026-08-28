/*******************************************************************************
 * Copyright (c) 2012, 2014, 2015 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.test.util;

import java.time.Duration;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchesListener;
import org.eclipse.debug.core.ILaunchesListener2;
import org.eclipse.debug.core.model.IProcess;

/**
 * A DebugEvent listener that is used for testing purposes. The listener provides a way to
 * Grab a hold of and then wait for the termination of a IProcess that is expected to
 * be started soon after the listener is added to the Eclipse {@link DebugPlugin}.
 * <p>
 * Typically, you don't use this class directly but rather call a method in {@link LaunchUtil}.
 *
 * @author Kris De Volder
 */
class LaunchTerminationListener implements ILaunchesListener, ILaunchesListener2 {

	private ILaunch launch;

	public LaunchTerminationListener(ILaunch launch) {
		this.launch = launch;
	}

	private synchronized boolean isTerminated() {
		return launch.isTerminated();
	}

	public synchronized void waitForProcessTermination(Duration timeout) {
		long start = System.currentTimeMillis();
		long end = start+timeout.toMillis();
		while (!isTerminated()) {
			if (System.currentTimeMillis() > end) {
				try {
					//The process may be stuck, try to kill it forcibly to avoid stuck tests
					launch.terminate();
				} catch (DebugException e) {
				}
			}
			try {
				wait(Math.max(500, end - System.currentTimeMillis()));
			} catch (InterruptedException e) {
			}
		}
	}

	public void launchesRemoved(ILaunch[] launches) {
		notifyMaybe(launches);
	}

	private void notifyMaybe(ILaunch[] launches) {
		for (ILaunch iLaunch : launches) {
			if (iLaunch == this.launch) {
				synchronized (this) {
					notifyAll();
				}
			}
		}
	}

	public void launchesAdded(ILaunch[] launches) {
		notifyMaybe(launches);
	}

	public void launchesChanged(ILaunch[] launches) {
		notifyMaybe(launches);
	}

	public void launchesTerminated(ILaunch[] launches) {
		notifyMaybe(launches);
	}

	public IProcess waitForProcess(Duration timeout) {
		waitForProcessTermination(timeout);
		return LaunchUtil.findProcess(launch);
	}

}
