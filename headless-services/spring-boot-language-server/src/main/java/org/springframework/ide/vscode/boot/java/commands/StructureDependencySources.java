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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * All {@link StructureDependencySource}s together: what a project's dependency picker offers.
 *
 * @author Martin Lippert
 */
public class StructureDependencySources {

	private static final Logger log = LoggerFactory.getLogger(StructureDependencySources.class);

	private static final Comparator<DependencyDescriptor> ORDER = Comparator
			.comparing(DependencyDescriptor::kind) // workspace projects first, then JARs
			.thenComparing(DependencyDescriptor::displayName, String.CASE_INSENSITIVE_ORDER);

	private final List<StructureDependencySource> sources;

	public StructureDependencySources(List<StructureDependencySource> sources) {
		this.sources = sources;
	}

	/**
	 * The dependencies all sources offer for the given project, one per id, workspace projects first
	 * and each kind in alphabetical order.
	 */
	public List<DependencyDescriptor> discoverAll(IJavaProject project) {
		Map<String, DependencyDescriptor> byId = new LinkedHashMap<>();

		for (StructureDependencySource source : sources) {
			try {
				source.discover(project).forEach(dependency -> byId.putIfAbsent(dependency.id(), dependency));
			} catch (Exception e) {
				log.error("cannot discover structure dependencies of project " + project.getElementName() + " via " + source, e);
			}
		}

		return byId.values().stream().sorted(ORDER).toList();
	}

	/**
	 * The subset of the given selected ids that currently refer to an offered dependency - a selected
	 * dependency can disappear (project closed, dependency removed from the build), and its id then
	 * simply stops resolving rather than being an error.
	 */
	public List<DependencyDescriptor> resolve(IJavaProject project, List<String> selectedIds) {
		if (selectedIds == null || selectedIds.isEmpty()) {
			return List.of();
		}

		return discoverAll(project).stream()
				.filter(dependency -> selectedIds.contains(dependency.id()))
				.toList();
	}

}
