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
package org.springframework.ide.eclipse.boot.launch.util;

import static org.springsource.ide.eclipse.commons.ui.launch.LaunchUtils.whenTerminated;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;

import com.google.common.collect.ImmutableSet;

/**
 * @author Kris De Volder
 */
public class BootLaunchUtils {

	/**
	 * Boot aware launch termination. Tries to use JMX lifecycle managment bean if available.
	 */
	public static void terminate(Iterable<ILaunch> launches) {
		//TODO: this terminates launches sequentially. It would be better to try to terminate all of them
		// in parallel and then wait for completion of each operation.
		for (ILaunch l : launches) {
			try {
				terminate(l);
			} catch (Exception e) {
				BootActivator.log(e);
			}
		}
	}

	/**
	 * Boot aware launch termination. Tries to use JMX lifecycle managment bean if available.
	 */
	public static void terminate(ILaunch l) throws DebugException, CoreException {
		ILaunchConfiguration conf = l.getLaunchConfiguration();
//		try {
			if (conf != null
					&& conf.getType().getIdentifier().equals(BootLaunchConfigurationDelegate.TYPE_ID)
					&& BootLaunchConfigurationDelegate.canUseLifeCycle(conf)
			) {
				SpringApplicationLifeCycleClientManager clientMgr = new SpringApplicationLifeCycleClientManager(l);
				try {
					SpringApplicationLifecycleClient client = clientMgr.getLifeCycleClient();
					if (client != null) {
						client.stop();
						whenTerminated(l).get(BootLaunchConfigurationDelegate.getTerminationTimeoutAsLong(l), TimeUnit.MILLISECONDS);
						return; //Success
					}
				} catch (Exception e) {
					//Nice termination failed. We'll ignore the exception and allow fallback to kick in.
					//BootActivator.log(e);
				} finally {
					clientMgr.disposeClient();
				}
			}
			// Fallback to default implementation if 'nice termination' not available.
			l.terminate();
//		} catch (Exception e) {
//			BootActivator.log(e);
//		}
	}

	public static IProject getProject(ILaunch launch) {
		ILaunchConfiguration conf = launch.getLaunchConfiguration();
		if (conf != null) {
			return BootLaunchConfigurationDelegate.getProject(conf);
		}
		return null;
	}

	public static boolean isBootLaunch(ILaunch l) {
		try {
			ILaunchConfiguration conf = l.getLaunchConfiguration();
			if (conf != null) {
					String type = conf.getType().getIdentifier();
				return BootLaunchConfigurationDelegate.TYPE_ID.equals(type);
			}
		} catch (Exception e) {
			BootActivator.log(e);
		}
		return false;
	}

	public static boolean isDebugging(ILaunch launch) {
		return ILaunchManager.DEBUG_MODE.equals(launch.getLaunchMode());
	}

	public static List<ILaunch> getLaunches(ILaunchConfiguration c) {
		return getLaunches(ImmutableSet.of(c));
	}

	public static List<ILaunch> getLaunches(Set<ILaunchConfiguration> configs) {
		ILaunch[] all = DebugPlugin.getDefault().getLaunchManager().getLaunches();
		ArrayList<ILaunch> selected = new ArrayList<>();
		for (ILaunch l : all) {
			ILaunchConfiguration lConf = l.getLaunchConfiguration();
			if (lConf != null && configs.contains(lConf)) {
				selected.add(l);
			}
			//This weird stuff below is for ngrok support in the boot dash which creates working copys that are really
			// used as a kind of 'temporary proxy' for its original LaunchConfiguration.
			while (lConf instanceof ILaunchConfigurationWorkingCopy) {
				lConf = ((ILaunchConfigurationWorkingCopy) lConf).getOriginal();
				if (configs.contains(lConf)) {
					selected.add(l);
				}
			}
		}
		return selected;
	}

}
