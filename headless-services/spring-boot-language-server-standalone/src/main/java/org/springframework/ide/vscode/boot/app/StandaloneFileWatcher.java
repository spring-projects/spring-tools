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

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;
import org.springframework.ide.vscode.commons.util.FileChangeNotifier;

/**
 * Follows the project directory of the standalone, MCP-only language server on disk and feeds
 * file system changes into the same {@link FileChangeNotifier} an LSP client would drive through
 * {@code workspace/didChangeWatchedFiles}.
 *
 * <p>Without an LSP client nothing tells the server about edits made by shell commands, other
 * tools or git, so the index would only follow the {@code fileChanged}/{@code fileDeleted}/
 * {@code refreshWorkspace} MCP calls the client remembers to make. This watcher closes that gap:
 * every regular file below the project directory (minus build output and dot directories) is
 * reported as created, changed or deleted, and a build file that appears after startup triggers
 * project discovery, so a freshly generated project is indexed without a refresh call.
 *
 * <p>Events are coalesced per path and flushed after a short quiet period, so a {@code git
 * checkout} touching hundreds of files becomes one batch notification. When the platform cannot
 * watch the tree (for example the inotify watch limit is reached) the watcher logs the reason and
 * stops; the MCP notification tools keep working.
 *
 * <p>System properties: {@value #PROPERTY_ENABLED} ({@code true} by default; the watcher never
 * starts while the LSP transport is enabled, because the client watches files in that mode),
 * {@value #PROPERTY_DEBOUNCE_MS} (quiet period in milliseconds, 300 by default) and
 * {@value #PROPERTY_MAX_DIRECTORIES} (directories watched before the watcher gives up, 20000 by
 * default).
 *
 * @author Broadcom, Inc.
 */
public class StandaloneFileWatcher implements ApplicationListener<ContextRefreshedEvent>, DisposableBean {

	static final String PROPERTY_ENABLED = "spring.boot.ls.project.watch";
	static final String PROPERTY_DEBOUNCE_MS = "spring.boot.ls.project.watch.debounce-ms";
	static final String PROPERTY_MAX_DIRECTORIES = "spring.boot.ls.project.watch.max-directories";
	static final String PROPERTY_LANGUAGE_SERVER_ENABLED = "languageserver.enabled";

	private static final Logger log = LoggerFactory.getLogger(StandaloneFileWatcher.class);

	private static final Set<String> BUILD_FILES = Set.of("pom.xml", "build.gradle", "build.gradle.kts");

	private enum Change { CREATED, CHANGED, DELETED }

	private final Environment environment;
	private final FileChangeNotifier fileChangeNotifier;
	private final LegacyJavaProjectsService projectsService;

	private final Map<WatchKey, Path> watchedDirectories = new HashMap<>();
	private final Map<Path, Change> pending = new LinkedHashMap<>();
	private final AtomicBoolean running = new AtomicBoolean();

	private WatchService watchService;
	private ScheduledExecutorService scheduler;
	private ScheduledFuture<?> flush;
	private Thread thread;
	private long debounceMillis;
	private int maxDirectories;
	private boolean discoverProjectsOnFlush;

	public StandaloneFileWatcher(Environment environment, FileChangeNotifier fileChangeNotifier, LegacyJavaProjectsService projectsService) {
		this.environment = environment;
		this.fileChangeNotifier = fileChangeNotifier;
		this.projectsService = projectsService;
	}

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		String projectDir = environment.getProperty(LegacyJavaProjectsService.PROPERTY_PROJECT_DIR);
		if (projectDir == null || projectDir.isEmpty()) {
			log.info("file watcher not started: {} is not set", LegacyJavaProjectsService.PROPERTY_PROJECT_DIR);
			return;
		}
		if (environment.getProperty(PROPERTY_LANGUAGE_SERVER_ENABLED, Boolean.class, true)) {
			log.info("file watcher not started: the LSP transport is enabled and the client watches files");
			return;
		}
		if (!environment.getProperty(PROPERTY_ENABLED, Boolean.class, true)) {
			log.info("file watcher disabled via -D{}=false", PROPERTY_ENABLED);
			return;
		}
		start(Path.of(projectDir).toAbsolutePath().normalize());
	}

	synchronized void start(Path root) {
		if (running.get() || !Files.isDirectory(root)) {
			return;
		}
		debounceMillis = environment.getProperty(PROPERTY_DEBOUNCE_MS, Long.class, 300L);
		maxDirectories = environment.getProperty(PROPERTY_MAX_DIRECTORIES, Integer.class, 20000);
		try {
			watchService = root.getFileSystem().newWatchService();
			int registered = registerTree(root);
			if (registered < 0) {
				log.warn("file watcher not started: more than {} directories below {}; raise -D{} or rely on the fileChanged/refreshWorkspace tools",
						maxDirectories, root, PROPERTY_MAX_DIRECTORIES);
				closeQuietly();
				return;
			}
			scheduler = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "spring-tools-file-watcher-flush"));
			running.set(true);
			thread = daemon(this::loop, "spring-tools-file-watcher");
			thread.start();
			log.info("file watcher started for {} ({} directories, {} ms quiet period)", root, registered, debounceMillis);
		} catch (IOException | UnsupportedOperationException e) {
			log.warn("file watcher not started for {}: {}; changes must be reported via the fileChanged/fileDeleted/refreshWorkspace tools",
					root, e.toString());
			closeQuietly();
		}
	}

	/** Registers {@code root} and its subdirectories; returns the count, or -1 when the cap was hit. */
	private int registerTree(Path root) throws IOException {
		int[] count = { 0 };
		IOException[] failure = { null };
		Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				if (!dir.equals(root) && isIgnoredDirectory(dir)) {
					return FileVisitResult.SKIP_SUBTREE;
				}
				if (count[0] >= maxDirectories) {
					count[0] = -1;
					return FileVisitResult.TERMINATE;
				}
				try {
					register(dir);
					count[0]++;
					return FileVisitResult.CONTINUE;
				} catch (IOException e) {
					failure[0] = e;
					return FileVisitResult.TERMINATE;
				}
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException exc) {
				return FileVisitResult.CONTINUE;
			}
		});
		if (failure[0] != null) {
			throw failure[0];
		}
		return count[0];
	}

	private void register(Path dir) throws IOException {
		WatchKey key = dir.register(watchService, new WatchEvent.Kind<?>[] {
				StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE },
				sensitivityModifiers());
		synchronized (watchedDirectories) {
			watchedDirectories.put(key, dir);
		}
	}

	/** On JDKs whose watch service polls (macOS), ask for the 2-second poll interval instead of the 10-second default. */
	private static WatchEvent.Modifier[] sensitivityModifiers() {
		try {
			Class<?> modifiers = Class.forName("com.sun.nio.file.SensitivityWatchEventModifier");
			return new WatchEvent.Modifier[] { (WatchEvent.Modifier) modifiers.getField("HIGH").get(null) };
		} catch (ReflectiveOperationException | RuntimeException e) {
			return new WatchEvent.Modifier[0];
		}
	}

	static boolean isIgnoredDirectory(Path dir) {
		Path name = dir.getFileName();
		if (name == null) {
			return false;
		}
		String fileName = name.toString();
		return fileName.startsWith(".") || LegacyJavaProjectsService.IGNORED_DIRECTORIES.contains(fileName);
	}

	private void loop() {
		while (running.get()) {
			WatchKey key;
			try {
				key = watchService.take();
			} catch (InterruptedException | ClosedWatchServiceException e) {
				return;
			}
			Path dir;
			synchronized (watchedDirectories) {
				dir = watchedDirectories.get(key);
			}
			for (WatchEvent<?> event : key.pollEvents()) {
				if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
					log.info("file watcher lost events for {}; scheduling a full refresh", dir);
					scheduleFullRefresh();
					continue;
				}
				if (dir == null) {
					continue;
				}
				Path path = dir.resolve((Path) event.context());
				if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
					created(path);
				} else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
					enqueue(path, Change.DELETED);
				} else if (Files.isRegularFile(path)) {
					enqueue(path, Change.CHANGED);
				}
			}
			if (!key.reset()) {
				synchronized (watchedDirectories) {
					watchedDirectories.remove(key);
				}
			}
		}
	}

	private void created(Path path) {
		if (Files.isDirectory(path)) {
			if (isIgnoredDirectory(path)) {
				return;
			}
			// A moved or extracted directory arrives as one event: watch it and report its files.
			try {
				Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
					@Override
					public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
						if (!dir.equals(path) && isIgnoredDirectory(dir)) {
							return FileVisitResult.SKIP_SUBTREE;
						}
						register(dir);
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
						enqueue(file, Change.CREATED);
						return FileVisitResult.CONTINUE;
					}

					@Override
					public FileVisitResult visitFileFailed(Path file, IOException exc) {
						return FileVisitResult.CONTINUE;
					}
				});
			} catch (IOException e) {
				log.warn("file watcher could not watch new directory {}: {}", path, e.toString());
			}
		} else {
			enqueue(path, Change.CREATED);
		}
	}

	private void enqueue(Path path, Change change) {
		synchronized (pending) {
			Change previous = pending.get(path);
			Change merged = change;
			if (previous == Change.CREATED && change == Change.CHANGED) {
				merged = Change.CREATED;
			} else if (previous == Change.CREATED && change == Change.DELETED) {
				pending.remove(path);
				merged = null;
			} else if (previous == Change.DELETED && change == Change.CREATED) {
				merged = Change.CHANGED;
			}
			if (merged != null) {
				pending.put(path, merged);
				if (merged == Change.CREATED && BUILD_FILES.contains(path.getFileName().toString())) {
					discoverProjectsOnFlush = true;
				}
			}
			scheduleFlush();
		}
	}

	private void scheduleFlush() {
		if (flush != null) {
			flush.cancel(false);
		}
		flush = scheduler.schedule(this::flushPending, debounceMillis, TimeUnit.MILLISECONDS);
	}

	private void scheduleFullRefresh() {
		scheduler.schedule(() -> {
			synchronized (pending) {
				pending.clear();
				discoverProjectsOnFlush = false;
			}
			projectsService.notifyProjectsChanged(true);
		}, debounceMillis, TimeUnit.MILLISECONDS);
	}

	void flushPending() {
		List<String> created = new ArrayList<>();
		List<String> changed = new ArrayList<>();
		List<String> deleted = new ArrayList<>();
		boolean discover;
		synchronized (pending) {
			for (Map.Entry<Path, Change> entry : pending.entrySet()) {
				String uri = entry.getKey().toUri().toASCIIString();
				switch (entry.getValue()) {
				case CREATED -> created.add(uri);
				case CHANGED -> changed.add(uri);
				case DELETED -> deleted.add(uri);
				}
			}
			pending.clear();
			discover = discoverProjectsOnFlush;
			discoverProjectsOnFlush = false;
		}
		if (created.isEmpty() && changed.isEmpty() && deleted.isEmpty()) {
			return;
		}
		log.info("file watcher: {} created, {} changed, {} deleted", created.size(), changed.size(), deleted.size());
		try {
			if (discover) {
				projectsService.discoverProjects();
			}
			if (!created.isEmpty()) {
				fileChangeNotifier.notifyFilesCreated(created.toArray(String[]::new));
			}
			if (!changed.isEmpty()) {
				fileChangeNotifier.notifyFilesChanged(changed.toArray(String[]::new));
			}
			if (!deleted.isEmpty()) {
				fileChangeNotifier.notifyFilesDeleted(deleted.toArray(String[]::new));
			}
		} catch (RuntimeException e) {
			log.error("file watcher failed to deliver file change notifications", e);
		}
	}

	boolean isRunning() {
		return running.get();
	}

	@Override
	public void destroy() {
		closeQuietly();
	}

	private synchronized void closeQuietly() {
		running.set(false);
		if (thread != null) {
			thread.interrupt();
			thread = null;
		}
		if (scheduler != null) {
			scheduler.shutdownNow();
			scheduler = null;
		}
		if (watchService != null) {
			try {
				watchService.close();
			} catch (IOException e) {
				// nothing left to release
			}
			watchService = null;
		}
		synchronized (watchedDirectories) {
			watchedDirectories.clear();
		}
	}

	private static Thread daemon(Runnable runnable, String name) {
		Thread t = new Thread(runnable, name);
		t.setDaemon(true);
		return t;
	}

}
