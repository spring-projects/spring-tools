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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.StatusCommand;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJava;

/**
 * Tells whether a git working tree currently holds changes that could move something in a
 * project's logical structure. {@link GitBaselineTracker} uses this to decide whether a snapshot
 * taken right now would faithfully represent the commit that {@code HEAD} points at.
 *
 * <p>Exists as an interface so the tracker's capture policy can be tested without building a real
 * repository for every case.
 *
 * @author Martin Lippert
 */
public interface WorkingTreeStatus {

	/**
	 * Whether nothing that the Spring index reads is pending for the given project, i.e. whether
	 * the indexed state of that project on disk is its state at {@code HEAD}.
	 *
	 * <p>Returns {@code false} when that cannot be determined, so an unreadable repository never
	 * causes a snapshot that claims to be a commit without being one.
	 *
	 * @param projectDirectory the project's own directory, so that pending changes elsewhere in the
	 *        repository - a sibling project, an unrelated module - don't count against it
	 */
	boolean isStructureClean(Repository repository, File projectDirectory);

	/**
	 * Judges cleanliness over the source files of that one project that can actually contribute to
	 * its logical structure, and ignores everything else: an edited README, a new scratch file, a
	 * changed CI config, or any pending change outside the project's own directory cannot move a
	 * node in its tree, and must not keep it from ever getting a snapshot.
	 */
	class IndexRelevant implements WorkingTreeStatus {

		private static final Logger log = LoggerFactory.getLogger(IndexRelevant.class);

		private final SpringSymbolIndex symbolIndex;

		public IndexRelevant(SpringSymbolIndex symbolIndex) {
			this.symbolIndex = symbolIndex;
		}

		@Override
		public boolean isStructureClean(Repository repository, File projectDirectory) {
			SpringIndexerJava javaIndexer = symbolIndex.getJavaIndexer();
			if (javaIndexer == null) {
				// no way to tell yet which files matter, so don't risk a baseline that claims to be
				// a commit without being one
				return false;
			}

			try (Git git = new Git(repository)) {
				StatusCommand statusCommand = git.status();

				// a repository can hold far more than this one project (a monorepo, a multi-module
				// build, sibling projects); only this project's own subtree can affect its structure
				String projectPath = repositoryRelativePathOf(repository, projectDirectory);
				if (projectPath != null && !projectPath.isEmpty()) {
					statusCommand.addPath(projectPath);
				}

				Status status = statusCommand.call();

				// git honours .gitignore here, so build output does not show up as untracked
				Set<String> pending = new LinkedHashSet<>();
				pending.addAll(status.getModified());
				pending.addAll(status.getChanged());
				pending.addAll(status.getAdded());
				pending.addAll(status.getRemoved());
				pending.addAll(status.getMissing());
				pending.addAll(status.getUntracked());
				pending.addAll(status.getConflicting());

				// asks the Java indexer itself rather than repeating its list of file extensions, so
				// this stays in step with what actually gets indexed. Deliberately only the Java
				// indexer: the logical structure is built from what Java sources produce, so XML
				// bean definitions and .factories files cannot move a node in it. (Asking every
				// indexer would also be unsafe here - the factories indexer resolves its argument
				// as a URI and throws on the repository-relative paths git status reports.)
				List<String> relevant = pending.stream().filter(javaIndexer::isInterestedIn).toList();

				if (!relevant.isEmpty()) {
					// logged at info, not debug: "why is there no baseline?" is otherwise invisible
					log.info("pending source changes in '{}' keep a logical structure baseline from being captured: {}",
							projectDirectory, relevant);
				}

				return relevant.isEmpty();
			} catch (Exception e) {
				log.warn("failed to read the git status of: " + repository.getDirectory(), e);
				return false;
			}
		}

		/**
		 * The project's path relative to the repository's working tree, in the forward-slash form
		 * git uses - or {@code null} if it can't be expressed that way (the project sits outside the
		 * working tree), in which case the caller falls back to looking at the whole repository.
		 */
		private static String repositoryRelativePathOf(Repository repository, File projectDirectory) {
			try {
				File workTree = repository.getWorkTree();
				if (workTree == null || projectDirectory == null) {
					return null;
				}

				String relative = workTree.toPath().toAbsolutePath().normalize()
						.relativize(projectDirectory.toPath().toAbsolutePath().normalize())
						.toString();

				// a project above/outside the work tree cannot be turned into a git path
				if (relative.startsWith("..")) {
					return null;
				}

				return relative.replace(File.separatorChar, '/');
			} catch (Exception e) {
				log.warn("failed to locate '" + projectDirectory + "' inside the git working tree of "
						+ repository.getDirectory(), e);
				return null;
			}
		}
	}

}
