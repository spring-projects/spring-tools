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

/**
 * @author Martin Lippert
 */
public class Constants {
	
	public static final String PLUGIN_ID = "org.springframework.tooling.boot.ls";
	
	public static final String PREF_LIVE_INFORMATION_ALL_JVM_PROCESSES = "boot-java.live-information.all-local-java-processes";
	public static final String PREF_LIVE_INFORMATION_FETCH_DATA_RETRY_MAX_NO = "boot-java.live-information.fetch-data.max-retries";
	public static final String PREF_LIVE_INFORMATION_FETCH_DATA_RETRY_DELAY_IN_SECONDS = "boot-java.live-information.fetch-data.retry-delay-in-seconds";

	public static final String PREF_SCAN_JAVA_TEST_SOURCES = "boot-java.scan-java-test-sources";

	public static final String PREF_SUPPORT_SPRING_XML_CONFIGS = "boot-java.support-spring-xml-config.on";
	public static final String PREF_XML_CONFIGS_SCAN_FOLDERS = "boot-java.support-spring-xml-config.scan-folders-globs";
	public static final String PREF_XML_CONFIGS_HYPERLINKS = "boot-java.support-spring-xml-config.hyperlinks";
	public static final String PREF_XML_CONFIGS_CONTENT_ASSIST = "boot-java.support-spring-xml-config.content-assist";

	public static final String PREF_JAVA_RECONCILE = "boot-java.java.reconcile";

	public static final String PREF_REWRITE_RECIPE_FILTERS = "boot-java.rewrite.recipe-filters";
	
	public static final String PREF_REWRITE_RECIPES_SCAN_FILES = "boot-java.rewrite.scan-files";
	
	public static final String PREF_REWRITE_RECIPES_SCAN_DIRS = "boot-java.rewrite.scan-directories";
	
	public static final String PREF_REWRITE_PROJECT_REFACTORINGS = "boot-java.rewrite.project-refactorings";
	
	public static final String PREF_START_LS_EARLY = "start.boot-ls.early";
	
	public static final String PREF_COMMON_PROPS_METADATA = "boot-java.common.properties-metadata";
	
	public static final String PREF_MODULITH = "boot-java.modulith-project-tracking";
	
	public static final String PREF_JPQL = "boot-java.jpql";
	
	public static final String PREF_PROPS_COMPLETIONS_ELIDE_PREFIX = "boot-java.properties.completions.elide-prefix";

	public static final String PREF_CRON_INLAY_HINTS = "boot-java.cron.inlay-hints";
	
	public static final String PREF_COMPLETION_JAVA_INJECT_BEAN = "boot-java.java.completions.inject-bean";

	public static final String PREF_BEANS_STRUCTURE_TREE = "boot-java.java.beans-structure-tree";
	
	public static final String PREF_SYMBOLS_FROM_NEW_INDEX = "boot-java.java.symbols-from-new-index";
	
	public static final String PREF_CODELENS_QUERY_METHODS = "boot-java.java.codelens-over-query-methods";
	
	public static final String PREF_AI_ENABLE_MCP = "boot-java.ai.mcp-server-enabled";
	
}
