/*******************************************************************************
 * Copyright (c) 2018, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.commons.console;

import java.util.HashMap;
import java.util.Map;

import org.springframework.tooling.ls.eclipse.commons.LanguageServerCommonsActivator;
import org.springframework.tooling.ls.eclipse.commons.console.ConsoleUtil.Console;
import org.springframework.tooling.ls.eclipse.commons.preferences.LanguageServerConsolePreferenceConstants.ServerInfo;

import com.google.common.base.Supplier;

public class LanguageServerConsoles {

	private static Map<String, Supplier<Console>> managers;

	public static synchronized Supplier<Console> getConsoleFactory(ServerInfo server) {
		if (managers==null) {
			managers = new HashMap<>();
		}
		return managers.computeIfAbsent(server.label(), label -> new Supplier<Console>() {
			ConsoleUtil consoleMgr = new ConsoleUtil(); //one console manager per language server type. This way each has their
														// own history (which limits number of open consoles per type)
			int consoleCounter = 0;

			@Override
			public Console get() {
				if (isConsoleEnabled(server)) {
					return consoleMgr.getConsole(server.label()+" Language Server "+consoleCounter());
				}
				return null;
			}

			private synchronized int consoleCounter() {
				return ++consoleCounter;
			}
		});
	}

	private static boolean isConsoleEnabled(ServerInfo server) {
		return LanguageServerCommonsActivator.getInstance().getPreferenceStore().getBoolean(server.preferenceKeyConsoleLog());
	}

}
