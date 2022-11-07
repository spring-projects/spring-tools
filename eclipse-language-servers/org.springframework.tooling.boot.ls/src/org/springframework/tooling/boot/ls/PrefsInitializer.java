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
package org.springframework.tooling.boot.ls;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.springframework.tooling.boot.ls.prefs.StringListEditor;

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

		preferenceStore.setDefault(Constants.PREF_LIVE_INFORMATION_FETCH_DATA_RETRY_MAX_NO, 10);
		preferenceStore.setDefault(Constants.PREF_LIVE_INFORMATION_FETCH_DATA_RETRY_DELAY_IN_SECONDS, 3);

		preferenceStore.setDefault(Constants.PREF_SUPPORT_SPRING_XML_CONFIGS, false);
		preferenceStore.setDefault(Constants.PREF_XML_CONFIGS_HYPERLINKS, true);
		preferenceStore.setDefault(Constants.PREF_XML_CONFIGS_CONTENT_ASSIST, true);
		preferenceStore.setDefault(Constants.PREF_XML_CONFIGS_SCAN_FOLDERS, "src/main");

		preferenceStore.setDefault(Constants.PREF_CHANGE_DETECTION, false);
		preferenceStore.setDefault(Constants.PREF_VALIDATION_SPEL_EXPRESSIONS, true);

		preferenceStore.setDefault(Constants.PREF_SCAN_JAVA_TEST_SOURCES, false);
		
		preferenceStore.setDefault(Constants.PREF_REWRITE_RECONCILE, false);
		preferenceStore.setDefault(Constants.PREF_REWRITE_PROJECT_REFACTORINGS, true);
		
		preferenceStore.setDefault(Constants.PREF_REWRITE_RECIPE_FILTERS, StringListEditor.encode(new String[] {
				"org.openrewrite.java.spring.boot2.SpringBoot2JUnit4to5Migration",
				"org.openrewrite.java.spring.boot2.SpringBoot2BestPractices",
				"org.openrewrite.java.spring.boot2.SpringBoot1To2Migration",
				"org.openrewrite.java.testing.junit5.JUnit5BestPractices",
				"org.openrewrite.java.testing.junit5.JUnit4to5Migration",
				"org.openrewrite.java.spring.boot2.UpgradeSpringBoot_2_6",
				"org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_0",
				"org.rewrite.java.security.*",
				"org.springframework.rewrite.test.*",
				"rewrite.test.*"
		}));
	}

}
