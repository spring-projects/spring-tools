/*******************************************************************************
 * Copyright (c) 2015 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.core.async;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A java.util.concurrent.Future which isn't created by some
 * executor, but rather just an object which is awaiting some kind
 * of event to resolve or reject it.
 *
 * Deprecated. Now that we have Java 8 you can use {@link CompletableFuture}
 *
 * @author Kris De Volder
 */
@Deprecated
public class ResolvableFuture<T> implements Future<T>, Resolvable<T> {

	private boolean isPending = true;
	private Exception exception;
	private T value;

	public synchronized void resolve(T v) {
		this.value = v;
		this.isPending = false;
		this.notifyAll();
	}

	public synchronized void reject(Exception e) {
		if (!isPending) {
			this.exception = e;
			this.isPending = false;
			this.notifyAll();
		}
	}

	@Override
	public synchronized boolean cancel(boolean mayInterruptIfRunning) {
		if (isPending) {
			reject(new CancellationException());
			return true;
		}
		return false;
	}

	@Override
	public boolean isCancelled() {
		return exception instanceof CancellationException;
	}

	@Override
	public synchronized boolean isDone() {
		return !isPending;
	}

	@Override
	public synchronized T get() throws InterruptedException, ExecutionException {
		while (isPending) {
			wait();
		}
		if (isPending) {
			throw new IllegalStateException("Still pending, can't fetch value or exception");
		}
		if (exception != null) {
			throw new ExecutionException(exception);
		} else {
			return value;
		}
	}

	@Override
	public synchronized T get(long timeout, TimeUnit unit) throws InterruptedException,
			ExecutionException, TimeoutException {
		long startTime = System.currentTimeMillis();
		long endTime = startTime + unit.toMillis(timeout);
		long waitTime;
		while (isPending
				&& 0 < (waitTime = endTime - System.currentTimeMillis())) {
			wait(waitTime);
		}
		if (isPending) {
			throw new TimeoutException();
		}
		if (exception != null) {
			throw new ExecutionException(exception);
		} else {
			return value;
		}
	}

}
