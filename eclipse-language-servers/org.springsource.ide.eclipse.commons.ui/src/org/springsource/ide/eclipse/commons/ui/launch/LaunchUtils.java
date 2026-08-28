/*******************************************************************************
 * Copyright (c) 2013 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.ui.launch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;

import org.eclipse.core.runtime.Platform;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.ui.DebugUITools;
import org.springsource.ide.eclipse.commons.frameworks.core.async.ResolvableFuture;

public class LaunchUtils {

	private static final boolean DEBUG = (""+Platform.getLocation()).contains("kdvolder");

	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	/**
	 * Execute some code as soon as a given list of launches are all terminated. If the launches are
	 * already terminated then the code is executed synchronously, otherwise it is executed asynchronously when
	 * a termination event is received.
	 */
	public static void whenTerminated(Collection<ILaunch> launches, Runnable runnable) {
		new WhenTerminated(new ArrayList<ILaunch>(launches), runnable);
	}

	/**
	 * @return A future that resolves when a given launch is terminated
	 */
	public static Future<Void> whenTerminated(ILaunch l) {
		return whenTerminated(Arrays.asList(l));
	}

	/**
	 * @return A future that resolves when a list of launches are all terminated
	 */
	public static Future<Void> whenTerminated(List<ILaunch> launches) {
		final ResolvableFuture<Void> done = new ResolvableFuture<Void>();
		whenTerminated(launches, new Runnable() {
			public void run() {
				done.resolve(null);
			}
		});
		return done;
	}

	private static class WhenTerminated implements IDebugEventSetListener {

		private final List<ILaunch> launches;
		private Runnable runnable;
		private final DebugPlugin debugPlugin;

		public WhenTerminated(List<ILaunch> launches, Runnable runnable) {
			this.launches = launches;
			this.runnable = runnable;
			this.debugPlugin = DebugPlugin.getDefault();
			debugPlugin.addDebugEventListener(this);

			//Careful... what if the launch has terminated since we last checked it...
			// in that case we might not get a termination event! So start off with
			// an initial check now.
			checkAndRun();
		}

		@Override
		public void handleDebugEvents(DebugEvent[] events) {
			for (DebugEvent e : events) {
				//Don't ckeck source==launch because we don't get termination events for launches
				// only for processes in a launch.
				if (e.getKind() == DebugEvent.TERMINATE) {
					checkAndRun();
				}
			}
		}

		private void checkAndRun() {
			Runnable runit = check();
			if (runit != null) {
				debugPlugin.removeDebugEventListener(this);
				runit.run();
			}
		}

		/**
		 * Checks whether condition for firing the runable is satisfied. If so, then the runable
		 * is returned. At the same time the runnable field is nulled to ensure it can not be
		 * executed more than once.
		 * <p>
		 * Executing the runable does not happen in this method because it is 'synchronized'
		 * and we don't want to hang on to the monitor any longer than necessary (especially
		 * not when firing the runnable!)
		 */
		private synchronized Runnable check() {
			if (runnable != null) {
				Iterator<ILaunch> iter = launches.iterator();
				while (iter.hasNext()) {
					ILaunch l = iter.next();
					if (l.isTerminated()) {
						iter.remove();
					}
				}
				if (launches.isEmpty()) {
					//bingo!
					Runnable it = runnable;
					runnable = null;
					return it;
				}
			}
			return null;
		}
	}

	public static void terminateAndRelaunch(final ILaunchConfiguration launchConf, final String mode) throws DebugException {
		List<ILaunch> launches = getLaunches(launchConf);
		terminate(launches);
		whenTerminated(launches, new UiRunnable() {
			@Override
			protected void uiRun() {
				//must run in UI thread since it may popup dialogs in some cases.
				DebugUITools.launch(launchConf, mode);
			}
		});
	}

	/**
	 * Terminate all launches in a list.
	 * This operation may be asynchronous. The caller can not rely
	 * on the launches being terminated before the method returns.
	 */
	public static void terminate(List<ILaunch> launches) throws DebugException {
		for (ILaunch l : launches) {
			if (!l.isTerminated()) {
				l.terminate();
			}
		}
	}

	public static List<ILaunch> getLaunches(ILaunchConfiguration launchConf) {
		ILaunch[] all = DebugPlugin.getDefault().getLaunchManager().getLaunches();
		ArrayList<ILaunch> selected = new ArrayList<ILaunch>();
		for (ILaunch l : all) {
			ILaunchConfiguration lConf = l.getLaunchConfiguration();
			if (lConf != null && lConf.equals(launchConf)) {
				selected.add(l);
			}
		}
		return selected;
	}

	/**
	 * Terminates all launches associated with given launch config.
	 * This operation may be asynchronous. The caller can not rely
	 * on the launches being terminated before the method returns.
	 */
	public static void terminate(ILaunchConfiguration conf) throws DebugException {
		terminate(getLaunches(conf));
	}


}
