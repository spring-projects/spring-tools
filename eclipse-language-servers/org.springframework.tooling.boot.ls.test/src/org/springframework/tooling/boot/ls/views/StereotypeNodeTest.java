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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.springframework.tooling.boot.ls.views.TestNodes.changed;
import static org.springframework.tooling.boot.ls.views.TestNodes.node;
import static org.springframework.tooling.boot.ls.views.TestNodes.project;
import static org.springframework.tooling.boot.ls.views.TestNodes.projectWithBaseline;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.springframework.tooling.boot.ls.views.StereotypeNode.Change;
import org.springframework.tooling.boot.ls.views.StereotypeNode.JavaElementReference;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * @author Martin Lippert
 */
public class StereotypeNodeTest {

	@Test
	public void childrenKnowTheirParent() {
		StereotypeNode leaf = node("leaf");
		StereotypeNode middle = node("middle", leaf);
		StereotypeNode root = project("root", middle);

		assertSame(middle, leaf.parent());
		assertSame(root, middle.parent());
		assertNull(root.parent());
	}

	@Test
	public void nodesAreEqualWhenTheirIdsAre() {
		assertEquals(node("a"), node("a", node("child")));
		assertFalse(node("a").equals(node("b")));
	}

	@Test
	public void aNodeWithoutChildrenHasAnEmptyArray() {
		assertEquals(0, new StereotypeNode("a", "a", null, null, null, null, null).children().length);
	}

	@Test
	public void parsesTheChangeOfANode() {
		assertEquals(Change.ADDED, changed("n", "added").change());
		assertEquals(Change.REMOVED, changed("n", "removed").change());
		assertEquals(Change.MODIFIED, changed("n", "modified").change());
		assertNull(changed("n", "something else").change());
		assertNull(node("n").change());
	}

	@Test
	public void aContainerOfAChangeContainsChangesWithoutHavingChangedItself() {
		// the language server marks the packages and groups on the path to a change as modified,
		// the change of a node itself is only what happened to it
		StereotypeNode container = changed("container", "modified");

		assertTrue(container.containsChanges());
		assertFalse(node("n").containsChanges());
	}

	@Test
	public void theBaselineInformationIsReadFromTheRootOfTheTree() {
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(StereotypeNodeDeserializer.PROJECT_ID, "p");
		attributes.put(StereotypeNode.HAS_BASELINE, true);
		attributes.put(StereotypeNode.COMPARED_AGAINST_SHA, "0123456789");
		attributes.put(StereotypeNode.COMPARED_AGAINST_MESSAGE, "a message");
		attributes.put(StereotypeNode.COMPARED_AGAINST_CAPTURED_AT, "2026-01-02T03:04:05Z");
		StereotypeNode leaf = node("leaf");
		StereotypeNode middle = node("middle", leaf);
		StereotypeNode root = node("p", attributes, middle);

		assertTrue(leaf.hasBaselineInformation());
		assertTrue(leaf.hasBaseline());
		assertEquals("0123456789", leaf.comparedAgainstSha());
		assertEquals("a message", leaf.comparedAgainstMessage());
		assertEquals("2026-01-02T03:04:05Z", leaf.comparedAgainstCapturedAt());
		assertTrue(root.isProject());
		assertFalse(leaf.isProject());
	}

	@Test
	public void aProjectWithoutABaselineSaysSo() {
		StereotypeNode leaf = node("leaf");
		projectWithBaseline("p", false, leaf);

		assertTrue(leaf.hasBaselineInformation());
		assertFalse(leaf.hasBaseline());
		assertNull(leaf.comparedAgainstSha());
	}

	@Test
	public void aTreeWithDependenciesCarriesNoBaselineInformationAtAll() {
		StereotypeNode leaf = node("leaf");
		project("p", leaf);

		assertFalse(leaf.hasBaselineInformation());
		assertFalse(leaf.hasBaseline());
	}

	@Test
	public void readsTheJavaElementOfANodeReadFromAJar() {
		Map<String, Object> reference = Map.of("projectUri", "file:///p", "bindingKey", "Lcom/example/Foo;");
		StereotypeNode node = node("n", Map.of(StereotypeNode.JAVA_ELEMENT, reference));

		assertEquals(new JavaElementReference("file:///p", "Lcom/example/Foo;"), node.javaElement());
		assertNull(node("other").javaElement());
	}

	@Test
	public void findsTheDeepestChangedNodes() {
		StereotypeNode addedMethod = changed("method", "added");
		StereotypeNode changedType = changed("type", "modified", addedMethod);
		StereotypeNode removedType = changed("removed type", "removed");
		StereotypeNode unchanged = node("unchanged", node("deep"));
		StereotypeNode root = project("p", changedType, removedType, unchanged);

		List<StereotypeNode> deepest = StereotypeNode.deepestChangedNodes(List.of(root));

		assertEquals(List.of(addedMethod, removedType), deepest);
	}

	@Test
	public void aChangedNodeWithoutChangedChildrenIsTheDeepestEvenWhenItsAncestorsChanged() {
		StereotypeNode leaf = changed("leaf", "modified");
		StereotypeNode root = project("p", changed("container", "modified", leaf));

		assertEquals(List.of(leaf), StereotypeNode.deepestChangedNodes(List.of(root)));
	}

	@Test
	public void readsANodeFromTheJsonOfTheLanguageServer() {
		String json = """
				{"attributes": {"nodeId": "p", "text": "project p", "icon": "project", "projectId": "p", "hasBaseline": true,
				                "comparedAgainstSha": "abc"},
				 "children": [
				   {"attributes": {"nodeId": "p/t", "text": "Type", "icon": "symbol-class", "change": "added",
				                   "location": {"uri": "file:///p/T.java", "range": {"start": {"line": 1, "character": 2}, "end": {"line": 3, "character": 4}}},
				                   "javaElement": {"projectUri": "file:///p", "bindingKey": "Lp/T;"}},
				    "children": []}
				 ]}
				""";
		Gson gson = new GsonBuilder().registerTypeAdapter(StereotypeNode.class, new StereotypeNodeDeserializer()).create();

		StereotypeNode project = gson.fromJson(json, StereotypeNode.class);

		assertEquals("p", project.id());
		assertEquals("project p", project.text());
		assertEquals("p", project.getProjectId());
		assertEquals(1, project.children().length);
		StereotypeNode type = project.children()[0];
		assertSame(project, type.parent());
		assertEquals("Type", type.text());
		assertEquals(Change.ADDED, type.change());
		assertEquals("file:///p/T.java", type.location().getUri());
		assertEquals(new JavaElementReference("file:///p", "Lp/T;"), type.javaElement());
		assertTrue(type.hasBaseline());
		assertEquals("abc", type.comparedAgainstSha());
	}

}
