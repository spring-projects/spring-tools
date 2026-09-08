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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider.StructureNode;

/**
 * Compares two {@link StructureNode} trees (typically a "before" and an "after" snapshot of the
 * same project's logical structure) and produces a tree of {@link DiffNode}s describing what was
 * added, removed or modified.
 *
 * <p>Nodes are matched by their {@code kind} and {@code text} (label) alone - source location is
 * deliberately excluded, so moving code around within a file, or across siblings, does not show up
 * as a change. A node whose label changed is therefore reported as one node removed and a
 * different node added, rather than a modification in place.
 *
 * <p>Only the node that actually changed is reported as changed. Its containers are reported as
 * {@link ChangeType#CONTAINS_CHANGES} instead, so a client can keep the path to a change visible
 * (and an ascii diff can keep it uncollapsed) without lighting up every package and group above it.
 * The one exception is a <em>removed</em> node with nothing added in its place: it no longer exists
 * in the current tree, so there is nowhere to report it except on the node it was removed from,
 * which is reported as {@link ChangeType#MODIFIED}. A removal alongside an addition is left to the
 * added node to show, since that pair is what a rename looks like here.
 *
 * @author Martin Lippert
 */
public class StructureTreeDiffer {

	private StructureTreeDiffer() {
	}

	public static StructureTreeDiff diff(String projectName, Instant baselineAt, Instant currentAt,
			StructureNode before, StructureNode after) {

		DiffNode root = diffNodes(before, after);
		DiffStats stats = statsOf(root);

		return new StructureTreeDiff(projectName, baselineAt, currentAt, root, stats);
	}

	private static DiffNode diffNodes(StructureNode before, StructureNode after) {
		if (before == null) {
			return wholeSubtree(after, ChangeType.ADDED);
		}
		if (after == null) {
			return wholeSubtree(before, ChangeType.REMOVED);
		}

		Map<String, StructureNode> beforeChildren = indexChildren(before.children());
		Map<String, StructureNode> afterChildren = indexChildren(after.children());

		Set<String> keys = new LinkedHashSet<>();
		keys.addAll(afterChildren.keySet());
		keys.addAll(beforeChildren.keySet());

		List<DiffNode> children = new ArrayList<>();
		boolean anyChildChanged = false;

		for (String key : keys) {
			DiffNode childDiff = diffNodes(beforeChildren.get(key), afterChildren.get(key));
			children.add(childDiff);
			if (childDiff.change() != ChangeType.UNCHANGED) {
				anyChildChanged = true;
			}
		}

		// the content hash catches changes to the source behind a node that leave the node itself
		// looking the same, e.g. an edited method body; nodes without a hash on either side (or
		// baselines captured before hashes existed) simply compare equal here
		boolean attributesChanged = !Objects.equals(before.icon(), after.icon())
				|| !Objects.equals(before.hover(), after.hover())
				|| !Objects.equals(before.contentHash(), after.contentHash());

		// a removed child is the one change that cannot be reported on the node it happened to -
		// that node is gone from the tree - so it counts as a change of the node it was removed
		// from. Unless something was added here too: then the removal is almost always the other
		// half of a rename (a renamed route is a removed node plus an added one, since nodes are
		// matched by label), and the added node already shows it.
		boolean childRemoved = children.stream().anyMatch(child -> child.change() == ChangeType.REMOVED);
		boolean childAdded = children.stream().anyMatch(child -> child.change() == ChangeType.ADDED);
		boolean unaccompaniedRemoval = childRemoved && !childAdded;

		ChangeType change;
		if (attributesChanged || unaccompaniedRemoval) {
			change = ChangeType.MODIFIED;
		}
		else if (anyChildChanged) {
			change = ChangeType.CONTAINS_CHANGES;
		}
		else {
			change = ChangeType.UNCHANGED;
		}

		return new DiffNode(after.nodeId(), after.text(), after.kind(), change, children);
	}

	private static DiffNode wholeSubtree(StructureNode node, ChangeType change) {
		List<DiffNode> children = node.children().stream()
				.map(child -> wholeSubtree(child, change))
				.toList();

		return new DiffNode(node.nodeId(), node.text(), node.kind(), change, children);
	}

	/**
	 * Groups the given children by their identity key ({@code kind + ":" + text}), disambiguating
	 * repeated keys among siblings with a {@code #n} suffix in order of appearance, so duplicate
	 * sibling labels don't collide.
	 */
	private static Map<String, StructureNode> indexChildren(List<StructureNode> children) {
		Map<String, Integer> occurrences = new HashMap<>();
		Map<String, StructureNode> result = new LinkedHashMap<>();

		for (StructureNode child : children) {
			String baseKey = keyOf(child);
			int occurrence = occurrences.merge(baseKey, 1, Integer::sum);
			String key = occurrence == 1 ? baseKey : baseKey + "#" + occurrence;
			result.put(key, child);
		}

		return result;
	}

	private static String keyOf(StructureNode node) {
		String kind = node.kind() == null ? "" : node.kind();
		String text = node.text() == null ? "" : node.text();
		return kind + ":" + text;
	}

	private static DiffStats statsOf(DiffNode node) {
		int[] counts = new int[5];
		accumulate(node, counts);
		return new DiffStats(counts[0], counts[1], counts[2], counts[3], counts[4]);
	}

	private static void accumulate(DiffNode node, int[] counts) {
		switch (node.change()) {
			case ADDED -> counts[0]++;
			case REMOVED -> counts[1]++;
			case MODIFIED -> counts[2]++;
			case UNCHANGED -> counts[3]++;
			case CONTAINS_CHANGES -> counts[4]++;
		}
		node.children().forEach(child -> accumulate(child, counts));
	}

	public enum ChangeType {

		ADDED("added"),
		REMOVED("removed"),
		MODIFIED("modified"),

		/**
		 * The node itself is unchanged, but something below it is. Kept apart from
		 * {@link #MODIFIED} so clients can show the path to a change without highlighting every
		 * container along the way, while still knowing not to collapse or filter it away.
		 */
		CONTAINS_CHANGES("containsChanges"),

		UNCHANGED("unchanged");

		private final String label;

		ChangeType(String label) {
			this.label = label;
		}

		/**
		 * How this change is named on the wire, for the {@code change} attribute of a structure
		 * tree node.
		 */
		public String label() {
			return label;
		}
	}

	/**
	 * The result of diffing two structure trees.
	 */
	public static record StructureTreeDiff(String projectName, Instant baselineAt, Instant currentAt, DiffNode root,
			DiffStats stats) {
	}

	/**
	 * Indexes the changed nodes of a diff tree by the node id they have in the tree they were
	 * diffed from, so the changes can be applied back onto that tree. Unchanged nodes are left out.
	 *
	 * <p>Removed nodes carry the node id they had in the baseline, which no longer exists in the
	 * current tree - they show up in the current tree only indirectly, through the node they were
	 * removed from being reported as {@link ChangeType#MODIFIED} (unless something was added there
	 * too, see this class' description).
	 */
	public static Map<String, ChangeType> changesByNodeId(DiffNode root) {
		Map<String, ChangeType> changes = new LinkedHashMap<>();
		collectChanges(root, changes);
		return changes;
	}

	private static void collectChanges(DiffNode node, Map<String, ChangeType> changes) {
		if (node.change() != ChangeType.UNCHANGED && node.nodeId() != null) {
			changes.put(node.nodeId(), node.change());
		}
		node.children().forEach(child -> collectChanges(child, changes));
	}

	/**
	 * A single node of the diff tree.
	 */
	public static record DiffNode(String nodeId, String label, String kind, ChangeType change, List<DiffNode> children) {

		/**
		 * The number of nodes in this subtree, including this node itself. Only meaningful when
		 * {@link #change()} is {@link ChangeType#UNCHANGED}, in which case every descendant is
		 * guaranteed unchanged as well.
		 */
		public int subtreeSize() {
			int size = 1;
			for (DiffNode child : children) {
				size += child.subtreeSize();
			}
			return size;
		}
	}

	/**
	 * Aggregate counts of nodes by change type across an entire diff tree.
	 */
	public static record DiffStats(int added, int removed, int modified, int unchanged, int containsChanges) {

		public boolean hasChanges() {
			return added > 0 || removed > 0 || modified > 0;
		}
	}

}
