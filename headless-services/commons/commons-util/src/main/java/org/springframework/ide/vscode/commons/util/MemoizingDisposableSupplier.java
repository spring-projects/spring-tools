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
package org.springframework.ide.vscode.commons.util;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.FinalizableReference;
import com.google.common.base.Supplier;

import reactor.core.Disposable;

/**
 * Wraps a Callable with memoization and logic that allows
 * proper cleanup of the memoized value.
 * <p>
 * Both real results and thrown exceptions are memoized.
 */
public class MemoizingDisposableSupplier<T> implements Disposable {
	
	private static Logger logger = LoggerFactory.getLogger(MemoizingDisposableSupplier.class);

	private T value;
	private Throwable failure;
	private Callable<T> computer;
	private Long lastComputed;
	
	private Long expireExceptionsAfter = null;
	private Consumer<T> disposeWith;
	
	public MemoizingDisposableSupplier(Callable<T> computer, Consumer<T> disposeWith) {
		this.computer = computer;
		this.disposeWith = disposeWith;
	}

	public synchronized void evict() {
		T oldValue = value;
		if (oldValue != null) {
			disposeWith.accept(oldValue);
		}
		value = null;
		failure = null;
		lastComputed = null;
	}
	
	@Override
	public void dispose() {
		boolean shouldDispose;
		synchronized (this) {
			shouldDispose = computer != null;
			computer = null;
		}
		if (shouldDispose) {
			if (disposeWith != null && value != null) {
				disposeWith.accept(value);
			}
			value = null;
			failure = null;
			lastComputed = null;
			disposeWith = null;
		}
	}

	public synchronized T get() throws Exception {
		Assert.isLegal(!isDisposed());
		if (shouldCompute()) {
			lastComputed = System.currentTimeMillis();
			T oldValue = value;
			if (oldValue instanceof Disposable) {
				((Disposable) oldValue).dispose();
			}
			try {
				value = computer.call();
				failure = null;
			} catch (Throwable e) {
				value = null;
				failure = e;
			}
		}
		if (failure != null) {
			throw ExceptionUtil.exception(failure);
		} else {
			return value;
		}
	}
	
	@Override
	public boolean isDisposed() {
		return computer == null;
	}

	private boolean shouldCompute() {
		if (lastComputed == null) {
			//Never computed
			return true;
		} else {
			//Computed before... should check expiration
			if (failure != null) {
				// cached result is a exception
				return expireExceptionsAfter != null && 
					System.currentTimeMillis() - lastComputed >= expireExceptionsAfter;
			} else {
				// cached result is a normal value
				return false; //for now normal values never expire.
			}
		}
	}

	public MemoizingDisposableSupplier<T> expireExceptions(Duration after) {
		this.expireExceptionsAfter = after.toMillis();
		return this;
	}
	
	@Override
	protected void finalize() throws Throwable {
		//TODO: consider removing this method when we have confidence we have no leaks that need cleaning up.
		//  Using finalizers is expensive so should be avoided. This is only here as a temporary fail-safe.
		//  Proper cleanup should be implemented and logger.error below should never be reached.
		if (!isDisposed()) {
			logger.error("Leaked Disposable detected: "+this);
			this.dispose();
		}
	}

}
