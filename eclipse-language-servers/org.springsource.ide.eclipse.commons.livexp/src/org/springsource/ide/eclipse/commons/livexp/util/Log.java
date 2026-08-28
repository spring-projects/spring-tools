/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.util;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.springsource.ide.eclipse.commons.livexp.Activator;

public class Log {

	/**
	 * This is for testing and allows test code to 'observe' whether any errors are
	 * being logged during test execution. 
	 */
	public static Consumer<Throwable> errorHandler = null;
	
	public static void log(Throwable e) {
		if (ExceptionUtil.isCancelation(e)) {
			//Don't log canceled operations, those aren't real errors.
			return;
		}
		try {
			Consumer<Throwable> eh = errorHandler;
			if (eh != null) {
				eh.accept(e);
			}
			Activator.getDefault().getLog().log(ExceptionUtil.status(e));
		} catch (NullPointerException npe) {
			//Can happen if errors are trying to be logged during Eclipse's shutdown
			e.printStackTrace();
		}
	}

	public static void info(String msg) {
		Activator.getDefault().getLog().log(ExceptionUtil.status(IStatus.INFO, msg));
	}

	public static void warn(String msg) {
		Activator.getDefault().getLog().log(ExceptionUtil.status(IStatus.WARNING, msg));
	}

	public static void warn(Throwable e) {
		if (ExceptionUtil.isCancelation(e)) {
			//Don't log canceled operations, those aren't real errors.
			return;
		}
		try {
			Activator.getDefault().getLog().log(ExceptionUtil.status(IStatus.WARNING, e));
		} catch (NullPointerException npe) {
			//Can happen if errors are trying to be logged during Eclipse's shutdown
			e.printStackTrace();
		}
	}

	public static void error(String string) {
		try {
			throw ExceptionUtil.coreException(string);
		} catch (CoreException e) {
			Log.log(e);
		}
	}

	public static void async(CompletableFuture<?> asyncWork) {
		asyncWork.exceptionally(e -> {
			Log.log(e);
			return null;
		});
	}

}
