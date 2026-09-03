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

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;

/**
 * Drives logical structure baseline capture from git activity, so users get diffs against "since
 * my last commit" without ever having to manually capture a baseline.
 *
 * <p>For every git-backed project, a baseline is captured the first time its structure is looked
 * at (so diffs are available from the very next commit onward, with no setup), and re-captured
 * whenever {@code HEAD} moves - a commit, checkout, merge, rebase or pull. A {@code git stash}
 * (working tree changes, {@code HEAD} unchanged) correctly does not trigger a recapture. No state
 * beyond the baseline itself needs to survive a restart: the baseline records the commit SHA it
 * was captured at, and that recorded SHA is exactly what "have I already captured for this
 * commit?" is checked against.
 *
 * <p>Depends on {@link BaselineAccess} rather than {@link StructureSnapshotStore} directly, so the
 * git-driven policy in this class can be tested without the rest of the structure-view machinery.
 *
 * @author Martin Lippert
 */
public class GitBaselineTracker {

	private static final Logger log = LoggerFactory.getLogger(GitBaselineTracker.class);

	private final JavaProjectFinder projectFinder;
	private final BootJavaConfig config;
	private final BaselineAccess baselines;

	/**
	 * Discovered repository per project, cached for the lifetime of the language server. A project
	 * without a repo is cached as {@link Optional#empty()} too, so a repo added to an already-open
	 * project only takes effect after a restart - a deliberately simple tradeoff for now.
	 */
	private final Map<String, Optional<Repository>> repositoriesByProject = new ConcurrentHashMap<>();

	public GitBaselineTracker(JavaProjectFinder projectFinder, SpringSymbolIndex symbolIndex, BootJavaConfig config,
			BaselineAccess baselines) {
		this.projectFinder = projectFinder;
		this.config = config;
		this.baselines = baselines;

		symbolIndex.onUpdate(this::onIndexUpdate);
	}

	/**
	 * Resolves the project's current git {@code HEAD} commit, if it has a repository and at least
	 * one commit. Read-only - does not capture or change anything.
	 */
	public Optional<String> currentCommitSha(IJavaProject project) {
		return isEnabled() ? repositoryOf(project).map(this::resolveHead) : Optional.empty();
	}

	/**
	 * Captures (or re-captures) the baseline for the project if it is git-backed and its
	 * {@code HEAD} differs from the commit its current baseline (if any) was captured at. Safe and
	 * cheap to call redundantly - e.g. once when a project's structure is first requested, and
	 * again on every subsequent index update.
	 */
	public void syncBaselineWithGit(IJavaProject project) {
		if (!isEnabled()) {
			return;
		}

		try {
			Optional<String> headSha = currentCommitSha(project);
			if (headSha.isEmpty()) {
				return;
			}

			String capturedSha = baselines.capturedCommitShaOf(project).orElse(null);
			if (!headSha.get().equals(capturedSha)) {
				baselines.captureBaseline(project, headSha.get());
			}
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
