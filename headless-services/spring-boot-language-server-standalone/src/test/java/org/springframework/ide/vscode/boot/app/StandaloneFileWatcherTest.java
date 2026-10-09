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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.ide.vscode.commons.util.FileChangeNotifier;

/**
 * @author Broadcom, Inc.
 */
class StandaloneFileWatcherTest {

	// Generous for the polling watch service on macOS (2 s interval with the HIGH sensitivity modifier).
	private static final long WAIT_MILLIS = 15_000;

	@TempDir Path root;

	private final Map<String, Object> properties = new HashMap<>();
	private final StandardEnvironment environment = new StandardEnvironment();
	private final FileChangeNotifier notifier = mock(FileChangeNotifier.class);
	private final LegacyJavaProjectsService projectsService = mock(LegacyJavaProjectsService.class);
	private StandaloneFileWatcher watcher;

	@BeforeEach
	void setUp() throws IOException {
		properties.put(StandaloneFileWatcher.PROPERTY_DEBOUNCE_MS, "50");
		environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
		Files.createDirectories(root.resolve("src/main/java"));
		watcher = new StandaloneFileWatcher(environment, notifier, projectsService);
	}

	@AfterEach
	void tearDown() {
		watcher.destroy();
		assertThat(watcher.isRunning()).isFalse();
	}

	@Test
	void reportsCreatedChangedAndDeletedFilesAsUris() throws Exception {
		watcher.start(root);
		assertThat(watcher.isRunning()).isTrue();

		Path file = root.resolve("src/main/java/Sample.java");
		String uri = file.toUri().toASCIIString();
		Files.writeString(file, "class Sample {}");
		verify(notifier, timeout(WAIT_MILLIS)).notifyFilesCreated(new String[] { uri });

		Files.writeString(file, "class Sample { int changed; }");
		Files.setLastModifiedTime(file, FileTime.from(Instant.now().plusSeconds(5)));
		verify(notifier, timeout(WAIT_MILLIS)).notifyFilesChanged(new String[] { uri });

		Files.delete(file);
		verify(notifier, timeout(WAIT_MILLIS)).notifyFilesDeleted(new String[] { uri });
		verify(projectsService, never()).discoverProjects();
	}

	@Test
	void directoryMovedIntoTheWorkspaceIsWatchedAndItsBuildFileTriggersProjectDiscovery(@TempDir Path staging) throws Exception {
		watcher.start(root);

		Path generated = staging.resolve("generated-app");
		Files.createDirectories(generated.resolve("src/main/java"));
		Files.writeString(generated.resolve("pom.xml"), "<project/>");
		Files.writeString(generated.resolve("src/main/java/App.java"), "class App {}");
		Path target = root.resolve("generated-app");
		Files.move(generated, target);

		verify(projectsService, timeout(WAIT_MILLIS)).discoverProjects();
		ArgumentCaptor<String[]> created = ArgumentCaptor.forClass(String[].class);
		verify(notifier, timeout(WAIT_MILLIS)).notifyFilesCreated(created.capture());
		assertThat(created.getValue()).containsExactlyInAnyOrder(
				target.resolve("pom.xml").toUri().toASCIIString(),
				target.resolve("src/main/java/App.java").toUri().toASCIIString());

		// The moved tree must be watched from now on, not only reported once.
		Path later = target.resolve("src/main/java/Later.java");
		Files.writeString(later, "class Later {}");
		verify(notifier, timeout(WAIT_MILLIS)).notifyFilesCreated(new String[] { later.toUri().toASCIIString() });
	}

	@Test
	void buildOutputAndDotDirectoriesAreIgnored() throws Exception {
		Files.createDirectories(root.resolve("target/classes"));
		Files.createDirectories(root.resolve(".git"));
		watcher.start(root);

		Files.writeString(root.resolve("target/classes/Ignored.class"), "");
		Files.writeString(root.resolve(".git/index"), "");
		Path visible = root.resolve("src/main/java/Visible.java");
		Files.writeString(visible, "class Visible {}");

		ArgumentCaptor<String[]> created = ArgumentCaptor.forClass(String[].class);
		verify(notifier, timeout(WAIT_MILLIS)).notifyFilesCreated(created.capture());
		assertThat(created.getAllValues()).hasSize(1);
		assertThat(created.getValue()).containsExactly(visible.toUri().toASCIIString());
		verify(notifier, never()).notifyFilesChanged(any());
	}

	@Test
	void createdAndDeletedWithinTheQuietPeriodIsNotReported() throws Exception {
		properties.put(StandaloneFileWatcher.PROPERTY_DEBOUNCE_MS, "400");
		watcher.start(root);

		Path file = root.resolve("src/main/java/Transient.java");
		Files.writeString(file, "class Transient {}");
		Files.delete(file);

		verify(notifier, after(1500).never()).notifyFilesCreated(any());
		verify(notifier, never()).notifyFilesDeleted(any());
	}

	@Test
	void givesUpAboveTheDirectoryCapAndStaysOffWithoutBlockingNotifications() throws Exception {
		properties.put(StandaloneFileWatcher.PROPERTY_MAX_DIRECTORIES, "2");
		Files.createDirectories(root.resolve("a/b"));
		Files.createDirectories(root.resolve("c"));

		watcher.start(root);

		assertThat(watcher.isRunning()).isFalse();
		Files.writeString(root.resolve("src/main/java/Unwatched.java"), "");
		verify(notifier, after(300).never()).notifyFilesCreated(any());
	}

	@Test
	void startsOnContextRefreshOnlyForTheMcpOnlyServerWithWatchingEnabled() {
		properties.put(LegacyJavaProjectsService.PROPERTY_PROJECT_DIR, root.toString());
		ContextRefreshedEvent event = new ContextRefreshedEvent(new StaticApplicationContext());

		properties.put(StandaloneFileWatcher.PROPERTY_LANGUAGE_SERVER_ENABLED, "true");
		watcher.onApplicationEvent(event);
		assertThat(watcher.isRunning()).as("LSP clients watch files themselves").isFalse();

		properties.put(StandaloneFileWatcher.PROPERTY_LANGUAGE_SERVER_ENABLED, "false");
		properties.put(StandaloneFileWatcher.PROPERTY_ENABLED, "false");
		watcher.onApplicationEvent(event);
		assertThat(watcher.isRunning()).as("opt-out via -Dspring.boot.ls.project.watch=false").isFalse();

		properties.remove(StandaloneFileWatcher.PROPERTY_ENABLED);
		watcher.onApplicationEvent(event);
		assertThat(watcher.isRunning()).isTrue();
	}

	@Test
	void doesNotStartWithoutAProjectDirectory() {
		properties.put(StandaloneFileWatcher.PROPERTY_LANGUAGE_SERVER_ENABLED, "false");
		watcher.onApplicationEvent(new ContextRefreshedEvent(new StaticApplicationContext()));
		assertThat(watcher.isRunning()).isFalse();
	}

	@Test
	void ignoredDirectoryNames() {
		assertThat(StandaloneFileWatcher.isIgnoredDirectory(Path.of("/w/target"))).isTrue();
		assertThat(StandaloneFileWatcher.isIgnoredDirectory(Path.of("/w/node_modules"))).isTrue();
		assertThat(StandaloneFileWatcher.isIgnoredDirectory(Path.of("/w/.idea"))).isTrue();
		assertThat(StandaloneFileWatcher.isIgnoredDirectory(Path.of("/w/src"))).isFalse();
		assertThat(StandaloneFileWatcher.isIgnoredDirectory(Path.of("/"))).isFalse();
	}
}
