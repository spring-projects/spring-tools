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

import java.util.List;

import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * One way of getting at a project's dependencies for the logical structure view - open workspace
 * projects ({@link WorkspaceProjectDependencySource}) today, JAR dependencies later. See
 * {@code docs/structure-view-dependencies.md}.
 *
 * <p>Two halves: which dependencies can be offered for selection ({@link #discover}), and the
 * elements a selected one contributes to the tree ({@link #elementsOf}).
 *
 * @author Martin Lippert
 */
public interface StructureDependencySource {

	/**
	 * The dependencies of the given project this source can offer for inclusion.
	 */
	List<DependencyDescriptor> discover(IJavaProject project);

	/**
	 * The elements the given dependency contributes to a tree, resolving stereotypes against the
	 * given catalog - the one of the tree they are included in.
	 *
	 * @param including the project the dependency is being included in - a JAR has no classpath of
	 *        its own, so resolving its classes' annotations (which can be declared several JARs away
	 *        from where they are used, e.g. a meta-annotation) needs the including project's
	 * @return null when the dependency is not one this source supplies elements for
	 */
	StructureElements elementsOf(DependencyDescriptor dependency, IJavaProject including, CachedSpringMetamodelIndex cachedIndex,
			AbstractStereotypeCatalog catalog);

	/**
	 * Whether the dependency has classes in the given package or below it - which, for a package
	 * Spring Modulith knows as an application module's, makes the dependency part of the
	 * application's structure whether it is selected or not.
	 *
	 * <p>Only for a source that can tell without doing expensive work - a workspace project, from
	 * the index. A JAR never answers {@code true}: it is only ever read once the user selected it,
	 * so a module whose classes are in a JAR stays empty until then.
	 */
	default boolean containsPackage(DependencyDescriptor dependency, String packageName, CachedSpringMetamodelIndex cachedIndex) {
		return false;
	}

	/**
	 * Whether {@link #elementsOf} can answer for the dependency right away, rather than having to
	 * do expensive work first - scanning a JAR, say.
	 */
	default boolean isReady(DependencyDescriptor dependency, IJavaProject including) {
		return true;
	}

	/**
	 * Does the work that makes the given dependencies - all of them not {@link #isReady} - ready,
	 * together, and returns once done.
	 */
	default void prepare(IJavaProject including, List<DependencyDescriptor> dependencies) {
	}

	/**
	 * Does, in the background, the work that makes the given dependencies - all of them not
	 * {@link #isReady} - ready, and calls {@code onReady} once done, so the including project's
	 * tree can be built again with them.
	 */
	default void prepareInBackground(IJavaProject including, List<DependencyDescriptor> dependencies, Runnable onReady) {
		onReady.run();
	}

}
