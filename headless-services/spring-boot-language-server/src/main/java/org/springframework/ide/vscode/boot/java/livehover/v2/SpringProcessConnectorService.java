/*******************************************************************************
 * Copyright (c) 2019, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.livehover.v2;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.commons.languageserver.DiagnosticService;
import org.springframework.ide.vscode.commons.languageserver.IndefiniteProgressTask;
import org.springframework.ide.vscode.commons.languageserver.ProgressService;
import org.springframework.ide.vscode.commons.languageserver.util.ShowMessageException;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

/**
 * @author Martin Lippert
 */
public class SpringProcessConnectorService {

	private static final String METRICS = "metrics";
	public static final String GC_PAUSES = "gcPauses";
	public static final String MEMORY = "memory";
	public static final String HEAP_MEMORY = "heapMemory";
	public static final String NON_HEAP_MEMORY = "nonHeapMemory";
	private static final String LOGGERS = "loggers";

	private static final Logger log = LoggerFactory.getLogger(SpringProcessConnectorService.class);

	private final SpringProcessLiveDataProvider liveDataProvider;

	private final ScheduledThreadPoolExecutor scheduler;
	private final ConcurrentMap<String, SpringProcessConnector> connectors;
	private final ConcurrentMap<String, Boolean> connectedSuccess;

	private final SpringProcessConnectionChangeListener connectorListener;

	private ProgressService progressService;
	private DiagnosticService diagnosticService;
	
	private int progressIdKey = 0;
	private int maxRetryCount;
	private int retryDelayInSeconds;
		
	public SpringProcessConnectorService(SimpleLanguageServer server, SpringProcessLiveDataProvider liveDataProvider) {
		this.liveDataProvider = liveDataProvider;
		this.scheduler = new ScheduledThreadPoolExecutor(10);
		this.connectors = new ConcurrentHashMap<>();
		this.connectedSuccess = new ConcurrentHashMap<>();
		
		this.progressService = server.getProgressService();
		if (this.progressService == null) {
			this.progressService = ProgressService.NO_PROGRESS;
		}
		
		this.diagnosticService = server.getDiagnosticService();
		
		this.maxRetryCount = BootJavaConfig.LIVE_INFORMATION_FETCH_DATA_RETRY_MAX_NO_DEFAULT;
		this.retryDelayInSeconds = BootJavaConfig.LIVE_INFORMATION_FETCH_DATA_RETRY_DELAY_IN_SECONDS_DEFAULT;
		
		this.connectorListener = new SpringProcessConnectionChangeListener() {
			@Override
			public void connectionClosed(String processKey) {
				disconnectProcess(processKey);
			}
		};
	}
	
	public void setMaxRetryCount(int maxRetryCount) {
		this.maxRetryCount = maxRetryCount;
	}
	
	public void setRetryDelayInSeconds(int retryDelayInSeconds) {
		this.retryDelayInSeconds = retryDelayInSeconds;
	}
	
	public CompletableFuture<Void> connectProcess(String processKey, SpringProcessConnector connector) {
		log.info("connect to process: " + processKey);

		this.connectors.put(processKey, connector);
		this.connectedSuccess.put(processKey, false);
		
		connector.addConnectorChangeListener(connectorListener);

		final IndefiniteProgressTask progressTask = getProgressTask(
				"spring-process-connector-service-connect-" + processKey, "Connect", null);			
		return scheduleConnect(progressTask, processKey, connector, 0, TimeUnit.SECONDS, 0);
	}
	
	public CompletableFuture<Void> refreshProcess(SpringProcessParams springProcessParams) {
		log.info("refresh process: " + springProcessParams.getProcessKey());
		
		SpringProcessConnector connector = this.connectors.get(springProcessParams.getProcessKey());
		if (connector != null) {
			final IndefiniteProgressTask progressTask = getProgressTask(
					"spring-process-connector-service-refresh-data-" + springProcessParams.getProcessKey(), "Refresh", null);

			return scheduleRefresh(progressTask, springProcessParams, connector, 0, TimeUnit.SECONDS, 0);
		}
		return CompletableFuture.completedFuture(null);
	}

	public SpringProcessLiveData getLiveData(String processKey) {
		return this.liveDataProvider.getCurrent(processKey);
	}
	
	public SpringProcessMemoryMetricsLiveData getMemoryMetricsLiveData(String processKey) {
		return this.liveDataProvider.getMemoryMetrics(processKey);
	}
	
	public SpringProcessGcPausesMetricsLiveData getGcPausesMetricsLiveData(String processKey) {
		return this.liveDataProvider.getGcPausesMetrics(processKey);
	}

	public void disconnectProcess(String processKey) {
		log.info("disconnect from process: " + processKey);

		this.liveDataProvider.remove(processKey);

		SpringProcessConnector connector = this.connectors.remove(processKey);
		this.connectedSuccess.put(processKey, false);
		
		if (connector != null) {
			final IndefiniteProgressTask progressTask = getProgressTask(
					"spring-process-connector-service-disconnect-" + processKey, "Disconnect", null);
			
			scheduleDisconnect(progressTask, processKey, connector, 0, TimeUnit.SECONDS, 0);
		}
	}
	
	public SpringProcessConnector[] getConnectedProcesses() {
		return (SpringProcessConnector[]) this.connectors.values().stream()
				.filter((connector) -> connectedSuccess.get(connector.getProcessKey())).toArray(SpringProcessConnector[]::new);
	}
	
	public boolean isKnownProcessKey(String processKey) {
		return this.connectors.containsKey(processKey);
	}

	/**
	 * common method to generate process keys from process IDs and process names
	 */
	public static String getProcessKey(String processID, String processName) {
		return processID;
	}
	
	private CompletableFuture<Void> scheduleConnect(IndefiniteProgressTask progressTask, String processKey, SpringProcessConnector connector, long delay, TimeUnit unit, int retryNo) {
		String progressMessage = "Connecting to process: " + processKey + " - retry no: " + retryNo;
		log.info(progressMessage);
		
		return CompletableFuture.runAsync(() -> {
			try {
				progressTask.progressEvent(progressMessage);
				connector.connect();
				progressTask.done();				
			}
			catch (Exception e) {
				throw new CompletionException(e);
			}
		}, CompletableFuture.delayedExecutor(delay, unit, scheduler)).thenCompose(v -> refreshProcess(new SpringProcessParams(processKey, "", "", ""))).exceptionallyCompose(e -> {
			log.info("problem occurred during process connect", e);

			if (retryNo < maxRetryCount && isKnownProcessKey(processKey)) {
				return scheduleConnect(progressTask, processKey, connector, retryDelayInSeconds, TimeUnit.SECONDS, retryNo + 1);
			} else {
				progressTask.done();
				
				// Send message to client if maximum retries reached on error
				if (isKnownProcessKey(processKey)) {
					diagnosticService.diagnosticEvent(ShowMessageException
								.error("Failed to connect to process " + processKey + " after retries: " + retryNo, e));	
				}
				return CompletableFuture.completedStage(null);
			}
		});
	}

	private void scheduleDisconnect(IndefiniteProgressTask progressTask, String processKey, SpringProcessConnector connector, long delay, TimeUnit unit, int retryNo) {
		String message = "Disconnect from process: " + processKey + " - retry no: " + retryNo;
		log.info(message);
	
		
		this.scheduler.schedule(() -> {
			try {
				progressTask.progressEvent(message);
				connector.disconnect();
				progressTask.done();
			}
			catch (Exception e) {
				log.info("problem occurred during process disconnect", e);

				if (retryNo < maxRetryCount) {
					scheduleDisconnect(progressTask, processKey, connector, retryDelayInSeconds, TimeUnit.SECONDS, retryNo + 1);
				} else {
					progressTask.done();
					
					// Send message to client if maximum retries reached on error
					diagnosticService.diagnosticEvent(ShowMessageException
							.error("Failed to disconnect from process " + processKey + " after retries: " + retryNo, e));
				
				}
			}
		}, delay, unit);
	}

	private CompletableFuture<Void> scheduleRefresh(IndefiniteProgressTask progressTask, SpringProcessParams springProcessParams, SpringProcessConnector connector, long delay, TimeUnit unit, int retryNo) {
		String processKey = springProcessParams.getProcessKey();
		String endpoint = springProcessParams.getEndpoint();
		String metricName = springProcessParams.getMetricName();
	    String progressMessage = "Refreshing data from Spring process: " + processKey + " - retry no: " + retryNo;
		log.info(progressMessage);
		
		return CompletableFuture.runAsync(() -> {
			try {
				progressTask.progressEvent(progressMessage);
				if(METRICS.equals(endpoint) && (MEMORY.equals(metricName))) {
					SpringProcessMemoryMetricsLiveData newMetricsLiveData = connector.refreshMemoryMetrics(this.liveDataProvider.getCurrent(processKey), metricName, springProcessParams.getTags());
					
					if (newMetricsLiveData != null) {
						if (!this.liveDataProvider.addMemoryMetrics(processKey, newMetricsLiveData)) {
							this.liveDataProvider.updateMemoryMetrics(processKey, newMetricsLiveData);
						}
						
						this.connectedSuccess.put(processKey, true);
					}
					
				} else if(METRICS.equals(endpoint) && GC_PAUSES.equals(metricName)) {
					SpringProcessGcPausesMetricsLiveData newMetricsLiveData = connector.refreshGcPausesMetrics(this.liveDataProvider.getCurrent(processKey), metricName, springProcessParams.getTags());
					
					if (newMetricsLiveData != null) {
						if (!this.liveDataProvider.addGcPausesMetrics(processKey, newMetricsLiveData)) {
							this.liveDataProvider.updateGcPausesMetrics(processKey, newMetricsLiveData);
						}
						
						this.connectedSuccess.put(processKey, true);
					}
					
				} else {
					SpringProcessLiveData newLiveData = connector.refresh(this.liveDataProvider.getCurrent(processKey));
	
					if (newLiveData != null) {
						if (!this.liveDataProvider.add(processKey, newLiveData)) {
							this.liveDataProvider.update(processKey, newLiveData);
						}
						
						this.connectedSuccess.put(processKey, true);
					}
				}
				progressTask.done();
			}
			catch (Exception e) {
				throw new CompletionException(e);
			}
		}, CompletableFuture.delayedExecutor(delay, unit, scheduler)).exceptionallyCompose(e -> {
			log.info("problem occurred during process live data refresh", e);
			
			if (retryNo < maxRetryCount && isKnownProcessKey(processKey)) {
				return scheduleRefresh(progressTask, springProcessParams, connector, retryDelayInSeconds, TimeUnit.SECONDS,
						retryNo + 1);
			}
			else {
				progressTask.done();
				
				// Send message to client if maximum retries reached on error
				if (isKnownProcessKey(processKey)) {
					diagnosticService.diagnosticEvent(ShowMessageException
							.error("Failed to refresh live data from process " + processKey + " after retries: " + retryNo, e));

					if (!connectedSuccess.containsKey(connector.getProcessKey())) {
						disconnectProcess(processKey);
					}
				}
				return CompletableFuture.completedFuture(null);
			}

		});
		
	}
	
	private IndefiniteProgressTask getProgressTask(String prefixId, String title, String message) {
		return this.progressService.createIndefiniteProgressTask(prefixId + progressIdKey++, title, message);
	}
	
	public SpringProcessLoggersData getLoggers(SpringProcessParams springProcessParams) {
		log.info("get loggers data: " + springProcessParams.getProcessKey());
		CompletableFuture<SpringProcessLoggersData> loggerData = new CompletableFuture<>();
		SpringProcessConnector connector = this.connectors.get(springProcessParams.getProcessKey());
		if (connector != null) {
			final IndefiniteProgressTask progressTask = getProgressTask(
					"spring-process-connector-service-get-loggers-data" + springProcessParams.getProcessKey(), "Loggers", null);
			getLoggersData(progressTask, springProcessParams, connector, loggerData, 0, TimeUnit.SECONDS, 0);
			 try {
			        return loggerData.get();
			    } catch (InterruptedException | ExecutionException e) {
			        log.error("Failed to fetch loggers data for the process "+ springProcessParams.getProcessKey());
			    }
		}
		return null;
	}
	
	public void getLoggersData(IndefiniteProgressTask progressTask, SpringProcessParams springProcessParams, SpringProcessConnector connector, CompletableFuture<SpringProcessLoggersData> loggerData, long delay, TimeUnit unit, int retryNo) {
		String processKey = springProcessParams.getProcessKey();
		String endpoint = springProcessParams.getEndpoint();

	    String progressMessage = "Get loggers for Spring process: " + processKey + " - retry no: " + retryNo;
		log.info(progressMessage);

		this.scheduler.schedule(() -> {

			try {
				progressTask.progressEvent(progressMessage);
				if(LOGGERS.equals(endpoint)) {
					SpringProcessLoggersData loggersData = connector.getLoggers(this.liveDataProvider.getCurrent(processKey));

					if (loggersData != null) {
						loggerData.complete(loggersData);
						this.connectedSuccess.put(processKey, true);
					}

				}
				progressTask.done();
			}
			catch (Exception e) {

				log.info("problem occurred during process live data refresh", e);

				if (retryNo < maxRetryCount && isKnownProcessKey(processKey)) {
					getLoggersData(progressTask, springProcessParams, connector, loggerData, retryDelayInSeconds, TimeUnit.SECONDS,
							retryNo + 1);
				}
				else {
					progressTask.done();

					// Send message to client if maximum retries reached on error
					if (isKnownProcessKey(processKey)) {
						diagnosticService.diagnosticEvent(ShowMessageException
								.error("Failed to refresh live data from process " + processKey + " after retries: " + retryNo, e));

						if (!connectedSuccess.containsKey(connector.getProcessKey())) {
							loggerData.complete(null);
							disconnectProcess(processKey);
						}
					}
				}
			}

		}, 0, TimeUnit.SECONDS);		

	}
	
	public void configureLogLevel(SpringProcessParams springProcessParams) {
		log.info("change log level: " + springProcessParams.getProcessKey());

		SpringProcessConnector connector = this.connectors.get(springProcessParams.getProcessKey());
		if (connector != null) {
			final IndefiniteProgressTask progressTask = getProgressTask(
					"spring-process-connector-service-configure-log-level" + springProcessParams.getProcessKey(), "Loggers", null);
			configureLogLevel(progressTask, springProcessParams, connector, 0, TimeUnit.SECONDS, 0);
		}
	}

	private void configureLogLevel(IndefiniteProgressTask progressTask, SpringProcessParams springProcessParams,
			SpringProcessConnector connector, long delay, TimeUnit unit, int retryNo) {
		String processKey = springProcessParams.getProcessKey();

	    String progressMessage = "configure log level for Spring process: " + processKey + " - retry no: " + retryNo;
		log.info(progressMessage);

		this.scheduler.schedule(() -> {

			try {
				progressTask.progressEvent(progressMessage);
				SpringProcessUpdatedLogLevelData springProcessUpdatedLoggersData = connector.configureLogLevel(this.liveDataProvider.getCurrent(processKey), springProcessParams.getArgs());

				if(springProcessUpdatedLoggersData != null) {
					this.liveDataProvider.updateLogLevel(processKey, springProcessUpdatedLoggersData);
					this.connectedSuccess.put(processKey, true);
				}	

				progressTask.done();
			}
			catch (Exception e) {

				log.info("problem occurred during process live data refresh", e);

				if (retryNo < maxRetryCount && isKnownProcessKey(processKey)) {
					configureLogLevel(progressTask, springProcessParams, connector, retryDelayInSeconds, TimeUnit.SECONDS,
							retryNo + 1);
				}
				else {
					progressTask.done();

					// Send message to client if maximum retries reached on error
					if (isKnownProcessKey(processKey)) {
						diagnosticService.diagnosticEvent(ShowMessageException
								.error("Failed to refresh live data from process " + processKey + " after retries: " + retryNo, e));

						if (!connectedSuccess.containsKey(connector.getProcessKey())) {
							disconnectProcess(processKey);
						}
					}
				}
			}

		}, 0, TimeUnit.SECONDS);	
	}

}
