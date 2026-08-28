/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.editor.support.reconcile;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.springframework.ide.eclipse.editor.support.EditorSupportActivator;
import org.springframework.ide.eclipse.editor.support.util.DocumentUtil;
import org.springframework.ide.eclipse.editor.support.util.UserInteractions;

/**
 * Default implementation of {@link QuickfixContext} that derives context information for
 * quickfixes from ISourceViewer.
 *
 * @author Kris De Volder
 */
public class DefaultQuickfixContext implements QuickfixContext {

	private final String PLUGIN_ID;
	private IPreferenceStore workspacePreferences;
	private ISourceViewer sourceViever;
	private UserInteractions ui;

	public DefaultQuickfixContext(String hostPluginId, IPreferenceStore workspacePreferences, ISourceViewer sourceViewer, UserInteractions ui) {
		this.PLUGIN_ID = hostPluginId;
		this.workspacePreferences = workspacePreferences;
		this.sourceViever = sourceViewer;
		this.ui = ui;
	}

	public IPreferenceStore getPreferences() {
		return workspacePreferences;
	}

	public IProject getProject() {
		IDocument doc = getDocument();
		if (doc != null) {
			return DocumentUtil.getProject(doc);
		}
		return null;
	}

	@Override
	public IJavaProject getJavaProject() {
		try {
			IProject p = getProject();
			if (p != null && p.isAccessible() && p.hasNature(JavaCore.NATURE_ID)) {
				return JavaCore.create(p);
			}
		} catch (Exception e) {
			EditorSupportActivator.log(e);
		}
		return null;
	}

	@Override
	public UserInteractions getUI() {
		return ui;
	}

	@Override
	public IPreferenceStore getWorkspacePreferences() {
		return workspacePreferences;
	}

	@Override
	public IPreferenceStore getProjectPreferences() {
		IProject project = getProject();
		if (project != null) {
			return new ScopedPreferenceStore(new ProjectScope(project), PLUGIN_ID);
		}
		return null;
	}

	@Override
	public IDocument getDocument() {
		return sourceViever.getDocument();
	}

}
