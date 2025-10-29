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
package org.springframework.tooling.boot.ls.prefs;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.springframework.tooling.boot.ls.BootLanguageServerPlugin;
import org.springframework.tooling.boot.ls.Constants;

/**
 * Preferences initializer for Boot-Java LS extension
 * 
 * @author Alex Boyko
 *
 */
public class PrefsInitializer extends AbstractPreferenceInitializer {

	public PrefsInitializer() {
	}

	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore preferenceStore = BootLanguageServerPlugin.getDefault().getPreferenceStore();
		
		preferenceStore.setDefault(Constants.PREF_START_LS_EARLY, true);

		preferenceStore.setDefault(Constants.PREF_LIVE_INFORMATION_FETCH_DATA_RETRY_MAX_NO, 10);
		preferenceStore.setDefault(Constants.PREF_LIVE_INFORMATION_FETCH_DATA_RETRY_DELAY_IN_SECONDS, 3);

		preferenceStore.setDefault(Constants.PREF_SUPPORT_SPRING_XML_CONFIGS, false);
		preferenceStore.setDefault(Constants.PREF_XML_CONFIGS_HYPERLINKS, true);
		preferenceStore.setDefault(Constants.PREF_XML_CONFIGS_CONTENT_ASSIST, true);
		preferenceStore.setDefault(Constants.PREF_XML_CONFIGS_SCAN_FOLDERS, "src/main");

		preferenceStore.setDefault(Constants.PREF_SCAN_JAVA_TEST_SOURCES, false);
		
		preferenceStore.setDefault(Constants.PREF_JAVA_RECONCILE, true);
		preferenceStore.setDefault(Constants.PREF_REWRITE_PROJECT_REFACTORINGS, false);
		
		preferenceStore.setDefault(Constants.PREF_REWRITE_RECIPE_FILTERS, StringListEditor.encode(new String[] {
				"org.openrewrite.java.spring.boot2.SpringBoot2JUnit4to5Migration",
				"org.openrewrite.java.spring.boot3.SpringBoot3BestPractices",
				"org.openrewrite.java.testing.junit5.JUnit5BestPractices",
				"org.openrewrite.java.testing.junit5.JUnit4to5Migration",
				"org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_7",
				"org.springframework.ide.vscode.rewrite.boot3.UpgradeSpringBoot_3_4",
				"org.springframework.ide.vscode.rewrite.boot3.UpgradeSpringBoot_3_5",
				"org.rewrite.java.security.*",
				"org.springframework.rewrite.test.*",
				"rewrite.test.*"
		}));
		
		preferenceStore.setDefault(Constants.PREF_MODULITH, true);
		preferenceStore.setDefault(Constants.PREF_LIVE_INFORMATION_ALL_JVM_PROCESSES, false);
		preferenceStore.setDefault(Constants.PREF_JPQL, true);
		preferenceStore.setDefault(Constants.PREF_PROPS_COMPLETIONS_ELIDE_PREFIX, false);
		preferenceStore.setDefault(Constants.PREF_CRON_INLAY_HINTS, true);
		preferenceStore.setDefault(Constants.PREF_COMPLETION_JAVA_INJECT_BEAN, true);
		preferenceStore.setDefault(Constants.PREF_BEANS_STRUCTURE_TREE, true);
		preferenceStore.setDefault(Constants.PREF_SYMBOLS_FROM_NEW_INDEX, true);
		preferenceStore.setDefault(Constants.PREF_CODELENS_QUERY_METHODS, true);
		preferenceStore.setDefault(Constants.PREF_CODELENS_WEB_CONFIGS_ON_CONTROLLER_CLASSES, true);
		
		preferenceStore.setDefault(Constants.PREF_AI_MCP_ENABLED, false);
		preferenceStore.setDefault(Constants.PREF_AI_MCP_PORT, 50627);
	}

}
