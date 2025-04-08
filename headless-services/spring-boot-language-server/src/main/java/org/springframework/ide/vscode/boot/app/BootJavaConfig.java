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
package org.springframework.ide.vscode.boot.app;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.ide.vscode.boot.common.SpringProblemCategories;
import org.springframework.ide.vscode.boot.properties.completions.PropertyCompletionSettings;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemCategory.Toggle;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;
import org.springframework.ide.vscode.commons.languageserver.util.ListenerList;
import org.springframework.ide.vscode.commons.languageserver.util.Settings;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleWorkspaceService;
import org.springframework.stereotype.Component;

import com.google.gson.JsonObject;

/**
 * Boot-Java LS settings
 *
 * @author Alex Boyko
 */
@Component
public class BootJavaConfig implements InitializingBean {
	
	private static final String SPRING_IO_API_URL = "https://api.spring.io/projects";

	private static final Logger log = LoggerFactory.getLogger(BootJavaConfig.class);
	
	public static final int LIVE_INFORMATION_FETCH_DATA_RETRY_MAX_NO_DEFAULT = 10;
	public static final int LIVE_INFORMATION_FETCH_DATA_RETRY_DELAY_IN_SECONDS_DEFAULT = 3;
	
	public static final boolean VALIDAITON_SPEL_EXPRESSIONS_ENABLED_DEFAULT = true;


	private final SimpleWorkspaceService workspace;
	private Settings settings = new Settings(null);
	private ListenerList<Void> listeners = new ListenerList<Void>();

	BootJavaConfig(SimpleLanguageServer server) {
		this.workspace = server.getWorkspaceService();
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
	
	public boolean isModulithAutoProjectTrackingEnabled() {
		Boolean enabled = settings.getBoolean("boot-java", "modulith-project-tracking");
		return enabled != null && enabled.booleanValue();
	}
	
	public boolean isCronInlayHintsEnabled() {
		Boolean enabled = settings.getBoolean("boot-java", "cron", "inlay-hints");
		return enabled == null || enabled.booleanValue();
	}
	
	public boolean isShowingAllJvmProcesses() {
		Boolean isAll = settings.getBoolean("boot-java", "live-information", "all-local-java-processes");
		return isAll != null && isAll.booleanValue();
	}
	
	public boolean isJpqlEnabled() {
		Boolean isEnabled = settings.getBoolean("boot-java", "jpql");
		return isEnabled != null && isEnabled.booleanValue();
	}
	
	public boolean isJavaEmbeddedLanguagesSyntaxHighlighting() {
		Boolean isEnabled = settings.getBoolean("boot-java", "embedded-syntax-highlighting");
		return isEnabled != null && isEnabled.booleanValue();
	}
	
	public String[] xmlBeansFoldersToScan() {
		String foldersStr = settings.getString("boot-java", "support-spring-xml-config", "scan-folders");
		if (foldersStr != null) {
			foldersStr = foldersStr.trim().replace("/", File.separator);
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

	public boolean isSpelExpressionValidationEnabled() {
		Toggle categorySwitch = SpringProblemCategories.SPEL.getToggle();
		return isProblemCategoryEnabled(categorySwitch);
	}
	
	public boolean isBootVersionValidationEnabled() {
		Toggle categorySwitch = SpringProblemCategories.VERSION_VALIDATION.getToggle();
		return isProblemCategoryEnabled(categorySwitch);
	}
	
	private boolean isProblemCategoryEnabled(Toggle categorySwitch) {
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
	
	public boolean isJavaSourceReconcileEnabled() {
		Boolean enabled = getRawSettings().getBoolean("boot-java", "java", "reconcilers");
		return enabled == null ? true : enabled.booleanValue();
	}
	
	public String getSpringIOApiUrl() {
		String url = getRawSettings().getString("boot-java", "io", "api");
		return url == null ? SPRING_IO_API_URL : url;
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
	
	public Path getCommonPropertiesFile() {
		String str = settings.getString("boot-java", "common", "properties-metadata");
		return str == null || str.isBlank() ? null : Paths.get(str);
	}
	
	public boolean isBeanInjectionCompletionEnabled() {
		Boolean b = settings.getBoolean("boot-java", "java", "completions", "inject-bean");
		return Boolean.TRUE.equals(b);
	}
	
	public boolean isBeanStructureTreeEnabled() {
		Boolean b = settings.getBoolean("boot-java", "java", "beans-structure-tree");
		return Boolean.TRUE.equals(b);
	}
	
	public boolean isSymbolsFromNewIndexEnabled() {
		Boolean b = settings.getBoolean("boot-java", "java", "symbols-from-new-index");
		return Boolean.TRUE.equals(b);
	}
	
	public Settings getRawSettings() {
		return settings;
	}
	
	public Toggle.Option getProblemApplicability(ProblemType problem) {
		try {
			if (problem != null && problem.getCategory() != null && problem.getCategory().getToggle() != null) {
				Toggle toggle = problem.getCategory().getToggle();
				String s = settings.getString((toggle.getPreferenceKey()).split("\\."));
				try {
					return s == null || s.isEmpty() ? toggle.getDefaultValue() : Toggle.Option.valueOf(s);
				} catch (IllegalArgumentException e) {
					// handle backward compatibility case of 'true'/'false'
					Boolean b = Boolean.valueOf(s);
					if (b == null) {
						throw e;
					} else {
						return b.booleanValue() ? Toggle.Option.ON : Toggle.Option.OFF;
					}
				}
			}
		} catch (Exception e) {
			log.error("", e);
		}
		return Toggle.Option.AUTO;
	}
	
	public PropertyCompletionSettings getPropertyCompletionSettings() {
		Boolean elidePrefix = settings.getBoolean("boot-java", "properties", "completions", "elide-prefix");
		return new PropertyCompletionSettings(
				elidePrefix != null && elidePrefix.booleanValue()
		);
	}
	
	public JsonObject getJavaValidationSettingsJson() {
		JsonObject javaValidationsJson = new JsonObject();
		List<String> javaValidationTypes = List.of(
				SpringProblemCategories.BOOT_2.getId(),
				SpringProblemCategories.BOOT_3.getId(),
				SpringProblemCategories.SPEL.getId(),
				SpringProblemCategories.SPRING_AOT.getId()
		);
		for (String type : javaValidationTypes) {
			javaValidationsJson.add(type, getRawSettings().getRawProperty("spring-boot", "ls", "problem", type));
		}
		return javaValidationsJson;
	}

}
