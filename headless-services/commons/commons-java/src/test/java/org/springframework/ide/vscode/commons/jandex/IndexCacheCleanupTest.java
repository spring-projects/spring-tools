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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IndexCacheCleanupTest {

	private static final long HOUR = TimeUnit.HOURS.toMillis(1);
	private static final long DAY = TimeUnit.DAYS.toMillis(1);

	/** base32 SHA-1 of a jar path, as used in jar index file names */
	private static final String DIGEST_A = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
	private static final String DIGEST_B = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB";

	private static final IndexCacheCleanup.Settings SETTINGS = new IndexCacheCleanup.Settings(60 * DAY, 1L << 30, HOUR, HOUR);

	@TempDir
	File cache;

	private final long now = System.currentTimeMillis();

	@Test
	void removesOnlyTempFilesThatNoOneCanStillBeWriting() throws Exception {
		Path abandoned = file("lib.jar-" + DIGEST_A + "-1.jdx-123.tmp", 10, now - 2 * HOUR);
		Path beingWritten = file("lib.jar-" + DIGEST_A + "-1.jdx-456.tmp", 10, now - 1000);

		IndexCacheCleanup.Result r = IndexCacheCleanup.clean(cache, now, SETTINGS);

		assertEquals(1, r.tmpFiles());
		assertFalse(Files.exists(abandoned));
		assertTrue(Files.exists(beingWritten));
	}

	@Test
	void removesIndexesOfJarsThatHaveBeenRebuilt() throws Exception {
		Path v1 = file("lib-1.0.jar-" + DIGEST_A + "-1000.jdx", 10, now);
		Path v2 = file("lib-1.0.jar-" + DIGEST_A + "-3000.jdx", 10, now);
		Path v3 = file("lib-1.0.jar-" + DIGEST_A + "-20000.jdx", 10, now); // numerically, not lexically, newest
		Path otherPathSameName = file("lib-1.0.jar-" + DIGEST_B + "-1000.jdx", 10, now);

		IndexCacheCleanup.Result r = IndexCacheCleanup.clean(cache, now, SETTINGS);

		assertEquals(2, r.superseded());
		assertFalse(Files.exists(v1));
		assertFalse(Files.exists(v2));
		assertTrue(Files.exists(v3));
		assertTrue(Files.exists(otherPathSameName));
	}

	@Test
	void removesIndexesUnusedForTooLongIncludingThoseOfUninstalledJdks() throws Exception {
		Path unused = file("gone.jar-" + DIGEST_A + "-1.jdx", 10, now - 61 * DAY);
		Path used = file("here.jar-" + DIGEST_A + "-1.jdx", 10, now - 59 * DAY);
		Path oldJdkModule = file(DIGEST_A + "/jrt-fs.jar.jdx/java.base.jdx", 10, now - 61 * DAY);
		Path currentJdkModule = file(DIGEST_B + "/jrt-fs.jar.jdx/java.base.jdx", 10, now - DAY);

		IndexCacheCleanup.Result r = IndexCacheCleanup.clean(cache, now, SETTINGS);

		assertEquals(2, r.unused());
		assertEquals(20, r.bytesFreed());
		assertFalse(Files.exists(unused));
		assertTrue(Files.exists(used));
		assertFalse(Files.exists(oldJdkModule));
		assertFalse(Files.exists(cache.toPath().resolve(DIGEST_A)), "empty JDK folder should be gone");
		assertTrue(Files.exists(currentJdkModule));
	}

	@Test
	void sizeLimitRemovesLeastRecentlyUsedButNeverWhatIsInUse() throws Exception {
		IndexCacheCleanup.Settings small = new IndexCacheCleanup.Settings(60 * DAY, 250, HOUR, HOUR);
		Path oldest = file("a.jar-" + DIGEST_A + "-1.jdx", 100, now - 3 * DAY);
		Path older = file("b.jar-" + DIGEST_A + "-1.jdx", 100, now - 2 * DAY);
		Path recent = file("c.jar-" + DIGEST_A + "-1.jdx", 100, now - DAY);

		IndexCacheCleanup.Result r = IndexCacheCleanup.clean(cache, now, small);
		assertEquals(1, r.overSizeLimit());
		assertFalse(Files.exists(oldest));
		assertTrue(Files.exists(older));
		assertTrue(Files.exists(recent));

		// everything in use right now: the limit is exceeded, but nothing may go
		IndexCacheCleanup.Settings tiny = new IndexCacheCleanup.Settings(60 * DAY, 10, HOUR, HOUR);
		older.toFile().setLastModified(now);
		recent.toFile().setLastModified(now);
		assertEquals(0, IndexCacheCleanup.clean(cache, now, tiny).total());
		assertTrue(Files.exists(older));
		assertTrue(Files.exists(recent));
	}

	@Test
	void runsAtMostOncePerDayAcrossProcesses() throws Exception {
		Path unused = file("gone.jar-" + DIGEST_A + "-1.jdx", 10, now - 61 * DAY);

		IndexCacheCleanup.Result first = IndexCacheCleanup.cleanIfDue(cache, now, SETTINGS);
		assertNotNull(first);
		assertEquals(1, first.unused());
		assertTrue(new File(cache, IndexCacheCleanup.MARKER).isFile());

		file("gone2.jar-" + DIGEST_A + "-1.jdx", 10, now - 61 * DAY);
		assertNull(IndexCacheCleanup.cleanIfDue(cache, now + 23 * HOUR, SETTINGS), "not due yet");
		assertNotNull(IndexCacheCleanup.cleanIfDue(cache, now + 25 * HOUR, SETTINGS));
		assertFalse(Files.exists(unused));
	}

	@Test
	void leavesUnrelatedFilesAlone() throws Exception {
		Path other = file("notes.txt", 10, now - 365 * DAY);
		IndexCacheCleanup.clean(cache, now, SETTINGS);
		assertTrue(Files.exists(other));
	}

	@Test
	void markUsedRefreshesStaleFilesOnly() throws Exception {
		Path stale = file("a.jar-" + DIGEST_A + "-1.jdx", 10, now - 10 * DAY);
		Path fresh = file("b.jar-" + DIGEST_A + "-1.jdx", 10, now - HOUR);

		IndexCacheCleanup.markUsed(stale.toFile());
		IndexCacheCleanup.markUsed(fresh.toFile());

		assertTrue(stale.toFile().lastModified() >= now - 1000);
		assertEquals(now - HOUR, fresh.toFile().lastModified(), 1000, "no write needed for recently used files");
	}

	private Path file(String relativePath, int size, long lastModified) throws IOException {
		Path f = cache.toPath().resolve(relativePath);
		Files.createDirectories(f.getParent());
		Files.write(f, new byte[size]);
		assertTrue(f.toFile().setLastModified(lastModified));
		return f;
	}

}
