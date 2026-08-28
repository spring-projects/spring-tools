/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.preferences;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.springframework.ide.eclipse.editor.support.reconcile.ProblemSeverity;
import org.springframework.ide.eclipse.editor.support.reconcile.ProblemType;
import org.springframework.ide.eclipse.editor.support.reconcile.SeverityProvider;

/**
 * Implementation of {@link SeverityProvider} that determines the severity of a problem
 * by consulting user's preferences in a preferences store.
 *
 * @author Kris De Volder
 */
public class PreferencesBasedSeverityProvider implements SeverityProvider {

	private EditorType editorType;
	private IPreferenceStore projectPrefs;
	private IPreferenceStore workspacePrefs;

	private Map<ProblemType, ProblemSeverity> cache = null;
	private final ProblemSeverityPreferencesUtil util;

	public PreferencesBasedSeverityProvider(ProblemSeverityPreferencesUtil util, IPreferenceStore projectPrefs, IPreferenceStore workspacePrefs, EditorType editorType) {
		this.util = util;
		this.projectPrefs = projectPrefs;
		this.workspacePrefs = workspacePrefs;
		this.editorType = editorType;
	}

	public PreferencesBasedSeverityProvider(ProblemSeverityPreferencesUtil util, IProject project, String pluginId, EditorType editorType) {
		this(
				util,
				new ScopedPreferenceStore(new ProjectScope(project), pluginId),
				new ScopedPreferenceStore(InstanceScope.INSTANCE, pluginId),
				editorType
		);
	}

	@Override public synchronized ProblemSeverity getSeverity(ProblemType problemType) {
		if (cache == null) {
			cache = new HashMap<>();
		}
		ProblemSeverity existing = cache.get(problemType);
		if (existing == null) {
			cache.put(problemType, existing = util.getSeverity(getPrefs(), problemType));
		}
		return existing;
	}

	protected IPreferenceStore getPrefs() {
		if (useProjectPreferences()) {
			return projectPrefs;
		} else {
			return workspacePrefs;
		}
	}

	private boolean useProjectPreferences() {
		if (projectPrefs != null) {
			return util.projectPreferencesEnabled(projectPrefs, editorType);
		}
		return false;
	}

	@Override
	public synchronized void startSession() {
		cache = null;
	}
}
