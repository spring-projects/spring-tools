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
package org.springframework.tooling.boot.ls.views;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.springframework.tooling.boot.ls.views.TestNodes.project;

import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * @author Martin Lippert
 */
public class StructureTreeMergerTest {

	private final StructureTreeMerger merger = new StructureTreeMerger();

	private static List<String> projects(List<StereotypeNode> nodes) {
		return nodes.stream().map(StereotypeNode::getProjectId).sorted().toList();
	}

	@Test
	public void aFullLoadShowsAllTheProjectsItAnswersWith() {
		int request = merger.startRequest();

		List<StereotypeNode> merged = merger.merge(request, null, List.of(project("a"), project("b")));

		assertEquals(List.of("a", "b"), projects(merged));
	}

	@Test
	public void aFullLoadDropsTheProjectsItDoesNotAnswerWith() {
		merger.merge(merger.startRequest(), null, List.of(project("a"), project("b")));

		List<StereotypeNode> merged = merger.merge(merger.startRequest(), Set.of(), List.of(project("a")));

		assertEquals(List.of("a"), projects(merged));
	}

	@Test
	public void aPartialLoadReplacesOnlyTheProjectsItWasAskedFor() {
		StereotypeNode a = project("a");
		StereotypeNode b = project("b");
		merger.merge(merger.startRequest(), null, List.of(a, b));

		StereotypeNode newB = project("b");
		List<StereotypeNode> merged = merger.merge(merger.startRequest(), Set.of("b"), List.of(newB));

		assertEquals(2, merged.size());
		assertSame(a, merged.stream().filter(n -> "a".equals(n.getProjectId())).findFirst().get());
		assertSame(newB, merged.stream().filter(n -> "b".equals(n.getProjectId())).findFirst().get());
	}

	@Test
	public void aPartialLoadRemovesAProjectItWasAskedForAndDoesNotAnswerWith() {
		merger.merge(merger.startRequest(), null, List.of(project("a"), project("b")));

		List<StereotypeNode> merged = merger.merge(merger.startRequest(), Set.of("b"), List.of());

		assertEquals(List.of("a"), projects(merged));
	}

	@Test
	public void aPartialLoadAddsAProjectItWasAskedForThatWasNotThereYet() {
		merger.merge(merger.startRequest(), null, List.of(project("a")));

		List<StereotypeNode> merged = merger.merge(merger.startRequest(), Set.of("b"), List.of(project("b")));

		assertEquals(List.of("a", "b"), projects(merged));
	}

	@Test
	public void aPartialLoadAlsoReplacesTheProjectsItAnswersWithThatItWasNotAskedFor() {
		// with dependencies included, the language server also rebuilds the projects that include
		// the project that changed
		StereotypeNode a = project("a");
		merger.merge(merger.startRequest(), null, List.of(a, project("b")));

		StereotypeNode newA = project("a");
		StereotypeNode newB = project("b");
		List<StereotypeNode> merged = merger.merge(merger.startRequest(), Set.of("b"), List.of(newB, newA));

		assertSame(newA, merged.stream().filter(n -> "a".equals(n.getProjectId())).findFirst().get());
		assertSame(newB, merged.stream().filter(n -> "b".equals(n.getProjectId())).findFirst().get());
	}

	@Test
	public void aSlowOlderFullLoadCannotRollBackAProjectANewerRequestReplaced() {
		int fullLoad = merger.startRequest();
		int partialLoad = merger.startRequest();

		StereotypeNode newA = project("a");
		merger.merge(partialLoad, Set.of("a"), List.of(newA));
		List<StereotypeNode> merged = merger.merge(fullLoad, null, List.of(project("a"), project("b")));

		assertSame(newA, merged.stream().filter(n -> "a".equals(n.getProjectId())).findFirst().get());
	}

	@Test
	public void aSlowOlderFullLoadStillBringsInProjectsTheNewerRequestDidNotKnow() {
		int fullLoad = merger.startRequest();
		int partialLoad = merger.startRequest();

		merger.merge(partialLoad, Set.of("a"), List.of(project("a")));
		List<StereotypeNode> merged = merger.merge(fullLoad, null, List.of(project("a"), project("b")));

		assertEquals(List.of("a", "b"), projects(merged));
	}

	@Test
	public void aSlowOlderFullLoadCannotDropAProjectANewerRequestBroughtIn() {
		int fullLoad = merger.startRequest();
		int partialLoad = merger.startRequest();

		merger.merge(partialLoad, Set.of("new"), List.of(project("new")));
		List<StereotypeNode> merged = merger.merge(fullLoad, null, List.of(project("a")));

		assertEquals(List.of("a", "new"), projects(merged));
	}

	@Test
	public void theMergedProjectsAreAvailableAfterwards() {
		List<StereotypeNode> merged = merger.merge(merger.startRequest(), null, List.of(project("a")));

		assertSame(merged, merger.rootElements());
	}

}
