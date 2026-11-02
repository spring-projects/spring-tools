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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

public class RetryingMemoizedSupplierTest {

	private final AtomicLong now = new AtomicLong(1000);

	@Test
	void valueIsComputedOnlyOnce() {
		AtomicInteger calls = new AtomicInteger();
		RetryingMemoizedSupplier<Object> s = new RetryingMemoizedSupplier<>(() -> {
			calls.incrementAndGet();
			return new Object();
		}, 100, now::get);

		Object first = s.get();
		assertSame(first, s.get());
		now.addAndGet(1_000_000);
		assertSame(first, s.get());
		assertEquals(1, calls.get());
	}

	@Test
	void failureIsRetriedOnlyAfterTheDelay() {
		AtomicInteger calls = new AtomicInteger();
		RetryingMemoizedSupplier<String> s = new RetryingMemoizedSupplier<>(
				() -> calls.incrementAndGet() == 1 ? null : "value", 100, now::get);

		assertNull(s.get());
		assertEquals(1, calls.get());

		now.addAndGet(99);
		assertNull(s.get()); // still within the delay: no new attempt
		assertEquals(1, calls.get());

		now.addAndGet(1);
		assertEquals("value", s.get());
		assertEquals(2, calls.get());

		now.addAndGet(1000);
		assertEquals("value", s.get()); // success is kept
		assertEquals(2, calls.get());
	}

	@Test
	void exceptionIsNotRemembered() {
		AtomicInteger calls = new AtomicInteger();
		RetryingMemoizedSupplier<String> s = new RetryingMemoizedSupplier<>(() -> {
			if (calls.incrementAndGet() == 1) {
				throw new IllegalStateException("boom");
			}
			return "value";
		}, 100, now::get);

		assertThrows(IllegalStateException.class, s::get);
		assertEquals("value", s.get());
		assertEquals(2, calls.get());
	}

	@Test
	void concurrentCallersShareOneComputation() throws Exception {
		int threads = 32;
		AtomicInteger calls = new AtomicInteger();
		RetryingMemoizedSupplier<Object> s = new RetryingMemoizedSupplier<>(() -> {
			calls.incrementAndGet();
			try {
				Thread.sleep(50); // make sure the other threads pile up while this one computes
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return new Object();
		}, 100, now::get);

		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			CountDownLatch go = new CountDownLatch(1);
			List<Future<Object>> results = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				results.add(pool.submit(() -> {
					go.await();
					return s.get();
				}));
			}
			go.countDown();
			Object first = results.get(0).get();
			for (Future<Object> r : results) {
				assertSame(first, r.get());
			}
			assertEquals(1, calls.get());
		} finally {
			pool.shutdownNow();
		}
	}

}
