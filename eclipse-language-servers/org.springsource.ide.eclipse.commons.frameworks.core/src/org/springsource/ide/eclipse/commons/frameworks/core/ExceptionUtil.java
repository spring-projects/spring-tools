/*******************************************************************************
 * Copyright (c) 2012 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.core;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;

/**
 * Utility methods to convert exceptions into other types of exceptions, status
 * objects etc.
 *
 * Deprecated because this class was moved to livexp bundle. Use the moved class instead of this one.
 *
 * @author Kris De Volder
 */
@Deprecated
public class ExceptionUtil {

	public static CoreException coreException(int severity, String msg) {
		return coreException(status(severity, msg));
	}

	public static CoreException coreException(IStatus status) {
		if (status == null) {
			return coreException("Null status?");
		}
		Throwable e = status.getException();
		if (e == null) {
			return new CoreException(status);
		}
		else if (e instanceof CoreException) {
			return (CoreException) e;
		}
		return new CoreException(status);
	}

	public static CoreException coreException(String msg) {
		return coreException(IStatus.ERROR, msg);
	}

	public static CoreException coreException(Throwable e) {
		if (e instanceof CoreException) {
			return (CoreException) e;
		}
		else {
			return coreException(status(e));
		}
	}

	public static Throwable getDeepestCause(Throwable e) {
		Throwable cause = e;
		Throwable parent = e.getCause();
		while (parent != null && parent != e) {
			cause = parent;
			parent = cause.getCause();
		}
		return cause;
	}

	public static String getMessage(Throwable e) {
		// The message of nested exception is usually more interesting than the
		// one on top.
		Throwable cause = getDeepestCause(e);
		String msg = cause.getClass().getSimpleName() + ": " + cause.getMessage();
		return msg;
	}

	public static IllegalStateException notImplemented(String string) {
		return new IllegalStateException("Not implemented: " + string);
	}

	public static IStatus status(int severity, String msg) {
		return new Status(severity, FrameworkCoreActivator.PLUGIN_ID, msg);
	}

	public static IStatus status(Throwable e) {
		return status(IStatus.ERROR, e);
	}

	public static IStatus status(int severity, Throwable e) {
		if (e instanceof OperationCanceledException || e instanceof InterruptedException) {
			return Status.CANCEL_STATUS;
		}
		if (e instanceof CoreException) {
			IStatus status = ((CoreException) e).getStatus();
			if (status != null && status.getSeverity() == severity) {
				Throwable ee = status.getException();
				if (ee != null) {
					return status;
				}
			}
		}
		return new Status(severity, FrameworkCoreActivator.PLUGIN_ID, getMessage(e), e);
	}

	public static IStatus status(String msg) {
		return status(IStatus.ERROR, msg);
	}

	public static final IStatus OK_STATUS = status(IStatus.OK, "");

	public static Exception exception(Throwable e) {
		if (e instanceof Exception) {
			return (Exception)e;
		} else {
			return coreException(e);
		}
	}

	public static RuntimeException unchecked(Exception e) {
		return new RuntimeException(e);
	}

	public static String stacktrace() {
		return stacktrace(new Exception("Stacktrace"));
	}

	public static String stacktrace(Exception exception) {
		ByteArrayOutputStream dump = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(dump);
		try {
			exception.printStackTrace(out);
		} finally {
			out.close();
		}
		return dump.toString();
	}

}
