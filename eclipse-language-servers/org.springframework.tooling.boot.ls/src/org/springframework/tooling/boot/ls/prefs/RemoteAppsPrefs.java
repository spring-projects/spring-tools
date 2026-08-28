/*******************************************************************************
 * Copyright (c) 2018, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls.prefs;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.springframework.tooling.boot.ls.BootLanguageServerPlugin;
import org.springsource.ide.eclipse.commons.boot.ls.remoteapps.RemoteBootAppsDataHolder.RemoteAppData;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.gson.Gson;

public class RemoteAppsPrefs {

	public static final String REMOTE_APPS_KEY = "remote-apps";

	private static final RemoteAppData[] NO_APPS = {};

	private IEclipsePreferences prefs = BootLanguageServerPlugin.getPreferences();
	
	public void setRawJson(String json) {
		prefs.put(REMOTE_APPS_KEY, json);
	}

	public String getRawJson() {
		return prefs.get(REMOTE_APPS_KEY, "");
	}

	public RemoteAppData[] getRemoteAppData() {
		String json = getRawJson();
		try {
			RemoteAppData[] parsed = parse(json);
			if (parsed != null) {
				for (RemoteAppData remoteAppData : parsed) {
					remoteAppData.setKeepChecking(true);
				}
				return parsed;
			}
		} catch (Exception e) {
			Log.warn("Problem parsing manually configured boot remote apps data: "+ExceptionUtil.getMessage(e));
		}
		return NO_APPS;
	}

	public static RemoteAppData[] parse(String json) {
		return new Gson().fromJson(json, RemoteAppData[].class);
	}

	public static Disposable addListener(Runnable runnable) {
		IPreferenceChangeListener l = event -> {
			if (event.getKey().equals(REMOTE_APPS_KEY)) {
				runnable.run();
			}
		};
		getPreferences().addPreferenceChangeListener(l);
		return () -> {
				getPreferences().removePreferenceChangeListener(l);
		};
	}

	private static IEclipsePreferences getPreferences() {
		return BootLanguageServerPlugin.getPreferences();
	}
}
