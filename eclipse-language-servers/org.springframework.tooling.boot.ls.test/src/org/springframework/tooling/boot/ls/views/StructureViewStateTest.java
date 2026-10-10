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
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.springframework.tooling.boot.ls.views.StructureViewState.Storage;

/**
 * @author Martin Lippert
 */
public class StructureViewStateTest {

	private static class InMemoryStorage implements Storage {

		final Map<String, String> values = new HashMap<>();

		@Override
		public String get(String key) {
			return values.get(key);
		}

		@Override
		public void put(String key, String value) {
			if (value == null) {
				values.remove(key);
			} else {
				values.put(key, value);
			}
		}
	}

	private final InMemoryStorage storage = new InMemoryStorage();
	private final StructureViewState state = new StructureViewState(storage);

	@Test
	public void everythingIsOffToBeginWith() {
		assertFalse(state.isHighlightChanges());
		assertFalse(state.isHideUnchanged());
		assertFalse(state.isIncludeDependencies());
		assertFalse(state.isShowingChanges());
	}

	@Test
	public void hidingUnchangedNodesOrHighlightingChangesShowsChanges() {
		state.setHighlightChanges(true);
		assertTrue(state.isShowingChanges());
		state.setHighlightChanges(false);
		assertFalse(state.isShowingChanges());

		state.setHideUnchanged(true);
		assertTrue(state.isShowingChanges());
	}

	@Test
	public void turningOnDependenciesTurnsOffTheChanges() {
		state.setHighlightChanges(true);
		state.setHideUnchanged(true);

		state.setIncludeDependencies(true);

		assertTrue(state.isIncludeDependencies());
		assertFalse(state.isHighlightChanges());
		assertFalse(state.isHideUnchanged());
	}

	@Test
	public void turningOnChangesTurnsOffTheDependencies() {
		state.setIncludeDependencies(true);
		state.setHighlightChanges(true);

		assertTrue(state.isHighlightChanges());
		assertFalse(state.isIncludeDependencies());

		state.setIncludeDependencies(true);
		state.setHideUnchanged(true);

		assertTrue(state.isHideUnchanged());
		assertFalse(state.isIncludeDependencies());
	}

	@Test
	public void turningOffOneDoesNotTurnOnTheOther() {
		state.setHighlightChanges(true);
		state.setHighlightChanges(false);

		assertFalse(state.isHighlightChanges());
		assertFalse(state.isIncludeDependencies());
	}

	@Test
	public void remembersTheTogglesForTheNextSession() {
		state.setHighlightChanges(true);
		state.setHideUnchanged(true);

		StructureViewState next = new StructureViewState(storage);

		assertTrue(next.isHighlightChanges());
		assertTrue(next.isHideUnchanged());
		assertFalse(next.isIncludeDependencies());

		next.setIncludeDependencies(true);

		StructureViewState afterwards = new StructureViewState(storage);
		assertTrue(afterwards.isIncludeDependencies());
		assertFalse(afterwards.isHighlightChanges());
		assertFalse(afterwards.isHideUnchanged());
	}

	@Test
	public void doesNotTrustAStoredStateWithBothOn() {
		storage.put("structure.highlightChanges", "true");
		storage.put("structure.includeDependencies", "true");

		StructureViewState restored = new StructureViewState(storage);

		assertTrue(restored.isIncludeDependencies());
		assertFalse(restored.isHighlightChanges());
	}

	@Test
	public void tellsTheListenersWhatChanged() {
		AtomicInteger changes = new AtomicInteger();
		Runnable listener = changes::incrementAndGet;
		state.addListener(listener);

		state.setHighlightChanges(true);
		assertEquals(1, changes.get());

		// nothing changed
		state.setHighlightChanges(true);
		assertEquals(1, changes.get());

		state.setSelectedDependencies("p", List.of("project:q"));
		state.setCompareAgainst("p", "2026-01-01T00:00:00Z");
		assertEquals(3, changes.get());

		state.removeListener(listener);
		state.setHideUnchanged(true);
		assertEquals(3, changes.get());
	}

	@Test
	public void remembersTheSelectedDependenciesPerProject() {
		state.setSelectedDependencies("a", List.of("project:b", "gav:org.example:lib"));
		state.setSelectedDependencies("c", List.of("project:d"));

		StructureViewState next = new StructureViewState(storage);

		assertEquals(List.of("project:b", "gav:org.example:lib"), next.getSelectedDependencies("a"));
		assertEquals(List.of("project:d"), next.getSelectedDependencies("c"));
		assertEquals(List.of(), next.getSelectedDependencies("other"));

		next.setSelectedDependencies("a", List.of());
		assertEquals(List.of(), next.getSelectedDependencies("a"));
		assertEquals(List.of("project:d"), next.getSelectedDependencies("c"));
	}

	@Test
	public void aRequestCarriesTheDependenciesOnlyInDependencyMode() {
		state.setSelectedDependencies("a", List.of("project:b"));

		assertNull(state.getDependenciesForRequest());

		state.setIncludeDependencies(true);
		assertEquals(Map.of("a", List.of("project:b")), state.getDependenciesForRequest());
	}

	@Test
	public void aRequestInDependencyModeWithoutSelectedDependenciesCarriesNone() {
		state.setIncludeDependencies(true);

		assertNull(state.getDependenciesForRequest());
	}

	@Test
	public void remembersTheBaselinePerProject() {
		state.setCompareAgainst("a", "2026-01-01T00:00:00Z");
		state.setCompareAgainst("b", "2026-02-01T00:00:00Z");

		StructureViewState next = new StructureViewState(storage);

		assertEquals("2026-01-01T00:00:00Z", next.getCompareAgainst("a"));
		assertEquals("2026-02-01T00:00:00Z", next.getCompareAgainst("b"));
		assertNull(next.getCompareAgainst("other"));

		next.setCompareAgainst("a", null);
		assertNull(next.getCompareAgainst("a"));
		assertEquals("2026-02-01T00:00:00Z", next.getCompareAgainst("b"));
	}

	@Test
	public void aRequestCarriesTheBaselinesNotInDependencyMode() {
		state.setCompareAgainst("a", "2026-01-01T00:00:00Z");

		assertEquals(Map.of("a", "2026-01-01T00:00:00Z"), state.getCompareAgainstForRequest());

		state.setIncludeDependencies(true);
		assertNull(state.getCompareAgainstForRequest());
	}

	@Test
	public void aRequestWithoutPinnedBaselinesCarriesNone() {
		assertNull(state.getCompareAgainstForRequest());
	}

	@Test
	public void treatsUnreadableStoredMapsAsNotSet() {
		storage.put("structure.dependencies", "this is not json");
		storage.put("structure.compareAgainst", "[1, 2");

		StructureViewState restored = new StructureViewState(storage);

		assertEquals(List.of(), restored.getSelectedDependencies("a"));
		assertNull(restored.getCompareAgainst("a"));
	}

}
