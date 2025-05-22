/*******************************************************************************
 * Copyright (c) 2017, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.internal.net.ProxyManager;
import org.eclipse.core.net.proxy.IProxyChangeListener;
import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.lsp4e.server.StreamConnectionProvider;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.messages.ResponseMessage;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.ui.PlatformUI;
import org.springframework.tooling.boot.ls.prefs.CategoryProblemsSeverityPrefsPage;
import org.springframework.tooling.boot.ls.prefs.FileListEditor;
import org.springframework.tooling.boot.ls.prefs.ProblemCategoryData;
import org.springframework.tooling.boot.ls.prefs.ProblemCategoryData.CategoryToggleData;
import org.springframework.tooling.boot.ls.prefs.StringListEditor;
import org.springsource.ide.eclipse.commons.boot.ls.remoteapps.RemoteBootAppsDataHolder;
import org.springsource.ide.eclipse.commons.boot.ls.remoteapps.RemoteBootAppsDataHolder.RemoteAppData;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.common.collect.ImmutableSet;

/**
 * if the system property "boot-java-ls-port" exists, delegate to the socket-based
 * stream connection provider, otherwise use the standard process-based stream
 * connection provider.
 * 
 * This allows you to run the language server in server mode (listens on a port for
 * a connection) and connect the IDE integration to that already running language
 * server instead of starting a new process for it.
 * 
 * @author Martin Lippert
 */
@SuppressWarnings("restriction")
public class DelegatingStreamConnectionProvider implements StreamConnectionProvider {
	
	private StreamConnectionProvider provider;
	private ResourceListener fResourceListener;
	private LanguageServer languageServer;
	
	private static final List<String> PREFS_EXCLUSIONS = List.of(Constants.PREF_START_LS_EARLY);
	private final IPropertyChangeListener configListener = (e) -> {
		if (!PREFS_EXCLUSIONS.contains(e.getProperty())) {
			sendConfiguration();
		}
	};
	
	private final ValueListener<ImmutableSet<RemoteAppData>> remoteAppsListener = (e, v) -> sendConfiguration();
	
	private final IProxyChangeListener proxySettingsListener = e -> sendConfiguration();
	
	public DelegatingStreamConnectionProvider() {
//		LanguageServerCommonsActivator.logInfo("Entering DelegatingStreamConnectionProvider()");
		String port = System.getProperty("boot-java-ls-port");
		if (port != null) {
			this.provider = new SpringBootLanguageServerViaSocket(Integer.parseInt(port));
		} else {
//			LanguageServerCommonsActivator.logInfo("DelegatingStreamConnectionProvider classloader = "+this.getClass().getClassLoader());
			Assert.isNotNull(SpringBootLanguageServer.class);
//			LanguageServerCommonsActivator.logInfo("SpringBootLanguageServer exists!");
			Assert.isLegal(SpringBootLanguageServer.class.getClassLoader().equals(DelegatingStreamConnectionProvider.class.getClassLoader()), "OSGI bug messing up our classloaders?");
			this.provider = new SpringBootLanguageServer();
		}
	}
		
	@Override
	public Object getInitializationOptions(URI rootUri) {
		return provider.getInitializationOptions(rootUri);
	}

	@Override
	public void start() throws IOException {
		this.provider.start();
		IProxyService proxyService = PlatformUI.getWorkbench().getService(IProxyService.class);
		if (proxyService != null) {
			proxyService.addProxyChangeListener(proxySettingsListener);
		}

	}

	@Override
	public InputStream getInputStream() {
		return this.provider.getInputStream();
	}

	@Override
	public OutputStream getOutputStream() {
		return this.provider.getOutputStream();
	}

	@Override
	public InputStream getErrorStream() {
		return provider.getErrorStream();
	}

	@Override
	public void stop() {
		IProxyService proxyService = PlatformUI.getWorkbench().getService(IProxyService.class);
		if (proxyService != null) {
			proxyService.removeProxyChangeListener(proxySettingsListener);
		}
		BootLanguageServerPlugin.getDefault().getLog().info("DelegatingStreamConnectionProvider - Stopping Boot LS");
		this.provider.stop();
		if (fResourceListener != null) {
			ResourcesPlugin.getWorkspace().removeResourceChangeListener(fResourceListener);
			fResourceListener = null;
		}
		BootLanguageServerPlugin.getDefault().getPreferenceStore().removePropertyChangeListener(configListener);
//		BootLanguageServerPlugin.getPreferences().removePreferenceChangeListener(prefsListener);
		RemoteBootAppsDataHolder.getDefault().getRemoteApps().removeListener(remoteAppsListener);
	}

	@Override
	public void handleMessage(Message message, LanguageServer languageServer, URI rootURI) {
		if (message instanceof ResponseMessage) {
			ResponseMessage responseMessage = (ResponseMessage)message;
			if (responseMessage.getResult() instanceof InitializeResult) {
				this.languageServer = languageServer;
				

				sendConfiguration();
				
				// Add config listener
				BootLanguageServerPlugin.getDefault().getPreferenceStore().addPropertyChangeListener(configListener);
//				BootLanguageServerPlugin.getPreferences().addPreferenceChangeListener(prefsListener);

				// Add resource listener
				ResourcesPlugin.getWorkspace().addResourceChangeListener(fResourceListener = new ResourceListener(languageServer, Arrays.asList(
						FileSystems.getDefault().getPathMatcher("glob:**/pom.xml"),
						FileSystems.getDefault().getPathMatcher("glob:**/*.xml"),
						FileSystems.getDefault().getPathMatcher("glob:**/*.gradle"),
						FileSystems.getDefault().getPathMatcher("glob:**/*.java"),
						FileSystems.getDefault().getPathMatcher("glob:**/*.json"),
						FileSystems.getDefault().getPathMatcher("glob:**/*.yml"),
						FileSystems.getDefault().getPathMatcher("glob:**/*.properties"),
						FileSystems.getDefault().getPathMatcher("glob:**/*.class"),
						FileSystems.getDefault().getPathMatcher("glob:**/META-INF/spring/*.factories")
				)));
				
				//Add remote boot apps listener
				RemoteBootAppsDataHolder.getDefault().getRemoteApps().addListener(remoteAppsListener);				
			}
		}
	}
	
	
	private void sendConfiguration() {
		Map<String, Object> settings = new HashMap<>();
		Map<String, Object> bootJavaObj = new HashMap<>();
		Map<String, Object> liveInformation = new HashMap<>();
		Map<String, Object> liveInformationFetchData = new HashMap<>();
		Map<String, Object> supportXML = new HashMap<>();
		Map<String, Object> scanTestJavaSources = new HashMap<>();
		Map<String, Object> javaSettings = new HashMap<>();
		Map<String, Object> javaCompletionSettings = new HashMap<>();

		IPreferenceStore preferenceStore = BootLanguageServerPlugin.getDefault().getPreferenceStore();

		liveInformationFetchData.put("max-retries", preferenceStore.getInt(Constants.PREF_LIVE_INFORMATION_FETCH_DATA_RETRY_MAX_NO));
		liveInformationFetchData.put("retry-delay-in-seconds", preferenceStore.getInt(Constants.PREF_LIVE_INFORMATION_FETCH_DATA_RETRY_DELAY_IN_SECONDS));
		
		liveInformation.put("fetch-data", liveInformationFetchData);
		liveInformation.put("all-local-java-processes", preferenceStore.getBoolean(Constants.PREF_LIVE_INFORMATION_ALL_JVM_PROCESSES));
		
		supportXML.put("on", preferenceStore.getBoolean(Constants.PREF_SUPPORT_SPRING_XML_CONFIGS));
		supportXML.put("scan-folders", preferenceStore.getString(Constants.PREF_XML_CONFIGS_SCAN_FOLDERS));
		supportXML.put("hyperlinks", preferenceStore.getString(Constants.PREF_XML_CONFIGS_HYPERLINKS));
		supportXML.put("content-assist", preferenceStore.getString(Constants.PREF_XML_CONFIGS_CONTENT_ASSIST));
		scanTestJavaSources.put("on", preferenceStore.getBoolean(Constants.PREF_SCAN_JAVA_TEST_SOURCES));

		javaCompletionSettings.put("inject-bean", preferenceStore.getBoolean(Constants.PREF_COMPLETION_JAVA_INJECT_BEAN));
		
		javaSettings.put("beans-structure-tree", preferenceStore.getBoolean(Constants.PREF_BEANS_STRUCTURE_TREE));
		javaSettings.put("symbols-from-new-index", preferenceStore.getBoolean(Constants.PREF_SYMBOLS_FROM_NEW_INDEX));
		javaSettings.put("completions", javaCompletionSettings);
		javaSettings.put("reconcilers", preferenceStore.getBoolean(Constants.PREF_JAVA_RECONCILE));
		javaSettings.put("codelens-over-query-methods", preferenceStore.getBoolean(Constants.PREF_CODELENS_QUERY_METHODS));

		bootJavaObj.put("jpql", preferenceStore.getBoolean(Constants.PREF_JPQL));
		bootJavaObj.put("live-information", liveInformation);
		bootJavaObj.put("support-spring-xml-config", supportXML);
		bootJavaObj.put("scan-java-test-sources", scanTestJavaSources);
		bootJavaObj.put("java", javaSettings);
		bootJavaObj.put("remote-apps", getAllRemoteApps());
		bootJavaObj.put("modulith-project-tracking", preferenceStore.getBoolean(Constants.PREF_MODULITH));
		
		bootJavaObj.put("rewrite", Map.of(
				"recipe-filters", StringListEditor.decode(preferenceStore.getString(Constants.PREF_REWRITE_RECIPE_FILTERS)),
				"scan-directories", FileListEditor.getValuesFromPreference(preferenceStore.getString(Constants.PREF_REWRITE_RECIPES_SCAN_DIRS)),
				"scan-files", FileListEditor.getValuesFromPreference(preferenceStore.getString(Constants.PREF_REWRITE_RECIPES_SCAN_FILES))
		));
		
		bootJavaObj.put("common", Map.of(
				"properties-metadata", preferenceStore.getString(Constants.PREF_COMMON_PROPS_METADATA)
		));
		
		bootJavaObj.put("properties", Map.of(
				"completions", Map.of(
						"elide-prefix", preferenceStore.getBoolean(Constants.PREF_PROPS_COMPLETIONS_ELIDE_PREFIX)
				)
		));
		
		bootJavaObj.put("cron", Map.of(
				"inlay-hints", preferenceStore.getBoolean(Constants.PREF_CRON_INLAY_HINTS)
			)
		);
		
		settings.put("boot-java", bootJavaObj);
		
		settings.put("http", createHttpProxySettings());
		
		putValidationPreferences(settings);
		putValidationCategoryToggles(settings);

		this.languageServer.getWorkspaceService().didChangeConfiguration(new DidChangeConfigurationParams(settings));
	}
	
	private Map<String, Object> createHttpProxySettings() {
		Map<String, Object> proxy = new HashMap<>();
		IProxyService proxyService = PlatformUI.getWorkbench().getService(IProxyService.class);
		if ((proxyService.isProxiesEnabled() || proxyService.isSystemProxiesEnabled()) &&  proxyService instanceof ProxyManager) {
			ProxyManager proxyManager = (ProxyManager) proxyService;
			if (proxyService.isSystemProxiesEnabled() && proxyManager.hasSystemProxies()) {
				for (IProxyData data : proxyManager.getNativeProxyData()) {
					if (data.getHost() != null && !data.getHost().isBlank()) {
						fillProxyData(proxy, data, proxyManager.getNativeNonProxiedHosts());
						break;
					}
				}
			} else if (proxyService.isProxiesEnabled()) {
				for (IProxyData data : proxyService.getProxyData()) {
					if (data.getHost() != null) {
						fillProxyData(proxy, data, proxyService.getNonProxiedHosts());
						break;
					}
				}
			}
		}
		return proxy;
	}
	
	private static void fillProxyData(Map<String, Object> proxy, IProxyData data, String[] exclusions) {
		proxy.put("proxy", data.getType().toLowerCase() + "://" + data.getHost() + (data.getPort() >= 0 ? ":" + data.getPort() : ""));
		if (data.isRequiresAuthentication()) {
			proxy.put("proxy-user", data.getUserId());
			proxy.put("proxy-password", data.getPassword());
		}
		proxy.put("noProxy", exclusions);
	}
	
	private void putValidationPreferences(Map<String, Object> settings) {
		try {
			IEclipsePreferences prefs = BootLanguageServerPlugin.getPreferences();
			for (String key : prefs.keys()) {
				if (key.startsWith("problem.")) {
					String val = prefs.get(key, null);
					if (val!=null) {
						dotPut(settings, "spring-boot.ls."+key, val);
					}
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
	}
	
	private void putValidationCategoryToggles(Map<String, Object> settings) {
		try {
			IEclipsePreferences prefs = BootLanguageServerPlugin.getPreferences();
			for (ProblemCategoryData category : CategoryProblemsSeverityPrefsPage.ALL_PROBLEM_CATEGORIES) {
				if (category.getToggle() != null) {
					CategoryToggleData toggle = category.getToggle();
					String val = prefs.get(toggle.getPreferenceKey(), null);
					if (val != null) {
						dotPut(settings, toggle.getPreferenceKey(), val);
					}
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
	}

	@SuppressWarnings("unchecked")
	private void dotPut(Object _settings, String dottedProperty, Object value) {
		if (_settings instanceof Map) {
			Map<String, Object> settings = (Map<String, Object>) _settings;
			int dot = dottedProperty.indexOf('.');
			if (dot>=0) {
				String first = dottedProperty.substring(0, dot);
				String rest = dottedProperty.substring(dot+1);
				Object nested = settings.getOrDefault(first, null);
				if (nested==null) {
					nested = new HashMap<>();
					settings.put(first, nested);
				}
				dotPut(nested, rest, value);
			} else {
				settings.put(dottedProperty, value);
			}
		}
	}

	/**
	 * Combines remote boot app data from all configuration sources.
	 */
	protected Collection<RemoteAppData> getAllRemoteApps() {
		return RemoteBootAppsDataHolder.getDefault().getRemoteApps().getValues();
	}
	
}
