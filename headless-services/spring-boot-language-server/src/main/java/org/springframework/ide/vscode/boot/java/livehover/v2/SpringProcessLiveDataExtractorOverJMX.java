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

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.util.StringUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * @author Martin Lippert
 */
public class SpringProcessLiveDataExtractorOverJMX {
	
	private static final Logger log = LoggerFactory.getLogger(SpringProcessLiveDataExtractorOverJMX.class);

	// NOTE: Gson-based serialisation replaces the old Jackson ObjectMapper. Not sure if this makes a difference in the long run, but to retain the same output that Jackson Object Mapper
	// was generating during serialisation, some configuration in Gson is required, as the default behaviour of Gson is different than Object Mapper.
	// Namely: Object Mapper does not escape Html, whereas Gson does by default (for example
	// '=' in Gson appears as '\u003d')
	private final Gson gson = new GsonBuilder()
							.disableHtmlEscaping()
							.create();
	
	/**
	 * @param processType 
	 * @param processID if null, will be determined searching existing mbeans for that information (for remote processes via platform beans runtime name)
	 * @param processName if null, will be determined searching existing mbeans for that information (for remote processes infering the java command from the system properties)
	 * @param urlScheme should always be != null
	 * @param host should always be != null
	 * @param contextPath if null, will be determined searching existing mbeans for that information (for local processes)
	 * @param port if null, will be determined searching existing mbeans for that information (for local processes)
	 * @param currentData currently stored live data
	 */
	public SpringProcessLiveData retrieveLiveData(ProcessType processType, JMXConnector jmxConnector, String processID, String processName,
			String urlScheme, String host, String contextPath, String port, SpringProcessLiveData currentData) {
		
		try {
			MBeanServerConnection connection = jmxConnector.getMBeanServerConnection();
			
			String domain = getDomainForActuator(connection);

			String environment = getEnvironment(connection, domain);
			String[] activeProfiles = getActiveProfiles(connection, environment);

			LiveProperties properties = getProperties(connection, environment);
			
			if (processID == null) {
				processID = getProcessID(connection);
			}
			
			if (processName == null) {
				Properties systemProperties = getSystemProperties(connection);
				if (systemProperties != null) {
					String javaCommand = getJavaCommand(systemProperties);
					processName = getProcessName(javaCommand);
				}
			}
			
			LiveConditional[] conditionals = getConditionals(connection, domain, processID, processName);
			LiveRequestMapping[] requestMappings = getRequestMappings(connection, domain);
			LiveBeansModel beans = getBeans(connection, domain);
			LiveMetricsModel metrics = getMetrics(connection, domain);
			StartupMetricsModel startup = getStartupMetrics(connection, domain, currentData == null ? null : currentData.getStartupMetrics());
			
			if (contextPath == null) {
				contextPath = getContextPath(connection, domain, environment);
			}
			
			if (port == null) {
				port = getPort(connection, environment);
			}
			
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
		}
		catch (Exception e) {
			log.error("error reading live data from: " + processID + " - " + processName, e);
		}
		
		return null;
	}
	
	/**
	 * @param processType 
	 * @param processID if null, will be determined searching existing mbeans for that information (for remote processes via platform beans runtime name)
	 * @param processName if null, will be determined searching existing mbeans for that information (for remote processes inferring the java command from the system properties)
	 * @param currentData currently stored live data
	 * @param metricName
	 * @param tags 
	 */
	public SpringProcessMemoryMetricsLiveData retrieveLiveMemoryMetricsData(ProcessType processType, JMXConnector jmxConnector, String processID, String processName,
			 SpringProcessLiveData currentData, String metricName, String tags) {
		
		try {
			MBeanServerConnection connection = jmxConnector.getMBeanServerConnection();
			List<LiveMemoryMetricsModel> heapMemoryMetricsList = new ArrayList<>();
			List<LiveMemoryMetricsModel> nonHeapMemoryMetricsList = new ArrayList<>();
			String domain = getDomainForActuator(connection);
			
			if (processID == null) {
				processID = getProcessID(connection);
			}
			
			if (processName == null) {
				Properties systemProperties = getSystemProperties(connection);
				if (systemProperties != null) {
					String javaCommand = getJavaCommand(systemProperties);
					processName = getProcessName(javaCommand);
				}
			}
			
			LiveMemoryMetricsModel[] heapMemResults = getMemoryMetrics(connection, heapMemoryMetricsList,
                    domain, "area:heap");

			LiveMemoryMetricsModel[] nonHeapMemResults = getMemoryMetrics(connection, nonHeapMemoryMetricsList,
                    domain, "area:nonheap");
			
			return new SpringProcessMemoryMetricsLiveData(
					processType,
					processName,
					processID,
					heapMemResults,
					nonHeapMemResults
			);
		}
		catch (Exception e) {
			log.error("error reading live metrics data from: " + processID + " - " + processName, e);
		}
		
		return null;
	}
	

    private LiveMemoryMetricsModel[] getMemoryMetrics(MBeanServerConnection connection, 
            List<LiveMemoryMetricsModel> memoryMetricsList, String domain, String tags) {

        List<String> memoryMetrics = Arrays.asList("jvm.memory.committed", "jvm.memory.max");

        LiveMemoryMetricsModel jvmMemUsedMetrics = getLiveMetrics(connection, domain, "jvm.memory.used", tags);
        if (jvmMemUsedMetrics != null ) {
            memoryMetricsList.add(jvmMemUsedMetrics);
            Arrays.sort(jvmMemUsedMetrics.getAvailableTags()[0].getValues());
            String[] memoryZones =  jvmMemUsedMetrics.getAvailableTags()[0].getValues();
            for(String zone : memoryZones) {
                String tag = tags+",id:"+zone;
                LiveMemoryMetricsModel metrics = getLiveMetrics(connection, domain, "jvm.memory.used", tag );
                if (metrics != null) {
                    memoryMetricsList.add(metrics);
                }
            }

            for(String metric : memoryMetrics) {
                LiveMemoryMetricsModel metrics = getLiveMetrics(connection, domain, metric, tags );
                if (metrics != null) {
                    memoryMetricsList.add(metrics);
                }
            }	    
        }

        LiveMemoryMetricsModel[] res = (LiveMemoryMetricsModel[]) memoryMetricsList.toArray(new LiveMemoryMetricsModel[memoryMetricsList.size()]);
        return res;
    }
	
	/**
	 * @param processType 
	 * @param processID if null, will be determined searching existing mbeans for that information (for remote processes via platform beans runtime name)
	 * @param processName if null, will be determined searching existing mbeans for that information (for remote processes inferring the java command from the system properties)
	 * @param currentData currently stored live data
	 * @param metricName 
	 * @param tags 
	 */
	public SpringProcessGcPausesMetricsLiveData retrieveLiveGcPausesMetricsData(ProcessType processType, JMXConnector jmxConnector, String processID, String processName,
			 SpringProcessLiveData currentData, String metricName, String tags) {
				
		try {
			MBeanServerConnection connection = jmxConnector.getMBeanServerConnection();
			List<LiveMemoryMetricsModel> memoryMetricsList = new ArrayList<>();
			String domain = getDomainForActuator(connection);
			
			if (processID == null) {
				processID = getProcessID(connection);
			}
			
			if (processName == null) {
				Properties systemProperties = getSystemProperties(connection);
				if (systemProperties != null) {
					String javaCommand = getJavaCommand(systemProperties);
					processName = getProcessName(javaCommand);
				}
			}
			
			LiveMemoryMetricsModel metrics = getLiveMetrics(connection, domain, "jvm.gc.pause", tags);
			if (metrics != null) {
				memoryMetricsList.add(metrics);
			}
			

			LiveMemoryMetricsModel[] res = (LiveMemoryMetricsModel[]) memoryMetricsList.toArray(new LiveMemoryMetricsModel[memoryMetricsList.size()]);
			return new SpringProcessGcPausesMetricsLiveData(
					processType,
					processName,
					processID,
					res
					);
		}
		catch (Exception e) {
			log.error("error reading live metrics data from: " + processID + " - " + processName, e);
		}
		
		return null;
	}
	
	private LiveMetricsModel getMetrics(MBeanServerConnection connection, String domain) {
		
		return new LiveMetricsModel() {
			
			@Override
			public RequestMappingMetrics getRequestMappingMetrics(String[] paths, String[] requestMethods) {
				try {
					List<Object> tags = new ArrayList<>();
					if (paths.length == 0) {
						return null;
					}
					tags.add("uri:" + String.join(",", paths));
					if (requestMethods.length > 0) {
						tags.add("method:" + String.join(",", requestMethods));
					}
					
					Object[] params = new Object[] {"http.server.requests", tags};
					String[] signature =  new String[] {String.class.getName(), List.class.getName()};
					
					Object metricsData = getActuatorDataFromOperation(connection,
							getObjectName(domain, "type=Endpoint,name=Metrics"), 
							"metric", 
							params, 
							signature);

					if (metricsData instanceof String) {
						return RequestMappingMetrics.parse((String) metricsData);
					} else if (metricsData != null) {
						return RequestMappingMetrics.parse(gson.toJson(metricsData));
					}
				} catch (Exception e) {
					log.error("", e);
				}
				return null;
			}

		};
	}
	
	/**
	 * @param processType 
	 * @param processID if null, will be determined searching existing mbeans for that information (for remote processes via platform beans runtime name)
	 * @param processName if null, will be determined searching existing mbeans for that information (for remote processes inferring the java command from the system properties)
	 * @param currentData currently stored live data
	 */
	public SpringProcessLoggersData retrieveLoggersData(ProcessType processType, JMXConnector jmxConnector, String processID, String processName,
			 SpringProcessLiveData currentData) {

		try {
			MBeanServerConnection connection = jmxConnector.getMBeanServerConnection();
			String domain = getDomainForActuator(connection);

			if (processID == null) {
				processID = getProcessID(connection);
			}

			if (processName == null) {
				Properties systemProperties = getSystemProperties(connection);
				if (systemProperties != null) {
					String javaCommand = getJavaCommand(systemProperties);
					processName = getProcessName(javaCommand);
				}
			}

			Loggers loggers = getLoggers(connection, domain);

			return new SpringProcessLoggersData(
					processType,
					processName,
					processID,
					loggers
					);
		}
		catch (Exception e) {
			log.error("error reading live metrics data from: " + processID + " - " + processName, e);
		}

		return null;
	}
	
	public Loggers getLoggers(MBeanServerConnection connection, String domain) {

		Object[] params1 = new Object[] {};
		String[] signature =  new String[] {String.class.getName(), List.class.getName()};

		try {
			Object loggersData = getActuatorDataFromOperation(connection,
					getObjectName(domain, "type=Endpoint,name=Loggers"), 
					"loggers", 
					params1, 
					signature);
			if (loggersData instanceof String) {
				return gson.fromJson((String)loggersData, Loggers.class);
			} else if (loggersData != null){
				ObjectMapper objectMapper = new ObjectMapper();
				return objectMapper.convertValue(loggersData, Loggers.class);
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return null;	
	}
	
	public SpringProcessUpdatedLogLevelData configureLogLevel(ProcessType processType, JMXConnector jmxConnector,
			String processID, String processName, SpringProcessLiveData currentData, Map<String, String> args) {
		try {
			MBeanServerConnection connection = jmxConnector.getMBeanServerConnection();
			String domain = getDomainForActuator(connection);

			if (processID == null) {
				processID = getProcessID(connection);
			}

			if (processName == null) {
				Properties systemProperties = getSystemProperties(connection);
				if (systemProperties != null) {
					String javaCommand = getJavaCommand(systemProperties);
					processName = getProcessName(javaCommand);
				}
			}

			changeLogLevel(connection, domain, args);
			return new SpringProcessUpdatedLogLevelData(
					processType,
					processName,
					processID,
					args.get("packageName"),
					args.get("effectiveLevel"),
					args.get("configuredLevel")
					);

		}
		catch (Exception e) {
			log.error("error changing log level : " + processID + " - " + processName + " : "+args.get("packageName"), e);
		}

		return null;
	}
	
	public void changeLogLevel(MBeanServerConnection connection, String domain, Map<String, String> args) throws Exception {
		
		Object[] params = new Object[] {args.get("packageName"), args.get("configuredLevel")};
		String[] signature =  new String[] {String.class.getName(), List.class.getName()};

		try {
			getActuatorDataFromOperation(connection,
					getObjectName(domain, "type=Endpoint,name=Loggers"), 
					"configureLogLevel", 
					params, 
					signature);
		} catch (Exception e) {
			log.error("", e);
			throw e;
		}
		return;	
	}
	
	private StartupMetricsModel getStartupMetrics(MBeanServerConnection connection, String domain, StartupMetricsModel currentStartup) {
		if (currentStartup != null) {
			return currentStartup;
		}
		try {
			Map<?,?> result = (Map<?,?>) getActuatorDataFromOperation(connection, getObjectName(domain, "type=Endpoint,name=Startup"), "startupSnapshot");
			if (result != null) {
				return StartupMetricsModel.parse(result);
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return null;
	}

	public String getProcessID(MBeanServerConnection connection) {
		try {
			RuntimeMXBean runtime = ManagementFactory.getPlatformMXBean(connection, RuntimeMXBean.class);
			return runtime.getName();
		} catch (Exception e) {
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
	
	public Properties getSystemProperties(MBeanServerConnection connection) {
		try {
			RuntimeMXBean runtime = ManagementFactory.getPlatformMXBean(connection, RuntimeMXBean.class);
		
			Properties props = new Properties();
			for (Entry<String, String> e : runtime.getSystemProperties().entrySet()) {
				props.put(e.getKey(), e.getValue());
			}
			return props;
		}
		catch (Exception e) {
			log.error("error fetching system properties", e);
		}
		return null;
	}
	
	public String getJavaCommand(Properties systemProperties) {
		return (String) systemProperties.get("sun.java.command");
	}

	public LiveBeansModel getBeans(MBeanServerConnection connection, String domain) {
		try {
			Object json = null;

			try {
				json = getBeansFromActuator(connection, domain);

				if (json == null) {
					json = getBeansFromNonBootMBean(connection);
				}
			} catch (IOException e) {
				// PT 160096886 - Don't throw exception, as actuator info will not be available when app stopping, and
				// this is not an error condition. Return empty model instead.
			} catch (ExecutionException e) {
				if (!(e.getCause() instanceof IOException)) {
					throw e;
				}
			}

			if (json != null) {
				if (json instanceof String) {
					return LiveBeansModel.parse((String)json);
				}
				else {
					return LiveBeansModel.parse(gson.toJson(json));
				}
			}

		} catch (Exception e) {
			log.error("Error parsing beans", e);
		}
		return LiveBeansModel.builder().build();
	}
	
	
	public LiveMemoryMetricsModel getLiveMetrics(MBeanServerConnection connection, String domain, String metricName, String tags) {
		
		Object[] params1 = new Object[] {metricName, tags};
		String[] signature =  new String[] {String.class.getName(), List.class.getName()};
		
		try {
			Object metricsData = getActuatorDataFromOperation(connection,
					getObjectName(domain, "type=Endpoint,name=Metrics"), 
					"metric", 
					params1, 
					signature);
			if (metricsData instanceof String) {
				return gson.fromJson((String)metricsData, LiveMemoryMetricsModel.class);
			} else if (metricsData != null){
				ObjectMapper mapper = new ObjectMapper();
				return mapper.convertValue(metricsData, LiveMemoryMetricsModel.class);
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return null;	
	}

	protected Object getBeansFromNonBootMBean(MBeanServerConnection connection) throws Exception {
		Set<ObjectName> nonBootSpringLiveMBeans = getNonBootSpringLiveMBeans(connection);
		if (nonBootSpringLiveMBeans.size() > 0) {
			return getActuatorDataFromAttribute(connection, nonBootSpringLiveMBeans.iterator().next(), "SnapshotAsJson");
		}
		else {
			return null;
		}
	}

	private Object getBeansFromActuator(MBeanServerConnection connection, String domain) throws Exception {
		Object result = getActuatorDataFromOperation(connection, getObjectName(domain, "type=Endpoint,name=Beans"), "beans");
		if (result != null) return result;

		return getActuatorDataFromAttribute(connection, getObjectName(domain, "type=Endpoint,name=beansEndpoint"), "Data");
	}

	private Set<ObjectName> getNonBootSpringLiveMBeans(MBeanServerConnection connection) {
		try {
			QueryExp queryExp = Query.isInstanceOf(Query.value("org.springframework.context.support.LiveBeansView"));
			return connection.queryNames(null, queryExp);
		} catch (Exception e) {
			log.error("error searching for non-boot spring mbeans", e);
		}
		return Collections.emptySet();
	}

	public LiveRequestMapping[] getRequestMappings(MBeanServerConnection connection, String domain) throws Exception {
		try {
			//Boot 2.x
			Object result = getActuatorDataFromOperation(connection, getObjectName(domain, "type=Endpoint,name=Mappings"), "mappings");
			if (result != null) {
				String mappings = gson.toJson(result);
				return parseRequestMappingsJson(mappings, "2.x");
			}

			//Boot 1.x
			result = getActuatorDataFromAttribute(connection, getObjectName(domain, "type=Endpoint,name=requestMappingEndpoint"), "Data");
			if (result != null) {
				String mappings = gson.toJson(result);
				return parseRequestMappingsJson(mappings, "1.x");
			}

		} catch (IOException e) {
			//ignore.. app stopped
		} catch (ExecutionException e) {
			if (!(e.getCause() instanceof IOException)) {
				throw e;
			}
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

	public LiveConditional[] getConditionals(MBeanServerConnection connection, String domain, String processId, String processName) {
		try {
			//Boot 3.x
			Object result = getActuatorDataFromOperation(connection, getObjectName(domain, "type=Endpoint,name=Conditions"), "conditions");
			if (result != null) {
				String report = gson.toJson(result);
				return LiveConditionalParser.parse(report, processId, processName);
			}

			//Boot 2.x
			result = getActuatorDataFromOperation(connection, getObjectName(domain, "type=Endpoint,name=Conditions"), "applicationConditionEvaluation");
			if (result != null) {
				String report = gson.toJson(result);
				return LiveConditionalParser.parse(report, processId, processName);
			}

			//Boot 1.x
			result = getActuatorDataFromAttribute(connection, getObjectName(domain, "type=Endpoint,name=autoConfigurationReportEndpoint"), "Data");
			if (result != null) {
				String report = gson.toJson(result);
				return LiveConditionalParser.parse(report, processId, processName);
			}

		} catch (IOException e) {
			//ignore. Happens a lot when apps are stopped while we try to talk to them.
		} catch (Exception e) {
			// ignore, might happen when communication with the running app is difficult
		}

		return null;
	}

	public String[] getActiveProfiles(MBeanServerConnection connection, String environment) {
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
	
	public LiveProperties getProperties(MBeanServerConnection connection, String environment) throws Exception {
		try {
			if (environment != null) {
				return LivePropertiesJsonParser.parseProperties(environment);
			}
		} catch (Exception e) {
			log.error("error resolving live properties from environment endpoint", e);
		}
		return null;
	}

	public String getEnvironment(MBeanServerConnection connection, String domain) throws Exception {
		try {
			Object result = getActuatorDataFromOperation(connection, getObjectName(domain, "type=Endpoint,name=Env"), "environment");
			if (result != null) {
				String environment = gson.toJson(result);
				return environment;
			}

			result = getActuatorDataFromAttribute(connection, getObjectName(domain, "type=Endpoint,name=environmentEndpoint"), "Data");
			if (result != null) {
				String environment = gson.toJson(result);
				return environment;
			}

		} catch (IOException e) {
			//ignore... probably just because app is stopped
		} catch (ExecutionException e) {
			if (!(e.getCause() instanceof IOException)) {
				throw e;
			}
		}
		return null;
	}

	
	private Object getActuatorDataFromAttribute(MBeanServerConnection connection, ObjectName objectName, String attribute) throws Exception {
		if (objectName != null) {
			try {
				return connection.getAttribute(objectName, attribute);
			}
			catch (InstanceNotFoundException|IOException e) {
				return null;
			}
			catch (ReflectionException e) {
				return null;
			}
		}
		return null;
	}

	private Object getActuatorDataFromOperation(MBeanServerConnection connection, ObjectName objectName, String operation) throws Exception {
		if (objectName != null) {
			try {
				return connection.invoke(objectName, operation, null, null);
			}
			catch (InstanceNotFoundException|IOException e) {
				return null;
			}
			catch (ReflectionException e) {
				return null;
			}
		}
		return null;
	}
	
	private Object getActuatorDataFromOperation(MBeanServerConnection connection, ObjectName objectName, String operation, Object[] parameters,  String[] signature) throws Exception {
		if (objectName != null) {
			try {
				return connection.invoke(objectName, operation, parameters, signature);
			}
			catch (InstanceNotFoundException|IOException e) {
				return null;
			}
			catch (ReflectionException e) {
				return null;
			}
		}
		return null;
	}
	
	private ObjectName getObjectName(String domain, String keyProperties) throws Exception {
		if (StringUtil.hasText(domain)  && StringUtil.hasText(keyProperties)) {
			String fullName = domain + ":" + keyProperties;
			return ObjectName.getInstance(fullName);
		}
		return null;
	}

	/**
	 * PT 156072399: Actuator information can be defined using a different JMX MBean domain.
	 * By default, Spring Boot exposes management endpoints as JMX MBeans under the 'org.springframework.boot' domain.
	 * Users can however define another domain in the app's application.properties, for example using this property:
	 * management.endpoints.jmx.domain=com.example.myapp
	 * <b/>
	 * Therefore we need to support other domains than just: 'org.springframework.boot'
	 * @return JMX MBean domain containing actuator information, or null if not resolved.
	 * @throws Exception when resolving domain from JMX
	 */
	public String getDomainForActuator(MBeanServerConnection connection) throws Exception {
		QueryExp queryExp = Query.or(Query.or(Query.isInstanceOf(Query.value("org.springframework.boot.actuate.endpoint.jmx.EndpointMBean")),
				Query.isInstanceOf(Query.value("org.springframework.boot.actuate.endpoint.jmx.DataEndpointMBean"))),
				Query.isInstanceOf(Query.value("org.springframework.context.support.LiveBeansView")));

		Set<ObjectName> names = connection.queryNames(null, queryExp);
		if (names != null && names.size() > 0) {
			return names.iterator().next().getDomain();
		}
		else {
			return null;
		}		
	}
	
	public String getPort(MBeanServerConnection connection, String environment) throws Exception {
		String port = getPortViaAdmin(connection);
		if (port != null) {
			return port;
		}

		port = getPortViaActuator(connection, environment);
		if (port != null) {
			return port;
		}

		port = getPortViaTomcatBean(connection);
		return port;
	}

	public String getContextPath(MBeanServerConnection connection, String domain, String environment) throws Exception {
		try {
			String bootVersion = null;

			// Boot 1.x
			Object result = getActuatorDataFromAttribute(connection, getObjectName(domain, "type=Endpoint,name=requestMappingEndpoint"), "Data");
			if (result != null) {
				bootVersion = "1.x";
			}

			// Boot 2.x
			result = getActuatorDataFromOperation(connection, getObjectName(domain, "type=Endpoint,name=Mappings"), "mappings");
			if (result != null) {
				bootVersion = "2.x";
			}
			return bootVersion != null && environment != null ? LiveContextPathUtil.getContextPath(bootVersion, environment) : null;
		} catch (IOException e) {
			//Ignore... happens a low when app is stopped
		} catch (ExecutionException e) {
			if (!(e.getCause() instanceof IOException)) {
				throw e;
			}
		}
		return null;
	}

	private String getPortViaAdmin(MBeanServerConnection connection) throws Exception {
		try {
			String DEFAULT_OBJECT_NAME = "org.springframework.boot:type=Admin,name=SpringApplication";
			ObjectName objectName = new ObjectName(DEFAULT_OBJECT_NAME);

			Object o = connection.invoke(objectName,"getProperty", new String[] {"local.server.port"}, new String[] {String.class.getName()});
			return o == null ? null : o.toString();
		}
		catch (InstanceNotFoundException e) {
			return null;
		}
	}

	private String getPortViaActuator(MBeanServerConnection connection, String environment) throws Exception {
		if (environment != null) {
			JSONObject env = new JSONObject(environment);
			if (env != null) {
				JSONObject portsObject = env.optJSONObject("server.ports");
				if (portsObject != null) {
					String portValue = portsObject.optString("local.server.port");
					if (portValue != null) {
						return portValue;
					}
				}
				//Not found as direct property value... in Boot 2.0 we must look inside the 'propertySources'.
				//Similar... but structure is more complex.
				JSONArray propertySources = env.optJSONArray("propertySources");
				if (propertySources != null) {
					for (Object _source : propertySources) {
						if (_source instanceof JSONObject) {
							JSONObject source = (JSONObject) _source;
							String sourceName = source.optString("name");
							if ("server.ports".equals(sourceName)) {
								JSONObject props = source.optJSONObject("properties");
								JSONObject valueObject = props.optJSONObject("local.server.port");
								if (valueObject != null) {
									String portValue = valueObject.optString("value");
									if (portValue != null) {
										return portValue;
									}
								}
							}
						}
					}
				}
			}
		}
		return null;
	}

	private String getPortViaTomcatBean(MBeanServerConnection connection) throws Exception {
		try {
			Set<ObjectName> queryNames = connection.queryNames(null, null);

			for (ObjectName objectName : queryNames) {
				if (objectName.toString().startsWith("Tomcat") && objectName.toString().contains("type=Connector")) {
					Object result = connection.getAttribute(objectName, "localPort");
					if (result != null) {
						return result.toString();
					}
				}
			}
		}
		catch (InstanceNotFoundException e) {
		}
		return null;
	}

}
