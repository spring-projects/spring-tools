/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls.views;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.osgi.service.prefs.BackingStoreException;
import org.springframework.tooling.boot.ls.BootLanguageServerPlugin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * What the user chose to see in the Logical Structure view, kept for the workspace: whether the
 * changes since the baseline are highlighted or the unchanged nodes hidden, whether the
 * dependencies selected for the projects are included, which dependencies those are, and which
 * baseline each project is compared against.
 *
 * Including dependencies and showing changes are mutually exclusive: a tree with dependencies
 * mixes the elements of several projects, so no baseline describes it. Turning one on turns the
 * other off.
 *
 * @author Martin Lippert
 */
class StructureViewState {

	/**
	 * Where the state is kept - a preference node in the plugin, or something else in tests.
	 */
	interface Storage {
		String get(String key);

		/**
		 * @param value the value to keep, or null to forget the key
		 */
		void put(String key, String value);
	}

	private static final String HIGHLIGHT_CHANGES_KEY = "structure.highlightChanges";
	private static final String HIDE_UNCHANGED_KEY = "structure.hideUnchanged";
	private static final String INCLUDE_DEPENDENCIES_KEY = "structure.includeDependencies";
	private static final String DEPENDENCIES_KEY = "structure.dependencies";
	private static final String COMPARE_AGAINST_KEY = "structure.compareAgainst";

	private static final Gson GSON = new Gson();

	private final Storage storage;

	private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

	private boolean highlightChanges;
	private boolean hideUnchanged;
	private boolean includeDependencies;

	StructureViewState() {
		this(new PreferenceStorage());
	}

	StructureViewState(Storage storage) {
		this.storage = storage;
		this.highlightChanges = Boolean.parseBoolean(storage.get(HIGHLIGHT_CHANGES_KEY));
		this.hideUnchanged = Boolean.parseBoolean(storage.get(HIDE_UNCHANGED_KEY));
		this.includeDependencies = Boolean.parseBoolean(storage.get(INCLUDE_DEPENDENCIES_KEY));
		if (includeDependencies && (highlightChanges || hideUnchanged)) {
			// the two never are on together - do not trust a state that says they are
			this.highlightChanges = false;
			this.hideUnchanged = false;
		}
	}

	/**
	 * Called whenever something changed.
	 */
	void addListener(Runnable listener) {
		listeners.add(listener);
	}

	void removeListener(Runnable listener) {
		listeners.remove(listener);
	}

	/**
	 * Whether nodes that changed since the baseline are visually highlighted.
	 */
	boolean isHighlightChanges() {
		return highlightChanges;
	}

	/**
	 * Whether the nodes that did not change since the baseline are hidden, showing only the changes
	 * and the path leading to them.
	 */
	boolean isHideUnchanged() {
		return hideUnchanged;
	}

	/**
	 * Whether each project's tree includes the elements of the dependencies selected for it -
	 * dependency mode, in which the trees carry no change information.
	 */
	boolean isIncludeDependencies() {
		return includeDependencies;
	}

	/**
	 * Whether any change is shown - highlighted, or by hiding what did not change. Without, the
	 * language server does not have to work out what changed.
	 */
	boolean isShowingChanges() {
		return highlightChanges || hideUnchanged;
	}

	void setHighlightChanges(boolean on) {
		if (on == highlightChanges) {
			return;
		}
		highlightChanges = on;
		if (on) {
			includeDependencies = false;
		}
		storeToggles();
		fireChanged();
	}

	void setHideUnchanged(boolean on) {
		if (on == hideUnchanged) {
			return;
		}
		hideUnchanged = on;
		if (on) {
			includeDependencies = false;
		}
		storeToggles();
		fireChanged();
	}

	void setIncludeDependencies(boolean on) {
		if (on == includeDependencies) {
			return;
		}
		includeDependencies = on;
		if (on) {
			highlightChanges = false;
			hideUnchanged = false;
		}
		storeToggles();
		fireChanged();
	}

	/**
	 * The ids of the dependencies selected for the given project.
	 */
	List<String> getSelectedDependencies(String projectName) {
		List<String> ids = readMap(DEPENDENCIES_KEY, new TypeToken<Map<String, List<String>>>() {}).get(projectName);
		return ids == null ? List.of() : Collections.unmodifiableList(ids);
	}

	void setSelectedDependencies(String projectName, List<String> ids) {
		Map<String, List<String>> all = readMap(DEPENDENCIES_KEY, new TypeToken<Map<String, List<String>>>() {});
		if (ids == null || ids.isEmpty()) {
			all.remove(projectName);
		} else {
			all.put(projectName, new ArrayList<>(ids));
		}
		writeMap(DEPENDENCIES_KEY, all);
		fireChanged();
	}

	/**
	 * What a structure request says about the dependencies to include - only in dependency mode, and
	 * a non-empty selection is what puts the language server into that mode.
	 *
	 * @return the selected dependencies per project, null if none are to be included
	 */
	Map<String, List<String>> getDependenciesForRequest() {
		if (!includeDependencies) {
			return null;
		}
		Map<String, List<String>> all = readMap(DEPENDENCIES_KEY, new TypeToken<Map<String, List<String>>>() {});
		return all.isEmpty() ? null : all;
	}

	/**
	 * Identifies the snapshot the given project is pinned to compare against, if the user picked
	 * one - null means the most recent one, which is what the language server falls back to.
	 */
	String getCompareAgainst(String projectName) {
		return readMap(COMPARE_AGAINST_KEY, new TypeToken<Map<String, String>>() {}).get(projectName);
	}

	void setCompareAgainst(String projectName, String snapshotKey) {
		Map<String, String> all = readMap(COMPARE_AGAINST_KEY, new TypeToken<Map<String, String>>() {});
		if (snapshotKey == null) {
			all.remove(projectName);
		} else {
			all.put(projectName, snapshotKey);
		}
		writeMap(COMPARE_AGAINST_KEY, all);
		fireChanged();
	}

	/**
	 * What a structure request says about the baselines to compare against - none in dependency
	 * mode, where no tree is compared against a baseline.
	 *
	 * @return the pinned snapshot per project, null if none
	 */
	Map<String, String> getCompareAgainstForRequest() {
		if (includeDependencies) {
			return null;
		}
		Map<String, String> all = readMap(COMPARE_AGAINST_KEY, new TypeToken<Map<String, String>>() {});
		return all.isEmpty() ? null : all;
	}

	private void storeToggles() {
		storage.put(HIGHLIGHT_CHANGES_KEY, highlightChanges ? "true" : null);
		storage.put(HIDE_UNCHANGED_KEY, hideUnchanged ? "true" : null);
		storage.put(INCLUDE_DEPENDENCIES_KEY, includeDependencies ? "true" : null);
	}

	private void fireChanged() {
		listeners.forEach(Runnable::run);
	}

	private <T> Map<String, T> readMap(String key, TypeToken<Map<String, T>> type) {
		String json = storage.get(key);
		if (json != null && !json.isBlank()) {
			try {
				Map<String, T> map = GSON.fromJson(json, type.getType());
				if (map != null) {
					return new LinkedHashMap<>(map);
				}
			} catch (RuntimeException e) {
				// unreadable, treated as not set
			}
		}
		return new LinkedHashMap<>();
	}

	private void writeMap(String key, Map<String, ?> map) {
		storage.put(key, map.isEmpty() ? null : GSON.toJson(map));
	}

	/**
	 * The state of the workspace: a node of its own in the instance scope, so that the plugin's
	 * preference store, whose changes are sent to the language server, does not notice it.
	 */
	private static class PreferenceStorage implements Storage {

		private IEclipsePreferences node() {
			return InstanceScope.INSTANCE.getNode(BootLanguageServerPlugin.getDefault().getBundle().getSymbolicName() + ".structure-view");
		}

		@Override
		public String get(String key) {
			return node().get(key, null);
		}

		@Override
		public void put(String key, String value) {
			IEclipsePreferences node = node();
			if (value == null) {
				node.remove(key);
			} else {
				node.put(key, value);
			}
			try {
				node.flush();
			} catch (BackingStoreException e) {
				BootLanguageServerPlugin.getDefault().getLog().error("Failed to store the state of the Logical Structure view", e);
			}
		}
	}

}
