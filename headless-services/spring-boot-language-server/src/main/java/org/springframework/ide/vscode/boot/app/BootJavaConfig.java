/*******************************************************************************
 * Copyright (c) 2017, 2022 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.ide.vscode.boot.common.SpringProblemCategories;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemCategory.Toggle;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.util.ListenerList;
import org.springframework.ide.vscode.commons.languageserver.util.Settings;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleWorkspaceService;
import org.springframework.stereotype.Component;

/**
 * Boot-Java LS settings
 *
 * @author Alex Boyko
 */
@Component
public class BootJavaConfig implements InitializingBean {
	
	private static final Logger log = LoggerFactory.getLogger(BootJavaConfig.class);
	
	public static final boolean LIVE_INFORMATION_AUTOMATIC_TRACKING_ENABLED_DEFAULT = false;
	public static final int LIVE_INFORMATION_AUTOMATIC_TRACKING_DELAY_DEFAULT = 5000;
	
	public static final int LIVE_INFORMATION_FETCH_DATA_RETRY_MAX_NO_DEFAULT = 10;
	public static final int LIVE_INFORMATION_FETCH_DATA_RETRY_DELAY_IN_SECONDS_DEFAULT = 3;
	
	public static final boolean VALIDAITON_SPEL_EXPRESSIONS_ENABLED_DEFAULT = true;


	private final SimpleWorkspaceService workspace;
	private Settings settings = new Settings(null);
	private ListenerList<Void> listeners = new ListenerList<Void>();

	BootJavaConfig(SimpleLanguageServer server) {
		this.workspace = server.getWorkspaceService();
	}

	public boolean isLiveInformationAutomaticTrackingEnabled() {
		Boolean enabled = settings.getBoolean("boot-java", "live-information", "automatic-tracking", "on");
		return enabled != null ? enabled.booleanValue() : LIVE_INFORMATION_AUTOMATIC_TRACKING_ENABLED_DEFAULT;
	}

	public int getLiveInformationAutomaticTrackingDelay() {
		Integer delay = settings.getInt("boot-java", "live-information", "automatic-tracking", "delay");
		return delay != null ? delay.intValue() : LIVE_INFORMATION_AUTOMATIC_TRACKING_DELAY_DEFAULT;
	}

	public int getLiveInformationFetchDataMaxRetryCount() {
		Integer delay = settings.getInt("boot-java", "live-information", "fetch-data", "max-retries");
		return delay != null ? delay.intValue() : LIVE_INFORMATION_FETCH_DATA_RETRY_MAX_NO_DEFAULT;
	}

	public int getLiveInformationFetchDataRetryDelayInSeconds() {
		Integer delay = settings.getInt("boot-java", "live-information", "fetch-data", "retry-delay-in-seconds");
		return delay != null ? delay.intValue() : LIVE_INFORMATION_FETCH_DATA_RETRY_DELAY_IN_SECONDS_DEFAULT;
	}

	public boolean isSpringXMLSupportEnabled() {
		Boolean enabled = settings.getBoolean("boot-java", "support-spring-xml-config", "on");
		return enabled != null && enabled.booleanValue();
	}
	
	public boolean isScanJavaTestSourcesEnabled() {
		Boolean enabled = settings.getBoolean("boot-java", "scan-java-test-sources", "on");
		return enabled != null && enabled.booleanValue();
	}
	
	public String[] xmlBeansFoldersToScan() {
		String foldersStr = settings.getString("boot-java", "support-spring-xml-config", "scan-folders");
		if (foldersStr != null) {
			foldersStr = foldersStr.trim();
		}
		String[] folders = foldersStr == null || foldersStr.isEmpty()? new String[0] : foldersStr.split("\\s*,\\s*");
		List<String> cleanedFolders = new ArrayList<>(folders.length);
		for (String folder : folders) {
			int startIndex = 0;
			int endIndex = folder.length();
			if (folder.startsWith(File.separator)) {
				startIndex += File.separator.length();
			}
			if (folder.endsWith(File.separator)) {
				endIndex -= File.separator.length();
			}
			if (startIndex > 0 || endIndex < folder.length()) {
				if (startIndex < endIndex) {
					cleanedFolders.add(folder.substring(startIndex, endIndex));
				}
			} else {
				cleanedFolders.add(folder);
			}
		}
		return cleanedFolders.toArray(new String[cleanedFolders.size()]);
	}

	public boolean isChangeDetectionEnabled() {
		Boolean enabled = settings.getBoolean("boot-java", "change-detection", "on");
		return enabled != null && enabled.booleanValue();
	}

	public boolean isSpelExpressionValidationEnabled() {
		Toggle categorySwitch = SpringProblemCategories.SPEL.getToggle();
		String enabled = settings.getString(categorySwitch.getPreferenceKey().split("\\."));
		if (enabled == null) {
			return categorySwitch.getDefaultValue() == Toggle.Option.ON;
		} else {
			// Legacy case
			if ("true".equalsIgnoreCase(enabled)) {
				return true;
			} else if ("false".equalsIgnoreCase(enabled)) {
				return false;
			} else {
				return Toggle.Option.valueOf(enabled) == Toggle.Option.ON;
			}
		}
	}

	public boolean areXmlHyperlinksEnabled() {
		Boolean enabled = settings.getBoolean("boot-java", "support-spring-xml-config", "hyperlinks");
		return enabled != null && enabled.booleanValue();
	}
	
	public boolean isRewriteReconcileEnabled() {
		Boolean enabled = getRawSettings().getBoolean("boot-java", "rewrite", "reconcile");
		return enabled == null ? false : enabled.booleanValue();
	}

	
	public boolean isXmlContentAssistEnabled() {
		Boolean enabled = settings.getBoolean("boot-java", "support-spring-xml-config", "content-assist");
		return enabled != null && enabled.booleanValue();
	}
	
	public void handleConfigurationChange(Settings newConfig) {
		this.settings = newConfig;
		listeners.fire(null);
	}

	public void addListener(Consumer<Void> l) {
		listeners.add(l);
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		workspace.onDidChangeConfiguraton(this::handleConfigurationChange);
	}
	
	public Set<String> getRecipeDirectories() {
		return settings.getStringSet("boot-java", "rewrite", "scan-directories");
	}
	
	public Set<String> getRecipesFilters() {
		return settings.getStringSet("boot-java", "rewrite", "recipe-filters");
	}

	public Set<String> getRecipeFiles() {
		return settings.getStringSet("boot-java", "rewrite", "scan-files");
	}
	
	public Settings getRawSettings() {
		return settings;
	}
	
	public Toggle.Option getProblemApplicability(ProblemType problem) {
		try {
			if (problem != null && problem.getCategory() != null && problem.getCategory().getToggle() != null) {
				Toggle toggle = problem.getCategory().getToggle();
				String s = settings.getString((toggle.getPreferenceKey()).split("\\."));
				return s == null || s.isEmpty() ? toggle.getDefaultValue() : Toggle.Option.valueOf(s);
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return Toggle.Option.AUTO;
	}

}
