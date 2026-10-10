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
package org.springframework.tooling.boot.ls;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jface.preference.IPersistentPreferenceStore;
import org.eclipse.jface.preference.IPreferenceStore;
import org.springframework.ide.vscode.commons.protocol.SetConfigurationParams;
import org.springframework.tooling.ls.eclipse.commons.STS4LanguageClientImpl;

/**
 * The language client of the Spring Boot language server: the common client, plus what only this
 * language server asks of its client.
 * 
 * @author Martin Lippert
 */
public class BootLanguageClientImpl extends STS4LanguageClientImpl {

	/**
	 * The settings the language server may set, after asking the user about them. The keys are those
	 * of the preferences of the same name.
	 */
	private static final Set<String> SETTABLE_BOOLEAN_PREFERENCES = Set.of(
			Constants.PREF_STRUCTURE_GIT_BASELINE_ENABLED,
			Constants.PREF_STRUCTURE_GIT_BASELINE_PROMPT);

	@Override
	public CompletableFuture<Object> setConfiguration(SetConfigurationParams params) {
		if (SETTABLE_BOOLEAN_PREFERENCES.contains(params.key()) && params.value() instanceof Boolean value) {
			IPreferenceStore store = BootLanguageServerPlugin.getDefault().getPreferenceStore();
			// the language server is told about the change like about any other preference change
			store.setValue(params.key(), value);
			if (store instanceof IPersistentPreferenceStore persistentStore) {
				try {
					persistentStore.save();
				} catch (Exception e) {
					BootLanguageServerPlugin.getDefault().getLog().error("Failed to save the preference " + params.key(), e);
				}
			}
		}
		return CompletableFuture.completedFuture(null);
	}

}
