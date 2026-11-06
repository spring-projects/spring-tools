/*******************************************************************************
 * Copyright (c) 2019, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.livehover.v2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Martin Lippert
 */
public class SpringProcessConnectorOverJMX implements SpringProcessConnector {

	private static final Logger log = LoggerFactory.getLogger(SpringProcessConnectorOverJMX.class);

	private static final String JMX_CLIENT_CONNECTION_CHECK_PERIOD_PROPERTY_KEY = "jmx.remote.x.client.connection.check.period";
	private static final long JMX_HEARTBEAT_INTERVAL = 1000;

	private final String processKey;
	private final String jmxURL;
	// same as jmxURL, but safe to log (see redact)
	private final String redactedURL;
	private final String urlScheme;
	private final String port;
	// optional JMX credentials (user, password) for password protected JMX endpoints (e.g. docker containers)
	private final String[] credentials;
	private final String projectName;
	
	// not final, might be updated with data from JMX process, if not initially set
	private String processID;
	private String processName;
	private String host;
	
	private final List<SpringProcessConnectionChangeListener> listeners;
	
	private JMXConnector jmxConnection;
	private JMXServiceURL jmxServiceURL;

	private final NotificationListener notificationListener;

	private final ProcessType processType;

	public SpringProcessConnectorOverJMX(ProcessType processType, String processKey, String jmxURL,
			String urlScheme, String processID, String processName, String projectName, String host, String port) {
		this(processType, processKey, jmxURL, urlScheme, processID, processName, projectName, host, port, null, null);
	}

	public SpringProcessConnectorOverJMX(ProcessType processType, String processKey, String jmxURL,
			String urlScheme, String processID, String processName, String projectName, String host, String port,
			String jmxUser, String jmxPassword) {
		this.processType = processType;
		this.credentials = jmxUser != null && jmxPassword != null ? new String[] { jmxUser, jmxPassword } : null;
		this.processKey = processKey;

		this.jmxURL = jmxURL;
		this.redactedURL = redact(jmxURL);
		this.urlScheme = urlScheme;
		this.processID = processID;
		this.processName = processName;
		this.projectName = projectName;
		this.host = host;
		this.port = port;

		this.jmxConnection = null;
		this.jmxServiceURL = null;
		
		this.listeners = new CopyOnWriteArrayList<>();
		
		this.notificationListener = new NotificationListener() {
			@Override
			public void handleNotification(Notification notification, Object handback) {
				String notificationType = notification.getType();
				
				if (JMXConnectionNotification.CLOSED.equals(notificationType)) {
					try {
						jmxConnection.removeConnectionNotificationListener(notificationListener);
						jmxConnection = null;
					}
					catch (Exception e) {
						log.error("exception while reacting to connection close of: " + redactedURL, e);
					}
					announceConnectionClosed();
				}
			}
		};
	}
	
	/**
	 * A JMX URL of the form <code>service:jmx:rmi://host/stub/&lt;serialized RMI stub&gt;</code> is a
	 * capability: the stub carries the object id of the JMX server, which is the only thing that
	 * protects an unauthenticated local JMX connector from other users of the machine. Such URLs
	 * must never be written to logs.
	 */
	static String redact(String jmxURL) {
		if (jmxURL != null) {
			int i = jmxURL.indexOf("/stub/");
			if (i >= 0) {
				return jmxURL.substring(0, i + "/stub/".length()) + "<redacted>";
			}
		}
		return jmxURL;
	}

	@Override
	public ProcessType getProcessType() {
		return processType;
	}

	@Override
	public String getProcessKey() {
		return processKey;
	}
	
	@Override
	public void connect() throws Exception {
		jmxServiceURL = new JMXServiceURL(jmxURL);
		
		Map<String, Object> environment = new HashMap<>();
		environment.put(JMX_CLIENT_CONNECTION_CHECK_PERIOD_PROPERTY_KEY, Long.valueOf(JMX_HEARTBEAT_INTERVAL));
		if (credentials != null) {
			environment.put(JMXConnector.CREDENTIALS, credentials);
		}
		jmxConnection = JMXConnectorFactory.connect(jmxServiceURL, environment);
		
		jmxConnection.addConnectionNotificationListener(notificationListener, null, null);
	}

	@Override
	public SpringProcessLiveData refresh(SpringProcessLiveData currentData) throws Exception {
		log.info("try to open JMX connection to: " + redactedURL);
		
		if (jmxConnection != null) {
			try {
				SpringProcessLiveDataExtractorOverJMX springJMXConnector = new SpringProcessLiveDataExtractorOverJMX();
	
				if (this.host == null) {
					this.host = jmxServiceURL.getHost();
				}
				
				log.info("retrieve live data from: " + redactedURL);
				SpringProcessLiveData liveData = springJMXConnector.retrieveLiveData(getProcessType(), jmxConnection, processID, processName, urlScheme, host, null, port, currentData);
				
				if (this.processID == null) {
					this.processID = liveData.getProcessID();
				}
				
				if (this.processName == null) {
					this.processName = liveData.getProcessName();
				}
				
				if (liveData != null && liveData.getBeans() != null && !liveData.getBeans().isEmpty()) {
					return liveData;
				}
			}
			catch (Exception e) {
				log.error("exception while connecting to jmx: " + redactedURL, e);
			}
		}
		
		throw new Exception("no live data received, lets try again");
	}
	
	@Override
	public SpringProcessMemoryMetricsLiveData refreshMemoryMetrics(SpringProcessLiveData currentData, String metricName, String tags) throws Exception {
		log.info("try to open JMX connection to: " + redactedURL);
		
		if (jmxConnection != null) {
			try {
				SpringProcessLiveDataExtractorOverJMX springJMXConnector = new SpringProcessLiveDataExtractorOverJMX();
	
				log.info("retrieve live data from: " + redactedURL);
				SpringProcessMemoryMetricsLiveData liveData = springJMXConnector.retrieveLiveMemoryMetricsData(getProcessType(), jmxConnection, processID, processName, currentData, metricName, tags);
				
				if (liveData != null && liveData.getHeapMemoryMetrics() != null && liveData.getHeapMemoryMetrics().length > 0 &&
				        liveData.getNonHeapMemoryMetrics() != null && liveData.getNonHeapMemoryMetrics().length > 0) {
					return liveData;
				}
			}
			catch (Exception e) {
				log.error("exception while connecting to jmx: " + redactedURL, e);
			}
		}
		
		throw new Exception("no live data received, lets try again");
	}
	
	@Override
	public SpringProcessGcPausesMetricsLiveData refreshGcPausesMetrics(SpringProcessLiveData currentData, String metricName, String tags) throws Exception {
		log.info("try to open JMX connection to: " + redactedURL);
		
		if (jmxConnection != null) {
			try {
				SpringProcessLiveDataExtractorOverJMX springJMXConnector = new SpringProcessLiveDataExtractorOverJMX();
	
				log.info("retrieve live data from: " + redactedURL);
				SpringProcessGcPausesMetricsLiveData liveData = springJMXConnector.retrieveLiveGcPausesMetricsData(getProcessType(), jmxConnection, processID, processName, currentData, metricName, tags);
				
				if (liveData != null /* && liveData.getGcPausesMetrics() != null && liveData.getGcPausesMetrics().length > 0*/ ) {
					return liveData;
				}
			}
			catch (Exception e) {
				log.error("exception while connecting to jmx: " + redactedURL, e);
			}
		}
		
		throw new Exception("no live data received, lets try again");
	}

	@Override
	public void disconnect() throws Exception {
		try {
			if (jmxConnection != null) {

				log.info("close JMX connection to: " + redactedURL);
				jmxConnection.removeConnectionNotificationListener(notificationListener);
				jmxConnection.close();
				jmxConnection = null;
			}
		}
		catch (Exception e) {
			log.error("error closing the JMX connection for: " + redactedURL, e);
		}
	}
	
	@Override
	public SpringProcessLoggersData getLoggers(SpringProcessLiveData currentData) throws Exception {
		log.info("try to open JMX connection to: " + redactedURL);

		if (jmxConnection != null) {
			try {
				SpringProcessLiveDataExtractorOverJMX springJMXConnector = new SpringProcessLiveDataExtractorOverJMX();

				log.info("retrieve live data from: " + redactedURL);
				SpringProcessLoggersData loggersData = springJMXConnector.retrieveLoggersData(getProcessType(), jmxConnection, processID, processName, currentData);

				if (loggersData != null) {
					return loggersData;
				}
			}
			catch (Exception e) {
				log.error("exception while connecting to jmx: " + redactedURL, e);
			}
		}

		throw new Exception("no loggers data received, lets try again");
	}
	
	@Override
	public SpringProcessUpdatedLogLevelData configureLogLevel(SpringProcessLiveData currentData, Map<String, String> args) throws Exception {
		log.info("try to open JMX connection to: " + redactedURL);

		if (jmxConnection != null) {
			try {
				SpringProcessLiveDataExtractorOverJMX springJMXConnector = new SpringProcessLiveDataExtractorOverJMX();

				log.info("retrieve live data from: " + redactedURL);
				SpringProcessUpdatedLogLevelData springProcessUpdatedLoggersData = springJMXConnector.configureLogLevel(getProcessType(), jmxConnection, processID, processName, currentData, args);

				return springProcessUpdatedLoggersData;
			}
			catch (Exception e) {
				log.error("exception while connecting to jmx: " + redactedURL, e);
			}
		}

		throw new Exception("configure log level failed, lets try again");
	}

	@Override
	public void addConnectorChangeListener(SpringProcessConnectionChangeListener listener) {
		this.listeners.add(listener);
	}

	@Override
	public void removeConnectorChangeListener(SpringProcessConnectionChangeListener listener) {
		this.listeners.remove(listener);
	}
	
	private void announceConnectionClosed() {
		for (SpringProcessConnectionChangeListener listener : this.listeners) {
			listener.connectionClosed(processKey);
		}
	}

	@Override
	public String getProjectName() {
		return projectName;
	}

	@Override
	public String getProcessId() {
		return processID;
	}

	@Override
	public String getProcessName() {
		return processName;
	}

	@Override
	public String toString() {
		return "SpringProcessConnectorOverJMX [jmxURL=" + redactedURL + ", processID=" + processID + ", processName="
				+ processName + ", listeners=" + listeners + ", jmxConnection=" + jmxConnection + ", jmxServiceURL="
				+ jmxServiceURL + ", notificationListener=" + notificationListener + "]";
	}

}
