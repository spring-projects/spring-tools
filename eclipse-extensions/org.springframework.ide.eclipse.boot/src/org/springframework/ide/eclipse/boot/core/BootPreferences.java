/*******************************************************************************
 * Copyright (c) 2015, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core;

import java.io.File;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.springframework.ide.eclipse.boot.util.Log;
import org.springsource.ide.eclipse.commons.core.preferences.StsProperties;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * @author Kris De Volder
 */
public class BootPreferences implements IPreferenceChangeListener {

	public static final String PREF_BOOT_PROJECT_EXCLUDE = "org.springframework.ide.eclipse.boot.project.exclude";
	public static final String PREF_BOOT_THIN_WRAPPER = "org.springframework.ide.eclipse.boot.thin.wrapper";
	public static final Pattern DEFAULT_BOOT_PROJECT_EXCLUDE = Pattern.compile("^$");
	public static final String PREF_IGNORE_SILENT_EXIT = "org.springframework.ide.eclipse.boot.ignore.silent.exit";
	public static final boolean DEFAULT_PREF_IGNORE_SILENT_EXIT = true;
	public static final String PREF_INITIALIZR_URL = "org.springframework.ide.eclipse.boot.wizard.initializr.url";

	public static final String PREF_BOOT_FAST_STARTUP_DEFAULT = "org.springframework.ide.eclipse.boot.DefaultFastStartup";
	public static final String PREF_BOOT_FAST_STARTUP_REMIND_MESSAGE = "org.springframework.ide.eclipse.boot.FastStartupRemindMessage";
	public static final String PREF_BOOT_FAST_STARTUP_JVM_ARGS = "org.springframework.ide.eclipse.boot.FastStartupJvmArgs";
	public static final String BOOT_FAST_STARTUP_DEFAULT_JVM_ARGS = "-XX:TieredStopAtLevel=1";

	public static final String PREF_BOOT_TESTJARS_LAUNCH_SUPPORT = "org.springframework.ide.eclipse.boot.TestJarsLaunch";


	// Boot Preference Page ID
	public static final String BOOT_PREFERENCE_PAGE_ID = "org.springframework.ide.eclipse.boot.ui.preferences.BootPreferencePage";

	private static BootPreferences INSTANCE = null;
	private IEclipsePreferences prefs;
	private final LiveExpression<Pattern> projectExclude = new LiveExpression<Pattern>(DEFAULT_BOOT_PROJECT_EXCLUDE) {
		@Override
		protected Pattern compute() {
			return compileProjectExclude();
		}
	};

	private BootPreferences() {
		this.prefs = getPrefs();
		this.prefs.addPreferenceChangeListener(this);
		this.projectExclude.refresh();
	}

	public synchronized static BootPreferences getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new BootPreferences();
		}
		return INSTANCE;
	}

	protected IEclipsePreferences getPrefs() {
		IEclipsePreferences prefs = InstanceScope.INSTANCE.getNode(BootActivator.PLUGIN_ID);
		return prefs;
	}

	@Override
	public void preferenceChange(PreferenceChangeEvent event) {
		if (event.getKey().equals(PREF_BOOT_PROJECT_EXCLUDE)) {
			projectExclude.refresh();
		}
	}

	public boolean isIgnoreSilentExit() {
		try {
			return prefs.getBoolean(PREF_IGNORE_SILENT_EXIT, DEFAULT_PREF_IGNORE_SILENT_EXIT);
		} catch (Exception e) {
			Log.log(e);
			return DEFAULT_PREF_IGNORE_SILENT_EXIT;
		}
	}

	public File getThinWrapper() {
		String path = prefs.get(PREF_BOOT_THIN_WRAPPER, null);
		if (StringUtil.hasText(path)) {
			return new File(path);
		}
		return null;
	}


	public void setThinWrapper(File thinWrapper) {
		if (thinWrapper != null) {
			prefs.put(PREF_BOOT_THIN_WRAPPER, thinWrapper.getAbsolutePath());
		} else {
			prefs.remove(PREF_BOOT_THIN_WRAPPER);
		}
	}


	public Pattern getProjectExclusion() {
		return projectExclude.getValue();
	}

	private Pattern compileProjectExclude() {
		try {
			if (prefs != null) {
				String patternString = prefs.get(PREF_BOOT_PROJECT_EXCLUDE, null);
				if (StringUtils.isNotBlank(patternString)) {
					return Pattern.compile(patternString);
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
		//Ensure there's always some default pattern returned, no matter what!
		return Pattern.compile("^$");
	}

	public LiveExpression<Pattern> getProjectExclusionExp() {
		return projectExclude;
	}

	public static String getInitializrUrl() {
		String[] urls = getInitializrUrls();
		if (urls != null && urls.length > 0) {
			return urls[0];
		}
		return null;
	}

	public static String getDefaultInitializrUrl() {
		String[] urls = getDefaultInitializrUrls();
		if (urls != null && urls.length > 0) {
			return urls[0];
		}
		return null;
	}

	public static String[] getDefaultInitializrUrls() {
		String encodedUrls = BootActivator.getDefault().getPreferenceStore().getDefaultString(PREF_INITIALIZR_URL);
		if (encodedUrls != null) {
			return decodeUrl(encodedUrls);
		}
		return new String[] {};
	}

	/**
	 * Adds a intializer url to the history (or moves it to front of list if it existed
	 * before.
	 */
	public static void addInitializrUrl(String newUrl) {
		String newUrls = clean(
			Mono.just(newUrl).concatWith(Flux.fromArray(getInitializrUrls()))
		)
		.take(10) // limit history to 10 elements
		.reduce(new StringBuilder(), (buf, s) -> buf.append(s + "\n"))
		.map(StringBuilder::toString)
		.block();
		IPreferenceStore store = BootActivator.getDefault().getPreferenceStore();
		store.setValue(PREF_INITIALIZR_URL, newUrls);
	}

	public static String[] getInitializrUrls() {
		String encodedUrls = BootActivator.getDefault().getPreferenceStore().getString(PREF_INITIALIZR_URL);
		if (encodedUrls != null) {
			return decodeUrl(encodedUrls);
		}
		return new String[] {};
	}

	/**
	 * Cleanup a number of items:
	 *  - removing trailing / leading whitespace
	 *  - remove emtpy elements
	 *  - convert old default http url to https
	 *  - remove duplicate elements
	 */
	private static Flux<String> clean(Flux<String> elements) {
		return elements
				.map(String::trim)
				.map(url -> convertToHttps(url))
				.filter((s) -> !s.isEmpty())
				.distinct();
	}

	private static String convertToHttps(String url) {
		//This is a bit yuck, but it is the only sure way to catch old values of this
		// that have gotten persisted somehow in user's old workspace state.
		if (url.equals("https://start.spring.io")) {
			return StsProperties.getInstance().getInstance().get("spring.initializr.json.url");
		}
		return url;
	}

	private static Flux<String> clean(String[] elements) {
		return clean(Flux.fromArray(elements));
	}

	public static String encodeUrls(String[] items) {
		StringBuilder encoded = new StringBuilder();
		clean(Flux.fromArray(items))
		.doOnNext((s) -> encoded.append(s+"\n"))
		.then()
		.block();
		return encoded.toString();
	}

	public static String[] decodeUrl(String stringList) {
		return clean(stringList.split("\n"))
		.toStream().toArray((sz) -> new String[sz]);
	}
}
