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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.ChangeType;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.StructureTreeDiff;
import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider.StructureNode;
import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * Keeps track of the logical structure baseline history of projects, so MCP tools and IDE clients
 * can show what changed in a project's logical structure since the most recent baseline, and
 * recognize what past commits are still available.
 *
 * <p>Diffing is entirely on-demand: the "current" structure is (re-)computed fresh every time it's
 * needed (a diff request, or building the structure tree for a client) and compared against the
 * most recent baseline right there - nothing beyond the retained history itself needs to be kept in
 * memory. That history is persisted via {@link StructureBaselineStorage}, so it survives a language
 * server restart.
 *
 * <p>Up to {@link BootJavaConfig#getStructureBaselineHistorySize()} snapshots are retained per
 * project, newest first; capturing one more than that drops the oldest. The most recent one is
 * always what {@link #diffAgainstBaseline} and {@link #annotateWithChangesSinceBaseline} compare
 * against - older ones are kept only so a past commit's snapshot can still be recognized (by its
 * commit message) later.
 *
 * <p>Implements {@link GitBaselineTracker.BaselineAccess} so {@link GitBaselineTracker} can drive
 * baseline capture from git activity without this class knowing anything about git.
 *
 * @author Martin Lippert
 */
public class StructureSnapshotStore implements GitBaselineTracker.BaselineAccess {

	private final StructureViewProvider structureViewProvider;
	private final StructureBaselineStorage storage;
	private final BootJavaConfig config;

	/**
	 * Retained snapshot history per project, newest first. An empty list (rather than a missing
	 * entry) represents "no baseline yet", so this caches just as well as "has a baseline" -
	 * unlike a plain {@code Map<String, StructureSnapshot>}, which cannot cache a {@code null}.
	 */
	private final Map<String, List<StructureSnapshot>> history = new ConcurrentHashMap<>();

	public StructureSnapshotStore(StructureViewProvider structureViewProvider, StructureBaselineStorage storage,
			BootJavaConfig config) {
		this.structureViewProvider = structureViewProvider;
		this.storage = storage;
		this.config = config;
	}

	/**
	 * Computes the current logical structure of the project and pins it as the baseline to compare
	 * against in {@link #diffAgainstBaseline}. Does not associate the baseline with a git commit -
	 * use {@link #captureBaseline(IJavaProject, String, String)} for that.
	 */
	public StructureSnapshot captureBaseline(IJavaProject project) {
		return captureBaseline(project, null, null);
	}

	/**
	 * Same as {@link #captureBaseline(IJavaProject)}, but records the given git commit SHA and
	 * message alongside the baseline (both may be {@code null} if the project isn't git-backed or
	 * the commit is unknown). {@link GitBaselineTracker} uses the recorded SHA to tell whether the
	 * project's {@code HEAD} has moved since the most recent baseline was captured.
	 *
	 * <p>Prepends the new snapshot to the project's retained history and trims it down to
	 * {@link BootJavaConfig#getStructureBaselineHistorySize()} entries, dropping the oldest ones.
	 * Read-modify-write, so this method is synchronized; captures happen at most once per poll tick
	 * per project, never a hot path, so a coarse lock is simplest and sufficient.
	 */
	@Override
	public synchronized StructureSnapshot captureBaseline(IJavaProject project, String commitSha, String commitMessage) {
		StructureSnapshot snapshot = snapshotNow(project, commitSha, commitMessage);
		String projectName = project.getElementName();

		List<StructureSnapshot> updated = new ArrayList<>(historyOf(project));
		updated.add(0, snapshot);

		int maxSize = config.getStructureBaselineHistorySize();
		while (updated.size() > maxSize) {
			updated.remove(updated.size() - 1);
		}

		history.put(projectName, List.copyOf(updated));
		storage.save(projectName, updated);

		return snapshot;
	}

	/**
	 * The git commit SHA the project's most recent baseline was captured at, if it has one and it
	 * is associated with a commit.
	 */
	@Override
	public Optional<String> capturedCommitShaOf(IJavaProject project) {
		StructureSnapshot baseline = baselineOf(project);
		return baseline == null ? Optional.empty() : Optional.ofNullable(baseline.commitSha());
	}

	/**
	 * Whether a baseline has been captured for the project, regardless of whether anything has
	 * changed since then.
	 */
	public boolean hasBaseline(IJavaProject project) {
		return baselineOf(project) != null;
	}

	/**
	 * The project's retained baseline history, newest first, so a client can recognize which past
	 * commits still have a snapshot. Empty if none has been captured yet.
	 */
	public List<StructureSnapshot> historyOf(IJavaProject project) {
		return history.computeIfAbsent(project.getElementName(), name -> storage.load(name));
	}

	/**
	 * Removes the project's entire retained baseline history, if any - both from memory and from
	 * disk. Purely a manual undo: for a git-backed project with automatic capture enabled, a fresh
	 * baseline is captured again as soon as the working tree holds no pending source changes, same
	 * as if none had ever been captured. While changes are pending, the project stays without a
	 * baseline until its next commit.
	 *
	 * @return whether the project actually had a baseline to remove
	 */
	public boolean clearBaseline(IJavaProject project) {
		String projectName = project.getElementName();
		boolean hadBaseline = baselineOf(project) != null;

		history.remove(projectName);
		storage.delete(projectName);

		return hadBaseline;
	}

	/**
	 * Diffs the current logical structure of the project against its most recent baseline.
	 *
	 * @return empty when no baseline has been captured yet for this project
	 */
	public Optional<StructureTreeDiff> diffAgainstBaseline(IJavaProject project) {
		StructureSnapshot baseline = baselineOf(project);
		if (baseline == null) {
			return Optional.empty();
		}

		StructureSnapshot current = snapshotNow(project, null, null);
		return Optional.of(StructureTreeDiffer.diff(project.getElementName(), baseline.capturedAt(),
				current.capturedAt(), baseline.root(), current.root()));
	}

	/**
	 * Annotates the nodes of a freshly built structure tree with how they changed compared to the
	 * most recent baseline of that project, so clients can highlight the changed parts of the tree.
	 *
	 * <p>Does nothing when no baseline was captured for the project.
	 *
	 * @param root the root of the tree to annotate, modified in place
	 */
	public void annotateWithChangesSinceBaseline(IJavaProject project, JsonNodeHandler.Node root) {
		StructureSnapshot baseline = baselineOf(project);
		if (baseline == null || root == null) {
			return;
		}

		StructureTreeDiff diff = StructureTreeDiffer.diff(project.getElementName(), baseline.capturedAt(),
				Instant.now(), baseline.root(), StructureViewProvider.toStructureNode(root));

		Map<String, ChangeType> changes = StructureTreeDiffer.changesByNodeId(diff.root());
		if (!changes.isEmpty()) {
			applyChanges(root, changes);
		}
	}

	private static void applyChanges(JsonNodeHandler.Node node, Map<String, ChangeType> changes) {
		Object nodeId = node.getAttribute(JsonNodeHandler.NODE_ID);
		ChangeType change = nodeId == null ? null : changes.get(nodeId.toString());

		if (change != null) {
			node.withAttribute(JsonNodeHandler.CHANGE, change.name().toLowerCase());
		}

		node.getChildren().forEach(child -> applyChanges(child, changes));
	}

	/**
	 * The project's most recent baseline - the newest entry of its retained history - loading that
	 * history from disk on first touch (per language server run) if it isn't in memory yet.
	 */
	private StructureSnapshot baselineOf(IJavaProject project) {
		List<StructureSnapshot> snapshots = historyOf(project);
		return snapshots.isEmpty() ? null : snapshots.get(0);
	}

	private StructureSnapshot snapshotNow(IJavaProject project, String commitSha, String commitMessage) {
		JsonNodeHandler.Node root = structureViewProvider.createTree(project, false, null);

		if (root == null) {
			// for Spring Modulith projects the tree cannot be created without the module metadata,
			// so try again and let the provider fetch that metadata first
			root = structureViewProvider.createTree(project, true, null);
		}

		if (root == null) {
			throw new IllegalStateException("no logical structure available for project with name " + project.getElementName());
		}

		return new StructureSnapshot(Instant.now(), commitSha, commitMessage, StructureViewProvider.toStructureNode(root));
	}

	public static record StructureSnapshot(Instant capturedAt, String commitSha, String commitMessage, StructureNode root) {

		public int nodeCount() {
			return nodeCount(root);
		}

		private static int nodeCount(StructureNode node) {
			int count = 1;
			for (StructureNode child : node.children()) {
				count += nodeCount(child);
			}
			return count;
		}
	}

}
