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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.java.commands.StructureSnapshotStore.StructureSnapshot;
import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * Tests the retained baseline history: capping to the configured size, newest-first ordering, and
 * that the newest entry is always what the rest of {@link StructureSnapshotStore} treats as "the"
 * baseline.
 *
 * @author Martin Lippert
 */
public class StructureSnapshotStoreTest {

	@Test
	void capturingMoreThanTheConfiguredSizeDropsTheOldest(@TempDir Path dir) throws Exception {
		StructureSnapshotStore store = storeWithHistorySize(dir, 2);
		IJavaProject project = project();

		store.captureBaseline(project, "sha1", "first commit");
		store.captureBaseline(project, "sha2", "second commit");
		store.captureBaseline(project, "sha3", "third commit");

		List<StructureSnapshot> history = store.historyOf(project);

		assertThat(history).extracting(StructureSnapshot::commitSha).containsExactly("sha3", "sha2");
	}

	@Test
	void theNewestCaptureIsWhatCapturedCommitShaAndHasBaselineReport(@TempDir Path dir) throws Exception {
		StructureSnapshotStore store = storeWithHistorySize(dir, 10);
		IJavaProject project = project();

		store.captureBaseline(project, "sha1", "first commit");
		store.captureBaseline(project, "sha2", "second commit");

		assertThat(store.hasBaseline(project)).isTrue();
		assertThat(store.capturedCommitShaOf(project)).contains("sha2");
	}

	@Test
	void clearBaselineRemovesTheWholeRetainedHistory(@TempDir Path dir) throws Exception {
		StructureSnapshotStore store = storeWithHistorySize(dir, 10);
		IJavaProject project = project();

		store.captureBaseline(project, "sha1", "first commit");
		store.captureBaseline(project, "sha2", "second commit");

		boolean hadBaseline = store.clearBaseline(project);

		assertThat(hadBaseline).isTrue();
		assertThat(store.hasBaseline(project)).isFalse();
		assertThat(store.historyOf(project)).isEmpty();
	}

	@Test
	void annotateWithChangesSinceBaselineComparesAgainstTheRequestedSnapshot(@TempDir Path dir) throws Exception {
		StructureSnapshotStore store = storeWithHistorySize(dir, 10);
		IJavaProject project = project();

		String olderKey = keyOf(store.captureBaseline(project, "sha1", "first commit"));
		store.captureBaseline(project, "sha2", "second commit");

		JsonNodeHandler.Node tree = new JsonNodeHandler.Node(null)
				.withAttribute(JsonNodeHandler.TEXT, "app")
				.withAttribute(JsonNodeHandler.KIND, JsonNodeHandler.KIND_APPLICATION);

		StructureSnapshot comparedAgainst = store.annotateWithChangesSinceBaseline(project, tree, olderKey);

		assertThat(comparedAgainst.commitSha()).isEqualTo("sha1");
		assertThat(comparedAgainst.commitMessage()).isEqualTo("first commit");
	}

	@Test
	void annotateWithChangesSinceBaselineFallsBackToTheNewestWhenTheRequestedSnapshotIsGone(@TempDir Path dir) throws Exception {
		StructureSnapshotStore store = storeWithHistorySize(dir, 10);
		IJavaProject project = project();

		store.captureBaseline(project, "sha1", "first commit");
		store.captureBaseline(project, "sha2", "second commit");

		JsonNodeHandler.Node tree = new JsonNodeHandler.Node(null)
				.withAttribute(JsonNodeHandler.TEXT, "app")
				.withAttribute(JsonNodeHandler.KIND, JsonNodeHandler.KIND_APPLICATION);

		// an evicted (or simply unknown) snapshot key - failing open to the default view rather than
		// showing nothing, since the picked snapshot is no longer available to compare against
		StructureSnapshot comparedAgainst = store.annotateWithChangesSinceBaseline(project, tree, "2020-01-01T00:00:00Z");

		assertThat(comparedAgainst.commitSha()).isEqualTo("sha2");
	}

	@Test
	void annotateWithChangesSinceBaselineReturnsNullWithoutAnyBaseline(@TempDir Path dir) throws Exception {
		StructureSnapshotStore store = storeWithHistorySize(dir, 10);
		IJavaProject project = project();

		JsonNodeHandler.Node tree = new JsonNodeHandler.Node(null)
				.withAttribute(JsonNodeHandler.TEXT, "app")
				.withAttribute(JsonNodeHandler.KIND, JsonNodeHandler.KIND_APPLICATION);

		assertThat(store.annotateWithChangesSinceBaseline(project, tree, null)).isNull();
		assertThat(store.annotateWithChangesSinceBaseline(project, tree, "2020-01-01T00:00:00Z")).isNull();
	}

	@Test
	void aManuallyCapturedSnapshotCarriesNoCommitButIsStillSelectable(@TempDir Path dir) throws Exception {
		StructureSnapshotStore store = storeWithHistorySize(dir, 10);
		IJavaProject project = project();

		String manualKey = keyOf(store.captureBaseline(project));
		store.captureBaseline(project, "sha2", "a later commit");

		assertThat(store.historyEntriesOf(project)).anySatisfy(entry -> {
			assertThat(entry.commitSha()).isNull();
			assertThat(entry.commitMessage()).isNull();
			assertThat(entry.capturedAt()).isEqualTo(manualKey);
		});

		JsonNodeHandler.Node tree = new JsonNodeHandler.Node(null)
				.withAttribute(JsonNodeHandler.TEXT, "app")
				.withAttribute(JsonNodeHandler.KIND, JsonNodeHandler.KIND_APPLICATION);

		StructureSnapshot comparedAgainst = store.annotateWithChangesSinceBaseline(project, tree, manualKey);

		assertThat(comparedAgainst.commitSha()).isNull();
		assertThat(keyOf(comparedAgainst)).isEqualTo(manualKey);
	}

	@Test
	void capturedCommitShaSkipsManualSnapshots(@TempDir Path dir) throws Exception {
		StructureSnapshotStore store = storeWithHistorySize(dir, 10);
		IJavaProject project = project();

		store.captureBaseline(project, "sha1", "a commit");
		store.captureBaseline(project);

		// the newest snapshot is the manual one, but "which commit do I already have a baseline
		// for?" must still answer with the commit - otherwise the git tracker captures a duplicate
		assertThat(store.capturedCommitShaOf(project)).contains("sha1");
	}

	@Test
	void historyEntriesOfMapsFieldsWithoutTheTree(@TempDir Path dir) throws Exception {
		StructureSnapshotStore store = storeWithHistorySize(dir, 10);
		IJavaProject project = project();

		store.captureBaseline(project, "sha1", "first commit");
		store.captureBaseline(project, "sha2", "second commit");

		List<StructureSnapshotStore.BaselineHistoryEntry> entries = store.historyEntriesOf(project);

		assertThat(entries).hasSize(2);
		assertThat(entries.get(0).commitSha()).isEqualTo("sha2");
		assertThat(entries.get(0).commitMessage()).isEqualTo("second commit");
		assertThat(entries.get(0).nodeCount()).isEqualTo(1);
		assertThat(entries.get(1).commitSha()).isEqualTo("sha1");
	}

	@Test
	void historyIsReloadedFromDiskByAFreshStoreInstance(@TempDir Path dir) throws Exception {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		IJavaProject project = project();

		StructureSnapshotStore first = storeWithHistorySize(storage, 10);
		first.captureBaseline(project, "sha1", "first commit");
		first.captureBaseline(project, "sha2", "second commit");

		StructureSnapshotStore second = storeWithHistorySize(storage, 10);

		assertThat(second.historyOf(project)).extracting(StructureSnapshot::commitSha).containsExactly("sha2", "sha1");
		assertThat(second.capturedCommitShaOf(project)).contains("sha2");
	}

	private static StructureSnapshotStore storeWithHistorySize(Path dir, int historySize) {
		return storeWithHistorySize(new StructureBaselineStorage(dir.toFile()), historySize);
	}

	private static StructureSnapshotStore storeWithHistorySize(StructureBaselineStorage storage, int historySize) {
		BootJavaConfig config = mock(BootJavaConfig.class);
		when(config.getStructureBaselineHistorySize()).thenReturn(historySize);

		StructureViewProvider structureViewProvider = mock(StructureViewProvider.class);
		when(structureViewProvider.createTree(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.any()))
				.thenAnswer(invocation -> new JsonNodeHandler.Node(null)
						.withAttribute(JsonNodeHandler.TEXT, "app")
						.withAttribute(JsonNodeHandler.KIND, JsonNodeHandler.KIND_APPLICATION));

		return new StructureSnapshotStore(structureViewProvider, storage, config);
	}

	/** The key a client uses to ask for one specific snapshot - see StructureSnapshotStore#keyOf. */
	private static String keyOf(StructureSnapshot snapshot) {
		return snapshot.capturedAt().toString();
	}

	private static IJavaProject project() {
		IJavaProject project = mock(IJavaProject.class);
		when(project.getElementName()).thenReturn("test-project");
		return project;
	}

}
