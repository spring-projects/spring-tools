/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.util;

import java.util.function.Consumer;

/**
 * Holds the current settings and notifies listeners when they change.
 *
 * <p>This is deliberately independent of how the settings arrive. When a language server session is
 * running, {@link SimpleWorkspaceService} feeds this store from
 * {@code workspace/didChangeConfiguration} notifications. Without a session — for example when the
 * server is embedded and driven over MCP — settings can be pushed in from any other source (a file
 * on disk, say) by calling {@link #update(Settings)} directly.
 *
 * @author Martin Lippert
 */
public class SettingsStore {

	private final ListenerList<Settings> listeners = new ListenerList<>();

	private volatile Settings settings = new Settings(null);

	/**
	 * The settings as they currently stand. Never null, but may be empty if nothing has been
	 * pushed in yet.
	 */
	public Settings getSettings() {
		return settings;
	}

	/**
	 * Replaces the current settings and notifies all listeners. Listeners are called on the calling
	 * thread, so callers that must not block (such as an LSP message loop) should dispatch this
	 * themselves.
	 */
	public void update(Settings settings) {
		this.settings = settings == null ? new Settings(null) : settings;
		listeners.fire(this.settings);
	}

	/**
	 * Registers a listener to be notified whenever the settings change.
	 */
	public void onDidChange(Consumer<Settings> listener) {
		listeners.add(listener);
	}

}
