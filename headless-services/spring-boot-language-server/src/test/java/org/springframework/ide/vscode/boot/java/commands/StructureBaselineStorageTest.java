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
	void roundTripsASnapshot(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		StructureSnapshot snapshot = new StructureSnapshot(Instant.parse("2026-01-01T00:00:00Z"), "abc123",
				new StructureNode("app", "app", "icon", "application", "hover", null, null, null, List.of(
						new StructureNode("app/type:A", "A", null, "type", null, null, null, null, List.of()))));

		storage.save("my-project", snapshot);
		StructureSnapshot loaded = storage.load("my-project");

		assertThat(loaded).isNotNull();
		assertThat(loaded.capturedAt()).isEqualTo(snapshot.capturedAt());
		assertThat(loaded.commitSha()).isEqualTo("abc123");
		assertThat(loaded.root().text()).isEqualTo("app");
		assertThat(loaded.root().children()).hasSize(1);
		assertThat(loaded.root().children().get(0).text()).isEqualTo("A");
	}

	@Test
	void roundTripsANullCommitSha(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		StructureSnapshot snapshot = new StructureSnapshot(Instant.now(), null,
				new StructureNode("app", "app", null, "application", null, null, null, null, List.of()));

		storage.save("my-project", snapshot);

		assertThat(storage.load("my-project").commitSha()).isNull();
	}

	@Test
	void loadingAProjectThatWasNeverSavedReturnsNull(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());

		assertThat(storage.load("never-saved")).isNull();
	}

	@Test
	void corruptFileIsDiscardedRatherThanThrowing(@TempDir Path dir) throws Exception {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());

		File file = new File(dir.toFile(), "my-project.json");
		try (FileWriter writer = new FileWriter(file)) {
			writer.write("{ not valid json ");
		}

		assertThat(storage.load("my-project")).isNull();
	}

	@Test
	void schemaVersionMismatchIsDiscardedRatherThanThrowing(@TempDir Path dir) throws Exception {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());

		File file = new File(dir.toFile(), "my-project.json");
		try (FileWriter writer = new FileWriter(file)) {
			writer.write("{ \"schemaVersion\": 999999, \"snapshot\": { \"capturedAt\": \"2026-01-01T00:00:00Z\", \"root\": { \"text\": \"app\" } } }");
		}

		assertThat(storage.load("my-project")).isNull();
	}

	@Test
	void deleteRemovesAPersistedBaseline(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		StructureSnapshot snapshot = new StructureSnapshot(Instant.now(), null,
				new StructureNode("app", "app", null, "application", null, null, null, null, List.of()));

		storage.save("my-project", snapshot);
		assertThat(storage.load("my-project")).isNotNull();

		storage.delete("my-project");

		assertThat(storage.load("my-project")).isNull();
	}

	@Test
	void deletingAProjectThatWasNeverSavedIsANoOp(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());

		storage.delete("never-saved");

		assertThat(storage.load("never-saved")).isNull();
	}

	@Test
	void rejectsProjectNamesThatWouldEscapeTheStorageDirectory(@TempDir Path dir) {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		StructureSnapshot snapshot = new StructureSnapshot(Instant.now(), null,
				new StructureNode("app", "app", null, "application", null, null, null, null, List.of()));

		assertThrows(IllegalArgumentException.class, () -> storage.save("../escape", snapshot));
	}

}
