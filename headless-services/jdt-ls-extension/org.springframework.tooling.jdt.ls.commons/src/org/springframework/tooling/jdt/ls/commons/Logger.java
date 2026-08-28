/*******************************************************************************
 * Copyright (c) 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.jdt.ls.commons;

import java.io.PrintWriter;
import java.util.Date;
import java.util.function.Supplier;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;


/**
 * Poor man's logger. The default implementation is only used as a last-resort
 * fallback when the Eclipse/OSGi logging APIs are unavailable, so it writes to
 * {@link System#err} rather than a predictable file location.
 */
public interface Logger {

	public static Logger DEFAULT = new DefaultLogger();

	public static class DefaultLogger implements Logger {
		private PrintWriter printwriter;
		public DefaultLogger() {
			printwriter = new PrintWriter(System.err, true);
			log("======== "+new Date()+" =======");
		}
		@Override
		public void log(String message) {
			printwriter.println(message);
			printwriter.flush();
		}

		@Override
		public void log(Exception e) {
			e.printStackTrace(printwriter);
		}
		@Override
		public void debug(String message) {
			log("DEBUG:" + message);
		}
	}

	static Logger forEclipsePlugin(Supplier<Plugin> _plugin) {
		return new Logger() {
			
			private Plugin plugin = null;
			private boolean DEBUG = false;
			
			@Override
			public void debug(String message) {
				init();
				if (DEBUG) {
					log(message);
				}
			}

			@Override
			public void log(String message) {
				init();
				try {
					plugin.getLog().log(new Status(IStatus.INFO, plugin.getBundle().getSymbolicName(), message));
				} catch (Exception ignore) {
					//Eclipse state is fubar... send log message someplace else.
					DEFAULT.log(message);
				}
			}

			@Override
			public void log(Exception e) {
				init();
				try {
					plugin.getLog().log(new Status(IStatus.ERROR, plugin.getBundle().getSymbolicName(), "", e));
				} catch (Exception ignore) {
					//Eclipse state is fubar... send log message someplace else.
					DEFAULT.log(e);
				}
			}

			private void init() {
				if (plugin == null) {
					plugin = _plugin.get();
					DEBUG = Boolean.getBoolean(plugin.getBundle().getSymbolicName() + ".DEBUG");
				}
			}
			
		};
	}
	

	void debug(String message);

	public static class TestLogger extends DefaultLogger {

		private Exception firstError;

		public TestLogger() {
			super();
		}
		
		@Override
		public void log(Exception e) {
			super.log(e);
			if (firstError != null) {
				firstError = e;
			}
		}
		
		public void assertNoErrors() throws Exception {
			if (firstError != null) {
				throw firstError;
			}
		}
	}

	void log(String message);
	void log(Exception e);
	
}
