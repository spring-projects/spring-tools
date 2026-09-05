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

import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
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
	 * Whether nothing that the Spring index reads is pending in the given repository's working
	 * tree, i.e. whether the indexed state on disk is the state of {@code HEAD}.
	 *
	 * <p>Returns {@code false} when that cannot be determined, so an unreadable repository never
	 * causes a snapshot that claims to be a commit without being one.
	 */
	boolean isStructureClean(Repository repository);

	/**
	 * Judges cleanliness over the source files that can actually contribute to the logical
	 * structure, and ignores everything else: an edited README, a new scratch file or a changed CI
	 * config cannot move a node in the tree, and must not keep a project from ever getting a
	 * snapshot.
	 */
	class IndexRelevant implements WorkingTreeStatus {

		private static final Logger log = LoggerFactory.getLogger(IndexRelevant.class);

		private final SpringSymbolIndex symbolIndex;

		public IndexRelevant(SpringSymbolIndex symbolIndex) {
			this.symbolIndex = symbolIndex;
		}

		@Override
		public boolean isStructureClean(Repository repository) {
			SpringIndexerJava javaIndexer = symbolIndex.getJavaIndexer();
			if (javaIndexer == null) {
				// no way to tell yet which files matter, so don't risk a baseline that claims to be
				// a commit without being one
				return false;
			}

			try (Git git = new Git(repository)) {
				Status status = git.status().call();

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
				return pending.stream().noneMatch(javaIndexer::isInterestedIn);
			} catch (Exception e) {
				log.warn("failed to read the git status of: " + repository.getDirectory(), e);
				return false;
			}
		}
	}

}
