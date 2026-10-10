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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.tooling.boot.ls.views.StructureClient.DependencyDescriptor;

/**
 * What the dialog selecting the dependencies to include in a project's tree shows, and what becomes
 * of the selection.
 * 
 * @author Martin Lippert
 */
final class DependencyChoices {

	private DependencyChoices() {
	}

	/**
	 * The dependencies in the order they are shown: the selected ones first, so they are seen
	 * without scrolling through a long list of libraries - and within the selected and the other
	 * ones alike, workspace projects before libraries. The language server sends workspace projects
	 * first, then libraries, both sorted by name, which this keeps.
	 */
	static List<DependencyDescriptor> orderForDisplay(List<DependencyDescriptor> offered, Collection<String> selected) {
		Set<String> selectedIds = Set.copyOf(selected);
		List<DependencyDescriptor> ordered = new ArrayList<>(offered.size());
		for (boolean wantSelected : new boolean[] { true, false }) {
			for (boolean wantProjects : new boolean[] { true, false }) {
				offered.stream()
						.filter(d -> selectedIds.contains(d.id()) == wantSelected && d.isWorkspaceProject() == wantProjects)
						.forEach(ordered::add);
			}
		}
		return ordered;
	}

	/**
	 * The selection once the user picked among the dependencies offered. The ids selected earlier
	 * that are not offered right now (a project closed meanwhile, say) are kept, so the selection
	 * comes back once they are offered again.
	 */
	static List<String> selectionAfterPicking(Collection<String> selected, List<DependencyDescriptor> offered, Collection<String> picked) {
		Set<String> offeredIds = Set.copyOf(offered.stream().map(DependencyDescriptor::id).toList());
		return Stream.concat(selected.stream().filter(id -> !offeredIds.contains(id)), picked.stream()).distinct().toList();
	}

	/**
	 * How a dependency is described next to its name: that it is a workspace project, or the Maven
	 * coordinates of a library, if they are known.
	 */
	static String describe(DependencyDescriptor dependency) {
		if (dependency.isWorkspaceProject()) {
			return "workspace project";
		}
		if (dependency.groupId() != null && dependency.artifactId() != null) {
			return Stream.of(dependency.groupId(), dependency.artifactId(), dependency.version())
					.filter(part -> part != null && !part.isEmpty())
					.reduce((a, b) -> a + ":" + b).orElse("");
		}
		return "";
	}

	/**
	 * Whether the dependency matches what the user typed to narrow the list down.
	 */
	static boolean matches(DependencyDescriptor dependency, String filter) {
		if (filter == null || filter.isBlank()) {
			return true;
		}
		String lowerCaseFilter = filter.trim().toLowerCase();
		return Stream.of(dependency.displayName(), describe(dependency), dependency.location())
				.anyMatch(text -> text != null && text.toLowerCase().contains(lowerCaseFilter));
	}

}
