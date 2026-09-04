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
	void bootstrapCapturesOnFirstStructureRequest(@TempDir Path dir) throws Exception {
		commit(dir, "initial content");
		IJavaProject project = projectAt(dir);
		FakeBaselineAccess baselines = new FakeBaselineAccess();
		GitBaselineTracker tracker = tracker(project, baselines, true);

		tracker.syncBaselineWithGit(project);

		assertThat(baselines.captureCount(project)).isEqualTo(1);
		assertThat(baselines.capturedCommitShaOf(project)).isPresent();
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
		assertThat(tracker.currentCommitSha(project)).isEmpty();
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
		assertThat(tracker.currentCommitSha(project)).isEmpty();
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

		new GitBaselineTracker(projectFinder, symbolIndex, config, baselines);
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

		SpringSymbolIndex symbolIndex = mock(SpringSymbolIndex.class);

		BootJavaConfig config = mock(BootJavaConfig.class);
		when(config.isStructureGitBaselineEnabled()).thenReturn(gitBaselineEnabled);

		return new GitBaselineTracker(projectFinder, symbolIndex, config, baselines);
	}

	private static IJavaProject projectAt(Path dir) {
		IJavaProject project = mock(IJavaProject.class);
		when(project.getElementName()).thenReturn("test-project");
		when(project.getLocationUri()).thenReturn(dir.toUri());
		return project;
	}

	private static void commit(Path dir, String content) throws Exception {
		boolean firstCommit = !new File(dir.toFile(), ".git").exists();

		try (Git git = firstCommit ? Git.init().setDirectory(dir.toFile()).call() : Git.open(dir.toFile())) {
			Files.writeString(dir.resolve("content.txt"), content);
			git.add().addFilepattern(".").call();
			git.commit()
					.setMessage("test commit")
					.setAuthor("Test", "test@example.com")
					.setCommitter("Test", "test@example.com")
					.call();
		}
	}

	/**
	 * Hand-rolled fake rather than a Mockito mock, so the assertions read as "how many times, and
	 * with what SHA" rather than a series of verify() calls.
	 */
	private static class FakeBaselineAccess implements GitBaselineTracker.BaselineAccess {

		private final Map<String, String> commitShaByProject = new HashMap<>();
		private final Map<String, List<String>> capturesByProject = new HashMap<>();

		@Override
		public Optional<String> capturedCommitShaOf(IJavaProject project) {
			return Optional.ofNullable(commitShaByProject.get(project.getElementName()));
		}

		@Override
		public StructureSnapshot captureBaseline(IJavaProject project, String commitSha) {
			commitShaByProject.put(project.getElementName(), commitSha);
			capturesByProject.computeIfAbsent(project.getElementName(), name -> new ArrayList<>()).add(commitSha);
			return new StructureSnapshot(Instant.now(), commitSha,
					new StructureViewProvider.StructureNode("app", project.getElementName(), null, "application", null, null, null, null, List.of()));
		}

		int captureCount(IJavaProject project) {
			return capturesByProject.getOrDefault(project.getElementName(), List.of()).size();
		}
	}

}
