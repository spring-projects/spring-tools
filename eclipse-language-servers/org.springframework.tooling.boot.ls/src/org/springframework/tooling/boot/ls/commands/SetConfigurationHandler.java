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

/**
 * Handles {@code boot-ls.client.set-configuration} - a generic "set a
 * configuration value" command the language server invokes from quick fixes
 * (e.g. the SQL dialect quick fix), reusable for future settings without a
 * dedicated command/handler pair per setting. Always writes to the plugin's
 * own preference store (no per-resource scope is supported); the
 * preference-change listener already registered in
 * {@code DelegatingStreamConnectionProvider} picks up the change and
 * re-sends the whole configuration to the language server, same as any
 * other preference change.
 * <p>
 * The key is the language server's own dotted setting name (as sent to it
 * over {@code workspace/didChangeConfiguration}). Settings relayed through
 * the {@code spring-boot.ls.problem}/{@code spring-boot.ls.problem-parameters}
 * mechanism (see {@code DelegatingStreamConnectionProvider}) are stored
 * locally without that prefix, so it's stripped here to get the actual
 * preference key.
 */
@SuppressWarnings("restriction")
public class SetConfigurationHandler extends AbstractHandler {

	private static final String PROBLEM_SETTINGS_PREFIX = "spring-boot.ls.";

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		try {
			String p = event.getParameter(LSPCommandHandler.LSP_COMMAND_PARAMETER_ID);
			Command cmd = new Gson().fromJson(p, Command.class);
			if (cmd != null && cmd.getArguments() != null && cmd.getArguments().size() >= 2) {
				String key = cmd.getArguments().get(0).toString();
				String value = cmd.getArguments().get(1).toString();
				String prefKey = key.startsWith(PROBLEM_SETTINGS_PREFIX) ? key.substring(PROBLEM_SETTINGS_PREFIX.length()) : key;
				IPreferenceStore preferenceStore = BootLanguageServerPlugin.getDefault().getPreferenceStore();
				preferenceStore.setValue(prefKey, value);
			}
			return null;
		} catch (Exception e) {
			throw new ExecutionException("Failed to execute Set Configuration command", e);
		}
	}

}
