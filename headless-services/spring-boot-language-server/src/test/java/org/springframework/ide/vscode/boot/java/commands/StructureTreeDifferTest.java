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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.ChangeType;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.DiffNode;
import org.springframework.ide.vscode.boot.java.commands.StructureTreeDiffer.StructureTreeDiff;
import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider.SourceLocation;
import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider.StructureNode;

/**
 * @author Martin Lippert
 */
public class StructureTreeDifferTest {

	private static final Instant BASELINE_AT = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant CURRENT_AT = Instant.parse("2026-01-02T00:00:00Z");

	@Test
	void addedLeafIsReportedAsAdded() {
		StructureNode before = node("application", "app", node("type", "A"));
		StructureNode after = node("application", "app", node("type", "A"), node("type", "B"));

		DiffNode root = diff(before, after).root();

		assertThat(root.change()).isEqualTo(ChangeType.MODIFIED);
		assertThat(changesByLabel(root)).containsEntry("A", ChangeType.UNCHANGED).containsEntry("B", ChangeType.ADDED);
	}

	@Test
	void removedLeafIsReportedAsRemoved() {
		StructureNode before = node("application", "app", node("type", "A"), node("type", "B"));
		StructureNode after = node("application", "app", node("type", "A"));

		DiffNode root = diff(before, after).root();

		assertThat(root.change()).isEqualTo(ChangeType.MODIFIED);
		assertThat(changesByLabel(root)).containsEntry("A", ChangeType.UNCHANGED).containsEntry("B", ChangeType.REMOVED);
	}

	@Test
	void addedSubtreeMarksTheWholeSubtreeAsAdded() {
		StructureNode before = node("application", "app");
		StructureNode addedChild = node("type", "NewController", node("method", "handle"));
		StructureNode after = node("application", "app", addedChild);

		DiffNode root = diff(before, after).root();
		DiffNode addedNode = root.children().get(0);

		assertThat(addedNode.change()).isEqualTo(ChangeType.ADDED);
		assertThat(addedNode.children()).extracting(DiffNode::change).containsExactly(ChangeType.ADDED);
	}

	@Test
	void removedSubtreeMarksTheWholeSubtreeAsRemoved() {
		StructureNode removedChild = node("type", "OldController", node("method", "handle"));
		StructureNode before = node("application", "app", removedChild);
		StructureNode after = node("application", "app");

		DiffNode root = diff(before, after).root();
		DiffNode removedNode = root.children().get(0);

		assertThat(removedNode.change()).isEqualTo(ChangeType.REMOVED);
		assertThat(removedNode.children()).extracting(DiffNode::change).containsExactly(ChangeType.REMOVED);
	}

	@Test
	void siblingReorderingIsUnchanged() {
		StructureNode before = node("application", "app", node("type", "A"), node("type", "B"));
		StructureNode after = node("application", "app", node("type", "B"), node("type", "A"));

		DiffNode root = diff(before, after).root();

		assertThat(root.change()).isEqualTo(ChangeType.UNCHANGED);
		assertThat(root.children()).extracting(DiffNode::change).containsOnly(ChangeType.UNCHANGED);
	}

	@Test
	void duplicateSiblingLabelsAreMatchedPositionally() {
		StructureNode before = node("application", "app", node("method", "handle"), node("method", "handle"));
		StructureNode after = node("application", "app", node("method", "handle"), node("method", "handle"), node("method", "handle"));

		DiffNode root = diff(before, after).root();

		assertThat(root.children()).hasSize(3);
		assertThat(root.children()).extracting(DiffNode::change)
				.containsExactlyInAnyOrder(ChangeType.UNCHANGED, ChangeType.UNCHANGED, ChangeType.ADDED);
	}

	@Test
	void emptyBaselineReportsAllChildrenAsAdded() {
		StructureNode before = node("application", "app");
		StructureNode after = node("application", "app", node("type", "A"), node("type", "B"));

		DiffNode root = diff(before, after).root();

		assertThat(root.change()).isEqualTo(ChangeType.MODIFIED);
		assertThat(root.children()).extracting(DiffNode::change).containsOnly(ChangeType.ADDED);
	}

	@Test
	void identicalTreesAreUnchanged() {
		StructureNode before = node("application", "app", node("type", "A", node("method", "m")));
		StructureNode after = node("application", "app", node("type", "A", node("method", "m")));

		StructureTreeDiff diff = diff(before, after);

		assertThat(diff.root().change()).isEqualTo(ChangeType.UNCHANGED);
		assertThat(diff.stats().hasChanges()).isFalse();
		assertThat(diff.stats().unchanged()).isEqualTo(3);
	}

	@Test
	void locationOnlyChangeIsUnchanged() {
		SourceLocation before = new SourceLocation("file:///A.java", 1, 0, 1, 10);
		SourceLocation after = new SourceLocation("file:///A.java", 40, 0, 40, 10);

		StructureNode beforeTree = node("application", "app",
				new StructureNode("app/type:A", "A", "icon", "type", null, before, null, List.of()));
		StructureNode afterTree = node("application", "app",
				new StructureNode("app/type:A", "A", "icon", "type", null, after, null, List.of()));

		DiffNode root = diff(beforeTree, afterTree).root();

		assertThat(root.change()).isEqualTo(ChangeType.UNCHANGED);
	}

	@Test
	void iconOrHoverChangeIsReportedAsModified() {
		StructureNode before = new StructureNode("app/type:A", "A", "icon-1", "type", "old hover", null, null, List.of());
		StructureNode after = new StructureNode("app/type:A", "A", "icon-2", "type", "old hover", null, null, List.of());

		DiffNode root = diff(node("application", "app", before), node("application", "app", after)).root();

		assertThat(root.children().get(0).change()).isEqualTo(ChangeType.MODIFIED);
	}

	@Test
	void changesAreIndexedByTheNodeIdOfTheCurrentTree() {
		StructureNode unchanged = new StructureNode("app/type:A", "A", null, "type", null, null, null, List.of());
		StructureNode added = new StructureNode("app/type:B", "B", null, "type", null, null, null, List.of());

		StructureNode before = node("application", "app", unchanged);
		StructureNode after = new StructureNode("app", "app", null, "application", null, null, null, List.of(unchanged, added));

		Map<String, ChangeType> changes = StructureTreeDiffer.changesByNodeId(diff(before, after).root());

		// the root is modified because of the added child, the added node itself is added, and the
		// unchanged node isn't listed at all
		assertThat(changes).containsExactlyInAnyOrderEntriesOf(
				Map.of("app", ChangeType.MODIFIED, "app/type:B", ChangeType.ADDED));
	}

	@Test
	void unchangedTreesProduceNoChangesToApply() {
		StructureNode before = node("application", "app", node("type", "A"));
		StructureNode after = node("application", "app", node("type", "A"));

		assertThat(StructureTreeDiffer.changesByNodeId(diff(before, after).root())).isEmpty();
	}

	private static StructureTreeDiff diff(StructureNode before, StructureNode after) {
		return StructureTreeDiffer.diff("test-project", BASELINE_AT, CURRENT_AT, before, after);
	}

	private static java.util.Map<String, ChangeType> changesByLabel(DiffNode node) {
		java.util.Map<String, ChangeType> result = new java.util.LinkedHashMap<>();
		for (DiffNode child : node.children()) {
			result.put(child.label(), child.change());
		}
		return result;
	}

	private static StructureNode node(String kind, String text, StructureNode... children) {
		return new StructureNode(text, text, null, kind, null, null, null, List.of(children));
	}

}
