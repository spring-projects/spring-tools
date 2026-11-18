/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link SpringSymbolIndex#waitForProjects}, the per-project variant of waiting for the
 * index's work queue.
 *
 * @author Martin Lippert
 */
public class SpringSymbolIndexWaitForProjectsTest {

	private final CompletableFuture<Void> wholeQueue = new CompletableFuture<>();
	private final AtomicInteger wholeQueueWaits = new AtomicInteger();
	private final Supplier<CompletableFuture<Void>> waitForAll = () -> {
		wholeQueueWaits.incrementAndGet();
		return wholeQueue;
	};

	@Test
	void waitsForTheLatestTaskOfEachProjectOnly() {
		CompletableFuture<Void> demo = new CompletableFuture<>();
		CompletableFuture<Void> other = new CompletableFuture<>();
		CompletableFuture<Void> unrelated = new CompletableFuture<>();
		Map<String, CompletableFuture<Void>> latest = Map.of("demo", demo, "other", other, "unrelated", unrelated);

		CompletableFuture<Void> settled = SpringSymbolIndex.waitForProjects(latest, List.of("demo", "other"), waitForAll);

		demo.complete(null);
		assertFalse(settled.isDone());
		other.complete(null);
		// done although the unrelated project's work, queued as well, is not
		assertTrue(settled.isDone());
		assertEquals(0, wholeQueueWaits.get());
	}

	@Test
	void aFailedTaskCountsAsDone() {
		CompletableFuture<Void> demo = new CompletableFuture<>();

		CompletableFuture<Void> settled = SpringSymbolIndex.waitForProjects(Map.of("demo", demo), List.of("demo"), waitForAll);
		demo.completeExceptionally(new IllegalStateException("indexing failed"));

		assertTrue(settled.isDone());
		assertFalse(settled.isCompletedExceptionally());
	}

	@Test
	void waitsForTheWholeQueueForAProjectWithoutTask() {
		CompletableFuture<Void> demo = CompletableFuture.completedFuture(null);

		// e.g. a project that appeared just now, its initialization not queued yet
		CompletableFuture<Void> settled = SpringSymbolIndex.waitForProjects(Map.of("demo", demo), List.of("demo", "new"), waitForAll);

		assertSame(wholeQueue, settled);
		assertEquals(1, wholeQueueWaits.get());
	}

	@Test
	void noProjectsIsSettledRightAway() {
		assertTrue(SpringSymbolIndex.waitForProjects(Map.of(), List.of(), waitForAll).isDone());
		assertEquals(0, wholeQueueWaits.get());
	}

}
