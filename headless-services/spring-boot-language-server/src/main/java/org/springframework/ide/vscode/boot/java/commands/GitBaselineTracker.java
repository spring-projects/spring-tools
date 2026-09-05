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

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

/**
 * Drives logical structure baseline capture from git activity, so users get diffs against "since
 * my last commit" without ever having to manually capture a baseline.
 *
 * <p>Every stored baseline represents the state of one specific commit, which is what makes the
 * diffs mean "changed since that commit". A baseline is therefore captured only while the working
 * tree holds no pending source changes - then the state on disk <i>is</i> the state of
 * {@code HEAD}. Concretely, for every git-backed project:
 *
 * <ul>
 * <li>{@code HEAD} still matches the stored baseline's commit: nothing to do.
 * <li>{@code HEAD} moved (a commit, checkout, merge, rebase or pull) and nothing the index reads is
 * pending: capture, recording that commit.
 * <li>{@code HEAD} moved but source changes are pending: capture nothing and wait for the next
 * commit. Snapshotting now would bake those changes into the baseline and so hide them from every
 * later diff. This is also what happens when a project is opened for the first time with pending
 * changes: no baseline until the next commit.
 * </ul>
 *
 * <p>A {@code git stash} (working tree changes, {@code HEAD} unchanged) correctly triggers nothing.
 * No state beyond the baseline itself needs to survive a restart: the baseline records the commit
 * SHA it was captured at, and that recorded SHA is exactly what "have I already captured for this
 * commit?" is checked against.
 *
 * <p>Commits are noticed by polling {@code HEAD}, because committing changes no file and so
 * produces no index update to react to. Without the poll, the first look after a commit would
 * typically come once the user has started editing again - too late, since the tree is dirty by
 * then and the commit would never get a baseline.
 *
 * <p>Depends on {@link BaselineAccess} and {@link WorkingTreeStatus} rather than on
 * {@link StructureSnapshotStore} and JGit status directly, so the policy in this class can be
 * tested without the rest of the structure-view machinery.
 *
 * @author Martin Lippert
 */
public class GitBaselineTracker {

	private static final Logger log = LoggerFactory.getLogger(GitBaselineTracker.class);

	/**
	 * How often {@code HEAD} is checked for every git-backed project. Resolving {@code HEAD} is a
	 * single small file read, and the costlier working tree scan only runs when it actually moved,
	 * so this can be short enough that a commit is picked up while the tree is still clean.
	 */
	private static final long POLL_SECONDS = 5;

	private final JavaProjectFinder projectFinder;
	private final BootJavaConfig config;
	private final BaselineAccess baselines;
	private final WorkingTreeStatus workingTreeStatus;

	/**
	 * Discovered repository per project, cached for the lifetime of the language server. A project
	 * without a repo is cached as {@link Optional#empty()} too, so a repo added to an already-open
	 * project only takes effect after a restart - a deliberately simple tradeoff for now.
	 */
	private final Map<String, Optional<Repository>> repositoriesByProject = new ConcurrentHashMap<>();

	/**
	 * Without a {@link SimpleLanguageServer} no poll is started, which is what the tests use to
	 * drive {@link #pollForCommits()} themselves instead of waiting for a timer.
	 */
	public GitBaselineTracker(JavaProjectFinder projectFinder, SpringSymbolIndex symbolIndex, BootJavaConfig config,
			BaselineAccess baselines, WorkingTreeStatus workingTreeStatus) {
		this(projectFinder, symbolIndex, config, baselines, workingTreeStatus, null);
	}

	public GitBaselineTracker(JavaProjectFinder projectFinder, SpringSymbolIndex symbolIndex, BootJavaConfig config,
			BaselineAccess baselines, WorkingTreeStatus workingTreeStatus, SimpleLanguageServer server) {
		this.projectFinder = projectFinder;
		this.config = config;
		this.baselines = baselines;
		this.workingTreeStatus = workingTreeStatus;

		symbolIndex.onUpdate(this::onIndexUpdate);

		if (server != null) {
			ScheduledExecutorService poll = Executors.newSingleThreadScheduledExecutor(
					r -> new Thread(r, "GitBaselineTracker-poll"));
			// fixed delay rather than fixed rate: a slow working tree scan on a big repository must
			// not let ticks pile up behind each other
			poll.scheduleWithFixedDelay(this::pollForCommits, POLL_SECONDS, POLL_SECONDS, TimeUnit.SECONDS);
			server.onShutdown(poll::shutdownNow);
		}
	}

	/**
	 * Checks every open project for a commit that has no baseline yet. Called from the poll, and
	 * directly by tests.
	 */
	void pollForCommits() {
		if (!isEnabled()) {
			return;
		}

		try {
			projectFinder.all().forEach(this::syncBaselineWithGit);
		} catch (Exception e) {
			// never let a failing tick kill the poll
			log.warn("failed to check the open projects for new commits", e);
		}
	}

	/**
	 * Resolves the project's current git {@code HEAD} commit, if it has a repository and at least
	 * one commit. Read-only - does not capture or change anything.
	 */
	public Optional<String> currentCommitSha(IJavaProject project) {
		return isEnabled() ? repositoryOf(project).map(this::resolveHead) : Optional.empty();
	}

	/**
	 * Captures the baseline for the project if it is git-backed, its {@code HEAD} differs from the
	 * commit its current baseline (if any) was captured at, and the working tree holds no pending
	 * source changes - see this class' description for why all three are required. Safe and cheap
	 * to call redundantly: the common "nothing moved" case costs one small file read.
	 */
	public void syncBaselineWithGit(IJavaProject project) {
		if (!isEnabled()) {
			return;
		}

		try {
			Optional<Repository> repository = repositoryOf(project);
			if (repository.isEmpty()) {
				return;
			}

			String headSha = resolveHead(repository.get());
			if (headSha == null) {
				// an "unborn" branch - a repository without any commit to snapshot yet
				return;
			}

			String capturedSha = baselines.capturedCommitShaOf(project).orElse(null);
			if (headSha.equals(capturedSha)) {
				return;
			}

			if (!workingTreeStatus.isStructureClean(repository.get())) {
				// snapshotting now would bake the pending changes into the baseline and hide them
				// from every later diff, so leave any existing baseline alone and wait for the
				// next commit
				log.debug("not capturing a logical structure baseline for project '{}' at commit {} - source changes are pending",
						project.getElementName(), headSha);
				return;
			}

			baselines.captureBaseline(project, headSha);
		} catch (Exception e) {
			log.warn("failed to synchronize logical structure baseline with git for project: " + project.getElementName(), e);
		}
	}

	private void onIndexUpdate(Set<String> affectedProjects) {
		if (!isEnabled() || affectedProjects == null || affectedProjects.isEmpty()) {
			return;
		}

		for (String projectName : affectedProjects) {
			projectFinder.all().stream()
					.filter(p -> p.getElementName().equals(projectName))
					.findFirst()
					.ifPresent(this::syncBaselineWithGit);
		}
	}

	/**
	 * On top of the {@code boot-java.structure.git-baseline-enabled} workspace setting, this
	 * system property lets the test suite opt out entirely - test project fixtures live inside
	 * this repository's own git working tree, so without it, every test that builds a structure
	 * tree would auto-capture a baseline against *this repository's* real git history, which has
	 * nothing to do with what any of those tests are actually about.
	 */
	private boolean isEnabled() {
		return config.isStructureGitBaselineEnabled() && System.getProperty("disable-structure-git-baseline") == null;
	}

	private String resolveHead(Repository repository) {
		try {
			ObjectId head = repository.resolve("HEAD");
			// null HEAD means an "unborn" branch - a repo with no commits yet
			return head == null ? null : head.getName();
		} catch (Exception e) {
			log.warn("failed to resolve HEAD for git repository: " + repository.getDirectory(), e);
			return null;
		}
	}

	private Optional<Repository> repositoryOf(IJavaProject project) {
		return repositoriesByProject.computeIfAbsent(project.getElementName(), name -> discoverRepository(project));
	}

	private Optional<Repository> discoverRepository(IJavaProject project) {
		try {
			File projectDir = new File(project.getLocationUri());
			File gitDir = new FileRepositoryBuilder().findGitDir(projectDir).getGitDir();

			return gitDir == null ? Optional.empty() : Optional.of(new FileRepositoryBuilder().setGitDir(gitDir).build());
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	/**
	 * The narrow slice of {@link StructureSnapshotStore} that this class needs, so its git-driven
	 * capture policy can be exercised in tests without the rest of the structure-view machinery.
	 */
	public interface BaselineAccess {

		/**
		 * The git commit SHA the project's current baseline was captured at, if it has a baseline
		 * and that baseline is associated with a commit.
		 */
		Optional<String> capturedCommitShaOf(IJavaProject project);

		/**
		 * Captures the project's current logical structure as its new baseline, associated with
		 * the given commit SHA (may be {@code null}).
		 */
		StructureSnapshotStore.StructureSnapshot captureBaseline(IJavaProject project, String commitSha);

	}

}
