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

		boolean attributesChanged = !Objects.equals(before.icon(), after.icon())
				|| !Objects.equals(before.hover(), after.hover());

		ChangeType change = (anyChildChanged || attributesChanged) ? ChangeType.MODIFIED : ChangeType.UNCHANGED;

		return new DiffNode(after.text(), after.kind(), change, children);
	}

	private static DiffNode wholeSubtree(StructureNode node, ChangeType change) {
		List<DiffNode> children = node.children().stream()
				.map(child -> wholeSubtree(child, change))
				.toList();

		return new DiffNode(node.text(), node.kind(), change, children);
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
		int[] counts = new int[4];
		accumulate(node, counts);
		return new DiffStats(counts[0], counts[1], counts[2], counts[3]);
	}

	private static void accumulate(DiffNode node, int[] counts) {
		switch (node.change()) {
			case ADDED -> counts[0]++;
			case REMOVED -> counts[1]++;
			case MODIFIED -> counts[2]++;
			case UNCHANGED -> counts[3]++;
		}
		node.children().forEach(child -> accumulate(child, counts));
	}

	public enum ChangeType {
		ADDED, REMOVED, MODIFIED, UNCHANGED
	}

	/**
	 * The result of diffing two structure trees.
	 */
	public static record StructureTreeDiff(String projectName, Instant baselineAt, Instant currentAt, DiffNode root,
			DiffStats stats) {
	}

	/**
	 * A single node of the diff tree.
	 */
	public static record DiffNode(String label, String kind, ChangeType change, List<DiffNode> children) {

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
	public static record DiffStats(int added, int removed, int modified, int unchanged) {

		public boolean hasChanges() {
			return added > 0 || removed > 0 || modified > 0;
		}
	}

}
