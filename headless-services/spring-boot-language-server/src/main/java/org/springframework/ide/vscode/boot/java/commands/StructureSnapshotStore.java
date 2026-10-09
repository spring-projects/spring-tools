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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.ChangeType;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.DiffNode;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.StructureTreeDiff;
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
 * project, newest first; capturing one more than that drops the oldest. The most recent one is what
 * {@link #diffAgainstBaseline} and {@link #annotateWithChangesSinceBaseline} compare against by
 * default - older ones are kept so a client can list them (a commit's sha and message, or the
 * capture time of a manual snapshot) and ask to compare against one of them instead.
 *
 * <p>Implements {@link GitBaselineTracker.BaselineAccess} so {@link GitBaselineTracker} can drive
 * baseline capture from git activity without this class knowing anything about git.
 *
 * @author Martin Lippert
 */
public class StructureSnapshotStore implements GitBaselineTracker.BaselineAccess {

	private static final Logger log = LoggerFactory.getLogger(StructureSnapshotStore.class);

	private final StructureViewProvider structureViewProvider;
	private final StructureBaselineStorage storage;
	private final BootJavaConfig config;

	/**
	 * Retained snapshot history per project, newest first. An empty list (rather than a missing
	 * entry) represents "no baseline yet", so this caches just as well as "has a baseline" -
	 * unlike a plain {@code Map<String, StructureSnapshot>}, which cannot cache a {@code null}.
	 */
	private final Map<String, List<StructureSnapshot>> history = new ConcurrentHashMap<>();

	/**
	 * The commit each project's baseline was last checked against, loaded along with its history -
	 * see {@link #capturedCommitShaOf}. Empty when none was recorded.
	 */
	private final Map<String, Optional<String>> checkedCommits = new ConcurrentHashMap<>();

	public StructureSnapshotStore(StructureViewProvider structureViewProvider, StructureBaselineStorage storage,
			BootJavaConfig config) {
		this.structureViewProvider = structureViewProvider;
		this.storage = storage;
		this.config = config;
	}

	/**
	 * Computes the current logical structure of the project and pins it as the baseline to compare
	 * against in {@link #diffAgainstBaseline}.
	 *
	 * <p>Deliberately records no commit information: this is what a <i>manual</i> capture uses, and
	 * a manual capture is normally taken over uncommitted work, so it does not represent any commit
	 * even though some commit happens to be checked out. {@link GitBaselineTracker} uses
	 * {@link #captureBaseline(IJavaProject, String, String)} for the snapshots that do.
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
	public StructureSnapshot captureBaseline(IJavaProject project, String commitSha, String commitMessage) {
		// deliberately outside the lock: building the tree is by far the expensive part here, and
		// nothing about it depends on the retained history
		StructureSnapshot snapshot = snapshotNow(project, commitSha, commitMessage);
		String projectName = project.getElementName();

		List<StructureSnapshot> updated;
		synchronized (this) {
			List<StructureSnapshot> current = historyOf(project);

			// GH-2021: a commit that changes nothing about the project's structure -
			// one that touches other projects of the same repository, a checkout, a pull - gets no
			// snapshot of its own. The current baseline stands for that commit just as well, and keeps
			// its own commit: the one that brought the structure in. Only the check is recorded, so the
			// commit is not looked at again. A manual capture is always kept: it was asked for.
			if (commitSha != null && !current.isEmpty() && current.get(0).elements().hasSameStructureAs(snapshot.elements())) {
				checkedCommits.put(projectName, Optional.of(commitSha));
				storage.saveCheckedCommit(project, commitSha);

				log.info("logical structure of project '{}' at commit {} is unchanged since its baseline{} - keeping the retained history as it is",
						projectName, commitSha, current.get(0).commitSha() == null ? " (manual)" : " at commit " + current.get(0).commitSha());
				return current.get(0);
			}

			updated = new ArrayList<>(current);
			updated.add(0, snapshot);

			int maxSize = config.getStructureBaselineHistorySize();
			while (updated.size() > maxSize) {
				updated.remove(updated.size() - 1);
			}

			history.put(projectName, List.copyOf(updated));
			if (commitSha != null) {
				checkedCommits.put(projectName, Optional.of(commitSha));
			}
		}

		storage.save(project, updated);
		if (commitSha != null) {
			storage.saveCheckedCommit(project, commitSha);
		}

		log.info("captured logical structure baseline for project '{}'{}, {} element(s), retaining {} of up to {} snapshot(s)",
				projectName, commitSha == null ? " (manual, no commit)" : " at commit " + commitSha,
				snapshot.elementCount(), updated.size(), config.getStructureBaselineHistorySize());

		return snapshot;
	}

	/**
	 * The git commit SHA the project's baseline was last checked against, which is what answers
	 * "have I already captured a baseline for this commit?". That is the most recent snapshot's
	 * commit - or a later one that changed nothing about the project's structure, and so got no
	 * snapshot of its own (see {@link #captureBaseline(IJavaProject, String, String)}).
	 *
	 * <p>Without a recorded check - a history written before checks were recorded - the most recent
	 * snapshot that represents a commit. Deliberately skipping manual snapshots rather than just
	 * looking at the newest one: a manual snapshot carries no commit, so treating it as "no commit
	 * captured yet" would have the tracker immediately capture a duplicate of a commit it already
	 * has - and push the user's manual snapshot out of the way seconds after they took it.
	 */
	@Override
	public Optional<String> capturedCommitShaOf(IJavaProject project) {
		List<StructureSnapshot> snapshots = historyOf(project);

		Optional<String> checked = checkedCommits.getOrDefault(project.getElementName(), Optional.empty());
		if (checked.isPresent()) {
			return checked;
		}

		return snapshots.stream()
				.map(StructureSnapshot::commitSha)
				.filter(sha -> sha != null)
				.findFirst();
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
		// copied on load: what the storage hands back is a plain mutable list, and this one is
		// cached and handed out to callers
		return history.computeIfAbsent(project.getElementName(), name -> {
			List<StructureSnapshot> loaded = List.copyOf(storage.load(project));
			// loaded together with the history - and only with a history: there is nothing to have
			// checked against without one
			checkedCommits.put(name, loaded.isEmpty() ? Optional.empty() : Optional.ofNullable(storage.loadCheckedCommit(project)));
			log.debug("loaded {} retained logical structure baseline snapshot(s) from disk for project '{}'",
					loaded.size(), name);
			return loaded;
		});
	}

	/**
	 * Same as {@link #historyOf(IJavaProject)}, mapped to the lightweight {@link BaselineHistoryEntry}
	 * DTO - without the structure tree itself, which callers that just want to list or pick a
	 * snapshot (an IDE QuickPick, an MCP tool) have no use for.
	 */
	public List<BaselineHistoryEntry> historyEntriesOf(IJavaProject project) {
		return historyOf(project).stream()
				.map(snapshot -> new BaselineHistoryEntry(snapshot.commitSha(), snapshot.commitMessage(),
						keyOf(snapshot), snapshot.elementCount()))
				.toList();
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
		checkedCommits.remove(projectName);
		storage.delete(project);

		log.info("cleared logical structure baseline history for project '{}' (had a baseline: {})",
				projectName, hadBaseline);

		return hadBaseline;
	}

	/**
	 * Diffs the current logical structure of the project against its most recent baseline, both
	 * rendered with every group the project's catalog has - the same tree shape a baseline is
	 * always captured with, so nothing about the group selection can affect this comparison.
	 *
	 * @return empty when no baseline has been captured yet for this project
	 */
	public Optional<StructureTreeDiff> diffAgainstBaseline(IJavaProject project) {
		StructureSnapshot baseline = baselineOf(project);
		if (baseline == null) {
			return Optional.empty();
		}

		JsonNodeHandler.Node baselineTree = structureViewProvider.createTree(project,
				new SnapshotStructureElements(baseline.elements()), null, false);
		JsonNodeHandler.Node currentTree = structureViewProvider.createCompleteTree(project);
		Instant currentAt = Instant.now();

		return Optional.of(StructureTreeDiffer.diff(project.getElementName(), baseline.capturedAt(), currentAt,
				StructureViewProvider.toComparableNode(baselineTree), StructureViewProvider.toComparableNode(currentTree)));
	}

	/**
	 * Annotates the nodes of a freshly built structure tree with how they changed compared to a
	 * baseline of that project, so clients can highlight the changed parts of the tree.
	 *
	 * <p>Does nothing when no baseline was captured for the project.
	 *
	 * @param root the root of the tree to annotate, modified in place
	 * @param snapshotKey the key of the retained snapshot to compare against (its capture time, as
	 *        reported by {@link BaselineHistoryEntry#capturedAt()}), or {@code null} for the most
	 *        recent one. Falls back to the most recent one if that snapshot is no longer retained
	 *        (evicted, or simply unknown) - failing open to the default view rather than showing
	 *        nothing.
	 * @param selectedGroups the same group selection {@code root} was built with - the baseline is
	 *        rebuilt into a tree with the exact same selection, which is what stops a group
	 *        selection from ever causing a spurious change (see
	 *        {@code docs/structure-diff-elements.md}); {@code null} means every group, same
	 *        convention as {@link StructureViewProvider#createTree}
	 * @return the baseline snapshot actually compared against, so the caller can report it (e.g. in
	 *         a tooltip) without a second lookup - {@code null} if the project has no baseline at all
	 */
	public StructureSnapshot annotateWithChangesSinceBaseline(IJavaProject project, JsonNodeHandler.Node root,
			String snapshotKey, Collection<String> selectedGroups) {

		StructureSnapshot baseline = baselineOf(project, snapshotKey);
		if (baseline == null || root == null) {
			return baseline;
		}

		JsonNodeHandler.Node baselineTree = structureViewProvider.createTree(project,
				new SnapshotStructureElements(baseline.elements()), selectedGroups, false);

		DiffNode diff = StructureTreeDiffer.diffTree(StructureViewProvider.toComparableNode(baselineTree), StructureViewProvider.toComparableNode(root));

		Map<String, ChangeType> changes = StructureTreeDiffer.changesByNodeId(diff);
		if (!changes.isEmpty()) {
			applyChanges(root, changes);
		}

		return baseline;
	}

	private static void applyChanges(JsonNodeHandler.Node node, Map<String, ChangeType> changes) {
		Object nodeId = node.getAttribute(JsonNodeHandler.NODE_ID);
		ChangeType change = nodeId == null ? null : changes.get(nodeId.toString());

		if (change != null) {
			node.withAttribute(JsonNodeHandler.CHANGE, change.label());
		}

		node.getChildren().forEach(child -> applyChanges(child, changes));
	}

	/**
	 * The project's most recent baseline - the newest entry of its retained history - loading that
	 * history from disk on first touch (per language server run) if it isn't in memory yet.
	 */
	private StructureSnapshot baselineOf(IJavaProject project) {
		return baselineOf(project, null);
	}

	/**
	 * Same as {@link #baselineOf(IJavaProject)}, except a non-null {@code snapshotKey} looks up that
	 * one specific snapshot in the retained history instead of the most recent one - falling back to
	 * the most recent one if it isn't retained anymore (or never existed).
	 *
	 * <p>Keyed by {@link StructureSnapshot#capturedAt()} rather than by commit sha, because manual
	 * snapshots have no commit and still need to be selectable - and because two snapshots can share
	 * a commit while the capture time identifies exactly one.
	 */
	public StructureSnapshot baselineOf(IJavaProject project, String snapshotKey) {
		List<StructureSnapshot> snapshots = historyOf(project);
		if (snapshots.isEmpty()) {
			return null;
		}
		if (snapshotKey == null) {
			return snapshots.get(0);
		}
		return snapshots.stream()
				.filter(snapshot -> snapshotKey.equals(keyOf(snapshot)))
				.findFirst()
				.orElseGet(() -> snapshots.get(0));
	}

	/**
	 * The stable key a client uses to ask for one specific retained snapshot - see
	 * {@link #baselineOf(IJavaProject, String)} for why it is the capture time.
	 */
	private static String keyOf(StructureSnapshot snapshot) {
		return snapshot.capturedAt() == null ? null : snapshot.capturedAt().toString();
	}

	/**
	 * Captures every type, method and member of the project unconditionally - a snapshot has no
	 * group selection of its own to be filtered by, and needs no Spring Modulith metadata either,
	 * unlike building a tree for display does. Diffing it later rebuilds it into a tree with
	 * whatever settings the *other* side of that diff was built with (see
	 * {@link #annotateWithChangesSinceBaseline}), so it does not matter that none are applied here.
	 */
	private StructureSnapshot snapshotNow(IJavaProject project, String commitSha, String commitMessage) {
		log.debug("capturing logical structure snapshot for project '{}'", project.getElementName());
		StructureElementSnapshot elements = structureViewProvider.captureSnapshot(project);
		StructureSnapshot snapshot = new StructureSnapshot(Instant.now(), commitSha, commitMessage, elements);
		log.debug("captured logical structure snapshot for project '{}' with {} element(s)",
				project.getElementName(), snapshot.elementCount());
		return snapshot;
	}

	/**
	 * A retained baseline snapshot without its structure tree, for callers that only want to list or
	 * pick one (an IDE QuickPick, an MCP tool) rather than diff against it.
	 *
	 * <p>{@code capturedAt} is an ISO-8601 string rather than an {@link Instant} on purpose: this
	 * record travels over JSON-RPC to the IDE clients, and lsp4j's Gson cannot reflect over
	 * {@code java.time} types ({@code module java.base does not "opens java.time"}). The same
	 * reason {@code SpringIndexCommands.CaptureBaselineResult} keeps its timestamp as a string.
	 */
	public static record BaselineHistoryEntry(String commitSha, String commitMessage, String capturedAt, int elementCount) {
	}

	public static record StructureSnapshot(Instant capturedAt, String commitSha, String commitMessage, StructureElementSnapshot elements) {

		public int elementCount() {
			int count = 0;
			for (StructureElementSnapshot.SnapshotType type : elements.types()) {
				count += 1 + type.methods().size() + type.members().size();
			}
			return count;
		}
	}

}
