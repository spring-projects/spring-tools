/*******************************************************************************
 * Copyright (c) 2016-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.util;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * Utility methods to convert exceptions into other types of exceptions, status
 * objects etc.
 *
 * @author Kris De Volder
 */
public class ExceptionUtil {

	public static Throwable getDeepestCause(Throwable e) {
		Throwable cause = e;
		Throwable parent = e.getCause();
		while (parent != null && parent != e) {
			cause = parent;
			parent = cause.getCause();
		}
		return cause;
	}
	
	/**
	 * 
	 * @param e
	 * @param toLookFor type of throwable to look for in the given throwable.
	 * @return the throwable instance of the given type, or null if nothing found.
	 */
	public static Throwable getThrowable(Throwable e, Class<? extends Throwable> toLookFor) {
		if (toLookFor.isAssignableFrom(e.getClass())) {
			return e;
		}
		
		Throwable cause = e;
		Throwable parent = e.getCause();
		while (parent != null && parent != e) {
			cause = parent;
			parent = cause.getCause();
			if (toLookFor.isAssignableFrom(cause.getClass())) {
				return cause;
			}
		}
		return null;
	}
	
	/**
	 * Given an exception, find if any of the exception types to look for is contained in the given exception
	 * @param e
	 * @param toLookFor non-null list of exception types to look for
	 * @return exception of specified type, if found, or null if not found
	 */
	public static Throwable findThrowable(Throwable e, List<Class<? extends Throwable>> toLookFor) {
		for (Class<? extends Throwable> klass : toLookFor) {
			Throwable found = getThrowable(e, klass);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	public static String getMessage(Throwable e) {
		// The message of nested exception is usually more interesting than the
		// one on top.
		Throwable cause = getDeepestCause(e);
		String errorType = cause.getClass().getSimpleName();
		String msg = cause.getMessage();
		if (ValueParseException.class.isInstance(cause) && msg != null) {
			return msg;
		}
		return errorType + ": " + msg;
	}
	
	public static String getMessageNoAppendedInformation(Throwable e) {
		Throwable deepestCause = ExceptionUtil.getDeepestCause(e);
		String msg = deepestCause != null ? deepestCause.getMessage() : null;

		if (StringUtil.hasText(msg)) {
			return msg;
		} else {
			return "An error occurred: " + getSimpleError(e);
		}
	}

	public static String getSimpleError(Throwable e) {
		return e.getClass().getSimpleName();
	}
	

	public static IllegalStateException notImplemented(String string) {
		return new IllegalStateException("Not implemented: " + string);
	}

	public static boolean isCancelation(Throwable e) {
		return (
//				e instanceof OperationCanceledException ||
				e instanceof InterruptedException ||
				e instanceof CancellationException
//				(
//						e instanceof CoreException &&
//						((CoreException)e).getStatus().getSeverity() == IStatus.CANCEL
//				)
		);
	}

	public static RuntimeException unchecked(Throwable e) {
		if (e instanceof RuntimeException) {
			return (RuntimeException)e;
		}
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

	/**
	 * Convert throwables into exception try not to wrap if not needing to.
	 */
	public static Exception exception(Throwable cause) {
		if (cause instanceof Exception) {
			return (Exception)cause;
		}
		return new ExecutionException(cause);
	}

	public static Exception exception(String message, Throwable error) {
		if (message != null) {
			// Wrap only if there is an additional message
			return new ExecutionException(message, error);
		} else {
			return exception(error);
		}
	}
}
