/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.util;

import java.io.IOException;
import java.net.MalformedURLException;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.springsource.ide.eclipse.commons.frameworks.core.util.StringUtils;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

/**
 * A JMX client for interacting with mbeans.
 *
 * @author Stephane Nicoll
 * @author Kris De Volder
 */
public class JMXClient implements Disposable {

	private static final Object[] NO_PARAMS = new Object[0];
	private static final String[] NO_SIGNATURES = new String[0];

	private JMXConnector connector;
	private MBeanServerConnection connection;

	public JMXClient(String url) throws IOException {
		this(createJmxConnectorFromUrl(url));
	}

	private JMXClient(JMXConnector connector) throws IOException {
		this(connector, connector.getMBeanServerConnection());
	}

	@Override
	public void dispose() {
		try {
			this.connector.close();
		} catch (IOException e) {
			//Ignore
		}
	}

	private JMXClient(JMXConnector connector, MBeanServerConnection connection) {
		this.connector = connector;
		this.connection = connection;
	}

	@SuppressWarnings("unchecked")
	public <T> T getAttribute(Class<T> klass, String objectName, String attributeName) throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException {
		Object value = getAttribute(objectName, attributeName);
		if (value == null || klass.isInstance(value)) {
			return (T)value;
		} else {
			throw new ClassCastException("Value '"+value+"' can't be cast to "+klass);
		}
	}

	public Object getAttribute(String objectName, String attributeName) throws AttributeNotFoundException, InstanceNotFoundException, MBeanException, ReflectionException, IOException {
		return this.connection.getAttribute(toObjectName(objectName), attributeName);
	}

	public Object callOperation(String objectName, String operationName) throws InstanceNotFoundException, MBeanException, ReflectionException, IOException {
		return this.connection.invoke(toObjectName(objectName), operationName, NO_PARAMS, NO_SIGNATURES);
	}


	private ObjectName toObjectName(String name) {
		try {
			return new ObjectName(name);
		}
		catch (MalformedObjectNameException ex) {
			throw new IllegalArgumentException("Invalid jmx name '" + name + "'");
		}
	}

	/**
	 * Create a connector for an {@link javax.management.MBeanServer} exposed on the
	 * current machine and the current port. Security should be disabled.
	 * @param port the port on which the mbean server is exposed
	 * @return a connection
	 * @throws IOException if the connection to that server failed
	 */
	public static String createLocalJmxUrl(String portStr) {
		if (StringUtils.hasText(portStr)) {
			try {
				int port = Integer.valueOf(portStr);
				return createLocalJmxUrl(port);
			} catch (Exception e) {
				Log.log(e);
			}
		}
		return null;
	}

	public static String createLocalJmxUrl(Integer port) {
		if (port != null && port > 0) {
			return "service:jmx:rmi:///jndi/rmi://127.0.0.1:" + port + "/jmxrmi";
		}
		return null;
	}

	public static JMXConnector createJmxConnectorFromUrl(String url) throws MalformedURLException, IOException {
		JMXServiceURL serviceUrl = new JMXServiceURL(url);
		return JMXConnectorFactory.connect(serviceUrl, null);
	}

	public static JMXConnector createLocalJmxConnector(int port) throws MalformedURLException, IOException {
		if (port > 0) {
			return createJmxConnectorFromUrl(createLocalJmxUrl(""+port));
		}
		return null;
	}

}
