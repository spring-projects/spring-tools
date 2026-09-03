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

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.ChangeType;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.StructureTreeDiff;
import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider.StructureNode;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;

import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Keeps track of the logical structure of projects over time, so that MCP tools can show what
 * changed since a pinned baseline or since the last Spring index update.
 *
 * <p>A project is <em>tracked</em> from the first time {@link #captureBaseline} or one of the
 * {@code diffAgainst...} methods is called for it. Once tracked, its "previous" snapshot is kept
 * up to date automatically whenever the Spring index reports that project as affected by an update
 * (debounced, so a burst of file changes only triggers one recompute per project). Index updates
 * for projects that aren't tracked - the common case - cost nothing here.
 *
 * @author Martin Lippert
 */
public class StructureSnapshotStore {

	private static final Logger log = LoggerFactory.getLogger(StructureSnapshotStore.class);

	private static final long DEBOUNCE_MILLIS = 500;

	private final StructureViewProvider structureViewProvider;
	private final JavaProjectFinder projectFinder;
	private final Scheduler scheduler = Schedulers.newSingle("structure-snapshot-store", true);

	private final Map<String, ProjectSnapshots> snapshots = new ConcurrentHashMap<>();
	private final Set<String> pendingAffectedProjects = ConcurrentHashMap.newKeySet();
	private volatile Disposable pendingRefresh;

	public StructureSnapshotStore(StructureViewProvider structureViewProvider, SpringSymbolIndex symbolIndex,
			JavaProjectFinder projectFinder) {
		this.structureViewProvider = structureViewProvider;
		this.projectFinder = projectFinder;

		symbolIndex.onUpdate(this::scheduleRollingRefresh);
	}

	/**
	 * Computes the current logical structure of the project and pins it as the baseline to compare
	 * against in {@link #diffAgainstBaseline}. Replaces any baseline previously captured for the
	 * same project.
	 */
	public StructureSnapshot captureBaseline(IJavaProject project) {
		StructureSnapshot snapshot = snapshotNow(project);

		ProjectSnapshots tracked = trackedSnapshotsOf(project);
		synchronized (tracked) {
			tracked.baseline = snapshot;
			tracked.current = snapshot;
		}

		return snapshot;
	}

	/**
	 * Diffs the current logical structure of the project against its pinned baseline.
	 *
	 * @return empty when no baseline has been captured yet for this project
	 */
	public Optional<StructureTreeDiff> diffAgainstBaseline(IJavaProject project) {
		return diffAgainst(project, tracked -> tracked.baseline);
	}

	/**
	 * Diffs the current logical structure of the project against the structure it had right before
	 * the most recent Spring index update.
	 *
	 * @return empty when the project has not been tracked through at least one index update yet
	 */
	public Optional<StructureTreeDiff> diffAgainstPrevious(IJavaProject project) {
		return diffAgainst(project, tracked -> tracked.previous);
	}

	private Optional<StructureTreeDiff> diffAgainst(IJavaProject project, Function<ProjectSnapshots, StructureSnapshot> reference) {
		String projectName = project.getElementName();
		StructureSnapshot current = snapshotNow(project);

		ProjectSnapshots tracked = trackedSnapshotsOf(project);
		StructureSnapshot comparedTo;
		synchronized (tracked) {
			comparedTo = reference.apply(tracked);
			tracked.current = current;
		}

		if (comparedTo == null) {
			return Optional.empty();
		}

		return Optional.of(StructureTreeDiffer.diff(projectName, comparedTo.capturedAt(), current.capturedAt(),
				comparedTo.root(), current.root()));
	}

	/**
	 * Whether a baseline has been captured for the project, regardless of whether anything has
	 * changed since then.
	 */
	public boolean hasBaseline(IJavaProject project) {
		ProjectSnapshots tracked = snapshots.get(project.getElementName());
		return tracked != null && tracked.baseline != null;
	}

	/**
	 * Annotates the nodes of a freshly built structure tree with how they changed compared to the
	 * pinned baseline of that project, so clients can highlight the changed parts of the tree.
	 *
	 * <p>Does nothing when no baseline was captured for the project. Deliberately does not start
	 * tracking the project either - rendering the structure view must not turn every project in the
	 * workspace into one whose snapshots are kept up to date in the background.
	 *
	 * @param root the root of the tree to annotate, modified in place
	 */
	public void annotateWithChangesSinceBaseline(IJavaProject project, Node root) {
		ProjectSnapshots tracked = snapshots.get(project.getElementName());
		StructureSnapshot baseline = tracked == null ? null : tracked.baseline;

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

	private static void applyChanges(Node node, Map<String, ChangeType> changes) {
		Object nodeId = node.getAttribute(JsonNodeHandler.NODE_ID);
		ChangeType change = nodeId == null ? null : changes.get(nodeId.toString());

		if (change != null) {
			node.withAttribute(JsonNodeHandler.CHANGE, change.name().toLowerCase());
		}

		node.getChildren().forEach(child -> applyChanges(child, changes));
	}

	private ProjectSnapshots trackedSnapshotsOf(IJavaProject project) {
		return snapshots.computeIfAbsent(project.getElementName(), name -> new ProjectSnapshots());
	}

	private StructureSnapshot snapshotNow(IJavaProject project) {
		Node root = structureViewProvider.createTree(project, false, null);

		if (root == null) {
			// for Spring Modulith projects the tree cannot be created without the module metadata,
			// so try again and let the provider fetch that metadata first
			root = structureViewProvider.createTree(project, true, null);
		}

		if (root == null) {
			throw new IllegalStateException("no logical structure available for project with name " + project.getElementName());
		}

		return new StructureSnapshot(Instant.now(), StructureViewProvider.toStructureNode(root));
	}

	private void scheduleRollingRefresh(Set<String> affectedProjects) {
		pendingAffectedProjects.addAll(affectedProjects);

		Disposable previous = pendingRefresh;
		pendingRefresh = Mono.delay(Duration.ofMillis(DEBOUNCE_MILLIS))
				.publishOn(scheduler)
				.doOnSuccess(v -> refreshAffectedTrackedProjects())
				.subscribe();

		if (previous != null) {
			previous.dispose();
		}
	}

	private void refreshAffectedTrackedProjects() {
		Set<String> dueForRefresh = new HashSet<>();
		Iterator<String> pending = pendingAffectedProjects.iterator();
		while (pending.hasNext()) {
			dueForRefresh.add(pending.next());
			pending.remove();
		}

		for (String projectName : dueForRefresh) {
			ProjectSnapshots tracked = snapshots.get(projectName);
			if (tracked == null) {
				// not a project any of the MCP tools have tracked, nothing to refresh
				continue;
			}

			projectFinder.all().stream()
					.filter(p -> p.getElementName().equals(projectName))
					.findFirst()
					.ifPresent(project -> refreshTrackedProject(project, tracked));
		}
	}

	private void refreshTrackedProject(IJavaProject project, ProjectSnapshots tracked) {
		try {
			StructureSnapshot fresh = snapshotNow(project);
			StructureSnapshot baseline;

			synchronized (tracked) {
				tracked.previous = tracked.current;
				tracked.current = fresh;
				baseline = tracked.baseline;
			}

			// diffing and rendering happens outside of the lock, it only needs the two snapshots
			logChangesSinceBaseline(project, baseline, fresh);
		} catch (Exception e) {
			log.warn("failed to refresh logical structure snapshot for project: " + project.getElementName(), e);
		}
	}

	/**
	 * Logs the ascii-art diff between the pinned baseline and the structure that was just computed,
	 * reusing that already computed snapshot instead of creating another one.
	 */
	private void logChangesSinceBaseline(IJavaProject project, StructureSnapshot baseline, StructureSnapshot current) {
		if (baseline == null) {
			return;
		}

		StructureTreeDiff diff = StructureTreeDiffer.diff(project.getElementName(), baseline.capturedAt(),
				current.capturedAt(), baseline.root(), current.root());

		if (diff.stats().hasChanges()) {
			log.info("\n\n" + AsciiStructureRenderer.render(diff, true) + "\n\n");
		}
	}

	public static record StructureSnapshot(Instant capturedAt, StructureNode root) {

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

	private static class ProjectSnapshots {
		volatile StructureSnapshot baseline;
		volatile StructureSnapshot previous;
		volatile StructureSnapshot current;
	}

}
