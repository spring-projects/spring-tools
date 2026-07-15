/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.RuntimeProcess;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.core.cli.BootInstallManager;
import org.springframework.ide.eclipse.boot.core.cli.install.CloudCliInstall;
import org.springframework.ide.eclipse.boot.core.cli.install.IBootInstall;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.livebean.JmxBeanSupport;
import org.springframework.ide.eclipse.boot.launch.process.BootProcessFactory;
import org.springframework.ide.eclipse.boot.launch.util.PortFinder;
import org.springframework.ide.eclipse.boot.util.version.Version;
import org.springframework.ide.eclipse.boot.util.version.VersionParser;
import org.springframework.ide.eclipse.boot.util.version.VersionRange;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * Spring Cloud CLI service launch configuration
 *
 * @author Alex Boyko
 *
 */
public class CloudCliServiceLaunchConfigurationDelegate extends BootCliLaunchConfigurationDelegate {

	private static final VersionRange SPRING_CLOUD_CLI_SINGLE_PROCESS_VERSION_RANGE = new VersionRange(new Version(1, 3, 0, null));
	private static final VersionRange SPRING_CLOUD_CLI_SUPPORTS_THIN_LAUNCH_PARAM = VersionParser.DEFAULT.parseRange("[1.3.0, 2.2.0)");

	public final static String TYPE_ID = "org.springframework.ide.eclipse.boot.launch.cloud.cli.service";

	public final static String ATTR_CLOUD_SERVICE_ID = "local-cloud-service-id";

	private final static String PREF_DONT_SHOW_PLATFORM_WARNING = "org.springframework.ide.eclipse.boot.launch.cloud.cli.NotSupportedPlatform";

	private List<String> getCloudCliServiceLifeCycleVmArguments(ILaunchConfiguration configuration, int jmxPort) {
		List<String> vmArgs = new ArrayList<>();
			EnumSet<JmxBeanSupport.Feature> enabled = BootLaunchConfigurationDelegate
					.getEnabledJmxFeatures(configuration);
			if (!enabled.isEmpty()) {
				String enableLiveBeanArgs = JmxBeanSupport.jmxBeanVmArgs(jmxPort, enabled);
				vmArgs.addAll(Arrays.asList(enableLiveBeanArgs.split("\n")));
			}
		return vmArgs;
	}

	protected String[] getProgramArgs(IBootInstall bootInstall, ILaunch launch, ILaunchConfiguration configuration) {
		try {
			CloudCliInstall cloudCliInstall = bootInstall.getExtension(CloudCliInstall.class);
			if (cloudCliInstall == null) {
				Log.error("No Spring Cloud CLI installation found");
			} else {
				String serviceId = configuration.getAttribute(ATTR_CLOUD_SERVICE_ID, (String) null);
				Version cloudCliVersion = cloudCliInstall.getVersion();
				List<String> vmArgs = new ArrayList<>();
				List<String> args = new ArrayList<>();

				args.add(CloudCliInstall.COMMAND_PREFIX);
				args.add(serviceId);

				if (cloudCliVersion != null && SPRING_CLOUD_CLI_SUPPORTS_THIN_LAUNCH_PARAM.match(cloudCliVersion)) {
					args.add("--deployer=thin");
				}

				args.add("--");
				args.add("--logging.level.org.springframework.cloud.launcher.deployer=DEBUG");

				// VM argument for the service log output
				if (BootLaunchConfigurationDelegate.supportsAnsiConsoleOutput()) {
					vmArgs.add("-Dspring.output.ansi.enabled=always");
				}

				if (CloudCliServiceLaunchConfigurationDelegate.SPRING_CLOUD_CLI_SINGLE_PROCESS_VERSION_RANGE.match(cloudCliVersion)) {
					if (!vmArgs.isEmpty()) {
						args.add("--spring.cloud.launcher.deployables." + serviceId + ".properties.spring.cloud.deployer.local.javaOpts=" + String.join(",", vmArgs));
					}
				} else if (CloudCliInstall.CLOUD_CLI_JAVA_OPTS_SUPPORTING_VERSIONS.match(cloudCliVersion)) {
					int jmxPort = getJmxPort(configuration);
					// Set the JMX port for launch
					launch.setAttribute(BootLaunchConfigurationDelegate.JMX_PORT, String.valueOf(jmxPort));
					vmArgs.addAll(getCloudCliServiceLifeCycleVmArguments(configuration, jmxPort));
					// Set the JMX port connection jvm args for the service
					if (!vmArgs.isEmpty()) {
						args.add("--spring.cloud.launcher.deployables." + serviceId + ".properties.JAVA_OPTS=" + String.join(",", vmArgs));
					}
				}
				return args.toArray(new String[args.size()]);
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return new String[0];
	}

	private int getJmxPort(ILaunchConfiguration configuration) {
		int port = 0;
		try {
			port = Integer.parseInt(BootLaunchConfigurationDelegate.getJMXPort(configuration));
		} catch (Exception e) {
			// ignore: bad data in launch config.
		}
		if (port == 0) {
			try {
				// slightly better than calling JmxBeanSupport.randomPort()
				port = PortFinder.findFreePort();
			} catch (IOException e) {
				Log.log(e);
			}
		}
		return port;
	}

	public static boolean isLocalCloudServiceLaunch(ILaunchConfiguration conf) {
		try {
			if (conf!=null) {
					String type = conf.getType().getIdentifier();
				return TYPE_ID.equals(type);
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return false;
	}

	public static ILaunchConfigurationWorkingCopy createLaunchConfig(String serviceId) throws CoreException {
		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type = launchManager.getLaunchConfigurationType(TYPE_ID);
		ILaunchConfigurationWorkingCopy config = type.newInstance(null, serviceId);

		// Set default config with life cycle tracking support because it should cover with life cycle tracking and without
		BootLaunchConfigurationDelegate.setDefaults(config, null, null);

		config.setAttribute(ATTR_CLOUD_SERVICE_ID, serviceId);

		// Overwrite process factory class because for latest version of Cloud CLI life cycle tracking through JMX port is not available for services
		BootLaunchConfigurationDelegate.setProcessFactory(config, CloudCliProcessFactory.class);
		return config;
	}

	public static boolean canUseLifeCycle(ILaunch launch) {
		ILaunchConfiguration conf = launch.getLaunchConfiguration();
		return conf!=null && canUseLifeCycle(conf);
	}

	public static boolean isSingleProcessServiceConfig(ILaunchConfiguration conf) {
		try {
			if (isCloudCliService(conf)) {
				IBootInstall bootInstall = BootInstallManager.getInstance().getDefaultInstall();
				if (bootInstall != null) {
					Version cloudCliVersion = bootInstall.getExtension(CloudCliInstall.class) == null ? null : bootInstall.getExtension(CloudCliInstall.class).getVersion();
					return SPRING_CLOUD_CLI_SINGLE_PROCESS_VERSION_RANGE.match(cloudCliVersion);
				}
			}
		} catch (Exception e) {
			// ignore
		}
		return false;
	}

	public static boolean isCloudCliService(ILaunchConfiguration conf) {
		try {
			return TYPE_ID.equals(conf.getType().getIdentifier());
		} catch (CoreException e) {
			// Ignore
		}
		return false;
	}

	public static boolean canUseLifeCycle(ILaunchConfiguration conf) {
		try {
			if (!isCloudCliService(conf)) {
				return false;
			}
			IBootInstall bootInstall = BootInstallManager.getInstance().getDefaultInstall();
			if (bootInstall == null) {
				return false;
			}
			Version cloudCliVersion = bootInstall.getExtension(CloudCliInstall.class) == null ? null : bootInstall.getExtension(CloudCliInstall.class).getVersion();
			// Cloud CLI version below 1.2.0 and over 1.3.0 can't have JMX connection to cloud service hence life cycle should be disabled.
			if (!canUseLifeCycle(cloudCliVersion)) {
				return false;
			}
			return SPRING_CLOUD_CLI_SINGLE_PROCESS_VERSION_RANGE.match(cloudCliVersion) || BootLaunchConfigurationDelegate.getEnableLifeCycle(conf);
		} catch (Exception e) {
			// Ignore
		}
		return false;
	}

	private static boolean canUseLifeCycle(Version cloudCliVersion) {
		// Cloud CLI version below 1.2.0 and over 1.3.0 can't have JMX connection to cloud service hence life cycle should be disabled.
		if (cloudCliVersion == null
				|| !CloudCliInstall.CLOUD_CLI_JAVA_OPTS_SUPPORTING_VERSIONS.match(cloudCliVersion)
				|| SPRING_CLOUD_CLI_SINGLE_PROCESS_VERSION_RANGE.match(cloudCliVersion)) {
			return false;
		}
		return true;
	}

	public static class CloudCliProcessFactory extends BootProcessFactory {

		@Override
		public IProcess newProcess(ILaunch launch, Process process, String label, Map<String, String> attributes) {
			try {
				IBootInstall bootInstall = BootInstallManager.getInstance().getDefaultInstall();
				if (bootInstall != null) {
					Version cloudCliVersion = bootInstall.getExtension(CloudCliInstall.class) == null ? null : bootInstall.getExtension(CloudCliInstall.class).getVersion();
					if (CloudCliServiceLaunchConfigurationDelegate.isSingleProcessServiceConfig(launch.getLaunchConfiguration())) {
						final IPreferenceStore store = BootActivator.getDefault().getPreferenceStore();
						// Set invalid PID initially thus if PID is failed to be calculated then set PID launch attribute to invalid PID to fallback to default non-JMX process tracking
						long pid = -1;
						try {
							pid = process.pid();
						} catch (UnsupportedOperationException e) {
							Log.warn(e);
							if (!store.getBoolean(PREF_DONT_SHOW_PLATFORM_WARNING)) {
								PlatformUI.getWorkbench().getDisplay().asyncExec(() -> {
									MessageDialogWithToggle dialog = MessageDialogWithToggle.openWarning(
										Display.getCurrent().getActiveShell(), "Cloud CLI Service Info Limitation",
										"Cloud service process life-cycle data is limited and port data is unavailable on the current platform.",
										"Don't show this message again",
										false, null, null);
									store.setValue(PREF_DONT_SHOW_PLATFORM_WARNING, dialog.getToggleState());
								});
							}
						}
						launch.setAttribute(BootLaunchConfigurationDelegate.PROCESS_ID, String.valueOf(pid));
						return new RuntimeProcess(launch, process, label, attributes);
					} else if (canUseLifeCycle(cloudCliVersion)) {
						return super.newProcess(launch, process, label, attributes);
					}
				}
			} catch (Exception e) {
				Log.log(e);
			}
			return new RuntimeProcess(launch, process, label, attributes);
		}

	}

}
