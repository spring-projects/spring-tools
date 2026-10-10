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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges the results of overlapping structure requests into the project nodes the view shows.
 *
 * Requests can overlap - an index update's partial refresh while a full load is still being
 * computed, say - and complete in any order. Each project's node is only ever replaced by the
 * result of a request started after the one it came from, so a slow older request can neither roll
 * a project back nor drop one a newer request brought in.
 *
 * Requests are numbered in the order they are started, with {@link #startRequest()}. A result is
 * merged in with {@link #merge(int, Collection, List)}, whenever it arrives.
 *
 * @author Martin Lippert
 */
class StructureTreeMerger {

	private int requestCounter = 0;

	/**
	 * Per project, the number of the request its current node came from.
	 */
	private final Map<String, Integer> projectVersions = new HashMap<>();

	private List<StereotypeNode> rootElements = List.of();

	/**
	 * @return the number of the request that starts now
	 */
	synchronized int startRequest() {
		return ++requestCounter;
	}

	/**
	 * Merges the result of a request into the current project nodes.
	 *
	 * @param requestNumber the number {@link #startRequest()} gave the request
	 * @param affectedProjects the projects the request asked for, none (or null) for a full load
	 * @param answered the project nodes the request answered with
	 * @return the project nodes as merged by now
	 */
	synchronized List<StereotypeNode> merge(int requestNumber, Collection<String> affectedProjects, List<StereotypeNode> answered) {
		boolean isPartialLoad = affectedProjects != null && !affectedProjects.isEmpty();

		Map<String, StereotypeNode> answeredByProject = new LinkedHashMap<>();
		for (StereotypeNode node : answered) {
			answeredByProject.put(node.getProjectId(), node);
		}

		// the projects this request speaks for: a partial one also answers for projects that were
		// not asked for - in dependency mode, those that include an affected one
		Set<String> covered = null;
		if (isPartialLoad) {
			covered = new HashSet<>(affectedProjects);
			covered.addAll(answeredByProject.keySet());
		}

		List<StereotypeNode> newNodes = new ArrayList<>();
		for (StereotypeNode node : rootElements) {
			String projectId = node.getProjectId();
			boolean speaksFor = covered == null || covered.contains(projectId);
			if (speaksFor && isNewer(projectId, requestNumber)) {
				StereotypeNode newNode = answeredByProject.get(projectId);
				if (newNode != null) {
					newNodes.add(newNode);
					projectVersions.put(projectId, requestNumber);
				} else {
					// element removed
					projectVersions.remove(projectId);
				}
			} else {
				newNodes.add(node);
			}
			answeredByProject.remove(projectId);
		}

		// elements added
		answeredByProject.forEach((projectId, node) -> {
			if (isNewer(projectId, requestNumber)) {
				newNodes.add(node);
				projectVersions.put(projectId, requestNumber);
			}
		});

		rootElements = newNodes;
		return rootElements;
	}

	/**
	 * @return the project nodes as merged by now
	 */
	synchronized List<StereotypeNode> rootElements() {
		return rootElements;
	}

	private boolean isNewer(String projectId, int requestNumber) {
		return projectVersions.getOrDefault(projectId, 0) < requestNumber;
	}

}
