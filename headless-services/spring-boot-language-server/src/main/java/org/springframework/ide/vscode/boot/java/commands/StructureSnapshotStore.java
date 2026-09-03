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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.ChangeType;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.StructureTreeDiff;
import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider.StructureNode;
import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * Keeps track of the logical structure baseline of projects, so MCP tools and IDE clients can show
 * what changed in a project's logical structure since that baseline was captured.
 *
 * <p>Diffing is entirely on-demand: the "current" structure is (re-)computed fresh every time it's
 * needed (a diff request, or building the structure tree for a client) and compared against the
 * baseline right there - nothing beyond the baseline itself needs to be kept in memory. Baselines
 * are persisted via {@link StructureBaselineStorage}, so they survive a language server restart.
 *
 * <p>Implements {@link GitBaselineTracker.BaselineAccess} so {@link GitBaselineTracker} can drive
 * baseline capture from git activity without this class knowing anything about git.
 *
 * @author Martin Lippert
 */
public class StructureSnapshotStore implements GitBaselineTracker.BaselineAccess {

	private final StructureViewProvider structureViewProvider;
	private final StructureBaselineStorage storage;

	private final Map<String, StructureSnapshot> baselines = new ConcurrentHashMap<>();

	public StructureSnapshotStore(StructureViewProvider structureViewProvider, StructureBaselineStorage storage) {
		this.structureViewProvider = structureViewProvider;
		this.storage = storage;
	}

	/**
	 * Computes the current logical structure of the project and pins it as the baseline to compare
	 * against in {@link #diffAgainstBaseline}. Replaces any baseline previously captured for the
	 * same project. Does not associate the baseline with a git commit - use
	 * {@link #captureBaseline(IJavaProject, String)} for that.
	 */
	public StructureSnapshot captureBaseline(IJavaProject project) {
		return captureBaseline(project, null);
	}

	/**
	 * Same as {@link #captureBaseline(IJavaProject)}, but records the given git commit SHA
	 * alongside the baseline (may be {@code null} if the project isn't git-backed or the commit is
	 * unknown). {@link GitBaselineTracker} uses the recorded SHA to tell whether the project's
	 * {@code HEAD} has moved since this baseline was captured.
	 */
	@Override
	public StructureSnapshot captureBaseline(IJavaProject project, String commitSha) {
		StructureSnapshot snapshot = snapshotNow(project, commitSha);
		String projectName = project.getElementName();

		baselines.put(projectName, snapshot);
		storage.save(projectName, snapshot);

		return snapshot;
	}

	/**
	 * The git commit SHA the project's baseline was captured at, if it has a baseline and that
	 * baseline is associated with a commit.
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
	 * Removes the project's baseline, if any - both from memory and from disk. Purely a manual
	 * undo: for a git-backed project with automatic capture enabled, the next structure request
	 * or index update will simply bootstrap a fresh baseline again, same as if none had ever been
	 * captured.
	 *
	 * @return whether the project actually had a baseline to remove
	 */
	public boolean clearBaseline(IJavaProject project) {
		String projectName = project.getElementName();
		boolean hadBaseline = baselineOf(project) != null;

		baselines.remove(projectName);
		storage.delete(projectName);

		return hadBaseline;
	}

	/**
	 * Diffs the current logical structure of the project against its pinned baseline.
	 *
	 * @return empty when no baseline has been captured yet for this project
	 */
	public Optional<StructureTreeDiff> diffAgainstBaseline(IJavaProject project) {
		StructureSnapshot baseline = baselineOf(project);
		if (baseline == null) {
			return Optional.empty();
		}

		StructureSnapshot current = snapshotNow(project, null);
		return Optional.of(StructureTreeDiffer.diff(project.getElementName(), baseline.capturedAt(),
				current.capturedAt(), baseline.root(), current.root()));
	}

	/**
	 * Annotates the nodes of a freshly built structure tree with how they changed compared to the
	 * pinned baseline of that project, so clients can highlight the changed parts of the tree.
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
	 * The project's baseline, loading it from disk on first touch (per language server run) if it
	 * isn't in memory yet.
	 */
	private StructureSnapshot baselineOf(IJavaProject project) {
		return baselines.computeIfAbsent(project.getElementName(), storage::load);
	}

	private StructureSnapshot snapshotNow(IJavaProject project, String commitSha) {
		JsonNodeHandler.Node root = structureViewProvider.createTree(project, false, null);

		if (root == null) {
			// for Spring Modulith projects the tree cannot be created without the module metadata,
			// so try again and let the provider fetch that metadata first
			root = structureViewProvider.createTree(project, true, null);
		}

		if (root == null) {
			throw new IllegalStateException("no logical structure available for project with name " + project.getElementName());
		}

		return new StructureSnapshot(Instant.now(), commitSha, StructureViewProvider.toStructureNode(root));
	}

	public static record StructureSnapshot(Instant capturedAt, String commitSha, StructureNode root) {

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
