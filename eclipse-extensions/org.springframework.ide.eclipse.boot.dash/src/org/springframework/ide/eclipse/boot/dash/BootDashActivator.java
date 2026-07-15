/*******************************************************************************
 * Copyright (c) 2015, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.springframework.ide.eclipse.boot.core.BootPropertyTester;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.liveprocess.CommandInfo;
import org.springframework.ide.eclipse.boot.dash.liveprocess.LiveProcessCommandsExecutor;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModelContext;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.model.DefaultBootDashModelContext;
import org.springframework.ide.eclipse.boot.dash.model.LocalBootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.RunTargets;
import org.springframework.ide.eclipse.boot.dash.util.LaunchConfRunStateTracker;
import org.springframework.ide.eclipse.boot.dash.util.RunStateTracker.RunStateListener;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * The activator class controls the plug-in life cycle
 */
public class BootDashActivator extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "org.springframework.ide.eclipse.boot.dash"; //$NON-NLS-1$

	public static final String PREF_LIVE_INFORMATION_AUTO_CONNECT = "boot-java.live-information.automatic-connection.on";

	public static final String DT_ICON_ID = "devttools";
	public static final String MANIFEST_ICON = "manifest";
	public static final String CLOUD_ICON = "cloud";
	public static final String REFRESH_ICON = "refresh";
	public static final String SERVICE_ICON = "service";
	public static final String SERVICE_INACTIVE_ICON = "service-inactive";
	public static final String BOOT_ICON = "boot";
	public static final String CHECK_ICON = "check";
	public static final String CHECK_GREYSCALE_ICON = "check-greyscale";

	// The shared instance
	private static BootDashActivator plugin;

	private BootDashViewModel model;

	private BootDashModelContext context;

	/**
	 * The constructor
	 */
	public BootDashActivator() {
	}

	private IProxyService proxyService;

	public static final String INJECTIONS_EXTENSION_ID = "org.springframework.ide.eclipse.boot.dash.injections";

	public synchronized IProxyService getProxyService() {
		if (proxyService==null) {
			BundleContext bc = getBundle().getBundleContext();
			if (bc!=null) {
				ServiceReference<IProxyService> sr = bc.getServiceReference(IProxyService.class);
				proxyService = bc.getService(sr);
			}
		}
		return proxyService;
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.
	 * BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		new M2ELogbackCustomizer().schedule();
		listenToLaunchedBootApps();
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.
	 * BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		stopListeningToLaunchedBootApps();
		plugin = null;
		super.stop(context);
		if (model!=null) {
			model.dispose();
		}
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static BootDashActivator getDefault() {
		return plugin;
	}

	/**
	 * Returns an image descriptor for the image file at the given plug-in
	 * relative path
	 *
	 * @param path
	 *            the path
	 * @return the image descriptor
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}

	/**
	 * Deprecated. Use static methods in {@link Log} instead.
	 */
	@Deprecated public static void log(Throwable e) {
		Log.log(e);
	}

	/**
	 * Deprecated. Use {@link Log}.warn() instead.
	 */
	@Deprecated public static void logWarning(String message) {
		Log.warn(message);
	}

	public synchronized BootDashViewModel getModel() {
		if (model==null) {
			model = getInjections().getBean(BootDashViewModel.class);

//			DebugSelectionListener debugSelectionListener = new DebugSelectionListener(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getSelectionService());
//			model.addDisposableChild(debugSelectionListener);
		}
		return model;
	}

	public synchronized SimpleDIContext getInjections() {
		if (context == null) {
			context = DefaultBootDashModelContext.create();
		}
		return context.injections;
	}

	@Override
	protected void initializeImageRegistry(ImageRegistry reg) {
		super.initializeImageRegistry(reg);
		reg.put(DT_ICON_ID, getImageDescriptor("/icons/DT.png"));
		reg.put(CLOUD_ICON, getImageDescriptor("/icons/cloud_obj.png"));
		reg.put(MANIFEST_ICON, getImageDescriptor("icons/selectmanifest.gif"));
		reg.put(REFRESH_ICON, getImageDescriptor("/icons/refresh.png"));
		reg.put(SERVICE_ICON, getImageDescriptor("icons/service.png"));
		reg.put(SERVICE_INACTIVE_ICON, getImageDescriptor("icons/service-inactive.png"));
		reg.put(BOOT_ICON, getImageDescriptor("icons/boot-icon.png"));
		reg.put(CHECK_ICON, getImageDescriptor("icons/check.png"));
		reg.put(CHECK_GREYSCALE_ICON, getImageDescriptor("icons/check_greyedout.png"));
	}

	public static IEclipsePreferences getPreferences() {
		return InstanceScope.INSTANCE.getNode(PLUGIN_ID);
	}

	private final RunStateListener<ILaunchConfiguration> RUN_STATE_LISTENER = new RunStateListener<>() {

		@Override
		public void stateChanged(ILaunchConfiguration owner) {
			if (BootLaunchConfigurationDelegate.getEnableJmx(owner)) {
				IJavaProject project = JavaCore.create(BootLaunchConfigurationDelegate.getProject(owner));
				try {
					if (project != null && Arrays.stream(project.getResolvedClasspath(true)).anyMatch(BootPropertyTester::isActuatorJar)) {
						LocalBootDashModel localModel = (LocalBootDashModel) getModel().getSectionByTargetId(RunTargets.LOCAL.getId());
						LaunchConfRunStateTracker tracker = localModel.getLaunchConfRunStateTracker();
						switch (tracker.getState(owner)) {
						case RUNNING:
						case DEBUGGING:
							for (ILaunch l : DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
								if (l.getLaunchConfiguration() == owner) {
									for (IProcess p : l.getProcesses()) {
										String pid = p.getAttribute(IProcess.ATTR_PROCESS_ID);
										if (pid != null) {
											Map<String, Object> liveDataArgs = new HashMap<>();
											liveDataArgs.put("host", "127.0.0.1");
											liveDataArgs.put("urlScheme", "http");
											// Only a pinned, nonzero JMX port has a stable jmxurl. For the
											// auto/dynamic port case no port is picked here; the language
											// server attaches to 'processId' on-demand instead.
											int jmxPort = BootLaunchConfigurationDelegate.getJMXPortAsInt(l);
											if (jmxPort > 0) {
												liveDataArgs.put("jmxurl", "service:jmx:rmi:///jndi/rmi://127.0.0.1:%s/jmxrmi".formatted(jmxPort));
											}
											liveDataArgs.put("manualConnect", !BootLaunchConfigurationDelegate.getAutoConnect(owner));
											liveDataArgs.put("processId", pid);
											liveDataArgs.put("processName", owner.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, p.getLabel()));
											liveDataArgs.put("projectName", project.getProject().getName());
											CommandInfo cmd = new CommandInfo("sts/livedata/localAdd", liveDataArgs);

											LiveProcessCommandsExecutor.getDefault().executeCommand(cmd).subscribe();
										}
									}
								}
							}
							break;
						case INACTIVE:
							for (ILaunch l : DebugPlugin.getDefault().getLaunchManager().getLaunches()) {
								if (l.getLaunchConfiguration() == owner) {
									int jmxPort = BootLaunchConfigurationDelegate.getJMXPortAsInt(l);
									// Must match the key used when the app was added above: jmxurl if a
									// port was pinned, otherwise the process id (see PROCESS_ID, captured
									// by BootProcessFactory for the auto/dynamic port case).
									String processKey = jmxPort > 0
											? "service:jmx:rmi:///jndi/rmi://127.0.0.1:%s/jmxrmi".formatted(jmxPort)
											: l.getAttribute(BootLaunchConfigurationDelegate.PROCESS_ID);
									if (processKey != null) {
										LiveProcessCommandsExecutor.getDefault().executeCommand("sts/livedata/localRemove", processKey).subscribe();
									}
								}
							}
						default:
						}
					}
				} catch (Exception e) {
					getLog().error("Failed to connect to Boot app", e);
				}
			}
		}
	};

	private void listenToLaunchedBootApps() {
		LocalBootDashModel localModel = (LocalBootDashModel) getModel().getSectionByTargetId(RunTargets.LOCAL.getId());
		LaunchConfRunStateTracker tracker = localModel.getLaunchConfRunStateTracker();
		tracker.addListener(RUN_STATE_LISTENER);

	}

	private void stopListeningToLaunchedBootApps() {
		LocalBootDashModel localModel = (LocalBootDashModel) getModel().getSectionByTargetId(RunTargets.LOCAL.getId());
		LaunchConfRunStateTracker tracker = localModel.getLaunchConfRunStateTracker();
		tracker.removeListener(RUN_STATE_LISTENER);
	}

}
