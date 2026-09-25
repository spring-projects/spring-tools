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
package org.springframework.ide.vscode.boot.java.commands;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.boot.java.commands.StructureElementSnapshot.SnapshotType;
import org.springframework.ide.vscode.boot.java.commands.StructureSnapshotStore.StructureSnapshot;
import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * @author Martin Lippert
 */
public class StructureBaselineStorageTest {

	@Test
	void roundTripsAHistory(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		IJavaProject project = project("my-project", "file:///projects/my-project");
		StructureSnapshot newest = new StructureSnapshot(Instant.parse("2026-01-02T00:00:00Z"), "def456", "second commit",
				snapshotOfOneType("com.example.A"));
		StructureSnapshot older = new StructureSnapshot(Instant.parse("2026-01-01T00:00:00Z"), "abc123", "first commit",
				emptySnapshot());

		storage.save(project, List.of(newest, older));
		List<StructureSnapshot> loaded = storage.load(project);

		assertThat(loaded).hasSize(2);
		assertThat(loaded.get(0).commitSha()).isEqualTo("def456");
		assertThat(loaded.get(0).commitMessage()).isEqualTo("second commit");
		assertThat(loaded.get(0).elements().mainApplicationPackage()).isEqualTo("com.example");
		assertThat(loaded.get(0).elements().types()).hasSize(1);
		assertThat(loaded.get(0).elements().types().get(0).fqn()).isEqualTo("com.example.A");
		assertThat(loaded.get(1).commitSha()).isEqualTo("abc123");
		assertThat(loaded.get(1).commitMessage()).isEqualTo("first commit");
	}

	@Test
	void roundTripsANullCommitShaAndMessage(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		IJavaProject project = project("my-project", "file:///projects/my-project");
		StructureSnapshot snapshot = new StructureSnapshot(Instant.now(), null, null, emptySnapshot());

		storage.save(project, List.of(snapshot));

		List<StructureSnapshot> loaded = storage.load(project);
		assertThat(loaded.get(0).commitSha()).isNull();
		assertThat(loaded.get(0).commitMessage()).isNull();
	}

	@Test
	void loadingAProjectThatWasNeverSavedReturnsAnEmptyList(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());

		assertThat(storage.load(project("never-saved", "file:///projects/never-saved"))).isEmpty();
	}

	@Test
	void twoProjectsWithTheSameNameAtDifferentLocationsDoNotShareAHistory(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		IJavaProject atHome = project("demo", "file:///home/demo");
		IJavaProject atWork = project("demo", "file:///work/demo");

		StructureSnapshot homeSnapshot = new StructureSnapshot(Instant.now(), "home-sha", "home commit", emptySnapshot());
		StructureSnapshot workSnapshot = new StructureSnapshot(Instant.now(), "work-sha", "work commit", emptySnapshot());

		storage.save(atHome, List.of(homeSnapshot));
		storage.save(atWork, List.of(workSnapshot));

		assertThat(storage.load(atHome)).extracting(StructureSnapshot::commitSha).containsExactly("home-sha");
		assertThat(storage.load(atWork)).extracting(StructureSnapshot::commitSha).containsExactly("work-sha");
	}

	@Test
	void corruptFileIsDiscardedRatherThanThrowing(@TempDir Path dir) throws Exception {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		IJavaProject project = project("my-project", "file:///projects/my-project");

		storage.save(project, List.of(new StructureSnapshot(Instant.now(), null, null, emptySnapshot())));

		try (FileWriter writer = new FileWriter(onlyFileIn(dir))) {
			writer.write("{ not valid json ");
		}

		assertThat(storage.load(project)).isEmpty();
	}

	@Test
	void schemaVersionMismatchIsDiscardedRatherThanThrowing(@TempDir Path dir) throws Exception {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		IJavaProject project = project("my-project", "file:///projects/my-project");

		storage.save(project, List.of(new StructureSnapshot(Instant.now(), null, null, emptySnapshot())));

		try (FileWriter writer = new FileWriter(onlyFileIn(dir))) {
			writer.write("{ \"schemaVersion\": 999999, \"history\": [ { \"capturedAt\": \"2026-01-01T00:00:00Z\", \"elements\": { \"mainApplicationPackage\": \"app\" } } ] }");
		}

		assertThat(storage.load(project)).isEmpty();
	}

	@Test
	void deleteRemovesAPersistedBaseline(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		IJavaProject project = project("my-project", "file:///projects/my-project");
		StructureSnapshot snapshot = new StructureSnapshot(Instant.now(), null, null, emptySnapshot());

		storage.save(project, List.of(snapshot));
		assertThat(storage.load(project)).isNotEmpty();

		storage.delete(project);

		assertThat(storage.load(project)).isEmpty();
	}

	@Test
	void deletingAProjectThatWasNeverSavedIsANoOp(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		IJavaProject project = project("never-saved", "file:///projects/never-saved");

		storage.delete(project);

		assertThat(storage.load(project)).isEmpty();
	}

	@Test
	void rejectsProjectNamesThatWouldEscapeTheStorageDirectory(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		IJavaProject project = project("../escape", "file:///projects/escape");
		StructureSnapshot snapshot = new StructureSnapshot(Instant.now(), null, null, emptySnapshot());

		assertThrows(IllegalArgumentException.class, () -> storage.save(project, List.of(snapshot)));
	}

	private static StructureElementSnapshot emptySnapshot() {
		return new StructureElementSnapshot("app", List.of(), List.of());
	}

	private static StructureElementSnapshot snapshotOfOneType(String fqn) {
		return new StructureElementSnapshot("com.example",
				List.of(new SnapshotType(fqn, "hash", List.of(), List.of(), List.of())), List.of());
	}

	private static IJavaProject project(String name, String location) {
		IJavaProject project = mock(IJavaProject.class);
		when(project.getElementName()).thenReturn(name);
		when(project.getLocationUri()).thenReturn(URI.create(location));
		return project;
	}

	/** The one file {@link StructureBaselineStorage} just wrote, so a test can corrupt it directly
	 * without needing to know (or duplicate) the storage's own file naming scheme. */
	private static File onlyFileIn(Path dir) {
		File[] files = dir.toFile().listFiles();
		assertThat(files).hasSize(1);
		return files[0];
	}

}
