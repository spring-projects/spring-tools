/*******************************************************************************
 * Copyright (c) 2012, 2018 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.commons.console;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;

import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.preferences.IDebugPreferenceConstants;
import org.eclipse.swt.graphics.Color;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IOConsole;
import org.eclipse.ui.console.IOConsoleInputStream;
import org.eclipse.ui.console.IOConsoleOutputStream;

/**
 * @author Kris De Volder
 */
@SuppressWarnings("restriction")
public class ConsoleUtil {

	public static class Console {
		public final InputStream in;
		public final OutputStream out;
		public final OutputStream err;

		public Console(IOConsoleInputStream in, IOConsoleOutputStream out,
				IOConsoleOutputStream err) {
			this.in = in;
			this.out = out;
			this.err = err;
		}

		public void close() {
			try {
				out.close();
			} catch (IOException e) {
			}
			try {
				err.close();
			} catch (IOException e) {
			}
			try {
				in.close();
			} catch (IOException e) {
			}
		}
	}

	/**
	 * Maximum number of old consoles to keep open. If this number is exceeded the oldest console will
	 * be removed from the UI.
	 */
	private final int MAX_SIZE = 3;

	public static Color getOutputColor() {
		return DebugUIPlugin.getPreferenceColor(IDebugPreferenceConstants.CONSOLE_SYS_OUT_COLOR);
	}

	public static Color getErrorColor() {
		return DebugUIPlugin.getPreferenceColor(IDebugPreferenceConstants.CONSOLE_SYS_ERR_COLOR);
	}

	public static Color getInputColor() {
		return DebugUIPlugin.getPreferenceColor(IDebugPreferenceConstants.CONSOLE_SYS_IN_COLOR);
	}

	private LinkedList<IOConsole> history = new LinkedList<IOConsole>();

	private void add(IOConsole console) {
		IOConsole toClose = null;
		synchronized (history) {
			history.addLast(console);
			if (history.size() > MAX_SIZE) {
				toClose = history.removeFirst();
			}
		}
		if (toClose != null) {
			close(toClose);
		}
		console.activate();
		ConsolePlugin.getDefault().getConsoleManager().addConsoles(new IConsole[]{ console });
	}

	private static void close(IOConsole console) {
		ConsolePlugin.getDefault().getConsoleManager().removeConsoles(new IConsole[] {console});
	}

	public Console getConsole(String title) {

		final IOConsole console = new LanguageServerIOConsole(title);
		final IOConsoleInputStream in  = console.getInputStream();
		final IOConsoleOutputStream out = console.newOutputStream();
		final IOConsoleOutputStream err = console.newOutputStream();

		in.setColor(getInputColor());
		out.setColor(getOutputColor());
		err.setColor(getErrorColor());

		add(console);
		return new Console(in, out, err);
	}

}
