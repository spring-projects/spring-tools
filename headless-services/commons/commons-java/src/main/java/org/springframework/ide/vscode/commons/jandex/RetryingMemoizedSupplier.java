/*******************************************************************************
 * Copyright (c) 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.jandex;

import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

import com.google.common.base.Supplier;

/**
 * Memoizing supplier that, unlike {@code Suppliers.memoize()}, does not remember a failure ({@code null} result)
 * forever: after a failed attempt, further calls return {@code null} right away until the retry delay has passed,
 * then the delegate is asked again. The delay keeps a permanently broken classpath entry from being re-indexed on
 * every type lookup.
 * <p>
 * Thread safe: only one thread at a time calls the delegate, other threads asking for the same value wait for it.
 * Once a value is there, reads are lock free.
 *
 * @author Martin Lippert
 */
final class RetryingMemoizedSupplier<T> implements Supplier<T> {

	static final long DEFAULT_RETRY_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(60);

	private final Supplier<T> delegate;
	private final long retryDelayMillis;
	private final LongSupplier clock;

	private volatile T value;

	// guarded by this
	private boolean failed;
	private long failedAt;

	RetryingMemoizedSupplier(Supplier<T> delegate) {
		this(delegate, DEFAULT_RETRY_DELAY_MILLIS, System::currentTimeMillis);
	}

	RetryingMemoizedSupplier(Supplier<T> delegate, long retryDelayMillis, LongSupplier clock) {
		this.delegate = delegate;
		this.retryDelayMillis = retryDelayMillis;
		this.clock = clock;
	}

	@Override
	public T get() {
		T v = value;
		if (v != null) {
			return v;
		}
		synchronized (this) {
			v = value;
			if (v != null) {
				return v;
			}
			if (failed && clock.getAsLong() - failedAt < retryDelayMillis) {
				return null;
			}
			v = delegate.get();
			if (v == null) {
				failed = true;
				failedAt = clock.getAsLong();
			} else {
				value = v;
			}
			return v;
		}
	}

}
