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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
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
	private final Consumer<IJavaProject> onDependenciesReady;

	public StructureDependencySources(List<StructureDependencySource> sources) {
		this(sources, project -> {});
	}

	/**
	 * @param onDependenciesReady told when dependencies left out of a project's tree for being
	 *        prepared in the background (see {@link #elementsOf(IJavaProject, List,
	 *        CachedSpringMetamodelIndex, AbstractStereotypeCatalog, boolean)}) are ready - so that
	 *        the project's tree can be built again, with them
	 */
	public StructureDependencySources(List<StructureDependencySource> sources, Consumer<IJavaProject> onDependenciesReady) {
		this.sources = sources;
		this.onDependenciesReady = onDependenciesReady;
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
	/**
	 * The dependencies of the project that have classes in one of the given packages or below them -
	 * see {@link StructureDependencySource#containsPackage}.
	 */
	public List<DependencyDescriptor> containing(IJavaProject project, Collection<String> packageNames, CachedSpringMetamodelIndex cachedIndex) {
		// an empty package name is everything: no module has one, and none must match every dependency
		List<String> modulePackages = packageNames.stream().filter(name -> name != null && !name.isBlank()).toList();
		if (modulePackages.isEmpty()) {
			return List.of();
		}

		return discoverAll(project).stream()
				.filter(dependency -> !project.getElementName().equals(dependency.projectName()))
				.filter(dependency -> sources.stream().anyMatch(source -> modulePackages.stream().anyMatch(packageName -> {
					try {
						return source.containsPackage(dependency, packageName, cachedIndex);
					} catch (Exception e) {
						log.error("cannot tell whether structure dependency " + dependency.id() + " has classes in " + packageName, e);
						return false;
					}
				})))
				.toList();
	}

	public List<DependencyDescriptor> resolve(IJavaProject project, List<String> selectedIds) {
		if (selectedIds == null || selectedIds.isEmpty()) {
			return List.of();
		}

		List<DependencyDescriptor> offered = discoverAll(project);
		List<DependencyDescriptor> resolved = offered.stream()
				.filter(dependency -> selectedIds.contains(dependency.id()))
				.toList();

		if (resolved.size() < selectedIds.size()) {
			List<String> resolvedIds = resolved.stream().map(DependencyDescriptor::id).toList();
			List<String> dropped = selectedIds.stream().filter(id -> !resolvedIds.contains(id)).toList();

			// worth an INFO, not silence: a JAR's id depends on GAV coordinates being found again
			// at discovery time (see ClasspathDependencyResolver.jarDependenciesOf) - unlike a
			// workspace project's stable "project:<name>", it can legitimately drift between
			// requests, which otherwise looks indistinguishable from "nothing was ever selected"
			log.info("project '{}': dropping previously selected dependency id(s) {} - not among what is offered now ({})",
					project.getElementName(), dropped, offered.stream().map(DependencyDescriptor::id).toList());
		}

		return resolved;
	}

	/**
	 * Tells all sources which dependencies the structure view includes in the tree of the given
	 * project now, see {@link StructureDependencySource#retainOnly}.
	 */
	public void retainOnly(IJavaProject including, List<DependencyDescriptor> dependencies) {
		for (StructureDependencySource source : sources) {
			try {
				source.retainOnly(including, dependencies);
			} catch (Exception e) {
				log.error("cannot release the structure dependencies no longer included in project " + including.getElementName() + " via " + source, e);
			}
		}
	}

	/**
	 * The elements of each of the given dependencies that some source can supply, in the given
	 * order - a dependency no source supplies elements for (yet) is left out.
	 *
	 * @param including the project the dependencies are being included in
	 */
	public List<StructureElements> elementsOf(IJavaProject including, List<DependencyDescriptor> dependencies,
			CachedSpringMetamodelIndex cachedIndex, AbstractStereotypeCatalog catalog) {
		return elementsOf(including, dependencies, cachedIndex, catalog, false);
	}

	/**
	 * @param inBackground whether a dependency that is not ready yet - a JAR still to be scanned -
	 *        is to be left out and prepared in the background rather than waited for, so that the
	 *        tree is there right away; {@code onDependenciesReady} is told once they are
	 */
	public List<StructureElements> elementsOf(IJavaProject including, List<DependencyDescriptor> dependencies,
			CachedSpringMetamodelIndex cachedIndex, AbstractStereotypeCatalog catalog, boolean inBackground) {

		// the dependencies not ready yet, per source - prepared together, for one progress per project
		Map<StructureDependencySource, List<DependencyDescriptor>> notReady = new LinkedHashMap<>();
		for (DependencyDescriptor dependency : dependencies) {
			for (StructureDependencySource source : sources) {
				if (!source.isReady(dependency, including)) {
					notReady.computeIfAbsent(source, s -> new ArrayList<>()).add(dependency);
				}
			}
		}

		if (!inBackground) {
			notReady.forEach((source, toPrepare) -> {
				try {
					source.prepare(including, toPrepare);
				} catch (Exception e) {
					log.error("cannot prepare the structure dependencies " + toPrepare.stream().map(DependencyDescriptor::id).toList()
							+ " of project " + including.getElementName() + " via " + source, e);
				}
			});
			notReady.clear();
		}

		List<StructureElements> result = new ArrayList<>();

		for (DependencyDescriptor dependency : dependencies) {
			for (StructureDependencySource source : sources) {
				try {
					if (notReady.getOrDefault(source, List.of()).contains(dependency)) {
						break; // left out for now - being prepared in the background
					}
					StructureElements elements = source.elementsOf(dependency, including, cachedIndex, catalog);
					if (elements != null) {
						result.add(elements);
						break;
					}
				} catch (Exception e) {
					log.error("cannot get the elements of structure dependency " + dependency.id() + " via " + source, e);
				}
			}
		}

		notReady.forEach((source, toPrepare) -> {
			log.info("project '{}': preparing structure dependencies {} in the background", including.getElementName(),
					toPrepare.stream().map(DependencyDescriptor::id).toList());
			source.prepareInBackground(including, toPrepare, () -> {
				log.info("project '{}': structure dependencies prepared in the background - asking for its tree to be built again",
						including.getElementName());
				onDependenciesReady.accept(including);
			});
		});

		return result;
	}

}
