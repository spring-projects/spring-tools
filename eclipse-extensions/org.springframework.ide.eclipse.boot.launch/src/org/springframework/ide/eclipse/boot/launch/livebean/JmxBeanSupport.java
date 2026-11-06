/*******************************************************************************
 * Copyright (c) 2014, 2026 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.livebean;

import java.util.EnumSet;

import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;

/**
 * Helper methods to generate vm args for launch config with JMX support enabled.
 *
 * @author Kris De Volder
 */
public class JmxBeanSupport {

	public static enum Feature {
		JMX(null),
		LIVE_BEAN_GRAPH("-Dspring.liveBeansView.mbeanDomain"),
		LIFE_CYCLE("-Dspring.application.admin.enabled=true");

		public final String vmArg;

		Feature(String vmArg) {
			this.vmArg = vmArg;
		}
	}

	/**
	 * VM args that enable 'live bean graph' and jmx.
	 */
	public static String jmxBeanVmArgs(EnumSet<Feature> enabled) {
		if (!enabled.isEmpty()) {
			//At least one feature enabled
			StringBuilder str = new StringBuilder();
			String[] args = autoJmxArgs();
			for (String a : args) {
				str.append(a+"\n");
			}
			for (Feature feature : enabled) {
				if (feature.vmArg != null) {
					str.append(feature.vmArg+"\n");
				}
			}
			return str.toString();
		} else {
			//No features enabled
			return "";
		}
	}

	/**
	 * No {@code com.sun.management.jmxremote*} args are emitted: the launched JVM never opens a
	 * JMX port. The JMX agent is started on-demand later via the Attach API against the child's
	 * real OS PID.
	 */
	public static String[] autoJmxArgs() {
		return new String[] {
				"-Dspring.jmx.enabled=true",
				"-Dmanagement.endpoints.jmx.exposure.include=*"
		};
	}

	public static final String LAUNCH_CONFIG_TYPE_ID = BootLaunchConfigurationDelegate.TYPE_ID;

}
