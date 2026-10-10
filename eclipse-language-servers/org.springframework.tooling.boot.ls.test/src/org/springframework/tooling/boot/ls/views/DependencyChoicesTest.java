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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;
import org.springframework.tooling.boot.ls.views.StructureClient.DependencyDescriptor;

/**
 * @author Martin Lippert
 */
public class DependencyChoicesTest {

	private static DependencyDescriptor project(String name) {
		return new DependencyDescriptor("project:" + name, "WORKSPACE_PROJECT", name, null, null, null, name, "/workspace/" + name);
	}

	private static DependencyDescriptor jar(String group, String artifact) {
		return new DependencyDescriptor("gav:" + group + ":" + artifact, "JAR", artifact, group, artifact, "1.0", null, "/repo/" + artifact + ".jar");
	}

	private static List<String> ids(List<DependencyDescriptor> dependencies) {
		return dependencies.stream().map(DependencyDescriptor::id).toList();
	}

	@Test
	public void showsTheSelectedDependenciesFirstAndWorkspaceProjectsBeforeLibraries() {
		List<DependencyDescriptor> offered = List.of(project("a"), project("b"), jar("org.example", "x"), jar("org.example", "y"));

		List<DependencyDescriptor> ordered = DependencyChoices.orderForDisplay(offered, List.of("gav:org.example:y", "project:b"));

		assertEquals(List.of("project:b", "gav:org.example:y", "project:a", "gav:org.example:x"), ids(ordered));
	}

	@Test
	public void keepsTheOrderTheLanguageServerSentWithinACategory() {
		List<DependencyDescriptor> offered = List.of(jar("g", "a"), jar("g", "b"), jar("g", "c"));

		assertEquals(ids(offered), ids(DependencyChoices.orderForDisplay(offered, List.of())));
	}

	@Test
	public void keepsTheSelectionOfDependenciesThatAreNotOfferedRightNow() {
		List<DependencyDescriptor> offered = List.of(project("a"), project("b"));

		List<String> selection = DependencyChoices.selectionAfterPicking(
				List.of("project:a", "project:closed"), offered, List.of("project:b"));

		assertEquals(List.of("project:closed", "project:b"), selection);
	}

	@Test
	public void deselectingEverythingOfferedLeavesTheOthers() {
		List<DependencyDescriptor> offered = List.of(project("a"));

		assertEquals(List.of("project:closed"),
				DependencyChoices.selectionAfterPicking(List.of("project:a", "project:closed"), offered, List.of()));
		assertEquals(List.of(), DependencyChoices.selectionAfterPicking(List.of("project:a"), offered, List.of()));
	}

	@Test
	public void describesAWorkspaceProjectAndALibrary() {
		assertEquals("workspace project", DependencyChoices.describe(project("a")));
		assertEquals("org.example:lib:1.0", DependencyChoices.describe(jar("org.example", "lib")));
		assertEquals("", DependencyChoices.describe(new DependencyDescriptor("jar:/x.jar", "JAR", "x.jar", null, null, null, null, "/x.jar")));
	}

	@Test
	public void matchesTheFilterAgainstNameCoordinatesAndLocationIgnoringCase() {
		DependencyDescriptor lib = jar("org.example", "Spring-Lib");

		assertTrue(DependencyChoices.matches(lib, null));
		assertTrue(DependencyChoices.matches(lib, "  "));
		assertTrue(DependencyChoices.matches(lib, "spring"));
		assertTrue(DependencyChoices.matches(lib, "ORG.EXAMPLE"));
		assertTrue(DependencyChoices.matches(lib, "/repo/"));
		assertFalse(DependencyChoices.matches(lib, "other"));
		assertTrue(DependencyChoices.matches(project("a"), "workspace project"));
	}

}
