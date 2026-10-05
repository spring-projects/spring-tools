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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

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

		StructureSnapshot comparedAgainst = store.annotateWithChangesSinceBaseline(project, tree, olderKey, null);

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
		StructureSnapshot comparedAgainst = store.annotateWithChangesSinceBaseline(project, tree, "2020-01-01T00:00:00Z", null);

		assertThat(comparedAgainst.commitSha()).isEqualTo("sha2");
	}

	@Test
	void annotateWithChangesSinceBaselineReturnsNullWithoutAnyBaseline(@TempDir Path dir) throws Exception {
		StructureSnapshotStore store = storeWithHistorySize(dir, 10);
		IJavaProject project = project();

		JsonNodeHandler.Node tree = new JsonNodeHandler.Node(null)
				.withAttribute(JsonNodeHandler.TEXT, "app")
				.withAttribute(JsonNodeHandler.KIND, JsonNodeHandler.KIND_APPLICATION);

		assertThat(store.annotateWithChangesSinceBaseline(project, tree, null, null)).isNull();
		assertThat(store.annotateWithChangesSinceBaseline(project, tree, "2020-01-01T00:00:00Z", null)).isNull();
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

		StructureSnapshot comparedAgainst = store.annotateWithChangesSinceBaseline(project, tree, manualKey, null);

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
		assertThat(entries.get(0).elementCount()).isEqualTo(1);
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

	/**
	 * GH-2021: in a repository shared by many projects, most commits change nothing about a given
	 * project's structure. Such a commit gets no snapshot of its own - the history keeps one entry
	 * per change of the structure, not per move of HEAD - and the file is not rewritten; only the
	 * check is recorded, so the commit counts as captured.
	 */
	@Test
	void aCommitThatChangesNothingAboutTheStructureKeepsTheHistoryAsItIs(@TempDir Path dir) throws Exception {
		StructureBaselineStorage storage = new StructureBaselineStorage(dir.toFile());
		StructureSnapshotStore store = store(storage, 10, () -> structure("app.Type", "same"));
		IJavaProject project = project();

		store.captureBaseline(project, "sha1", "first commit");
		File historyFile = historyFileIn(dir);
		long writtenAt = historyFile.lastModified();
		historyFile.setLastModified(writtenAt - 10_000);

		StructureSnapshot kept = store.captureBaseline(project, "sha2", "touches another project");

		assertThat(store.historyOf(project)).extracting(StructureSnapshot::commitSha).containsExactly("sha1");
		assertThat(kept.commitSha()).isEqualTo("sha1");
		assertThat(store.capturedCommitShaOf(project)).contains("sha2");
		assertThat(historyFile.lastModified()).isEqualTo(writtenAt - 10_000);
	}

	@Test
	void theCheckedCommitSurvivesARestart(@TempDir Path dir) throws Exception {
		StructureSnapshotStore store = store(new StructureBaselineStorage(dir.toFile()), 10, () -> structure("app.Type", "same"));
		IJavaProject project = project();
		store.captureBaseline(project, "sha1", "first commit");
		store.captureBaseline(project, "sha2", "touches another project");

		StructureSnapshotStore restarted = store(new StructureBaselineStorage(dir.toFile()), 10, () -> structure("app.Type", "same"));

		assertThat(restarted.capturedCommitShaOf(project)).contains("sha2");
		assertThat(restarted.historyOf(project)).extracting(StructureSnapshot::commitSha).containsExactly("sha1");
	}

	@Test
	void aChangedStructureIsKept(@TempDir Path dir) throws Exception {
		List<StructureElementSnapshot> structures = new ArrayList<>(List.of(
				structure("app.Type", "before"), structure("app.Type", "after"), structure("app.Other", "after")));
		StructureSnapshotStore store = store(new StructureBaselineStorage(dir.toFile()), 10, () -> structures.remove(0));
		IJavaProject project = project();

		store.captureBaseline(project, "sha1", "first commit");
		store.captureBaseline(project, "sha2", "edits the type");
		store.captureBaseline(project, "sha3", "renames the type");

		assertThat(store.historyOf(project)).extracting(StructureSnapshot::commitSha).containsExactly("sha3", "sha2", "sha1");
		assertThat(store.capturedCommitShaOf(project)).contains("sha3");
	}

	@Test
	void theSameTypesInAnotherOrderAreTheSameStructure() {
		StructureElementSnapshot.SnapshotType a = new StructureElementSnapshot.SnapshotType("app.A", "a", List.of("s1"), List.of(), List.of());
		StructureElementSnapshot.SnapshotType b = new StructureElementSnapshot.SnapshotType("app.B", "b", List.of(), List.of(), List.of());
		StructureElementSnapshot.SnapshotStereotype s1 = new StructureElementSnapshot.SnapshotStereotype("s1", "S1", 0, List.of());
		StructureElementSnapshot.SnapshotStereotype s2 = new StructureElementSnapshot.SnapshotStereotype("s2", "S2", 0, List.of());

		StructureElementSnapshot one = new StructureElementSnapshot("app", List.of(a, b), List.of(s1, s2));
		StructureElementSnapshot reordered = new StructureElementSnapshot("app", List.of(b, a), List.of(s2, s1));
		StructureElementSnapshot edited = new StructureElementSnapshot("app",
				List.of(new StructureElementSnapshot.SnapshotType("app.A", "a-edited", List.of("s1"), List.of(), List.of()), b), List.of(s1, s2));
		StructureElementSnapshot restereotyped = new StructureElementSnapshot("app",
				List.of(new StructureElementSnapshot.SnapshotType("app.A", "a", List.of(), List.of(), List.of()), b), List.of(s1, s2));

		assertThat(one.hasSameStructureAs(reordered)).isTrue();
		assertThat(one.hasSameStructureAs(edited)).isFalse();
		assertThat(one.hasSameStructureAs(restereotyped)).isFalse();
		assertThat(one.hasSameStructureAs(null)).isFalse();
	}

	/**
	 * A manual capture was asked for, so it is kept even with nothing changed - and does not count
	 * as checking a commit.
	 */
	@Test
	void aManualCaptureIsAlwaysKept(@TempDir Path dir) throws Exception {
		StructureSnapshotStore store = store(new StructureBaselineStorage(dir.toFile()), 10, () -> structure("app.Type", "same"));
		IJavaProject project = project();

		store.captureBaseline(project, "sha1", "first commit");
		store.captureBaseline(project);

		assertThat(store.historyOf(project)).extracting(StructureSnapshot::commitSha).containsExactly(null, "sha1");
		assertThat(store.capturedCommitShaOf(project)).contains("sha1");
	}

	@Test
	void clearingTheBaselineForgetsTheCheckedCommitToo(@TempDir Path dir) throws Exception {
		StructureSnapshotStore store = store(new StructureBaselineStorage(dir.toFile()), 10, () -> structure("app.Type", "same"));
		IJavaProject project = project();
		store.captureBaseline(project, "sha1", "first commit");
		store.captureBaseline(project, "sha2", "touches another project");

		store.clearBaseline(project);

		assertThat(store.capturedCommitShaOf(project)).isEmpty();
		assertThat(dir.toFile().listFiles()).isEmpty();
	}

	private static File historyFileIn(Path dir) {
		File[] files = dir.toFile().listFiles((d, name) -> name.endsWith(".json"));
		assertThat(files).hasSize(1);
		return files[0];
	}

	private static StructureSnapshotStore storeWithHistorySize(Path dir, int historySize) {
		return storeWithHistorySize(new StructureBaselineStorage(dir.toFile()), historySize);
	}

	/**
	 * A store whose project's structure changes with every capture - its type's content hash does -
	 * so that every capture keeps a snapshot of its own.
	 */
	private static StructureSnapshotStore storeWithHistorySize(StructureBaselineStorage storage, int historySize) {
		AtomicInteger captures = new AtomicInteger();
		return store(storage, historySize, () -> structure("app.Type", "hash-" + captures.incrementAndGet()));
	}

	private static StructureSnapshotStore store(StructureBaselineStorage storage, int historySize, Supplier<StructureElementSnapshot> structure) {
		BootJavaConfig config = mock(BootJavaConfig.class);
		when(config.getStructureBaselineHistorySize()).thenReturn(historySize);

		StructureViewProvider structureViewProvider = mock(StructureViewProvider.class);
		when(structureViewProvider.captureSnapshot(any())).thenAnswer(invocation -> structure.get());

		return new StructureSnapshotStore(structureViewProvider, storage, config);
	}

	private static StructureElementSnapshot structure(String typeName, String contentHash) {
		return new StructureElementSnapshot("app",
				List.of(new StructureElementSnapshot.SnapshotType(typeName, contentHash, List.of(), List.of(), List.of())), List.of());
	}

	/** The key a client uses to ask for one specific snapshot - see StructureSnapshotStore#keyOf. */
	private static String keyOf(StructureSnapshot snapshot) {
		return snapshot.capturedAt().toString();
	}

	private static IJavaProject project() {
		IJavaProject project = mock(IJavaProject.class);
		when(project.getElementName()).thenReturn("test-project");
		when(project.getLocationUri()).thenReturn(URI.create("file:///projects/test-project"));
		return project;
	}

}
