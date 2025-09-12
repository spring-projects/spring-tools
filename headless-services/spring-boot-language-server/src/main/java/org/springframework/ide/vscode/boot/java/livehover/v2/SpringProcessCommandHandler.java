/*******************************************************************************
 * Copyright (c) 2019, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.livehover.v2;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessConnectorRemote.RemoteBootAppData;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.protocol.LiveProcessSummary;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * @author mlippert
 */
public class SpringProcessCommandHandler {
	
	private static final Logger log = LoggerFactory.getLogger(SpringProcessCommandHandler.class);
	
	private static final String COMMAND_LIST_PROCESSES = "sts/livedata/listProcesses";
	private static final String COMMAND_CONNECT = "sts/livedata/connect";
	private static final String COMMAND_REFRESH = "sts/livedata/refresh";
	private static final String COMMAND_DISCONNECT = "sts/livedata/disconnect";
	private static final String COMMAND_GET = "sts/livedata/get";
	private static final String COMMAND_LIST_CONNECTED = "sts/livedata/listConnected";
	private static final String COMMAND_GET_METRICS = "sts/livedata/get/metrics";
	private static final String COMMAND_GET_REFRESH_METRICS = "sts/livedata/refresh/metrics";
	private static final String COMMAND_GET_LOGGERS = "sts/livedata/getLoggers";
	private static final String COMMAND_CONFIGURE_LOGLEVEL = "sts/livedata/configure/logLevel";
	
	private final SpringProcessConnectorService connectorService;
	private final SpringProcessConnectorLocal localProcessConnector;
	private final Collection<SpringProcessConnectorRemote> remoteProcessConnectors;

	public SpringProcessCommandHandler(SimpleLanguageServer server, SpringProcessConnectorService connectorService,
			SpringProcessConnectorLocal localProcessConnector, Collection<SpringProcessConnectorRemote> remoteProcessConnectors) {
		this.connectorService = connectorService;
		this.localProcessConnector = localProcessConnector;
		this.remoteProcessConnectors = remoteProcessConnectors;

		server.onCommand(COMMAND_LIST_PROCESSES, (params) -> {
			return getProcessCommands();
		});
		log.info("Registered command handler: {}",COMMAND_LIST_PROCESSES);
		
		server.onCommand(COMMAND_CONNECT, (params) -> {
			return connect(params);
		});
		log.info("Registered command handler: {}",COMMAND_CONNECT);
		
		server.onCommand(COMMAND_REFRESH, (params) -> {
			return refresh(params);
		});
		log.info("Registered command handler: {}",COMMAND_REFRESH);

		server.onCommand(COMMAND_DISCONNECT, (params) -> {
			return disconnect(params);
		});
		log.info("Registered command handler: {}",COMMAND_DISCONNECT);
		
		server.onCommand(COMMAND_GET, (params) -> {
			return handleLiveProcessRequest(params);
		});
		log.info("Registered command handler: {}",COMMAND_GET);
		
		server.onCommand(COMMAND_GET_METRICS, (params) -> {
			return handleLiveMetricsProcessRequest(params);
		});
		log.info("Registered command handler: {}",COMMAND_GET_METRICS);
		
		server.onCommand(COMMAND_GET_REFRESH_METRICS, (params) -> {
			return refreshMetrics(params);
		});
		log.info("Registered command handler: {}",COMMAND_GET_METRICS);

		server.onCommand(COMMAND_GET_LOGGERS, (params) -> {
			return getLoggers(params);
		});
		log.info("Registered command handler: {}",COMMAND_GET_LOGGERS);

		server.onCommand(COMMAND_CONFIGURE_LOGLEVEL, (params) -> {
			return configureLogLevel(params);
		});
		log.info("Registered command handler: {}",COMMAND_CONFIGURE_LOGLEVEL);
		
		server.onCommand(COMMAND_LIST_CONNECTED, (params) -> {
			List<LiveProcessSummary> result = new ArrayList<>();
			for (SpringProcessConnector process : connectorService.getConnectedProcesses()) {
				String processKey = process.getProcessKey();
				SpringProcessLiveData liveData = connectorService.getLiveData(processKey);
				if (liveData!=null) {
					result.add(SpringProcessLiveDataProvider.createProcessSummary(processKey, liveData));
				}
			}
			return CompletableFuture.completedFuture(result);
		});
	}

	private CompletableFuture<?> connect(ExecuteCommandParams params) {
		String processKey = getProcessKey(params);
		if (processKey != null) {
			
			// try local processes
			if (localProcessConnector.isAvailable()) {
				
				// Try cached processes.
				SpringProcessDescriptor[] processes = localProcessConnector
						.getProcesses(false, SpringProcessStatus.REGULAR, SpringProcessStatus.AUTO_CONNECT);
				for (SpringProcessDescriptor process : processes) {
					if (process.getProcessKey().equals(processKey)) {
						return localProcessConnector.connectProcess(process);
					}
				}
				
				processes = localProcessConnector
						.getProcesses(true, SpringProcessStatus.REGULAR, SpringProcessStatus.AUTO_CONNECT);
				for (SpringProcessDescriptor process : processes) {
					if (process.getProcessKey().equals(processKey)) {
						return localProcessConnector.connectProcess(process);
					}
				}
				
			}
			
			// try remote processes
			for (SpringProcessConnectorRemote remoteProcessConnector : remoteProcessConnectors) {
				RemoteBootAppData[] remoteProcesses = remoteProcessConnector.getProcesses();
				for (RemoteBootAppData remoteProcess : remoteProcesses) {
					String key = SpringProcessConnectorRemote.getProcessKey(remoteProcess);
					if (processKey.equals(key)) {
						return remoteProcessConnector.connectProcess(remoteProcess);
					}
				}
			}
		}

		return CompletableFuture.completedFuture(null);
	}
	
	private CompletableFuture<?> refresh(ExecuteCommandParams params) {
	    SpringProcessParams springProcessParams = new SpringProcessParams();
        springProcessParams.setProcessKey(getProcessKey(params));    
        springProcessParams.setEndpoint(getArgumentByKey(params, "endpoint"));
		if (springProcessParams.getProcessKey() != null) {
			return connectorService.refreshProcess(springProcessParams);
		}

		return CompletableFuture.completedFuture(null);
	}
	
	private CompletableFuture<?> refreshMetrics(ExecuteCommandParams params) {
	    SpringProcessParams springProcessParams = new SpringProcessParams();
	    springProcessParams.setProcessKey(getProcessKey(params));    
	    springProcessParams.setEndpoint(getArgumentByKey(params, "endpoint"));
	    springProcessParams.setMetricName(getArgumentByKey(params, "metricName"));
	    springProcessParams.setTags(getArgumentByKey(params, "tags"));  // Convert tags to a map
		if (springProcessParams.getProcessKey() != null) {
			return connectorService.refreshProcess(springProcessParams);
		}

		return CompletableFuture.completedFuture(null);
	}

	private CompletableFuture<Object> disconnect(ExecuteCommandParams params) {
		String processKey = getProcessKey(params);
		if (processKey != null) {
			connectorService.disconnectProcess(processKey);
		}

		return CompletableFuture.completedFuture(null);
	}

	private CompletableFuture<Object> getProcessCommands() {
		List<LiveProcessCommand> result = new ArrayList<>();
		
		Set<String> alreadyConnected = new HashSet<>();

		// already connected processes
		SpringProcessConnector[] connectedProcesses = connectorService.getConnectedProcesses();
		for (SpringProcessConnector process : connectedProcesses) {
			String processKey = process.getProcessKey();
			String label = createLabel(process.getProcessId(), process.getProcessName());
			result.add(new LiveProcessCommand(COMMAND_REFRESH, processKey, label, process.getProjectName(), process.getProcessId()));
			result.add(new LiveProcessCommand(COMMAND_DISCONNECT, processKey, label, process.getProjectName(), process.getProcessId()));
			alreadyConnected.add(processKey);
		}

		
		// collect available remote process. Some of them might be local processes, make a note of these too
		List<LiveProcessCommand> remoteProcessCommands = new ArrayList<>();
		Map<String, LiveProcessCommand> localProcessCommands = new HashMap<>();
		for (SpringProcessConnectorRemote remoteProcessConnector : remoteProcessConnectors) {
			RemoteBootAppData[] remoteProcesses = remoteProcessConnector.getProcesses();
			for (RemoteBootAppData remoteProcess : remoteProcesses) {
				String processKey = SpringProcessConnectorRemote.getProcessKey(remoteProcess);
				boolean isLocal = remoteProcess.getProcessID() != null && ("localhost".equals(remoteProcess.getHost()) || "127.0.0.1".equals(remoteProcess.getHost()));
				if (alreadyConnected.contains(processKey)) {
					alreadyConnected.add(SpringProcessConnectorService.getProcessKey(remoteProcess.getProcessID(), remoteProcess.getProcessName()));
				} else {
					String label = createLabel(remoteProcess.getProcessID(), SpringProcessConnectorRemote.getProcessName(remoteProcess));
					LiveProcessCommand command = new LiveProcessCommand(COMMAND_CONNECT, processKey, label, remoteProcess.getProjectName(), remoteProcess.getProcessID());
					if (isLocal) {
						// Local add these later while checking for local boot processes
						localProcessCommands.put(remoteProcess.getProcessID(), command);
					} else {
						// Keep these to be added at the end
						remoteProcessCommands.add(command);
					}
				}
			}
		}
		
		// other available local processes
		if (localProcessConnector.isAvailable()) {
			SpringProcessDescriptor[] localProcesses = localProcessConnector.getProcesses(true, SpringProcessStatus.REGULAR, SpringProcessStatus.AUTO_CONNECT);
			for (SpringProcessDescriptor localProcess : localProcesses) {
				String processKey = localProcess.getProcessKey();
				if (!alreadyConnected.contains(processKey)) {
					LiveProcessCommand command = localProcessCommands.remove(localProcess.getProcessID());
					if (command == null) {
						String label = createLabel(localProcess.getProcessID(), localProcess.getProcessName());
						command = new LiveProcessCommand(COMMAND_CONNECT, processKey, label, localProcess.getProjectName(), null);
					}
					result.add(command);
				}
			}
		}
		
		// other available remote processes
		for (LiveProcessCommand command : localProcessCommands.values()) {
			result.add(command);
		}
		result.addAll(remoteProcessCommands);
		
		log.debug("getProcessCommands => {}", result);
		return CompletableFuture.completedFuture((Object[]) result.toArray(new Object[result.size()]));
	}

	private String createLabel(String processId, String processName) {
		if (processId != null && processId.length() < 10) {
			return processName +" (pid: "+ processId + ")";
		} else {
			//long processid is just too much clutter, so don't show that.
			return processName;
		}
	}

	private String getProcessKey(ExecuteCommandParams params) {
		return getArgumentByKey(params, "processKey");
	}
	
	private String getArgumentByKey(ExecuteCommandParams params, String name) {
		List<Object> arguments = params.getArguments();
		for (Object arg : arguments) {
			if (arg instanceof Map<?, ?>) {
				Object value = ((Map<?, ?>) arg).get(name);
				if (value != null) {
					return value.toString();
				}
			}
			else if (arg instanceof JsonObject) {
				JsonElement element = ((JsonObject) arg).get(name);
				if (element != null && element instanceof JsonObject) {
					return element.toString();
				} else if ( element != null) {
				    return element.getAsString();
				}
			}
		}
		
		return null;
	}
	
	private CompletableFuture<Object> handleLiveProcessRequest(ExecuteCommandParams params) {
		String processKey = getProcessKey(params);
		String endpoint = getArgumentByKey(params, "endpoint");
		if (processKey != null) {
			SpringProcessLiveData data = connectorService.getLiveData(processKey);
			if (data != null) {
				switch(endpoint) {
				case "properties": {
					return CompletableFuture.completedFuture(data.getLiveProperties());
				}
				case "beans": {
					String beanName = getArgumentByKey(params, "beanName");
					if (beanName != null) {
						return CompletableFuture.completedFuture(data.getBeans().getBeansOfName(beanName));
					}
					String dependingOn = getArgumentByKey(params, "dependingOn");
					if (dependingOn != null) {
						return CompletableFuture.completedFuture(data.getBeans().getBeansDependingOn(dependingOn));	
					}
					return CompletableFuture.completedFuture(data.getBeans().getBeanNames());
					
				}
				case "mappings": {
					return CompletableFuture.completedFuture(data.getRequestMappings());
				}
				case "contextPath": {
					return CompletableFuture.completedFuture(data.getContextPath());
				}
				case "port": {
					return CompletableFuture.completedFuture(data.getPort());
				}
				case "profiles": {
					return CompletableFuture.completedFuture(data.getActiveProfiles());
				}
				default: {}
				}
			} else {
				return CompletableFuture.failedFuture(new IllegalStateException("Live Data is not yet available!"));
			}
		}
		
		return CompletableFuture.failedFuture(new IllegalStateException("Live process key is missing from the request parameters!"));
	}
	
	private CompletableFuture<Object> handleLiveMetricsProcessRequest(ExecuteCommandParams params) {
		String processKey = getProcessKey(params);
		String metricName = getArgumentByKey(params, "metricName");
		if (processKey != null) {
			switch(metricName) {
				case SpringProcessConnectorService.GC_PAUSES: {
					SpringProcessGcPausesMetricsLiveData data = connectorService.getGcPausesMetricsLiveData(processKey);
					return data != null ? CompletableFuture.completedFuture(data.getGcPausesMetrics()) : CompletableFuture.failedFuture(new IllegalStateException("Live Metrics Data is not yet available!"));
				}
				case SpringProcessConnectorService.HEAP_MEMORY: {
					SpringProcessMemoryMetricsLiveData data = connectorService.getMemoryMetricsLiveData(processKey);
					return data != null ? CompletableFuture.completedFuture(data.getHeapMemoryMetrics()): CompletableFuture.failedFuture(new IllegalStateException("Live Metrics Data is not yet available!"));
				}
				case SpringProcessConnectorService.NON_HEAP_MEMORY: {
                    SpringProcessMemoryMetricsLiveData data = connectorService.getMemoryMetricsLiveData(processKey);
                    return data != null ? CompletableFuture.completedFuture(data.getNonHeapMemoryMetrics()) : CompletableFuture.failedFuture(new IllegalStateException("Live Metrics Data is not yet available!"));
                }
				default: {}
			}
		}
		
		return CompletableFuture.completedFuture(null);
	}


	private CompletableFuture<Object> getLoggers(ExecuteCommandParams params) {
		SpringProcessLoggersData loggersData = null;
		SpringProcessParams springProcessParams = new SpringProcessParams();
	    springProcessParams.setProcessKey(getProcessKey(params));    
	    springProcessParams.setEndpoint(getArgumentByKey(params, "endpoint"));
		if (springProcessParams.getProcessKey() != null) {
			loggersData = connectorService.getLoggers(springProcessParams);
			return CompletableFuture.completedFuture(loggersData);
		}

		return CompletableFuture.completedFuture(loggersData);
	}
	
	private CompletableFuture<Object> configureLogLevel(ExecuteCommandParams params) {
		Map<String, String> args = new HashMap<>();
	    args.put("packageName", getArgumentByKey(params, "packageName"));
	    args.put("configuredLevel", getArgumentByKey(params, "configuredLevel"));
	    args.put("effectiveLevel", getArgumentByKey(params, "effectiveLevel"));
	    SpringProcessParams springProcessParams = new SpringProcessParams();
	    springProcessParams.setProcessKey(getProcessKey(params));   
		springProcessParams.setArgs(args);
	    
		if (springProcessParams.getProcessKey() != null) {
			connectorService.configureLogLevel(springProcessParams);
		}

		return CompletableFuture.completedFuture(null);
	}

}
