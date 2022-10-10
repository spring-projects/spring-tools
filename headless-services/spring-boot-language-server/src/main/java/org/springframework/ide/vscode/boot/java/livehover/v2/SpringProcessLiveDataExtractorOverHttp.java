/*******************************************************************************
 * Copyright (c) 2022 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.livehover.v2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SpringProcessLiveDataExtractorOverHttp {
	
	private static final Logger log = LoggerFactory.getLogger(SpringProcessLiveDataExtractorOverJMX.class);

	// NOTE: Gson-based serialisation replaces the old Jackson ObjectMapper. Not sure if this makes a difference in the long run, but to retain the same output that Jackson Object Mapper
	// was generating during serialisation, some configuration in Gson is required, as the default behaviour of Gson is different than Object Mapper.
	// Namely: Object Mapper does not escape Html, whereas Gson does by default (for example
	// '=' in Gson appears as '\u003d')
	private final Gson gson = new GsonBuilder()
							.disableHtmlEscaping()
							.create();
	
	/**
	 * @param processType distinguish different types of processes (i.e. local vs remote)
	 * @param processID if null, will be determined searching existing mbeans for that information (for remote processes via platform beans runtime name)
	 * @param processName if null, will be determined searching existing mbeans for that information (for remote processes infering the java command from the system properties)
	 * @param urlScheme should always be != null
	 * @param host should always be != null
	 * @param contextPath if null, will be determined searching existing mbeans for that information (for local processes)
	 * @param port if null, will be determined searching existing mbeans for that information (for local processes)
	 * @param currentData currently stored live data
	 */
	public SpringProcessLiveData retrieveLiveData(ProcessType processType, ActuatorConnection connection, String processID, String processName,
			String urlScheme, String host, String contextPath, String port, SpringProcessLiveData currentData) {
		
		try {
			
			String environment = connection.getEnvironment();
			String[] activeProfiles = getActiveProfiles(environment);

			LiveProperties properties = getProperties(environment);
			
			if (processID == null) {
				processID = connection.getProcessID();
			}
			
			if (processName == null) {
				Properties systemProperties = connection.getSystemProperties();
				if (systemProperties != null) {
					String javaCommand = getJavaCommand(systemProperties);
					processName = getProcessName(javaCommand);
				}
			}
			
			LiveConditional[] conditionals = getConditionals(connection, processID, processName);
			LiveRequestMapping[] requestMappings = getRequestMappings(connection);
			LiveBeansModel beans = getBeans(connection);
			LiveMetricsModel metrics = getMetrics(connection);
			StartupMetricsModel startup = getStartupMetrics(connection, currentData == null ? null : currentData.getStartupMetrics());
//			LiveMemoryMetricsModel[] memoryMetrics = getMemoryMetrics(connection);
//			LiveMemoryMetricsModel gcPausesMetrics = getGcPausesMetrics(connection);
			
			if (contextPath == null) {
				contextPath = getContextPath(environment);
			}
			
//			if (port == null) {
//				port = getPort(connection, environment);
//			}
			
			return new SpringProcessLiveData(
					processType,
					processName,
					processID,
					contextPath,
					urlScheme,
					port,
					host,
					beans,
					activeProfiles,
					requestMappings,
					conditionals,
					properties,
					metrics,
					startup
					);
//					memoryMetrics,
//					gcPausesMetrics);
		}
		catch (Exception e) {
			log.error("error reading live data from: " + processID + " - " + processName, e);
		}
		
		return null;
	}
	
	private LiveMetricsModel getMetrics(ActuatorConnection connection) {
		
		return new LiveMetricsModel() {
			
			@Override
			public RequestMappingMetrics getRequestMappingMetrics(String[] paths, String[] requestMethods) {
				try {
					if (paths.length == 0) {
						return null;
					}
					Map<String, String> tags = new HashMap<>();
					tags.put("uri", String.join(",", paths));
					if (requestMethods.length > 0) {
						tags.put("method", String.join(",", requestMethods));
					}
					
					String metricsData = connection.getMetrics("http.server.requests", tags);

					return RequestMappingMetrics.parse(metricsData);
				} catch (IOException e) {
					// ignore
				} catch (Exception e) {
					log.error("", e);
				}
				return null;
			}

		};
	}
	
	private StartupMetricsModel getStartupMetrics(ActuatorConnection connection, StartupMetricsModel currentStartup) {
		if (currentStartup != null) {
			return currentStartup;
		}
		try {
			Map<?,?> r = connection.getStartup();
			if (r != null) {
				return StartupMetricsModel.parse(r);
			}
		} catch (IOException e) {
			// ignore
		} catch (Exception e) {
			log.error("", e);
		}
		return null;
	}

	public String getProcessName(String command) throws Exception {
		if (command != null) {
			int space = command.indexOf(' ');
			if (space >= 0) {
				command = command.substring(0, space);
			}
			command = command.trim();
			if (!"".equals(command)) {
				return command;
			}
		}
		return "Unknown";
	}
	
	public String getJavaCommand(Properties systemProperties) {
		return (String) systemProperties.get("sun.java.command");
	}

	public LiveBeansModel getBeans(ActuatorConnection connection) {
		try {
			String json = connection.getBeans();

			if (json instanceof String) {
				return LiveBeansModel.parse((String) json);
			} else {
				return LiveBeansModel.parse(gson.toJson(json));
			}
		} catch (IOException e) {
			// ignore
		} catch (Exception e) {
			log.error("Error parsing beans", e);
		}
		return LiveBeansModel.builder().build();
	}

	public LiveRequestMapping[] getRequestMappings(ActuatorConnection connection) throws Exception {
		try {
			String mappings = connection.getRequestMappings();
			return parseRequestMappingsJson(mappings, "2.x");
		} catch (IOException e) {
			//ignore.. app stopped
		}
		return null;
	}

	private LiveRequestMapping[] parseRequestMappingsJson(String json, String bootVersion) {
		JSONObject obj = new JSONObject(json);
		if (bootVersion.equals("2.x")) {
			return LiveRequestMappingBoot2xParser.parse(obj);
		} else { //1.x
			List<LiveRequestMapping> result = new ArrayList<>();
			Iterator<String> keys = obj.keys();
			while (keys.hasNext()) {
				String rawKey = keys.next();
				JSONObject value = obj.getJSONObject(rawKey);
				result.add(new LiveRequestMappingBoot1xRequestMapping(rawKey, value));
			}
			return (LiveRequestMapping[]) result.toArray(new LiveRequestMapping[result.size()]);
		}
	}

	public LiveConditional[] getConditionals(ActuatorConnection connection, String processId, String processName) {
		try {
			String report = connection.getConditionalsReport();
			return LiveConditionalParser.parse(report, processId, processName);
		} catch (IOException e) {
			//ignore. Happens a lot when apps are stopped while we try to talk to them.
		}
		return null;
	}

	public String[] getActiveProfiles(String environment) {
		try {
			if (environment != null) {
				JSONObject env = new JSONObject(environment);

				Object _profiles = env.opt("activeProfiles"); //Boot 2.0
				if (_profiles == null) {
					_profiles = env.opt("profiles"); //Boot 1.5
				}

				if (_profiles instanceof JSONArray) {
					JSONArray profiles = (JSONArray) _profiles;
					ImmutableList.Builder<String> list = ImmutableList.builder();
					for (Object object : profiles) {
						if (object instanceof String) {
							list.add((String) object);
						}
					}
					return list.build().toArray(new String[0]);
				}
			}
		} catch (Exception e) {
			log.error("error resolving profiles from env", e);
		}
		return null;
	}
	
	public LiveProperties getProperties(String environment) throws Exception {

		try {
			if (environment != null) {
				return LivePropertiesJsonParser.parseProperties(environment);
			}
		} catch (Exception e) {
			log.error("error resolving live properties from environment endpoint", e);
		}
		return null;
	}



	public String getContextPath(String environment) throws Exception {
		return environment != null ? LiveContextPathUtil.getContextPath("2.x", environment) : null;
	}

}
