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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.springframework.tooling.ls.eclipse.commons.LanguageServerCommonsActivator;
import org.springframework.tooling.ls.eclipse.commons.LoggingTarget;
import org.springframework.tooling.ls.eclipse.commons.preferences.LanguageServerConsolePreferenceConstants.ServerInfo;

public class LanguageServerPreferencesPage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	static IPreferenceStore getPrefsStoreFromPlugin() {
		return LanguageServerCommonsActivator.getInstance().getPreferenceStore();
	}

	@Override
	public void init(IWorkbench workbench) {
		setDescription("Log settings for STS Language Servers. Changes only take effect the next time a Language Server is started.");
		setPreferenceStore(getPrefsStoreFromPlugin());
	}

	@Override
	protected void createFieldEditors() {
		ServerInfo[] installedServers = LsPreferencesUtil.getInstalledLs();
		Composite fieldEditorParent = getFieldEditorParent();
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		fieldEditorParent.setLayout(layout);
		for (ServerInfo s : installedServers) {
			Group group = new Group(fieldEditorParent, SWT.None);
			group.setText(s.label());
			group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			layout = new GridLayout();
			layout.numColumns = 1;
			layout.marginWidth = 0;
			layout.marginHeight = 0;
			group.setLayout(layout);

			Composite c = new Composite(group, SWT.NONE);
			c.setLayout(new GridLayout());
			c.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			addField(new ComboFieldEditor(s.preferenceKeyLogLevel(), "Logging Level", new String[][] {
				{"Error", "error"},
				{"Warn", "warn"},
				{"Info", "info"},
				{"Debug", "debug"},
				{"Trace", "trace"},
				{"Off", "off"},
			}, c));

			c = new Composite(group, SWT.NONE);
			c.setLayout(new GridLayout());
			c.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			addField(new ComboFieldEditor(s.prefernceKeyLogTarget(), "Logging to IDE", new String[][] {
				{"Off", LoggingTarget.OFF.toString()},
				{"Console", LoggingTarget.CONSOLE.toString()},
				{"Error Log", LoggingTarget.ERROR_LOG.toString()},
			}, c));

			c = new Composite(group, SWT.NONE);
			c.setLayout(new GridLayout());
			c.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			addField(new FileFieldEditor(s.preferenceKeyFileLog(), "Logging to File", true, c) {

				@Override
				protected boolean checkState() {

					String msg = null;

					String path = getTextControl().getText();
					if (path != null) {
						path = path.trim();
					} else {
						path = "";//$NON-NLS-1$
					}
					if (path.isEmpty()) {
						if (!isEmptyStringAllowed()) {
							msg = getErrorMessage();
						}
					} else {
						Path p = new File(path).toPath();
						if (!Files.isDirectory(p) && !Files.isSymbolicLink(p)) {
							if (!p.isAbsolute()) {
								msg = JFaceResources
										.getString("FileFieldEditor.errorMessage2");//$NON-NLS-1$
							}
						} else {
							msg = getErrorMessage();
						}
					}

					if (msg != null) { // error
						showErrorMessage(msg);
						return false;
					}

					if(doCheckState()) { // OK!
						clearErrorMessage();
						return true;
					}
					msg = getErrorMessage(); // subclass might have changed it in the #doCheckState()
					if (msg != null) {
						showErrorMessage(msg);
					}
					return false;
				}

			});

		}
	}
}
