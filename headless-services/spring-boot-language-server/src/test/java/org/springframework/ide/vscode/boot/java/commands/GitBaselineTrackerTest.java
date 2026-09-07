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
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.java.commands.StructureSnapshotStore.StructureSnapshot;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJava;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;

/**
 * @author Martin Lippert
 */
public class GitBaselineTrackerTest {

	private static final String DISABLE_PROPERTY = "disable-structure-git-baseline";

	private String originalDisableProperty;

	@BeforeEach
	void resetSystemProperty() {
		// other test classes (e.g. IndexerTestConf) may already have this set for the whole JVM
		// fork via a static initializer, which doesn't re-run - so it may already be "true" here,
		// which would defeat every test in this class except the one that wants it disabled.
		// Force a known, enabled starting point for every test, and restore whatever was actually
		// there afterward, rather than clear()ing it permanently for the rest of the fork.
		originalDisableProperty = System.getProperty(DISABLE_PROPERTY);
		System.clearProperty(DISABLE_PROPERTY);
	}

	@AfterEach
	void restoreSystemProperty() {
		if (originalDisableProperty == null) {
			System.clearProperty(DISABLE_PROPERTY);
		} else {
			System.setProperty(DISABLE_PROPERTY, originalDisableProperty);
		}
	}

	@Test
	void capturesNothingForAProjectOpenedWithPendingSourceChanges(@TempDir Path dir) throws Exception {
		commit(dir, "Sample.java", "class Sample {}");
		writeWithoutCommitting(dir, "Sample.java", "class Sample { void added() {} }");

		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		tracker.syncBaselineWithGit(project);

		// a baseline captured now would contain the pending change and hide it from every later
		// diff, so there must be no baseline at all until the next commit
		assertThat(baselines.captureCount(project)).isZero();
		assertThat(baselines.capturedCommitShaOf(project)).isEmpty();
	}

	@Test
	void capturesOnceThePendingSourceChangesAreCommitted(@TempDir Path dir) throws Exception {
		commit(dir, "Sample.java", "class Sample {}");
		writeWithoutCommitting(dir, "Sample.java", "class Sample { void added() {} }");

		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		tracker.syncBaselineWithGit(project);
		assertThat(baselines.captureCount(project)).isZero();

		commit(dir, "Sample.java", "class Sample { void added() {} }");
		tracker.syncBaselineWithGit(project);

		assertThat(baselines.captureCount(project)).isEqualTo(1);
		assertThat(baselines.capturedCommitShaOf(project)).isPresent();
	}

	@Test
	void capturesWhenOnlyStructurallyIrrelevantFilesArePending(@TempDir Path dir) throws Exception {
		commit(dir, "initial content");
		// an edited text file and an untracked note cannot move a node in the logical structure,
		// so they must not keep the project from ever getting a baseline
		writeWithoutCommitting(dir, "content.txt", "edited outside of any source file");
		writeWithoutCommitting(dir, "NOTES.md", "scratch notes");

		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		tracker.syncBaselineWithGit(project);

		assertThat(baselines.captureCount(project)).isEqualTo(1);
	}

	@Test
	void capturesWhenOnlyFilesOutsideTheProjectArePending(@TempDir Path dir) throws Exception {
		// one repository, several projects (a monorepo, a multi-module build): pending Java changes
		// in a sibling say nothing about whether *this* project matches its commit, and must not
		// keep it from ever getting a baseline
		Files.createDirectories(dir.resolve("my-project/src"));
		Files.createDirectories(dir.resolve("sibling-project/src"));
		Files.writeString(dir.resolve("my-project/src/App.java"), "class App {}");
		Files.writeString(dir.resolve("sibling-project/src/Other.java"), "class Other {}");
		commitEverything(dir, "initial");

		writeWithoutCommitting(dir, "sibling-project/src/Other.java", "class Other { void added() {} }");

		IJavaProject project = projectAt(dir.resolve("my-project"));
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		tracker.syncBaselineWithGit(project);

		assertThat(baselines.captureCount(project)).isEqualTo(1);
	}

	@Test
	void capturesNothingWhenTheProjectsOwnSourcesArePendingInAMultiProjectRepository(@TempDir Path dir) throws Exception {
		// the flip side of the test above: scoping to the project must not lose track of the
		// project's *own* pending changes when it lives in a subdirectory
		Files.createDirectories(dir.resolve("my-project/src"));
		Files.writeString(dir.resolve("my-project/src/App.java"), "class App {}");
		commitEverything(dir, "initial");

		writeWithoutCommitting(dir, "my-project/src/App.java", "class App { void added() {} }");

		IJavaProject project = projectAt(dir.resolve("my-project"));
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		tracker.syncBaselineWithGit(project);

		assertThat(baselines.captureCount(project)).isZero();
	}

	@Test
	void capturesNothingForAnUntrackedSourceFile(@TempDir Path dir) throws Exception {
		commit(dir, "initial content");
		writeWithoutCommitting(dir, "BrandNew.java", "class BrandNew {}");

		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		tracker.syncBaselineWithGit(project);

		assertThat(baselines.captureCount(project)).isZero();
	}

	@Test
	void leavesAManualSnapshotAloneWhileChangesArePending(@TempDir Path dir) throws Exception {
		commit(dir, "Sample.java", "class Sample {}");

		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		writeWithoutCommitting(dir, "Sample.java", "class Sample { void added() {} }");

		// what a manual capture leaves behind: a snapshot with no commit information, because it
		// was taken over uncommitted work and so represents no commit
		baselines.captureBaseline(project, null, null);

		tracker.syncBaselineWithGit(project);
		assertThat(baselines.captureCount(project)).isEqualTo(1);

		// ... and the git-driven model takes over again at the next commit
		commit(dir, "Sample.java", "class Sample { void added() {} }");
		tracker.syncBaselineWithGit(project);

		assertThat(baselines.captureCount(project)).isEqualTo(2);
		assertThat(baselines.capturedCommitShaOf(project)).isPresent();
	}

	@Test
	void doesNotRecaptureACommitItAlreadyHasAfterAManualSnapshot(@TempDir Path dir) throws Exception {
		commit(dir, "Sample.java", "class Sample {}");

		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		tracker.syncBaselineWithGit(project);
		assertThat(baselines.captureCount(project)).isEqualTo(1);

		// a manual snapshot on a clean tree must not look like "this commit has no baseline yet",
		// or the very next tick would capture a duplicate and displace the manual one
		baselines.captureBaseline(project, null, null);
		tracker.syncBaselineWithGit(project);

		assertThat(baselines.captureCount(project)).isEqualTo(2);
	}

	@Test
	void pollCapturesForEveryOpenProject(@TempDir Path dir) throws Exception {
		commit(dir, "initial content");
		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		// a commit changes no file and so triggers no index update - the poll is what notices it
		tracker.pollForCommits();

		assertThat(baselines.captureCount(project)).isEqualTo(1);
	}

	@Test
	void pollDoesNothingWhenDisabled(@TempDir Path dir) throws Exception {
		commit(dir, "initial content");
		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, false);

		tracker.pollForCommits();

		assertThat(baselines.captureCount(project)).isZero();
	}

	@Test
	void capturesOnTheFirstLookWhenTheWorkingTreeIsClean(@TempDir Path dir) throws Exception {
		commit(dir, "initial content");
		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		tracker.syncBaselineWithGit(project);

		assertThat(baselines.captureCount(project)).isEqualTo(1);
		assertThat(baselines.capturedCommitShaOf(project)).isPresent();
	}

	@Test
	void capturedBaselineCarriesTheCommitsShortMessage(@TempDir Path dir) throws Exception {
		commit(dir, "Sample.java", "class Sample {}", "add the Sample class");
		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		tracker.syncBaselineWithGit(project);

		assertThat(baselines.capturedCommitMessageOf(project)).contains("add the Sample class");
	}

	@Test
	void doesNotRecaptureWhenHeadIsUnchanged(@TempDir Path dir) throws Exception {
		commit(dir, "initial content");
		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		tracker.syncBaselineWithGit(project);
		tracker.syncBaselineWithGit(project);
		tracker.syncBaselineWithGit(project);

		assertThat(baselines.captureCount(project)).isEqualTo(1);
	}

	@Test
	void recapturesAfterACommitMovesHead(@TempDir Path dir) throws Exception {
		commit(dir, "initial content");
		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		tracker.syncBaselineWithGit(project);
		String firstSha = baselines.capturedCommitShaOf(project).orElseThrow();

		commit(dir, "changed content");
		tracker.syncBaselineWithGit(project);

		assertThat(baselines.captureCount(project)).isEqualTo(2);
		assertThat(baselines.capturedCommitShaOf(project)).isPresent().get().isNotEqualTo(firstSha);
	}

	@Test
	void doesNothingForANonGitProject(@TempDir Path dir) throws Exception {
		// no git repository initialized in dir at all
		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		tracker.syncBaselineWithGit(project);

		assertThat(baselines.captureCount(project)).isZero();
	}

	@Test
	void doesNothingForAnUnbornBranch(@TempDir Path dir) throws Exception {
		Git.init().setDirectory(dir.toFile()).call().close();
		// repository exists, but has no commits yet
		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		tracker.syncBaselineWithGit(project);

		assertThat(baselines.captureCount(project)).isZero();
	}

	@Test
	void doesNothingWhenDisabledViaWorkspaceSetting(@TempDir Path dir) throws Exception {
		commit(dir, "initial content");
		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, false);

		tracker.syncBaselineWithGit(project);

		assertThat(baselines.captureCount(project)).isZero();
	}

	@Test
	void doesNothingWhenDisabledViaSystemProperty(@TempDir Path dir) throws Exception {
		System.setProperty(DISABLE_PROPERTY, "true");

		commit(dir, "initial content");
		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		tracker.syncBaselineWithGit(project);

		assertThat(baselines.captureCount(project)).isZero();
	}

	@SuppressWarnings("unchecked")
	@Test
	void indexUpdatesForAffectedProjectsTriggerSync(@TempDir Path dir) throws Exception {
		commit(dir, "initial content");
		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();

		JavaProjectFinder projectFinder = mock(JavaProjectFinder.class);
		doReturn(List.of(project)).when(projectFinder).all();

		SpringSymbolIndex symbolIndex = mock(SpringSymbolIndex.class);
		ArgumentCaptor<Consumer<Set<String>>> listenerCaptor = ArgumentCaptor.forClass(Consumer.class);

		BootJavaConfig config = mock(BootJavaConfig.class);
		when(config.isStructureGitBaselineEnabled()).thenReturn(true);

		new GitBaselineTracker(projectFinder, symbolIndex, config, baselines,
				new WorkingTreeStatus.IndexRelevant(indexWithRealJavaIndexerPredicate()));
		verify(symbolIndex).onUpdate(listenerCaptor.capture());

		// an update for an unrelated project must not trigger anything
		listenerCaptor.getValue().accept(Set.of("some-other-project"));
		assertThat(baselines.captureCount(project)).isZero();

		listenerCaptor.getValue().accept(Set.of(project.getElementName()));
		assertThat(baselines.captureCount(project)).isEqualTo(1);
	}

	private static GitBaselineTracker tracker(IJavaProject project, FakeBaselineAccess baselines, boolean gitBaselineEnabled) {
		JavaProjectFinder projectFinder = mock(JavaProjectFinder.class);
		doReturn(List.of(project)).when(projectFinder).all();

		BootJavaConfig config = mock(BootJavaConfig.class);
		when(config.isStructureGitBaselineEnabled()).thenReturn(gitBaselineEnabled);

		SpringSymbolIndex symbolIndex = mock(SpringSymbolIndex.class);

		// deliberately the real working tree status against the real repository, so these tests
		// cover the actual "is anything the index reads pending?" rule rather than a stand-in
		return new GitBaselineTracker(projectFinder, symbolIndex, config, baselines,
				new WorkingTreeStatus.IndexRelevant(indexWithRealJavaIndexerPredicate()));
	}

	/**
	 * A {@link SpringSymbolIndex} whose Java indexer answers {@code isInterestedIn} with the real
	 * production implementation. That method reads no instance state, so calling it on an
	 * unconstructed mock is safe - and it keeps these tests honest about which files actually
	 * count, instead of restating that rule here.
	 */
	private static SpringSymbolIndex indexWithRealJavaIndexerPredicate() {
		SpringSymbolIndex symbolIndex = mock(SpringSymbolIndex.class);
		when(symbolIndex.getJavaIndexer()).thenReturn(mock(SpringIndexerJava.class, CALLS_REAL_METHODS));
		return symbolIndex;
	}

	private static IJavaProject projectAt(Path dir) {
		IJavaProject project = mock(IJavaProject.class);
		when(project.getElementName()).thenReturn("test-project");
		when(project.getLocationUri()).thenReturn(dir.toUri());
		return project;
	}

	private static void commit(Path dir, String content) throws Exception {
		commit(dir, "content.txt", content);
	}

	/**
	 * Commits the given file, leaving the working tree clean. Which file matters: only paths the
	 * Spring index reads (notably {@code *.java}) keep a baseline from being captured, so
	 * {@code content.txt} is the right fixture whenever a test wants a commit without touching
	 * anything structurally relevant.
	 */
	private static void commit(Path dir, String fileName, String content) throws Exception {
		commit(dir, fileName, content, "test commit");
	}

	private static void commit(Path dir, String fileName, String content, String message) throws Exception {
		boolean firstCommit = !new File(dir.toFile(), ".git").exists();

		try (Git git = firstCommit ? Git.init().setDirectory(dir.toFile()).call() : Git.open(dir.toFile())) {
			Files.writeString(dir.resolve(fileName), content);
			git.add().addFilepattern(".").call();
			git.commit()
					.setMessage(message)
					.setAuthor("Test", "test@example.com")
					.setCommitter("Test", "test@example.com")
					.call();
		}
	}

	/**
	 * Commits whatever is currently in the working tree, initializing the repository first if
	 * needed - for tests that lay out several files (several projects) up front.
	 */
	private static void commitEverything(Path dir, String message) throws Exception {
		boolean firstCommit = !new File(dir.toFile(), ".git").exists();

		try (Git git = firstCommit ? Git.init().setDirectory(dir.toFile()).call() : Git.open(dir.toFile())) {
			git.add().addFilepattern(".").call();
			git.commit()
					.setMessage(message)
					.setAuthor("Test", "test@example.com")
					.setCommitter("Test", "test@example.com")
					.call();
		}
	}

	/**
	 * Writes a file without committing it, leaving the working tree dirty.
	 */
	private static void writeWithoutCommitting(Path dir, String fileName, String content) throws Exception {
		Files.writeString(dir.resolve(fileName), content);
	}

	/**
	 * Hand-rolled fake rather than a Mockito mock, so the assertions read as "how many times, and
	 * with what SHA" rather than a series of verify() calls.
	 */
	private static class FakeBaselineAccess implements GitBaselineTracker.BaselineAccess {

		private final Map<String, String> commitShaByProject = new HashMap<>();
		private final Map<String, String> commitMessageByProject = new HashMap<>();
		private final Map<String, List<String>> capturesByProject = new HashMap<>();

		/**
		 * Mirrors the real store: the most recent sha of a snapshot that <em>represents a commit</em>,
		 * so a later manual snapshot (which has none) doesn't read as "no commit captured yet".
		 */
		@Override
		public Optional<String> capturedCommitShaOf(IJavaProject project) {
			return capturesByProject.getOrDefault(project.getElementName(), List.of()).stream()
					.filter(sha -> sha != null)
					.reduce((first, second) -> second);
		}

		@Override
		public StructureSnapshot captureBaseline(IJavaProject project, String commitSha, String commitMessage) {
			commitShaByProject.put(project.getElementName(), commitSha);
			commitMessageByProject.put(project.getElementName(), commitMessage);
			capturesByProject.computeIfAbsent(project.getElementName(), name -> new ArrayList<>()).add(commitSha);
			return new StructureSnapshot(Instant.now(), commitSha, commitMessage,
					new StructureViewProvider.StructureNode("app", project.getElementName(), null, "application", null, null, null, null, List.of()));
		}

		int captureCount(IJavaProject project) {
			return capturesByProject.getOrDefault(project.getElementName(), List.of()).size();
		}

		Optional<String> capturedCommitMessageOf(IJavaProject project) {
			return Optional.ofNullable(commitMessageByProject.get(project.getElementName()));
		}
	}

}
