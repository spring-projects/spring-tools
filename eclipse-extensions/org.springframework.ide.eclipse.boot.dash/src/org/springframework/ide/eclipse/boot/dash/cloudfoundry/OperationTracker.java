/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.cloudfoundry;

import java.util.function.Supplier;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Platform;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.dash.util.CancelationTokens.CancelationToken;
import org.springsource.ide.eclipse.commons.livexp.core.LiveCounter;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;


/**
 * Keeps track of whether a certain 'operation' is currently in progress.
 *
 * @author Kris De Volder
 */
public class OperationTracker {

	private static final boolean DEBUG = (""+Platform.getLocation()).contains("kdvolder");

	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	@FunctionalInterface
	public interface Task {
		void run() throws Exception;
	}

	private LiveVariable<Throwable> error;

	/**
	 * Counter that keeps a count of the number of 'nested' operations are
	 * currently in progress (i.e. operation was started but not yet ended)
	 */
	public final LiveCounter inProgress = new LiveCounter();

	private Supplier<String> name;

	public OperationTracker(Supplier<String> name, LiveVariable<Throwable> error) {
		this.name = name;
		this.error = error;
	}

	private void start() {
		setError(null);
		inProgress.increment();
		debug("starting: "+name.get()+" ["+inProgress.getValue()+"]");
	}

	private void setError(Throwable e) {
		error.setValue(e);
	}

	public void whileExecuting(UserInteractions ui, CancelationToken cancelationToken, IProgressMonitor monitor, Task task) throws Exception {
		Throwable error = null;
		start();
		try {
			task.run();
		} catch (Throwable e) {
			error = e;
		} finally {
			end(error, ui, cancelationToken, monitor);
		}
	}

	private void end(Throwable error, UserInteractions ui, CancelationToken cancelationToken, IProgressMonitor monitor) throws Exception {
		Assert.isLegal(inProgress.getValue() > 0);
		int level = inProgress.decrement();
		debug("ended: "+name.get()+" ["+inProgress.getValue()+"]");
		if (cancelationToken.isCanceled() || monitor.isCanceled()) {
			//Avoid setting error results for canceled operation. If an op is canceled
			// its errors should simply be ignored.
			throw new OperationCanceledException();
		}
		if (level == 0 && !(ExceptionUtil.isCancelation(error))) {
			setError(error);
		}
		if (error != null) {
			throw ExceptionUtil.exception(error);
		}
	}



}
