/*******************************************************************************
 * Copyright (c) 2015, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.devtools;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.jdt.internal.launching.LaunchingMessages;
import org.eclipse.jdt.internal.launching.LaunchingPlugin;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IVMConnector;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.osgi.util.NLS;
import org.springframework.ide.eclipse.boot.launch.AbstractBootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.BootLaunchActivator;
import org.springframework.ide.eclipse.boot.launch.util.WaitFor;
import org.springframework.ide.eclipse.boot.util.ProcessListenerAdapter;
import org.springframework.ide.eclipse.boot.util.ProcessTracker;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

@SuppressWarnings("restriction")
public class BootDevtoolsClientLaunchConfigurationDelegate extends AbstractBootLaunchConfigurationDelegate {

	public static final String TYPE_ID = "org.springframework.ide.eclipse.boot.devtools.client.launch";

	private static final long DEBUG_CONNECT_TIMEOUT = 20000;
	public static final String REMOTE_SPRING_APPLICATION = "org.springframework.boot.devtools.RemoteSpringApplication";
	public static final String REMOTE_URL = "spring.devtools.remote.url";
	public static final String REMOTE_SECRET = "spring.devtools.remote.secret";
	public static final String REMOTE_SECRET_ID = "spring.devtools.remote.secret.id";
	public static final String DEFAULT_REMOTE_SECRET = "";
	public static final String DEBUG_PORT = "spring.devtools.remote.debug.local-port";

	private static final String MANAGED = "spring.devtools.isManagedLaunch";

	private final ThreadLocal<Integer> localDebugPort = new ThreadLocal<>();

	@Override
	public String getMainTypeName(ILaunchConfiguration configuration) throws CoreException {
		return REMOTE_SPRING_APPLICATION;
	}

	@Override
	public String getProgramArguments(ILaunchConfiguration conf) throws CoreException {
		Properties props = getApplicationProperties(conf);
		ArrayList<String> args = new ArrayList<>();
		addPropertiesArguments(args, props, PROGRAM_ARG_PREFIX);
		String secret = getSecret(conf);
		if (StringUtil.hasText(secret)) {
			args.add(propertyAssignmentArgument(REMOTE_SECRET, secret, PROGRAM_ARG_PREFIX));
		}
		Integer debugPort = localDebugPort.get();
		if (debugPort != null) {
			args.add(propertyAssignmentArgument(DEBUG_PORT, ""+debugPort, PROGRAM_ARG_PREFIX));
		}
		args.add(getRemoteUrl(conf));
		return DebugPlugin.renderArguments(args.toArray(new String[args.size()]), null);
	}

	private String getSecret(ILaunchConfiguration conf) {
		String secret = getRemoteSecret(conf);
		return secret == null ? DEFAULT_REMOTE_SECRET : secret;
	}

	@Override
	public void launch(ILaunchConfiguration conf, String mode, ILaunch launch, IProgressMonitor mon)
			throws CoreException {
		boolean isDebug = ILaunchManager.DEBUG_MODE.equals(mode);
		int work =  isDebug ? 2 : 1;
		mon.beginTask("Launching DevTools Client for"+getProjectName(conf), work);
		if (isDebug) {
			localDebugPort.set(findFreePort());
		}
		try {
			//Launch client: Generally we don't wanna debug the client itself so always use 'RUN_MODE'

			super.launch(conf, ILaunchManager.RUN_MODE, launch, new SubProgressMonitor(mon, 1));
			if (isDebug) {
				//TODO: set debug port in config (or chosen dynamically?)
				launchRemote(localDebugPort.get(), conf, launch, new SubProgressMonitor(mon, 1));
			}
		} finally {
			localDebugPort.remove();
			mon.done();
		}
	}

	/**
	 * Returns a free port number on localhost, or -1 if unable to find a free port.
	 *
	 * @return a free port number on localhost, or -1 if unable to find a free port
	 */
	public static int findFreePort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			socket.setReuseAddress(true);
			return socket.getLocalPort();
		} catch (IOException e) {
		}
		return -1;
	}


	public static String getRemoteUrl(ILaunchConfiguration conf) {
		try {
			return conf.getAttribute(REMOTE_URL, (String)null);
		} catch (CoreException e) {
			BootLaunchActivator.getInstance().getLog().error("Failed to retrieve remote URL", e);
		}
		return null;
	}

	public static void setRemoteUrl(ILaunchConfigurationWorkingCopy conf, String value) {
		conf.setAttribute(REMOTE_URL, value);
	}

	/**
	 * Stores the remote secret in Eclipse's secure storage rather than as a plain
	 * launch configuration attribute, so it is never written in cleartext to the
	 * {@code .launch} XML file (workspace metadata, or the project tree if the
	 * launch configuration is shared).
	 */
	public static void setRemoteSecret(ILaunchConfigurationWorkingCopy conf, String value) {
		String oldId = getSecretId(conf);
		if (StringUtil.hasText(value)) {
			String id = oldId != null ? oldId : UUID.randomUUID().toString();
			DevtoolsRemoteSecretStore.put(id, value);
			conf.setAttribute(REMOTE_SECRET_ID, id);
		} else {
			DevtoolsRemoteSecretStore.remove(oldId);
			conf.setAttribute(REMOTE_SECRET_ID, (String) null);
		}
		// Clear out any legacy cleartext copy (from configs saved before this fix).
		conf.setAttribute(REMOTE_SECRET, (String) null);
	}

	public static String getRemoteSecret(ILaunchConfiguration conf) {
		String id = getSecretId(conf);
		if (id != null) {
			return DevtoolsRemoteSecretStore.get(id);
		}
		// Legacy fallback for configs saved before secure storage was used.
		try {
			return conf.getAttribute(REMOTE_SECRET, (String) null);
		} catch (CoreException e) {
			BootLaunchActivator.getInstance().getLog().error("", e);
		}
		return null;
	}

	/**
	 * Removes the secret from secure storage. Callers that delete a devtools
	 * client launch configuration (e.g. managed configs created for a remote
	 * debug session) should call this first to avoid leaving an orphaned entry.
	 */
	public static void clearRemoteSecret(ILaunchConfiguration conf) {
		DevtoolsRemoteSecretStore.remove(getSecretId(conf));
	}

	private static String getSecretId(ILaunchConfiguration conf) {
		try {
			return conf.getAttribute(REMOTE_SECRET_ID, (String) null);
		} catch (CoreException e) {
			BootLaunchActivator.getInstance().getLog().error("Failed to retrieve DevTools secret id", e);
			return null;
		}
	}

	/**
	 * Create debugging target similar to a remote debugging session would and add them to the launch.
	 * This is to support debugging of the remote boot-app that is reachable over http tunnel
	 * the client creates. From our side this just as if we are opening a remote debug
	 * session to the client.
	 */
	private void launchRemote(int port, ILaunchConfiguration configuration, final ILaunch launch, IProgressMonitor _monitor) throws CoreException {
		if (port < 0) {
			return;
		}
		final IProgressMonitor monitor = _monitor == null ? new NullProgressMonitor() : _monitor;

		monitor.beginTask(NLS.bind(LaunchingMessages.JavaRemoteApplicationLaunchConfigurationDelegate_Attaching_to__0_____1, new String[]{configuration.getName()}), 3);
		// check for cancellation
		if (monitor.isCanceled()) {
			return;
		}
		try {
			monitor.subTask(LaunchingMessages.JavaRemoteApplicationLaunchConfigurationDelegate_Verifying_launch_attributes____1);

			//String connectorId = "org.eclipse.jdt.launching.socketListenConnector";//getVMConnectorId(configuration);
			String connectorId = "org.eclipse.jdt.launching.socketAttachConnector";
			final IVMConnector connector = JavaRuntime.getVMConnector(connectorId);
			if (connector == null) {
				abort(LaunchingMessages.JavaRemoteApplicationLaunchConfigurationDelegate_Connector_not_specified_2, null, IJavaLaunchConfigurationConstants.ERR_CONNECTOR_NOT_AVAILABLE);
			}

			final Map<String, String> argMap = new HashMap<>();

	        int connectTimeout = Platform.getPreferencesService().getInt(
	        		LaunchingPlugin.ID_PLUGIN,
	        		JavaRuntime.PREF_CONNECT_TIMEOUT,
	        		JavaRuntime.DEF_CONNECT_TIMEOUT,
	        		null);
	        argMap.put("hostname", "localhost");
	        argMap.put("timeout", ""+connectTimeout);
	        argMap.put("port", ""+port);

			// check for cancellation
			if (monitor.isCanceled()) {
				return;
			}

			monitor.worked(1);

			//Don't think we need to set source location since the main launch method already does this.

//			monitor.subTask(LaunchingMessages.JavaRemoteApplicationLaunchConfigurationDelegate_Creating_source_locator____2);
//			// set the default source locator if required
//			setDefaultSourceLocator(launch, configuration);
//			monitor.worked(1);

			// connect to remote VM
			try {
				new WaitFor(DEBUG_CONNECT_TIMEOUT) {
					public void run() throws Exception {
						connector.connect(argMap, monitor, launch);
					}
				};
				new ProcessTracker(new ProcessListenerAdapter() {
					public void debugTargetTerminated(ProcessTracker tracker, IDebugTarget target) {
						handleTermination(tracker, target.getLaunch());
					}
					public void processTerminated(ProcessTracker tracker, IProcess process) {
						handleTermination(tracker, process.getLaunch());
					}
					private void handleTermination(ProcessTracker tracker, ILaunch targetLaunch) {
						if (launch.equals(targetLaunch)) {
							tracker.dispose();
							terminateAllTargets(launch);
						}
					}
				});
			} catch (Exception e) {
				terminateAllTargets(launch);
				throw ExceptionUtil.coreException(e);
			}

			// check for cancellation
			if (monitor.isCanceled()) {
				terminateAllTargets(launch);
	            return;
			}
		}
		finally {
			monitor.done();
		}
	}

	public void terminateAllTargets(final ILaunch launch) {
		//Note: its better to discconect debugtargets before terminating processes
		// because that allows a cleaner disconnect from the debugged process.
		// (If the devtools client process is terminated its no longer possible to talk to the
		// debugged process).
		IDebugTarget[] debugTargets = launch.getDebugTargets();
		for (int i = 0; i < debugTargets.length; i++) {
		    IDebugTarget target = debugTargets[i];
		    if (target.canDisconnect()) {
		    	try {
		    		target.disconnect();
		    	} catch (Exception e) {
		    		BootLaunchActivator.getInstance().getLog().error("Failed to disconnect.", e);
		    	}
		    }
		}
		IProcess[] processes = launch.getProcesses();
		for (IProcess process : processes) {
			if (process.canTerminate()) {
		    	try {
					process.terminate();
		    	} catch (Exception e) {
		    		BootLaunchActivator.getInstance().getLog().error("Failed to terminate.", e);
		    	}
			}
		}
	}

	public static void setManaged(ILaunchConfigurationWorkingCopy wc, boolean isManaged) {
		wc.setAttribute(MANAGED, isManaged);
	}

	public static boolean isManaged(ILaunchConfiguration c) {
		try {
			return c.getAttribute(MANAGED, false);
		} catch (CoreException e) {
			Log.log(e);
			return false;
		}
	}

}
