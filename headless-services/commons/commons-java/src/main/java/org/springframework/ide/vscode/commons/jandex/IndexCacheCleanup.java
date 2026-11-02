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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the on-disk Jandex index cache ({@code ~/.sts-jandex}) from growing forever. Removes:
 * <ul>
 * <li>temp files left behind by an interrupted index write (only once they are old enough that no other process can
 * still be writing them),</li>
 * <li>indexes of jars that have since been rebuilt (a newer index for the same jar path exists, the old one can never
 * be used again since the jar's modification time is part of the index file name),</li>
 * <li>indexes not used for a long time (deleted jars, old library versions, uninstalled JDKs) - loading an index
 * refreshes its file modification time, see {@link #markUsed(File)},</li>
 * <li>least recently used indexes if the cache exceeds its size limit; indexes used recently are never removed.</li>
 * </ul>
 * The clean-up runs in the background once per process and folder, and at most once per day across all processes
 * sharing the folder. Deleting an index file is always safe: whoever needs it again simply re-creates it.
 * <p>
 * System properties: {@code jandex.cache.cleanup} (default {@code true}), {@code jandex.cache.maxUnusedDays} (default
 * 60), {@code jandex.cache.maxSizeMB} (default 2048).
 *
 * @author Martin Lippert
 */
final class IndexCacheCleanup {

	private static final Logger log = LoggerFactory.getLogger(IndexCacheCleanup.class);

	static final String MARKER = ".last-cleanup";

	static final long CLEANUP_INTERVAL_MILLIS = TimeUnit.DAYS.toMillis(1);

	/** how stale a modification time may get before loading an index refreshes it */
	static final long MARK_USED_GRANULARITY_MILLIS = TimeUnit.DAYS.toMillis(1);

	/** {@code <jar name>-<base32 SHA-1 of the jar path>-<jar modification time>.jdx}, see {@code JandexClasspath} */
	private static final Pattern JAR_INDEX = Pattern.compile("(.+-[A-Z2-7]{32})-(\\d+)\\.jdx");

	private static final Set<String> scheduled = ConcurrentHashMap.newKeySet();

	record Settings(long maxUnusedMillis, long maxTotalBytes, long tmpMaxAgeMillis, long recentlyUsedMillis) {

		static Settings fromSystemProperties() {
			return new Settings(
					TimeUnit.DAYS.toMillis(Long.getLong("jandex.cache.maxUnusedDays", 60)),
					Long.getLong("jandex.cache.maxSizeMB", 2048) << 20,
					TimeUnit.HOURS.toMillis(1),
					TimeUnit.HOURS.toMillis(1));
		}
	}

	record Result(int tmpFiles, int superseded, int unused, int overSizeLimit, long bytesFreed) {

		int total() {
			return tmpFiles + superseded + unused + overSizeLimit;
		}
	}

	private IndexCacheCleanup() {
	}

	/**
	 * Starts a background clean-up of the given cache folder, unless one has been started for it already in this
	 * process or clean-up is disabled.
	 */
	static void scheduleOnce(File folder) {
		if (!Boolean.parseBoolean(System.getProperty("jandex.cache.cleanup", "true"))) {
			return;
		}
		if (!scheduled.add(folder.getAbsolutePath())) {
			return;
		}
		Thread t = new Thread(() -> {
			try {
				cleanIfDue(folder, System.currentTimeMillis(), Settings.fromSystemProperties());
			} catch (Throwable e) {
				log.warn("Clean-up of Jandex index cache '" + folder + "' failed", e);
			}
		}, "jandex-cache-cleanup");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}

	/**
	 * Cleans the folder unless that has been done within the last day (by any process).
	 *
	 * @return the result, or {@code null} if the clean-up was not due
	 */
	static Result cleanIfDue(File folder, long now, Settings settings) throws IOException {
		File marker = new File(folder, MARKER);
		if (marker.isFile() && now - marker.lastModified() < CLEANUP_INTERVAL_MILLIS) {
			return null;
		}
		// claim this round first, so that other processes starting at the same time skip it
		if (!marker.isFile()) {
			Files.createDirectories(folder.toPath());
			try {
				Files.createFile(marker.toPath());
			} catch (IOException e) {
				// created concurrently, fine
			}
		}
		marker.setLastModified(now);

		Result result = clean(folder, now, settings);
		if (result.total() > 0) {
			log.info("Jandex index cache '{}': removed {} temp, {} superseded, {} unused, {} over size limit files ({} MB)",
					folder, result.tmpFiles(), result.superseded(), result.unused(), result.overSizeLimit(),
					result.bytesFreed() >> 20);
		}
		return result;
	}

	static Result clean(File folder, long now, Settings settings) throws IOException {
		Path root = folder.toPath();
		List<Path> tmpFiles = new ArrayList<>();
		List<Path> indexFiles = new ArrayList<>();
		List<Path> dirs = new ArrayList<>();
		if (!Files.isDirectory(root)) {
			return new Result(0, 0, 0, 0, 0);
		}
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				if (!dir.equals(root)) {
					dirs.add(dir);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				String name = file.getFileName().toString();
				if (name.endsWith(".tmp")) {
					tmpFiles.add(file);
				} else if (name.endsWith(".jdx")) {
					indexFiles.add(file);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				return FileVisitResult.CONTINUE; // e.g. deleted concurrently
			}
		});

		long[] freed = new long[1];

		int tmp = 0;
		for (Path f : tmpFiles) {
			if (now - lastModified(f) > settings.tmpMaxAgeMillis() && delete(f, freed)) {
				tmp++;
			}
		}

		// superseded jar indexes: keep only the one for the latest jar modification time per jar path
		int superseded = 0;
		Map<String, List<Path>> byJar = new HashMap<>();
		for (Path f : indexFiles) {
			Matcher m = JAR_INDEX.matcher(f.getFileName().toString());
			if (root.equals(f.getParent()) && m.matches()) {
				byJar.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(f);
			}
		}
		List<Path> remaining = new ArrayList<>(indexFiles);
		for (List<Path> versions : byJar.values()) {
			if (versions.size() > 1) {
				versions.sort(Comparator.comparingLong(IndexCacheCleanup::jarModificationTime));
				for (Path old : versions.subList(0, versions.size() - 1)) {
					remaining.remove(old);
					if (delete(old, freed)) {
						superseded++;
					}
				}
			}
		}

		int unused = 0;
		List<Path> kept = new ArrayList<>();
		for (Path f : remaining) {
			if (now - lastModified(f) > settings.maxUnusedMillis()) {
				if (delete(f, freed)) {
					unused++;
				}
			} else {
				kept.add(f);
			}
		}

		// size limit: least recently used first, but never what is in use right now
		int overSize = 0;
		long total = kept.stream().mapToLong(IndexCacheCleanup::size).sum();
		if (total > settings.maxTotalBytes()) {
			kept.sort(Comparator.comparingLong(IndexCacheCleanup::lastModified));
			for (Path f : kept) {
				if (total <= settings.maxTotalBytes() || now - lastModified(f) <= settings.recentlyUsedMillis()) {
					break;
				}
				long size = size(f);
				if (delete(f, freed)) {
					total -= size;
					overSize++;
				}
			}
		}

		// folders of JDKs whose indexes are all gone
		dirs.sort(Comparator.comparingInt((Path d) -> d.getNameCount()).reversed());
		for (Path d : dirs) {
			try (var entries = Files.list(d)) {
				if (entries.findAny().isEmpty()) {
					Files.deleteIfExists(d);
				}
			} catch (IOException e) {
				// not empty anymore, in use, ... - leave it
			}
		}

		return new Result(tmp, superseded, unused, overSize, freed[0]);
	}

	/**
	 * Records that an index file has been used, so that it doesn't get removed as unused. Touches the file at most
	 * once per {@link #MARK_USED_GRANULARITY_MILLIS} to keep the cost of loading indexes low.
	 */
	static void markUsed(File indexFile) {
		long now = System.currentTimeMillis();
		if (now - indexFile.lastModified() > MARK_USED_GRANULARITY_MILLIS) {
			indexFile.setLastModified(now);
		}
	}

	private static long jarModificationTime(Path indexFile) {
		Matcher m = JAR_INDEX.matcher(indexFile.getFileName().toString());
		return m.matches() ? Long.parseLong(m.group(2)) : 0;
	}

	private static long lastModified(Path f) {
		return f.toFile().lastModified();
	}

	private static long size(Path f) {
		return f.toFile().length();
	}

	private static boolean delete(Path f, long[] freed) {
		long size = size(f);
		try {
			if (Files.deleteIfExists(f)) {
				freed[0] += size;
				return true;
			}
		} catch (IOException e) {
			// e.g. on Windows a file can't be deleted while another process reads it - next time
			log.debug("Could not delete '" + f + "'", e);
		}
		return false;
	}

}
