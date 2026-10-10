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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.springframework.tooling.boot.ls.views.TestNodes.changed;
import static org.springframework.tooling.boot.ls.views.TestNodes.node;
import static org.springframework.tooling.boot.ls.views.TestNodes.project;
import static org.springframework.tooling.boot.ls.views.TestNodes.projectWithBaseline;

import java.util.List;

import org.junit.Test;

/**
 * @author Martin Lippert
 */
public class StructureTreeContentProviderTest {

	private final StructureTreeContentProvider showAll = new StructureTreeContentProvider(() -> false);
	private final StructureTreeContentProvider hideUnchanged = new StructureTreeContentProvider(() -> true);

	@Test
	public void showsAllTheChildrenByDefault() {
		StereotypeNode changed = changed("changed", "added");
		StereotypeNode unchanged = node("unchanged");
		StereotypeNode project = projectWithBaseline("p", true, changed, unchanged);

		assertArrayEquals(new Object[] { changed, unchanged }, showAll.getChildren(project));
	}

	@Test
	public void showsOnlyTheChangesAndThePathToThemWhenHidingUnchangedNodes() {
		StereotypeNode addedMethod = changed("method", "added");
		StereotypeNode unchangedMethod = node("other method");
		StereotypeNode type = changed("type", "modified", addedMethod, unchangedMethod);
		StereotypeNode unchangedType = node("unchanged type");
		StereotypeNode project = projectWithBaseline("p", true, type, unchangedType);

		assertArrayEquals(new Object[] { type }, hideUnchanged.getChildren(project));
		assertArrayEquals(new Object[] { addedMethod }, hideUnchanged.getChildren(type));
		assertFalse(hideUnchanged.hasChildren(addedMethod));
	}

	@Test
	public void hidesEverythingWhenNothingChangedSinceTheBaseline() {
		StereotypeNode project = projectWithBaseline("p", true, node("a"), node("b"));

		assertEquals(0, hideUnchanged.getChildren(project).length);
		assertFalse(hideUnchanged.hasChildren(project));
	}

	@Test
	public void neverHidesAnythingOfAProjectWithoutBaseline() {
		StereotypeNode project = projectWithBaseline("p", false, node("a"), node("b"));

		assertEquals(2, hideUnchanged.getChildren(project).length);
	}

	@Test
	public void neverHidesAnythingOfATreeWithoutBaselineInformation() {
		// as the tree of a project with dependencies included
		StereotypeNode project = project("p", node("a"), node("b"));

		assertEquals(2, hideUnchanged.getChildren(project).length);
	}

	@Test
	public void knowsTheParentOfANode() {
		StereotypeNode child = node("child");
		StereotypeNode project = project("p", child);

		assertSame(project, showAll.getParent(child));
		assertNull(showAll.getParent(project));
	}

	@Test
	public void sortsTheProjectsByName() {
		StereotypeNode b = project("b");
		StereotypeNode a = project("a");

		assertArrayEquals(new Object[] { a, b }, showAll.getElements(List.of(b, a)));
		assertTrue(showAll.getElements(List.of()).length == 0);
	}

}
