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
package org.springframework.tooling.boot.ls.commands;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.lsp4e.command.LSPCommandHandler;
import org.eclipse.lsp4j.Command;
import org.springframework.tooling.boot.ls.BootLanguageServerPlugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Handles {@code boot-ls.client.set-configuration} - a generic "set a
 * configuration value" command usable by any quick fix, reusable for future
 * settings without a dedicated command/handler pair per setting. Writes to
 * the plugin's preference store, stripping the {@code spring-boot.ls.}
 * prefix used for settings relayed via {@code DelegatingStreamConnectionProvider}.
 */
@SuppressWarnings("restriction")
public class SetConfigurationHandler extends AbstractHandler {

	private static final String PROBLEM_SETTINGS_PREFIX = "spring-boot.ls.";

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		try {
			String p = event.getParameter(LSPCommandHandler.LSP_COMMAND_PARAMETER_ID);
			Command cmd = GSON.fromJson(p, Command.class);
			if (cmd != null && cmd.getArguments() != null && cmd.getArguments().size() >= 2) {
				String key = cmd.getArguments().get(0).toString();
				String value = toPreferenceString(cmd.getArguments().get(1));
				String prefKey = key.startsWith(PROBLEM_SETTINGS_PREFIX) ? key.substring(PROBLEM_SETTINGS_PREFIX.length()) : key;
				IPreferenceStore preferenceStore = BootLanguageServerPlugin.getDefault().getPreferenceStore();
				preferenceStore.setValue(prefKey, value);
			}
			return null;
		} catch (Exception e) {
			throw new ExecutionException("Failed to execute Set Configuration command", e);
		}
	}

	private static String toPreferenceString(Object value) {
		if (value instanceof String || value instanceof Number || value instanceof Boolean) {
			return value.toString();
		}
		return GSON.toJson(value);
	}

}
