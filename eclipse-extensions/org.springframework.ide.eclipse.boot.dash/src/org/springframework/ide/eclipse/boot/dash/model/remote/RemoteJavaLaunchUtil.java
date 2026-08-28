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

import java.util.Collection;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.Launch;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.JavaRuntime;
import org.springframework.ide.eclipse.boot.dash.model.ClasspathPropertyTester;
import org.springframework.ide.eclipse.boot.util.RetryUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class RemoteJavaLaunchUtil {

	public static final String DISABLE_HCR_LAUNCH_ATTRIBUTE = "org.eclipse.jdt.debug.disable.hcr"; //$NON-NLS-1$
		//should be same value as org.eclipse.jdt.debug.core.JDIDebugModel.DISABLE_HCR_LAUNCH_ATTRIBUTE
		//We don't refer to that constant directly because it will only exist in Eclipse 4.18 (assuming PR is accepted)
		//See: https://git.eclipse.org/r/c/jdt/eclipse.jdt.debug/+/169601
		//In the mean time, we can already set this attrbute, it will do no harm (has no effect).


	public static void cleanupOldLaunchConfigs(Collection<GenericRemoteAppElement> existinginElements) {
		//TODO: implement this and find a good place and time to call it from.
	}

	public static final String APP_NAME = "sts.boot.dash.element.name";

	/**
	 * Check the state of a remote boot dash element and whether it needs to have a debugger
	 * attached. If yes, make sure there is a debugger attached.
	 */
	public synchronized static void synchronizeWith(GenericRemoteAppElement app) {
		if (isDebuggable(app)) {
			 ILaunch l = ensureDebuggerAttached(app);
			 if (app.hasClasspathProperty(ClasspathPropertyTester.HAS_DEVTOOLS)) {
				 l.setAttribute(DISABLE_HCR_LAUNCH_ATTRIBUTE, "true");
			 }
			 if (l != null) {
				 terminationListener().add(l, app);
			 }
		}
	}

	/**
	 * When containerized apps are being 'suspended' while being debugged the debug connections
	 * get confused. So 'Pause' action should disconnect debuggers from these apps.
	 * @param app
	 */
	public static void disconnectRelatedLaunches(GenericRemoteAppElement app) {
		ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();
		for (ILaunch _l : lm.getLaunches()) {
			try {
				if (_l instanceof Launch) {
					Launch l = (Launch) _l;
					ILaunchConfiguration conf = l.getLaunchConfiguration();
					if (conf != null) {
						if (app.getName().equals(conf.getAttribute(APP_NAME, ((String)null)))) {
							if (!l.isTerminated()) {
								if (((Launch)l).canDisconnect()) {
									l.disconnect();
								}
							}
						}
					}
				}
			} catch (Exception e) {
				Log.log(e);
			}
		}
	}

	private static DebugLaunchTerminationListener terminationListener;

	private synchronized static DebugLaunchTerminationListener terminationListener() {
		if (terminationListener == null) {
			terminationListener = new DebugLaunchTerminationListener();
			DebugPlugin.getDefault().getLaunchManager().addLaunchListener(terminationListener);
		}
		return terminationListener;
	}

	private static ILaunch ensureDebuggerAttached(GenericRemoteAppElement app) {
		try {
			ILaunchConfiguration conf = getLaunchConfig(app);
			if (conf == null) {
				conf = createLaunchConfig(app);
			}
			return ensureActiveLaunch(conf);
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}


	private static ILaunchConfiguration createLaunchConfig(GenericRemoteAppElement app) throws CoreException {
		ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();
		String launchConfName = lm.generateLaunchConfigurationName(app.getStyledName(null).getString());

		ILaunchConfigurationWorkingCopy wc = remoteJavaType().newInstance(null, launchConfName);
		wc.setAttribute(APP_NAME, app.getName());
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_ALLOW_TERMINATE, true);
		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_CONNECTOR, JavaRuntime.getDefaultVMConnector().getIdentifier());

		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, app.getProject().getName());

		wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_CONNECT_MAP, ImmutableMap.of(
				"hostname", "localhost",
				"port", ""+app.getDebugPort()
		));
		return wc.doSave();
	}

	private static ILaunch ensureActiveLaunch(ILaunchConfiguration conf) throws Exception {
		ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();
		for (ILaunch l : lm.getLaunches()) {
			if (conf.equals(l.getLaunchConfiguration())) {
				if (!l.isTerminated()) {
					return l;
				}
			}
		};
		ILaunch newLaunch = RetryUtil.retry(50, 1500, () ->
			 conf.launch(ILaunchManager.DEBUG_MODE, new NullProgressMonitor(), false, true)
		);
		return newLaunch;
	}

	private static ILaunchConfiguration getLaunchConfig(GenericRemoteAppElement app) {
		try {
			ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();
			ILaunchConfigurationType remoteJavaType = remoteJavaType();
			ILaunchConfiguration[] configs = lm.getLaunchConfigurations(remoteJavaType);
			for (ILaunchConfiguration conf : configs) {
				if (app.getName().equals(getAppName(conf))) {
					return conf;
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}


	private static ILaunchConfigurationType remoteJavaType() {
		ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType remoteJavaType = lm.getLaunchConfigurationType(IJavaLaunchConfigurationConstants.ID_REMOTE_JAVA_APPLICATION);
		return remoteJavaType;
	}


	private static String getAppName(ILaunchConfiguration conf) {
		try {
			return conf.getAttribute(APP_NAME, (String)null);
		} catch (CoreException e) {
			Log.log(e);
		}
		return null;
	}


	private static boolean isDebuggable(GenericRemoteAppElement app) {
		try {
			int debugPort = app.getDebugPort();
			IProject project = app.getProject();
			return debugPort > 0 && project != null && project.isAccessible() && project.hasNature(JavaCore.NATURE_ID);
		} catch (Exception e) {
			Log.log(e);
		}
		return false;
	}

	public static ImmutableSet<ILaunchConfiguration> getLaunchConfigs(GenericRemoteAppElement element) {
		try {
			String appName = element.getName();
			ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();
			ILaunchConfigurationType type = lm.getLaunchConfigurationType(IJavaLaunchConfigurationConstants.ID_REMOTE_JAVA_APPLICATION);
			ILaunchConfiguration[] confs = lm.getLaunchConfigurations(type);
			if (confs != null) {
				ImmutableSet.Builder<ILaunchConfiguration> found = ImmutableSet.builder();
				for (ILaunchConfiguration c : confs) {
					if (appName.equals(c.getAttribute(APP_NAME, (String)null))) {
						found.add(c);
					}
				}
				return found.build();
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return ImmutableSet.of();
	}
}