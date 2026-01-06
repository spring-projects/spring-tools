/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.commons.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;
import org.springframework.tooling.ls.eclipse.commons.LoggingTarget;
import org.springframework.tooling.ls.eclipse.commons.preferences.LanguageServerConsolePreferenceConstants.ServerInfo;

public class PrefsInitializer extends AbstractPreferenceInitializer {
	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore store = LanguageServerPreferencesPage.getPrefsStoreFromPlugin();
		ServerInfo[] installedServers = LsPreferencesUtil.getInstalledLs();
		for (ServerInfo s : installedServers) {
			store.setDefault(s.preferenceKeyLogLevel(), "error");
			store.setDefault(s.prefernceKeyLogTarget(), LoggingTarget.OFF.toString());
//			Bundle bundle = Platform.getBundle(s.bundleId);
//			if (bundle != null) {
//				IPath stateLocation = Platform.getStateLocation(bundle);
//				if (stateLocation != null) {
//					store.setDefault(s.preferenceKeyFileLog, stateLocation.append(s.label.toLowerCase().replaceAll("\\s+", "-") + ".log").toFile().getAbsoluteFile().getPath());
//				}
//			}
		}
		store.setDefault(PreferenceConstants.HIGHLIGHT_CODELENS_PREFS, false);
	}
}