/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.api;

import java.io.IOException;
import java.io.OutputStream;

import org.springframework.ide.eclipse.boot.dash.console.LogType;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

public interface AppConsole {

	void write(String string, LogType stdout) throws Exception ;

	default OutputStream getOutputStream(LogType type) {
		return new OutputStream() {
			StringBuffer line = new StringBuffer();
			private boolean closed = false;

			@Override
			public void write(int b) throws IOException {
				if (closed) {
					throw new IOException("AppConsole closed");
				}
				if (b == '\n') {
					try {
						AppConsole.this.write(line.toString(), type);
						line.delete(0, line.length());
					} catch (IOException e) {
						throw e;
					} catch (Exception e) {
						Log.log(e);
					}
				} else if (b == '\r') {
				} else {
					line.append((char)b);
				}
			}

			@Override
			public void close() throws IOException {
				this.closed = true;
				super.close();
			}
		};

	}

	void show();

	default void logCommand(String string) {
		try {
			String[] pieces = string.split("\\n");
			for (int i = 0; i < pieces.length; i++) {
				write((i == 0 ? "$ " : "> ")+pieces[i], LogType.STDOUT);
			}
		} catch (Exception e) {
			Log.log(e);
		}
	}

	default void logCommandResult(String string) {
		try {
			write(string, LogType.STDOUT);
		} catch (Exception e) {
			Log.log(e);
		}
	}

}
