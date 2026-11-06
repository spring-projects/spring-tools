/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.docker.jmx;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import org.springframework.ide.eclipse.boot.launch.util.PortFinder;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * Helper class providing functionality to connect to JMX on a remote spring boot app
 * running on CF, using ssh tunneling.
 * <p>
 * The main responsiblity of this class is to manage tunnel life-cycle based on the
 * app's state. (I.e. ensure tunnel is created when app started and tunnel is closed
 * when app stopped or deleted).
 *
 * @author Kris De Volder
 */
public class JmxSupport {

	private static final String JAVA_OPTS = "JAVA_OPTS";

	private static final String JMX_OPTION_PAT =
			"-D(com\\.sun\\.management\\.jmxremote|java\\.rmi\\.server|spring\\.jmx)\\.[a-z\\.]*=\\S*\\s*";

	/**
	 * User name of the JMX credentials generated for the container.
	 */
	public static final String JMX_USER = "spring-tools";

	/**
	 * Location of the JMX password and access files inside the container. The files are only readable
	 * by the user that runs the app (see {@link JmxPasswordInstaller}), the password itself is
	 * never part of the container's command line or environment.
	 */
	public static final String PASSWORD_FILE = "/tmp/spring-tools-jmxremote.password";
	// The JVM requires an access file naming the user, it otherwise falls back to its default one
	public static final String ACCESS_FILE = "/tmp/spring-tools-jmxremote.access";

	private static final SecureRandom RANDOM = new SecureRandom();

	private static String randomPassword() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static final String JMX_ARGS(int port) {
//		return JmxBeanSupport.jmxBeanVmArgs(port, EnumSet.of(JMX, LIFE_CYCLE));
		return "-Dcom.sun.management.jmxremote.ssl=false " +
			   "-Dcom.sun.management.jmxremote.authenticate=true " +
			   "-Dcom.sun.management.jmxremote.password.file="+PASSWORD_FILE+" " +
			   "-Dcom.sun.management.jmxremote.access.file="+ACCESS_FILE+" " +
			   "-Dcom.sun.management.jmxremote.port="+port+" " +
			   "-Dcom.sun.management.jmxremote.rmi.port="+port+" " +
			   "-Djava.rmi.server.hostname=localhost " +
			   "-Dcom.sun.management.jmxremote.local.only=false "+
			   "-Dspring.jmx.enabled=true " +
			   "-Dspring.application.admin.enabled=true " +
			   "-Dmanagement.endpoints.jmx.exposure.include=*";
	}
	
	int port;
	private final String password;

	/**
	 * Re-creates the support for an existing container. 
	 * @param password the JMX password the container was created with or <code>null</code> if
	 * 	JMX of that container is not password protected
	 */
	public JmxSupport(int port, String password) {
		this.port = port;
		this.password = password;
	}
	
	public JmxSupport() {
		this.password = randomPassword();
		try {
			port = PortFinder.findFreePort();
		} catch (Exception e) {
			Log.log(e);
			port = 0;
		}
	}

	public String getJmxUser() {
		return password == null ? null : JMX_USER;
	}

	public String getJmxPassword() {
		return password;
	}

	/**
	 * @return content of the JMX access file to be installed in the container
	 */
	public String getAccessFileContent() {
		return JMX_USER + " readwrite\n";
	}

	/**
	 * @return content of the JMX password file to be installed in the container
	 */
	public String getPasswordFileContent() {
		return JMX_USER + " " + password + "\n";
	}

	public void setupEnvVars(int port, Map<String, String> env) {
		if (port > 0) {
			String javaOpts = env.get(JAVA_OPTS);
			if (javaOpts != null) {
				//Erase old vars
				javaOpts = javaOpts.replaceAll(JMX_OPTION_PAT, "").trim();
			} else {
				javaOpts = "";
			}
			String jmxArgs = JMX_ARGS(port);
			if ("".equals(javaOpts)) {
				// no other java opts yet
				javaOpts = jmxArgs;
			} else {
				javaOpts = javaOpts + " " +jmxArgs;
			}
			env.put(JAVA_OPTS, javaOpts);
		}
	}

	public String getJmxUrl() {
		if (port > 0) {
			return "service:jmx:rmi://localhost:"+port+"/jndi/rmi://localhost:"+port+"/jmxrmi";
		} else {
			return null;
		}
	}

	public int getPort() {
		return port;
	}

	public String getJavaOpts() {
		return JMX_ARGS(port);
	}
}
