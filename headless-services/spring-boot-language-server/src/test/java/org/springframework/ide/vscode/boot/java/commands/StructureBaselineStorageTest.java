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

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.boot.java.commands.StructureSnapshotStore.StructureSnapshot;
import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider.StructureNode;

/**
 * @author Martin Lippert
 */
public class StructureBaselineStorageTest {

	@Test
	void roundTripsAHistory(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		StructureSnapshot newest = new StructureSnapshot(Instant.parse("2026-01-02T00:00:00Z"), "def456", "second commit",
				new StructureNode("app", "app", "icon", "application", "hover", null, null, null, List.of(
						new StructureNode("app/type:A", "A", null, "type", null, null, null, null, List.of()))));
		StructureSnapshot older = new StructureSnapshot(Instant.parse("2026-01-01T00:00:00Z"), "abc123", "first commit",
				new StructureNode("app", "app", null, "application", null, null, null, null, List.of()));

		storage.save("my-project", List.of(newest, older));
		List<StructureSnapshot> loaded = storage.load("my-project");

		assertThat(loaded).hasSize(2);
		assertThat(loaded.get(0).commitSha()).isEqualTo("def456");
		assertThat(loaded.get(0).commitMessage()).isEqualTo("second commit");
		assertThat(loaded.get(0).root().text()).isEqualTo("app");
		assertThat(loaded.get(0).root().children()).hasSize(1);
		assertThat(loaded.get(0).root().children().get(0).text()).isEqualTo("A");
		assertThat(loaded.get(1).commitSha()).isEqualTo("abc123");
		assertThat(loaded.get(1).commitMessage()).isEqualTo("first commit");
	}

	@Test
	void roundTripsANullCommitShaAndMessage(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		StructureSnapshot snapshot = new StructureSnapshot(Instant.now(), null, null,
				new StructureNode("app", "app", null, "application", null, null, null, null, List.of()));

		storage.save("my-project", List.of(snapshot));

		List<StructureSnapshot> loaded = storage.load("my-project");
		assertThat(loaded.get(0).commitSha()).isNull();
		assertThat(loaded.get(0).commitMessage()).isNull();
	}

	@Test
	void loadingAProjectThatWasNeverSavedReturnsAnEmptyList(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());

		assertThat(storage.load("never-saved")).isEmpty();
	}

	@Test
	void corruptFileIsDiscardedRatherThanThrowing(@TempDir Path dir) throws Exception {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());

		File file = new File(dir.toFile(), "my-project.json");
		try (FileWriter writer = new FileWriter(file)) {
			writer.write("{ not valid json ");
		}

		assertThat(storage.load("my-project")).isEmpty();
	}

	@Test
	void schemaVersionMismatchIsDiscardedRatherThanThrowing(@TempDir Path dir) throws Exception {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());

		File file = new File(dir.toFile(), "my-project.json");
		try (FileWriter writer = new FileWriter(file)) {
			writer.write("{ \"schemaVersion\": 999999, \"history\": [ { \"capturedAt\": \"2026-01-01T00:00:00Z\", \"root\": { \"text\": \"app\" } } ] }");
		}

		assertThat(storage.load("my-project")).isEmpty();
	}

	@Test
	void deleteRemovesAPersistedBaseline(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		StructureSnapshot snapshot = new StructureSnapshot(Instant.now(), null, null,
				new StructureNode("app", "app", null, "application", null, null, null, null, List.of()));

		storage.save("my-project", List.of(snapshot));
		assertThat(storage.load("my-project")).isNotEmpty();

		storage.delete("my-project");

		assertThat(storage.load("my-project")).isEmpty();
	}

	@Test
	void deletingAProjectThatWasNeverSavedIsANoOp(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());

		storage.delete("never-saved");

		assertThat(storage.load("never-saved")).isEmpty();
	}

	@Test
	void rejectsProjectNamesThatWouldEscapeTheStorageDirectory(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		StructureSnapshot snapshot = new StructureSnapshot(Instant.now(), null, null,
				new StructureNode("app", "app", null, "application", null, null, null, null, List.of()));

		assertThrows(IllegalArgumentException.class, () -> storage.save("../escape", List.of(snapshot)));
	}

}
