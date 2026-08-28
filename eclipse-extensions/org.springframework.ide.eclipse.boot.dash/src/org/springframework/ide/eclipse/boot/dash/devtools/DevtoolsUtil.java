/*******************************************************************************
 * Copyright (c) 2015, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.devtools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.springframework.ide.eclipse.boot.core.BootPropertyTester;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.api.App;
import org.springframework.ide.eclipse.boot.dash.api.DevtoolsConnectable;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.model.remote.GenericRemoteAppElement;
import org.springframework.ide.eclipse.boot.dash.util.CollectionUtils;
import org.springframework.ide.eclipse.boot.dash.views.RestartDevtoolsClientAction;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.devtools.BootDevtoolsClientLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.util.ProcessListenerAdapter;
import org.springframework.ide.eclipse.boot.util.ProcessTracker;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

/**
 * @author Kris De Volder
 */
public class DevtoolsUtil {

	private static final String TARGET_ID = "boot.dash.target.id";
	private static final String APP_NAME = "boot.dash.cloudfoundry.app-name";

	private static final QualifiedName REMOTE_CLIENT_SECRET_PROPERTY = new QualifiedName(BootDashActivator.PLUGIN_ID, "spring.devtools.remote.secret");

	private static final String JAVA_OPTS_ENV_VAR = "JAVA_OPTS";
	public static final String REMOTE_SECRET_PROP = "spring.devtools.remote.secret";
	private static final String REMOTE_SECRET_JVM_ARG = "-D"+REMOTE_SECRET_PROP+"=";
//	private static final String REMOTE_DEBUG_JVM_ARGS = "-Dspring.devtools.restart.enabled=false -Xdebug -Xrunjdwp:server=y,transport=dt_socket,suspend=n";

	private static ILaunchManager getLaunchManager() {
		return DebugPlugin.getDefault().getLaunchManager();
	}

	private static ILaunchConfigurationType getConfigurationType() {
		return getLaunchManager().getLaunchConfigurationType(BootDevtoolsClientLaunchConfigurationDelegate.TYPE_ID);
	}

	private static ILaunchConfigurationWorkingCopy createConfiguration(IProject project, BootDashElement bde) throws CoreException {
		ILaunchConfigurationType configType = getConfigurationType();
		String projectName = project.getName();
		ILaunchConfigurationWorkingCopy wc = configType.newInstance(null, getLaunchManager().generateLaunchConfigurationName("remote-devtools-client["+projectName+"]"));

		BootLaunchConfigurationDelegate.setProject(wc, project);
		BootDevtoolsClientLaunchConfigurationDelegate.setRemoteUrl(wc, remoteUrl(bde));
		BootDevtoolsClientLaunchConfigurationDelegate.setManaged(wc, true);

		wc.setMappedResources(new IResource[] {project});
		return wc;
	}

	public static String remoteUrl(BootDashElement bde) {
		String host = bde.getLiveHost();
		Integer port = CollectionUtils.getSingle(bde.getLivePorts());
		String proto = bde.getProtocol();
		if (proto != null && host != null && port != null && port > 0) {
			return proto +"://"+host+":"+port;
		}
		return null;
	}

	public static ILaunch launchDevtools(IProject project, String debugSecret, BootDashElement bde, String mode, IProgressMonitor monitor) throws CoreException {
		ILaunchConfiguration conf = getOrCreateLaunchConfig(project, debugSecret, bde);
		if (conf != null) {
			return conf.launch(mode, monitor == null ? new NullProgressMonitor() : monitor);
		}
		throw ExceptionUtil.coreException("Can't launch, no remote url?");
	}

	private static ILaunchConfiguration getOrCreateLaunchConfig(IProject project, String debugSecret, BootDashElement bde) throws CoreException {
		String remoteUrl = DevtoolsUtil.remoteUrl(bde);
		if (remoteUrl != null) {
			ILaunchConfiguration existing = findConfig(project, remoteUrl);
			ILaunchConfigurationWorkingCopy wc;
			if (existing != null) {
				wc = existing.getWorkingCopy();
			} else {
				wc = createConfiguration(project, bde);
			}
			BootDevtoolsClientLaunchConfigurationDelegate.setRemoteSecret(wc, debugSecret);
			setElement(wc, bde);
			return wc.doSave();
		}
		return null;
	}

	private static ILaunchConfiguration findConfig(IProject project, String remoteUrl) {
		try {
			for (ILaunchConfiguration c : getLaunchManager().getLaunchConfigurations(getConfigurationType())) {
				if (project.equals(BootLaunchConfigurationDelegate.getProject(c))
					&& remoteUrl.equals(BootDevtoolsClientLaunchConfigurationDelegate.getRemoteUrl(c))) {
					return c;
				}
			}
		} catch (CoreException e) {
			Log.log(e);
		}
		return null;
	}

	private static List<ILaunch> findLaunches(IProject project, BootDashElement bde) {
		String remoteUrl = remoteUrl(bde);
		if (remoteUrl != null) {
			List<ILaunch> launches = new ArrayList<>();
			for (ILaunch l : getLaunchManager().getLaunches()) {
				try {
					ILaunchConfiguration c = l.getLaunchConfiguration();
					if (c != null) {
						if (project.equals(BootLaunchConfigurationDelegate.getProject(c))
							&& remoteUrl.equals(BootDevtoolsClientLaunchConfigurationDelegate.getRemoteUrl(c))) {
							launches.add(l);
						}
					}
				} catch (Exception e) {
					Log.log(e);
				}
			}
			return launches;
		}
		return ImmutableList.of();
	}


	public static boolean isDevClientAttached(BootDashElement bde, String launchMode) {
		IProject project = bde.getProject();
		if (project != null) { // else not associated with a local project... can't really attach debugger then
			String host = bde.getLiveHost();
			if (host != null) { // else app not running, can't attach debugger then
				return isLaunchMode(findLaunches(project, bde), launchMode);
			}
		}
		return false;
	}

	private static boolean isLaunchMode(List<ILaunch> launches, String launchMode) {
		for (ILaunch l : launches) {
			if (!l.isTerminated()) {
				if (ILaunchManager.DEBUG_MODE.equals(launchMode) && launchMode.equals(l.getLaunchMode())) {
					for (IDebugTarget p : l.getDebugTargets()) {
						if (!p.isDisconnected() && !p.isTerminated()) {
							return true;
						}
					}
				} else if (ILaunchManager.RUN_MODE.equals(launchMode) && launchMode.equals(l.getLaunchMode())) {
					for (IProcess p : l.getProcesses()) {
						if (!p.isTerminated()) {
							return true;
						}
					}
				} else if (launchMode == null) {
					// Launch mode not specified? Launch is not terminated hence just return true
					return true;
				}
			}
		}
		return false;
	}

	public static ILaunch launchDevtools(BootDashElement cde, String debugSecret, String mode, IProgressMonitor monitor) throws CoreException {
		return launchDevtools(cde.getProject(), debugSecret, cde, mode, monitor);
	}

	public static void setElement(ILaunchConfigurationWorkingCopy l, BootDashElement bde) {
		//Tag the launch so we can easily determine what CDE it belongs to later.
		l.setAttribute(TARGET_ID, bde.getTarget().getId());
		l.setAttribute(APP_NAME, bde.getName());
	}

	public static boolean isLaunchFor(ILaunch l, BootDashElement bde) {
		String targetId = getAttribute(l, TARGET_ID);
		String appName = getAttribute(l, APP_NAME);
		if (targetId != null && appName != null) {
			return targetId.equals(bde.getTarget().getId())
					&& appName.equals(bde.getName());
		}
		return false;
	}

	/**
	 * Retreive corresponding CDE for a given launch.
	 */
	public static BootDashElement getElement(ILaunchConfiguration l, BootDashViewModel model) {
		String targetId = getAttribute(l, TARGET_ID);
		String appName = getAttribute(l, APP_NAME);
		if (targetId != null && appName != null) {
			BootDashModel section = model.getSectionByTargetId(targetId);
			if (section != null) {
				return section.getApplication(appName);
			}
		}
		return null;
	}

	public static BootDashElement getElement(ILaunch l, BootDashViewModel viewModel) {
		ILaunchConfiguration conf = l.getLaunchConfiguration();
		if (conf != null) {
			return getElement(conf, viewModel);
		}
		return null;
	}


	private static String getAttribute(ILaunch l, String name) {
		try {
			ILaunchConfiguration c = l.getLaunchConfiguration();
			if (c != null) {
				return c.getAttribute(name, (String)null);
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}

	private static String getAttribute(ILaunchConfiguration l, String name) {
		try {
			return l.getAttribute(name, (String)null);
		} catch (DebugException e) {
			//ignore Eclipse throws this sometimes (trying to read attribute from launch who's config was deleted.
		} catch (CoreException e) {
			Log.log(e);
		}
		return null;
	}

	public static ProcessTracker createProcessTracker(final BootDashViewModel viewModel) {
		return new ProcessTracker(new ProcessListenerAdapter() {
			@Override
			public void debugTargetCreated(ProcessTracker tracker, IDebugTarget target) {
				handleStateChange(target.getLaunch(), "debugTargetCreated");
			}
			@Override
			public void debugTargetTerminated(ProcessTracker tracker, IDebugTarget target) {
				handleStateChange(target.getLaunch(), "debugTargetTerminated");
				deleteConf(target.getLaunch());
			}

			@Override
			public void processTerminated(ProcessTracker tracker, IProcess process) {
				handleStateChange(process.getLaunch(), "processTerminated");
				deleteConf(process.getLaunch());
			}
			private void deleteConf(ILaunch launch) {
				try {
					ILaunchConfiguration conf = launch.getLaunchConfiguration();
					if (conf != null && BootDevtoolsClientLaunchConfigurationDelegate.isManaged(conf)) {
						BootDevtoolsClientLaunchConfigurationDelegate.clearRemoteSecret(conf);
						conf.delete();
					}
				} catch (Exception e) {
					Log.log(e);
				}
			}
			@Override
			public void processCreated(ProcessTracker tracker, IProcess process) {
				handleStateChange(process.getLaunch(), "processCreated");
			}
			private void handleStateChange(ILaunch l, Object info) {
				BootDashElement e = DevtoolsUtil.getElement(l, viewModel);
				if (e != null) {
					BootDashModel model = e.getBootDashModel();
					model.notifyElementChanged(e, info);
				}
			}

			@Override
			public void dispose() {
				try {
					for (ILaunchConfiguration c : DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurations()) {
						if (BootDevtoolsClientLaunchConfigurationDelegate.isManaged(c)) {
							BootDevtoolsClientLaunchConfigurationDelegate.clearRemoteSecret(c);
							c.delete();
						}
					}
				} catch (Exception e) {
					Log.log(e);
				}
			}
		});
	}

	public static void disconnectDevtoolsClientsFor(BootDashElement e) {
		ILaunchManager lm = getLaunchManager();
		for (ILaunch l : lm.getLaunches()) {
			if (!l.isTerminated() && isLaunchFor(l, e)) {
				if (l.canTerminate()) {
					try {
						l.terminate();
					} catch (DebugException de) {
						Log.log(de);
					}
				}
			}
		}
	}

	public static String getSecret(IProject project) {
		try {
			String secret = project.getPersistentProperty(REMOTE_CLIENT_SECRET_PROPERTY);
			if (secret == null) {
				secret = RandomStringUtils.secure().nextAlphabetic(20);
				project.setPersistentProperty(REMOTE_CLIENT_SECRET_PROPERTY, secret);
			}
			return secret;
		} catch (Exception e) {
			Log.log(e);
			return null;
		}
	}

	public static boolean isEnvVarSetupForRemoteClient(Map<String, String> envVars, String secret) {
		String javaOpts = envVars.get(JAVA_OPTS_ENV_VAR);
		if (javaOpts != null && javaOpts.matches("(.*\\s+|^)" + REMOTE_SECRET_JVM_ARG + secret + "(\\s+.*|$)")) {
//			if (runOrDebug == RunState.DEBUGGING) {
//				return javaOpts.matches("(.*\\s+|^)" + REMOTE_DEBUG_JVM_ARGS + "(\\s+.*|$)");
//			} else {
//				return !javaOpts.matches("(.*\\s+|^)" + REMOTE_DEBUG_JVM_ARGS + "(\\s+.*|$)");
//			}
			return true;
		}
		return false;
	}

	public static void setupEnvVarsForRemoteClient(Map<String, String> envVars, String secret) {
		String javaOpts = clearJavaOpts(envVars.get(JAVA_OPTS_ENV_VAR));
		StringBuilder sb = javaOpts == null ? new StringBuilder() : new StringBuilder(javaOpts);
		if (sb.length() > 0) {
			sb.append(' ');
		}
		sb.append(REMOTE_SECRET_JVM_ARG);
		sb.append(secret);
//		if (runOrDebug == RunState.DEBUGGING) {
//			sb.append(' ');
//			sb.append(REMOTE_DEBUG_JVM_ARGS);
//		}
		envVars.put(JAVA_OPTS_ENV_VAR, sb.toString());
	}

	private static String clearJavaOpts(String opts) {
		if (opts != null) {
//			opts = opts.replaceAll(REMOTE_DEBUG_JVM_ARGS + "\\s*", "");
			opts = opts.replaceAll(REMOTE_SECRET_JVM_ARG +"\\w+\\s*", "");
		}
		return opts;
	}

	public static void launchClientIfNeeded(GenericRemoteAppElement app) {
		if (wantsDevtools(app)) {
			if (!isDevClientAttached(app, ILaunchManager.RUN_MODE)) {
				app.restartRemoteDevtoolsClient();
			}
		}
	}

	private static boolean wantsDevtools(GenericRemoteAppElement app) {
		if (app.getRunState().isActive()) {
			App data = app.getAppData();
			return data instanceof DevtoolsConnectable && ((DevtoolsConnectable)data).isDevtoolsConnectable().isTrue();
		}
		return false;
	}

}
