/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls.prefs;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.springframework.tooling.boot.ls.BootLanguageServerPlugin;
import org.springframework.tooling.boot.ls.Constants;

public class AiConfigPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	
	private BooleanFieldEditor mcpEnabledEditor;
	private StringFieldEditor mcpPortEditor;
	
	public AiConfigPreferencePage() {
		super(GRID);
	}
	
	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(BootLanguageServerPlugin.getDefault().getPreferenceStore());
	}
	
	@Override
	protected void createFieldEditors() {
		Composite fieldEditorParent = getFieldEditorParent();
		
		mcpEnabledEditor = new BooleanFieldEditor(Constants.PREF_AI_MCP_ENABLED, "Enable embedded MCP server (experimental)", fieldEditorParent);
		addField(mcpEnabledEditor);
		
		mcpPortEditor = new StringFieldEditor(Constants.PREF_AI_MCP_PORT, "Port used by the embedded MCP server", fieldEditorParent);
		addField(mcpPortEditor);
		
		// Initialize the enabled state of the port field
		updateMcpPortFieldState();
	}
	
	@Override
	public void propertyChange(PropertyChangeEvent event) {
		super.propertyChange(event);
		
		// Update the MCP port field enabled state when the MCP enabled preference changes
		if (event.getSource() == mcpEnabledEditor) {
			updateMcpPortFieldState();
		}
	}
	
	private void updateMcpPortFieldState() {
		if (mcpEnabledEditor != null && mcpPortEditor != null) {
			boolean mcpEnabled = mcpEnabledEditor.getBooleanValue();
			mcpPortEditor.setEnabled(mcpEnabled, getFieldEditorParent());
		}
	}

}
