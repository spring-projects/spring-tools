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
package org.springframework.ide.eclipse.boot.launch.util;

import java.util.concurrent.Callable;

import javax.management.remote.JMXConnector;

import org.eclipse.core.runtime.Assert;
import org.eclipse.debug.core.ILaunch;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springsource.ide.eclipse.commons.core.util.ProcessUtils;

/**
 * Creates and manages an instance of {@link SpringApplicationLifecycleClient}.
 *
 * @author Kris De Volder
 */
public class SpringApplicationLifeCycleClientManager {

	private Callable<JMXConnector> connectionProvider;
	private JMXConnector connector;
	private SpringApplicationLifecycleClient client;

	public SpringApplicationLifeCycleClientManager(Callable<JMXConnector> connectionProvider) {
		Assert.isNotNull(connectionProvider);
		this.connectionProvider = connectionProvider;
	}

	/**
	 * Convenenience method, use ILaunch as the connection provider: attach to the launched
	 * process by its real OS PID and start its local management agent on-demand.
	 */
	public SpringApplicationLifeCycleClientManager(ILaunch l) {
		this(() -> {
			String pid = l.getAttribute(BootLaunchConfigurationDelegate.PROCESS_ID);
			if (pid == null) {
				throw new IllegalStateException("No process id is available for this launch");
			}
			return ProcessUtils.createJMXConnector(pid);
		});
	}

	/**
	 * Dispose of current client and JMX connection. This does not
	 * make the manager itself unusable, as an attempt will be made to
	 * re-establish the connection the next time it is needed.
	 */
	public synchronized void disposeClient() {
		try {
			if (connector != null) {
				connector.close();
			}
		} catch (Exception e) {
			//ignore
		}
		client = null;
		connector = null;
	}

	/**
	 * Try to obtain a client, may return null if a connection could not be established.
	 */
	public SpringApplicationLifecycleClient getLifeCycleClient() throws Exception {
		try {
			if (client == null) {
				connector = connectionProvider.call();
				if (connector != null) {
					client = new SpringApplicationLifecycleClient(
							connector.getMBeanServerConnection(),
							SpringApplicationLifecycleClient.DEFAULT_OBJECT_NAME
					);
				}
			}
			return client;
		} catch (Exception e) {
			//e.printStackTrace();
			//Someting went wrong creating client (most likely process we are trying to connect
			// doesn't exist yet or has been terminated.
			disposeClient();
			throw e;
		}
	}

}
