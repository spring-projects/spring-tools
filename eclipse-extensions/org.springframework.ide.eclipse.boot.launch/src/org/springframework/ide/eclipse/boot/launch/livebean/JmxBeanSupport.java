/*******************************************************************************
 * Copyright (c) 2014, 2022 Pivotal Software, Inc.
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
	public static String jmxBeanVmArgs(int jmxPort, EnumSet<Feature> enabled) {
		return jmxBeanVmArgs(""+jmxPort, enabled);
	}

	public static String jmxBeanVmArgs(String jmxPort, EnumSet<Feature> enabled) {
		if (!enabled.isEmpty()) {
			//At least one feature enabled
			StringBuilder str = new StringBuilder();
			String[] args = "0".equals(jmxPort) ? autoJmxArgs() : enableJmxArgs(jmxPort);
			for (String a : args) {
				str.append(a+"\n");
			}
			for (Feature feature : enabled) {
				if (feature.vmArg!=null) {
					str.append(feature.vmArg+"\n");
				}
			}
			return str.toString();
		} else {
			//No features enabled
			return "";
		}
	}

	public static String[] enableJmxArgs(String jmxPort) {
		return new String[] {
				"-Dcom.sun.management.jmxremote", //enable jmx to access the beans
				"-D"+ JMX_PORT_PROP +"="+jmxPort,
				"-Dcom.sun.management.jmxremote.authenticate=false",
				"-Dcom.sun.management.jmxremote.ssl=false",
				"-Djava.rmi.server.hostname=localhost",
				//
				// PT 164402781 - For Boot 2.2 and above, jmx is no longer enabled by default.
				// We explicitly enable it here. Adding this doesn't seem to affect older boot versions
				// and we get live information from JMX for those too.
				//
				"-Dspring.jmx.enabled=true",
				//
				// GH-862: Spring Boot 3 doesn't expose all actuators over JMX anymore by default
				// therefore we need to set this for live hovers here
				//
				"-Dmanagement.endpoints.jmx.exposure.include=*"
		};
	}

	/**
	 * VM args for the auto/dynamic port case (jmxPort == 0). No
	 * {@code com.sun.management.jmxremote*} args are emitted here: rather than picking a port
	 * up front, the JMX agent is started on-demand later via the Attach API against the
	 * child's real OS PID, once the child actually exists and can bind its own port.
	 */
	public static String[] autoJmxArgs() {
		return new String[] {
				"-Dspring.jmx.enabled=true",
				"-Dmanagement.endpoints.jmx.exposure.include=*"
		};
	}

	public static final String JMX_PORT_PROP = "com.sun.management.jmxremote.port";
	public static final String LAUNCH_CONFIG_TYPE_ID = BootLaunchConfigurationDelegate.TYPE_ID;
	public static int randomPort() {
		return (int) (5000 + Math.random()*60000);
	}

}
