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
import java.util.List;
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
 * <p>The poll must not, however, be the thing that captures a project's <em>very first</em>
 * baseline: a project can look git-clean (nothing pending on disk) well before its initial indexing
 * has actually finished, since that only reflects git status, not indexing progress. Capturing then
 * would permanently record an empty or partial tree as the baseline for the current commit - "have
 * I already captured this commit?" never revisits it afterward. So the poll only acts on a project
 * once its index has updated at least once; until then, only the index-update listener itself
 * (which by definition fires once indexing genuinely completes) is allowed to capture.
 *
 * <p>{@link #syncBaselineWithGit} can run concurrently for the same project: the poll runs on its
 * own timer thread, while the index-update listener runs on {@code SpringSymbolIndex}'s own worker
 * thread, and both can decide to check a project around the same moment. Its "have I already
 * captured this commit?" check and the capture itself are therefore serialized per project (see
 * {@link #projectLocks}) - without that, two concurrent calls could both read "not captured yet"
 * before either one's capture lands, and both go on to capture the very same commit.
 *
 * <p>Depends on {@link BaselineAccess} and {@link WorkingTreeStatus} rather than on
 * {@link StructureSnapshotStore} and JGit status directly, so the policy in this class can be
 * tested without the rest of the structure-view machinery.
 *
 * <p>Implements {@link AutoCloseable} rather than hooking cleanup to the real LSP {@code shutdown}
 * request: Spring detects and calls {@link #close()} automatically once this bean's application
 * context closes, which happens on normal server shutdown in production, but also - without this
 * class ever needing to know it's under test - whenever a test run closes or evicts the context
 * (the actual LSP {@code shutdown} request is never sent in a unit test, so relying on that alone
 * would leak the poll thread for as long as the test JVM keeps running).
 *
 * @author Martin Lippert
 */
public class GitBaselineTracker implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(GitBaselineTracker.class);

	/**
	 * How often {@code HEAD} is checked for every git-backed project. Resolving {@code HEAD} is a
	 * single small file read, and the costlier working tree scan only runs when it actually moved,
	 * so this can be short enough that a commit is picked up while the tree is still clean.
	 */
	private static final long POLL_SECONDS = 5;

	/**
	 * How long a poll tick waits for the index to settle before giving up on that tick - see
	 * {@link #pollForCommits()}. Generous, because the point is to wait out indexing that is
	 * genuinely in progress; a tick that hits the timeout simply retries {@link #POLL_SECONDS}
	 * later.
	 */
	private static final long INDEX_DRAIN_TIMEOUT_SECONDS = 30;

	private final JavaProjectFinder projectFinder;
	private final SpringSymbolIndex symbolIndex;
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
	 * Names of projects for which at least one index update has been observed since this tracker was
	 * created. Gates {@link #pollForCommits()} - see this class' description for why - but
	 * deliberately not {@link #syncBaselineWithGit}, which the index-update listener itself calls
	 * directly regardless of this set.
	 */
	private final Set<String> indexedProjects = ConcurrentHashMap.newKeySet();

	/**
	 * One lock object per project, created on first use and never removed - see this class'
	 * description for why {@link #syncBaselineWithGit} needs one at all. Cheap to keep around
	 * indefinitely: a handful of bytes per project name, the same simple tradeoff already made for
	 * {@link #repositoriesByProject}.
	 */
	private final Map<String, Object> projectLocks = new ConcurrentHashMap<>();

	/**
	 * The background poll, if one was started - {@code null} otherwise. Stopped by {@link #close()}.
	 */
	private final ScheduledExecutorService poll;

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
		this.symbolIndex = symbolIndex;
		this.config = config;
		this.baselines = baselines;
		this.workingTreeStatus = workingTreeStatus;

		symbolIndex.onUpdate(this::onIndexUpdate);

		if (server != null) {
			this.poll = Executors.newSingleThreadScheduledExecutor(
					r -> new Thread(r, "GitBaselineTracker-poll"));
			// fixed delay rather than fixed rate: a slow working tree scan on a big repository must
			// not let ticks pile up behind each other
			poll.scheduleWithFixedDelay(this::pollForCommits, POLL_SECONDS, POLL_SECONDS, TimeUnit.SECONDS);
			log.debug("started the git baseline poll (every {}s)", POLL_SECONDS);
		}
		else {
			this.poll = null;
			log.debug("no SimpleLanguageServer given - git baseline poll not started");
		}
	}

	/**
	 * Stops the background poll (if one was started) and releases discovered git repository
	 * handles. Not called directly - Spring detects and invokes this automatically when the bean's
	 * application context closes, which is what keeps the poll from ever outliving its context:
	 * in production that's on normal server shutdown, and in tests that's whenever the test
	 * context cache evicts this context (or, at the latest, when the test JVM itself exits) - never
	 * requiring this class to know it's under test at all.
	 */
	@Override
	public void close() {
		if (poll != null) {
			poll.shutdownNow();
		}
		closeRepositories();
	}

	/**
	 * Checks every open project that has completed at least one index update for a commit that has
	 * no baseline yet. Called from the poll, and directly by tests.
	 *
	 * <p>Skips a project with no observed index update yet rather than trusting whatever tree can be
	 * built for it right now - see this class' description for why. Its first baseline is left to
	 * the index-update listener, which fires exactly once its indexing genuinely completes; every
	 * later commit is then fair game for the poll, same as before.
	 *
	 * <p>Waits for the index to finish whatever it has queued before looking at any project. A tick
	 * lands at an arbitrary moment, and the two sides of the capture decision read different
	 * sources: whether to capture is answered from disk (git status), but what gets captured comes
	 * from the in-memory index, which lags disk while a re-index is still queued. Right after a
	 * revert, stash or branch switch, git already reports clean while the index still holds the
	 * pre-change content - capturing in that window records a tree that matches neither state, and
	 * because it is recorded against the current commit, the correct capture that the finishing
	 * index update would otherwise trigger is skipped as "already captured". Draining the queue
	 * first closes that window: by the time the decision is made, the index reflects the same disk
	 * state git was asked about.
	 *
	 * <p>The wait must stay here rather than moving into {@link #syncBaselineWithGit}, which is also
	 * called by {@link #onIndexUpdate} - and that runs on the index's own single worker thread, so
	 * waiting for that same thread's queue from within it would deadlock. It needs no wait anyway:
	 * being called from an index update is itself the signal that the index is up to date.
	 */
	void pollForCommits() {
		if (!isEnabled()) {
			return;
		}

		try {
			List<? extends IJavaProject> eligible = projectFinder.all().stream()
					.filter(project -> indexedProjects.contains(project.getElementName()))
					.toList();

			if (eligible.isEmpty()) {
				return;
			}

			log.debug("poll tick: checking {} indexed project(s) for git commits: {}", eligible.size(),
					eligible.stream().map(IJavaProject::getElementName).toList());

			if (!awaitSettledIndex()) {
				return;
			}

			eligible.forEach(this::syncBaselineWithGit);
		} catch (Exception e) {
			// never let a failing tick kill the poll
			log.warn("failed to check the open projects for new commits", e);
		}
	}

	/**
	 * Blocks until the index has worked off everything queued at the moment of the call - see
	 * {@link #pollForCommits()} for why a tick may not act before that.
	 *
	 * @return {@code false} if the index did not settle within
	 *         {@link #INDEX_DRAIN_TIMEOUT_SECONDS}, meaning the caller should skip this tick rather
	 *         than capture from an index it knows to be behind; the next tick tries again
	 */
	private boolean awaitSettledIndex() {
		try {
			symbolIndex.waitOperation().get(INDEX_DRAIN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
			return true;
		} catch (InterruptedException e) {
			// the poll is being shut down - stop cleanly instead of capturing anything
			Thread.currentThread().interrupt();
			return false;
		} catch (Exception e) {
			log.debug("the index did not settle within {}s - skipping this git baseline poll tick",
					INDEX_DRAIN_TIMEOUT_SECONDS, e);
			return false;
		}
	}

	/**
	 * Captures the baseline for the project if it is git-backed, its {@code HEAD} differs from the
	 * commit its current baseline (if any) was captured at, and the working tree holds no pending
	 * source changes - see this class' description for why all three are required. Safe and cheap
	 * to call redundantly: the common "nothing moved" case costs one small file read.
	 *
	 * <p>Serialized per project (see this class' description) so two calls racing for the same
	 * project - typically the poll and the index-update listener, right as a project's initial
	 * indexing completes - cannot both capture the same commit.
	 */
	public void syncBaselineWithGit(IJavaProject project) {
		if (!isEnabled()) {
			return;
		}

		try {
			synchronized (projectLocks.computeIfAbsent(project.getElementName(), name -> new Object())) {
				Optional<Repository> repository = repositoryOf(project);
				if (repository.isEmpty()) {
					log.trace("project '{}' is not git-backed - nothing to synchronize", project.getElementName());
					return;
				}

				ObjectId head = repository.get().resolve("HEAD");
				if (head == null) {
					// an "unborn" branch - a repository without any commit to snapshot yet
					log.debug("project '{}' has a git repository but no commit yet (unborn branch) - nothing to synchronize",
							project.getElementName());
					return;
				}

				String headSha = head.getName();
				String capturedSha = baselines.capturedCommitShaOf(project).orElse(null);
				if (headSha.equals(capturedSha)) {
					log.trace("project '{}' already has a baseline for HEAD ({}) - nothing to do",
							project.getElementName(), headSha);
					return;
				}

				if (!workingTreeStatus.isStructureClean(repository.get(), new File(project.getLocationUri()))) {
					// snapshotting now would bake the pending changes into the baseline and hide them
					// from every later diff, so leave any existing baseline alone and wait for the
					// next commit
					log.debug("not capturing a logical structure baseline for project '{}' at commit {} - source changes are pending",
							project.getElementName(), headSha);
					return;
				}

				String commitMessage = resolveCommitMessage(repository.get(), head);
				log.debug("HEAD for project '{}' moved to {} ({}) with a clean working tree - capturing a new logical structure baseline",
						project.getElementName(), headSha, commitMessage);
				baselines.captureBaseline(project, headSha, commitMessage);
			}
		} catch (Exception e) {
			log.warn("failed to synchronize logical structure baseline with git for project: " + project.getElementName(), e);
		}
	}

	private void onIndexUpdate(Set<String> affectedProjects) {
		if (affectedProjects == null || affectedProjects.isEmpty()) {
			return;
		}

		// recorded unconditionally, even while disabled: a pure fact about indexing, independent of
		// whether git-driven capture is currently switched on
		indexedProjects.addAll(affectedProjects);

		if (!isEnabled()) {
			return;
		}

		List<? extends IJavaProject> matched = projectFinder.all().stream()
				.filter(project -> affectedProjects.contains(project.getElementName()))
				.toList();

		if (!matched.isEmpty()) {
			log.debug("index update for {} - synchronizing git baseline for {} matching open project(s)",
					affectedProjects, matched.size());
		}

		matched.forEach(this::syncBaselineWithGit);
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

	/**
	 * The short (first-line) message of the given, already-resolved commit. {@code null} (rather
	 * than throwing) if it cannot be read - a missing message is a cosmetic loss, not a reason to
	 * abort a capture that already has everything else it needs.
	 */
	private String resolveCommitMessage(Repository repository, ObjectId commit) {
		try {
			return repository.parseCommit(commit).getShortMessage();
		} catch (Exception e) {
			log.warn("failed to read the commit message for " + commit.getName() + " in " + repository.getDirectory(), e);
			return null;
		}
	}

	/**
	 * Releases the discovered repositories - each one holds on to pack file handles for as long as
	 * it is open, and they are cached for the lifetime of the server.
	 */
	private void closeRepositories() {
		log.debug("closing {} cached git repository handle(s)", repositoriesByProject.size());
		repositoriesByProject.values().forEach(repository -> repository.ifPresent(Repository::close));
		repositoriesByProject.clear();
	}

	private Optional<Repository> repositoryOf(IJavaProject project) {
		return repositoriesByProject.computeIfAbsent(project.getElementName(), name -> discoverRepository(project));
	}

	private Optional<Repository> discoverRepository(IJavaProject project) {
		try {
			File projectDir = new File(project.getLocationUri());
			File gitDir = new FileRepositoryBuilder().findGitDir(projectDir).getGitDir();

			if (gitDir == null) {
				log.debug("no git repository found for project '{}' at {}", project.getElementName(), projectDir);
				return Optional.empty();
			}

			log.debug("discovered git repository for project '{}' at {}", project.getElementName(), gitDir);
			return Optional.of(new FileRepositoryBuilder().setGitDir(gitDir).build());
		} catch (Exception e) {
			log.warn("failed to discover a git repository for project: " + project.getElementName(), e);
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
		 * the given commit SHA and message (either may be {@code null}).
		 */
		StructureSnapshotStore.StructureSnapshot captureBaseline(IJavaProject project, String commitSha, String commitMessage);

	}

}
