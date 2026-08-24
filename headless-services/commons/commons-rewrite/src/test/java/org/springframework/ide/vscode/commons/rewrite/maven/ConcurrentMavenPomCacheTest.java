/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.rewrite.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.openrewrite.maven.cache.InMemoryMavenPomCache;
import org.openrewrite.maven.tree.MavenRepository;

public class ConcurrentMavenPomCacheTest {

	@Test
	void delegatesNormallyWhenNoConcurrency() throws Exception {
		ConcurrentMavenPomCache cache = new ConcurrentMavenPomCache(new InMemoryMavenPomCache());

		assertNull(cache.getNormalizedRepository(MavenRepository.MAVEN_CENTRAL));

		cache.putNormalizedRepository(MavenRepository.MAVEN_CENTRAL, MavenRepository.MAVEN_LOCAL_DEFAULT);

		assertEquals(Optional.of(MavenRepository.MAVEN_LOCAL_DEFAULT), cache.getNormalizedRepository(MavenRepository.MAVEN_CENTRAL));
	}

	@Test
	void concurrentMissesOnTheSameKeyCoalesceIntoOneFetch() throws Exception {
		ConcurrentMavenPomCache cache = new ConcurrentMavenPomCache(new InMemoryMavenPomCache());
		AtomicInteger fetchCount = new AtomicInteger();
		CountDownLatch winnerStartedFetching = new CountDownLatch(1);
		CountDownLatch releaseWinner = new CountDownLatch(1);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<Optional<MavenRepository>> winner = pool.submit(() -> {
				Optional<MavenRepository> cached = cache.getNormalizedRepository(MavenRepository.MAVEN_CENTRAL);
				assertNull(cached); // the winner genuinely misses and must fetch
				fetchCount.incrementAndGet();
				winnerStartedFetching.countDown();
				releaseWinner.await(5, TimeUnit.SECONDS); // simulate a slow fetch
				cache.putNormalizedRepository(MavenRepository.MAVEN_CENTRAL, MavenRepository.MAVEN_LOCAL_DEFAULT);
				return Optional.of(MavenRepository.MAVEN_LOCAL_DEFAULT);
			});

			winnerStartedFetching.await(5, TimeUnit.SECONDS);
			Future<Optional<MavenRepository>> waiter = pool.submit(() -> cache.getNormalizedRepository(MavenRepository.MAVEN_CENTRAL));

			Thread.sleep(200); // let the waiter start blocking on the winner's future
			releaseWinner.countDown();

			assertEquals(Optional.of(MavenRepository.MAVEN_LOCAL_DEFAULT), winner.get(5, TimeUnit.SECONDS));
			assertEquals(Optional.of(MavenRepository.MAVEN_LOCAL_DEFAULT), waiter.get(5, TimeUnit.SECONDS));
			assertEquals(1, fetchCount.get()); // waiter never fetched itself
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void waiterFallsBackToReportingAMissAfterTimeout() throws Exception {
		ConcurrentMavenPomCache cache = new ConcurrentMavenPomCache(new InMemoryMavenPomCache(), Duration.ofMillis(200));
		CountDownLatch winnerStartedFetching = new CountDownLatch(1);

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			// Claims the key, then never calls putNormalizedRepository - simulates a failed fetch.
			Future<?> winner = pool.submit(() -> {
				cache.getNormalizedRepository(MavenRepository.MAVEN_CENTRAL);
				winnerStartedFetching.countDown();
			});
			winner.get(5, TimeUnit.SECONDS);
			winnerStartedFetching.await(5, TimeUnit.SECONDS);

			Future<Optional<MavenRepository>> waiter = pool.submit(() -> cache.getNormalizedRepository(MavenRepository.MAVEN_CENTRAL));

			assertNull(waiter.get(5, TimeUnit.SECONDS));
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void abandonedEntryIsCleanedUpSoLaterCallsDontKeepBlocking() throws Exception {
		Duration shortTimeout = Duration.ofMillis(150);
		ConcurrentMavenPomCache cache = new ConcurrentMavenPomCache(new InMemoryMavenPomCache(), shortTimeout);

		// Claims the key, never completes it - nothing will resolve this entry on its own.
		assertNull(cache.getNormalizedRepository(MavenRepository.MAVEN_CENTRAL));

		// A caller arriving while the claim is still live pays the timeout once.
		long start = System.nanoTime();
		assertNull(cache.getNormalizedRepository(MavenRepository.MAVEN_CENTRAL));
		long waitedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
		assertTrue(waitedMs >= shortTimeout.toMillis() - 50,
				"expected to wait out roughly the full timeout at least once, only waited " + waitedMs + "ms");

		Thread.sleep(shortTimeout.toMillis() + 200); // let the self-expiry callback remove the entry

		// A call arriving after self-expiry must return immediately, not pay the timeout
		// again - proving the entry was cleaned up rather than poisoned forever.
		start = System.nanoTime();
		assertNull(cache.getNormalizedRepository(MavenRepository.MAVEN_CENTRAL));
		long immediateMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
		assertTrue(immediateMs < shortTimeout.toMillis(),
				"expected an immediate fresh miss, not another wait; took " + immediateMs + "ms");
	}

	@Test
	void fileSchemeLookupsBypassCoalescingEntirely() throws Exception {
		// A deliberately generous timeout - if a file-scheme lookup were (wrongly) coalesced,
		// the assertion below would still catch the block well before this elapses.
		ConcurrentMavenPomCache cache = new ConcurrentMavenPomCache(new InMemoryMavenPomCache(), Duration.ofSeconds(30));

		// MAVEN_LOCAL_DEFAULT is a file:// repository - claim it, then never complete it,
		// simulating MavenPomDownloader's real behavior when the local file isn't there.
		assertNull(cache.getNormalizedRepository(MavenRepository.MAVEN_LOCAL_DEFAULT));

		// A second caller for the same key must return immediately, not block at all.
		ExecutorService pool = Executors.newFixedThreadPool(1);
		try {
			long start = System.nanoTime();
			Future<Optional<MavenRepository>> second = pool.submit(() -> cache.getNormalizedRepository(MavenRepository.MAVEN_LOCAL_DEFAULT));
			assertNull(second.get(2, TimeUnit.SECONDS));
			long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
			assertTrue(elapsedMs < 1000, "expected an immediate return, took " + elapsedMs + "ms");
		} finally {
			pool.shutdownNow();
		}
	}

}
