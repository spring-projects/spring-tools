/*******************************************************************************
 * Copyright (c) 2015, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.console;

import java.io.IOException;

import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.api.App;
import org.springframework.ide.eclipse.boot.dash.api.AppConsole;
import org.springframework.ide.eclipse.boot.dash.api.AppConsoleProvider;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.model.RunTarget;
import org.springframework.ide.eclipse.boot.dash.views.BootDashModelConsoleManager;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import reactor.core.Disposable;

public class CloudAppLogManager extends BootDashModelConsoleManager implements AppConsoleProvider {

	static final String CONSOLE_TYPE = "org.springframework.ide.eclipse.boot.dash.console";
	static final String APP_CONSOLE_ID = "consoleId";

	private IConsoleManager consoleManager;

	public CloudAppLogManager(SimpleDIContext injections) {
		consoleManager = ConsolePlugin.getDefault().getConsoleManager();
	}

	@Override
	protected void doWriteToConsole(App element, String message, LogType type) throws Exception {
		ApplicationLogConsole console = getExisitingConsole(element);
		if (console != null) {
			console.writeApplicationLog(message, type);

			// To avoid console "jumping", only show console
			// if none are visible
			showIfNoOtherConsoles(console);
		}
	}

	public IConsoleManager getConsoleManager() {
		return consoleManager;
	}

	/**
	 * Only shows the given console if no other console is visible. This avoid
	 * "jumping" between consoles when multiple consoles are streaming in
	 * parallel.
	 */
	protected void showIfNoOtherConsoles(IConsole console) {
		IConsole[] consoles = consoleManager.getConsoles();
		if (consoles == null || consoles.length == 0) {
			consoleManager.showConsoleView(console);
		}
	}

	@Override
	public synchronized void resetConsole(App element) {
		ApplicationLogConsole console = getExisitingConsole(element);
		if (console != null) {
			console.clearConsole();
			console.setLogStreamingToken(null);
		}
	}

	/**
	 *
	 * @param targetProperties
	 * @param appName
	 * @return existing console, or null if it does not exist.
	 */
	public synchronized ApplicationLogConsole getExisitingConsole(App element) {
		IConsole[] consoles = ConsolePlugin.getDefault().getConsoleManager().getConsoles();
		if (consoles != null) {
			for (IConsole console : consoles) {
				if (console instanceof ApplicationLogConsole) {
					String id = (String) ((MessageConsole) console).getAttribute(APP_CONSOLE_ID);
					String idToCheck = getConsoleId(element);
					if (idToCheck.equals(id)) {
						ApplicationLogConsole appConsole = (ApplicationLogConsole) console;
						connect(appConsole, element);
						return appConsole;
					}
				}
			}
		}

		return null;
	}

	@Override
	public synchronized void terminateConsole(App element) throws Exception {
		ApplicationLogConsole console = getExisitingConsole(element);
		if (console != null) {
			console.close();
		}
	}

	/**
	 *
	 * @param targetProperties
	 * @param appName
	 * @return non-null console for the given appname and target properties
	 * @throws Exception
	 *             if console was not created or found
	 */
	public synchronized ApplicationLogConsole getOrCreateConsole(App element) throws Exception {
		ApplicationLogConsole appConsole = getExisitingConsole(element);

		if (appConsole == null) {
			appConsole = new ApplicationLogConsole(getConsoleDisplayName(element), CONSOLE_TYPE, element.getTarget().getIcon());
			appConsole.setAttribute(APP_CONSOLE_ID, getConsoleId(element));
			consoleManager.addConsoles(new IConsole[] { appConsole });

			connect(appConsole, element);
			showConsole(element);
		}

		return appConsole;
	}



	protected void connect(ApplicationLogConsole logConsole, App element) {
		if (logConsole == null) {
			return;
		}
		if (element instanceof LegacyLogSource) {
			Disposable existingToken = logConsole.getLogStreamingToken();
			if (existingToken == null) {
				LegacyLogSource source = (LegacyLogSource) element;
				logConsole.setLogStreamingToken(source.connectLog(logConsole));
			}
		}
	}

	public static String getConsoleId(App element) {
		return element.getTarget().getId()+":"+element.getName();
	}

	public static String getConsoleDisplayName(App element) {
		String displayName = element.getConsoleDisplayName();
		return displayName != null ? displayName : element.getName() +" @ "+ element.getTarget().getDisplayName();
	}

	@Override
	public void showConsole(App element) throws Exception {
		ApplicationLogConsole console = getOrCreateConsole(element);
		consoleManager.showConsoleView(console);
	}

	@Override
	public void reconnect(App element) throws Exception {
		ApplicationLogConsole console = getOrCreateConsole(element);
		console.setLogStreamingToken(null);
		connect(console, element);
		consoleManager.showConsoleView(console);
	}

	@Override
	public ApplicationLogConsole safeGetOrCreateConsole(App element) {
		try {
			return getOrCreateConsole(element);
		} catch (Exception e) {
			return null;
		}
	}

	public boolean hasConsole(App element) {
		return getExisitingConsole(element) != null;
	}

	@Override
	public AppConsole getConsole(App app) {
		return new AppConsole() {

			@Override
			public String toString() {
				return "AppConsole("+app.getName()+")";
			}

			@Override
			public void write(String message, LogType type) throws Exception {
				writeToConsole(app, message, type);
			}

			@Override
			public void show() {
				try {
					showConsole(app);
				} catch (Exception e) {
					Log.log(e);
				}
			}
		};
	}

	public void showConsole(RunTarget<?> target, String appName) throws Exception {
		showConsole(new App() {

			@Override
			public String getName() {
				return appName;
			}

			@Override
			public RunTarget<?> getTarget() {
				return target;
			}

		});
	}

}
