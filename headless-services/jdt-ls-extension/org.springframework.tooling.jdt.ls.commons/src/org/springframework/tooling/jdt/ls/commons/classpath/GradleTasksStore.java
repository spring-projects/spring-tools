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
package org.springframework.tooling.jdt.ls.commons.classpath;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * The names of the tasks of Gradle projects, as last seen when Buildship synchronized the project.
 * <p>
 * Buildship keeps the Gradle model of a build in memory only after a synchronization and does not save the tasks,
 * so right after the IDE is started there is nothing to ask without running Gradle. The tasks are remembered here,
 * saved per project in the state area of the bundle (never in the project), and replaced by the next synchronization.
 *
 * @author Alex Boyko
 */
class GradleTasksStore {

	public static final GradleTasksStore INSTANCE = new GradleTasksStore(defaultDirectory());

	private final Path directory;
	private final Map<String, Set<String>> tasks = new ConcurrentHashMap<>();
	private final Set<String> loaded = ConcurrentHashMap.newKeySet();
	private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

	/**
	 * @param directory where the tasks are saved, <code>null</code> to only keep them in memory
	 */
	GradleTasksStore(Path directory) {
		this.directory = directory;
	}

	private static Path defaultDirectory() {
		try {
			Bundle bundle = FrameworkUtil.getBundle(GradleTasksStore.class);
			if (bundle != null) {
				return Platform.getStateLocation(bundle).append("gradle-tasks").toFile().toPath();
			}
		} catch (Exception e) {
			// no state location, e.g. the workspace is not set up. Keep the tasks in memory.
		}
		return null;
	}

	/**
	 * @return the tasks of the project or <code>null</code> if they are not known
	 */
	Set<String> get(String project) {
		if (loaded.add(project)) {
			Set<String> saved = read(project);
			if (saved != null) {
				tasks.putIfAbsent(project, saved);
			}
		}
		return tasks.get(project);
	}

	/**
	 * Remembers the tasks and tells the listeners if they are not the same as before.
	 *
	 * @return whether the tasks changed
	 */
	boolean put(String project, Set<String> newTasks) {
		Set<String> copy = Set.copyOf(newTasks);
		Set<String> old = get(project);
		if (copy.equals(old)) {
			return false;
		}
		tasks.put(project, copy);
		write(project, copy);
		listeners.forEach(l -> l.accept(project));
		return true;
	}

	void remove(String project) {
		tasks.remove(project);
		loaded.add(project);
		if (directory != null) {
			try {
				Files.deleteIfExists(file(project));
			} catch (IOException e) {
				// ignore, it would be replaced or not used
			}
		}
	}

	/**
	 * @param listener gets the name of the project whose tasks changed
	 */
	void addListener(Consumer<String> listener) {
		listeners.add(listener);
	}

	void removeListener(Consumer<String> listener) {
		listeners.remove(listener);
	}

	private Path file(String project) {
		return directory.resolve(URLEncoder.encode(project, StandardCharsets.UTF_8) + ".txt");
	}

	private Set<String> read(String project) {
		if (directory != null) {
			try {
				Path file = file(project);
				if (Files.isRegularFile(file)) {
					return Set.copyOf(Files.readAllLines(file, StandardCharsets.UTF_8));
				}
			} catch (IOException | RuntimeException e) {
				// not usable, the next synchronization writes it again
			}
		}
		return null;
	}

	private void write(String project, Set<String> projectTasks) {
		if (directory != null) {
			try {
				Files.createDirectories(directory);
				Files.write(file(project), new TreeSet<>(projectTasks), StandardCharsets.UTF_8);
			} catch (IOException e) {
				// remembered in memory anyway
			}
		}
	}

}
