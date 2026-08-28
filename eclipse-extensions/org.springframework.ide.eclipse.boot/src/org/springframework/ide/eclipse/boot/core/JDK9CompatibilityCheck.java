/*******************************************************************************
 * Copyright (c) 2017, 2018 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.core;

import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

/**
 * Listen to the error log for errors that are indicative of JDK9 comaptibility issues. Show a warning to
 * the user if such messages are received.
 *
 * See: https://www.pivotaltracker.com/story/show/146914165
 *
 * @author Kris De Volder
 */
public class JDK9CompatibilityCheck {

	public static void initialize() {
		String version = System.getProperty("java.version");
		int major = getJavaMajorVersion(version);

		//Unless IDE is running in java 9, there's no point in any of these checks!
		if (major >= 9) {
			//Schedule a job to avoid directly triggering lots of classloading during bundle activation.
			Job job = new Job("Start JDK Compatibility Check") {

				@Override
				protected IStatus run(IProgressMonitor monitor) {
					new Checker();
					return Status.OK_STATUS;
				}
			};
			job.schedule();
		}
	}



	private static int getJavaMajorVersion(String version) {
		try {
			int major = Integer.parseInt(version.split("\\D")[0]);
			return major;
		} catch (Exception e) {
			return -1;
		}
	}

	private static class Checker implements ILogListener {

		private Disposable disposable = null;

		public Checker() {
			Platform.addLogListener(this);
			disposable = () -> Platform.removeLogListener(this);
		}

		@Override
		public void logging(IStatus status, String plugin) {
			String indicator = getProblemIndicator(status, plugin);
			if (indicator != null) {
				showWarning(indicator);
			}
		}

		private String getProblemIndicator(IStatus status, String plugin) {
			if (status.getSeverity() == Status.ERROR) {
				Throwable e = status.getException();
				if (e != null) {
					String m= ExceptionUtil.getMessage(e);
					if (m.equals("NoClassDefFoundError: Could not initialize class org.codehaus.plexus.archiver.jar.JarArchiver")) {
						return m;
					}
				}
			}
			return null;
		}

		private synchronized void showWarning(String indicativeErrorMessage) {
			String version = System.getProperty("java.version");

			// Show the accurate major version instead of a generic "JDK 9" message.
			// See: PT 161750169
			int major = getJavaMajorVersion(version);

			String title = "JDK " + major + " Compatibility Issue Detected";
			String message = "STS is currently running with a JDK " + major + " (java.version=" + version + ").\n" +
			"\n" +
			"An error was logged in the error log which is indicative of an incompatibility of the `plexus-archiver` maven plugin "+
			"with JDK " + major + ".\n" +
			"\n" +
			"The error message was: '"+indicativeErrorMessage+"'\n"+
			"\n" +
			"Note that Boot projects with version < 2.0 use an older `plexus-archiver` plugin. They will not build properly "+
			"and may even cause STS itself to behave erratically (producing a continual stream of workspace build errors)\n" +
			"\n" +
			"Recommended action is to run STS with a JDK 8 by changing your `STS.ini` file.\n" +
			"Alternatively, make sure none of your workspace projects use an outdated `plexus-archiver`. For boot projects, that means "+
			"updating to Spring Boot version 2.0 or later.\n" +
			"\n" +
			"See https://bugs.eclipse.org/bugs/show_bug.cgi?id=516887 for some additional details.\n";
			showWarning(title, message);
		}

		private synchronized void showWarning(String title, String message) {
			//Use the disposable to ensure we only show the warning at most once (per session)
			if (disposable != null) {
				disposable.dispose();
				disposable = null;
				Display.getDefault().asyncExec(() -> {
					MessageDialog.openWarning(null, title,
							message
					);
				});
			}
		}
	}

}
