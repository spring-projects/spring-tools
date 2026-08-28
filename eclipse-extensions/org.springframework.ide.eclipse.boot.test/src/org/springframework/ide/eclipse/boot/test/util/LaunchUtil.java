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

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.eclipse.debug.core.model.IStreamsProxy;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.process.BootProcessFactory;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;

/**
 * Utility class providing methods to help launching a process from a ILauncConfiguration and waiting for it to
 * terminate while capturing the output for testing purposes.
 *
 * @author Kris De Volder
 */
public class LaunchUtil {

	public static LaunchResult synchLaunch(ILaunchConfiguration launchConf) throws CoreException {
		return synchLaunch(launchConf, Duration.ofMinutes(2));
	}

	@SuppressWarnings("resource")
	public static LaunchResult synchLaunch(ILaunchConfiguration launchConf, Duration timeout) throws CoreException {
		//The way we capture output only works for BootLaunchConfigurationDelegate so make sure that's what this is being used
		// for:
		if (launchConf.getType().getIdentifier().equals(BootLaunchConfigurationDelegate.TYPE_ID)) {
			LiveVariable<StringBuilder> out = new LiveVariable<>();
			LiveVariable<StringBuilder> err = new LiveVariable<>();
			Disposable disposable = BootProcessFactory.addStreamsProxyListener((streams, launch) -> {
				if (launch.getLaunchConfiguration().equals(launchConf)) {
					out.setValue(capture(streams.getOutputStreamMonitor()));
					err.setValue(capture(streams.getErrorStreamMonitor()));
				}
			});
			try {
				ILaunch l = launchConf.launch("run", new NullProgressMonitor(), false, true);
				IProcess p = synchLaunch(l, timeout);
				return new LaunchResult(p.getExitValue(), out.getValue().toString(), err.getValue().toString());
			} finally {
				disposable.dispose();
			}
		} else {
			//This code has a known race condition, (it is not certain we grab all the output because we can only attach
			// listeners *after* process already started). We attempted to address this above. But above approach only
			// works if BootLaunchConfgurationDeletate is used. So we still keep this slightly broken old solution as 
			// a fallback.
			ILaunch l = launchConf.launch("run", new NullProgressMonitor(), false, true);
			IProcess process = findProcess(l);
			IStreamsProxy streams = process.getStreamsProxy();

			StringBuilder out = capture(streams.getOutputStreamMonitor());
			StringBuilder err = capture(streams.getErrorStreamMonitor());
			IProcess p = synchLaunch(l, timeout);
			return new LaunchResult(p.getExitValue(), out.toString(), err.toString());
		}
	}

	private static StringBuilder capture(IStreamMonitor stream) {
		final StringBuilder out = new StringBuilder();
		synchronized (stream) {
			out.append(stream.getContents());
			stream.addListener(new IStreamListener() {
				public void streamAppended(String text, IStreamMonitor monitor) {
					out.append(text);
				}
			});
		}
		return out;
	}

	private static IProcess synchLaunch(ILaunch launch, Duration timeout) {
		DebugPlugin mgr = DebugPlugin.getDefault();
		LaunchTerminationListener listener = null;
		try {
			//DebugUITools.launch(launchConf, "run");
			listener = new LaunchTerminationListener(launch);
			mgr.getLaunchManager().addLaunchListener(listener);
			return listener.waitForProcess(timeout);
		} finally {
			if (listener != null) {
				mgr.getLaunchManager().removeLaunchListener(listener);
			}
		}
	}

	public static ILaunchManager getLaunchManager() {
		return DebugPlugin.getDefault().getLaunchManager();
	}

	public static IProcess findProcess(ILaunch launch) {
		IProcess[] processes = launch.getProcesses();
		if (processes != null && processes.length > 0) {
			Assert.isTrue(processes.length == 1);
			return processes[0];
		}
		return null;
	}

}
