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
import java.util.Set;
import java.util.stream.Collectors;

import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.springframework.ide.vscode.commons.java.ClasspathDependencyResolver;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;

/**
 * Offers the open workspace projects a project depends on, as found on its classpath by
 * {@link ClasspathDependencyResolver} - restricted to the ones the language server actually knows
 * (and therefore indexes), since only those have elements to include.
 *
 * @author Martin Lippert
 */
public class WorkspaceProjectDependencySource implements StructureDependencySource {

	private final ClasspathDependencyResolver resolver;
	private final JavaProjectFinder projectFinder;

	public WorkspaceProjectDependencySource(ClasspathDependencyResolver resolver, JavaProjectFinder projectFinder) {
		this.resolver = resolver;
		this.projectFinder = projectFinder;
	}

	@Override
	public List<DependencyDescriptor> discover(IJavaProject project) {
		Set<String> knownProjects = projectFinder.all().stream()
				.map(IJavaProject::getElementName)
				.collect(Collectors.toSet());

		return resolver.workspaceProjectDependenciesOf(project).stream()
				.filter(dependency -> knownProjects.contains(dependency.projectName()))
				.map(dependency -> DependencyDescriptor.workspaceProject(dependency.projectName(), dependency.location()))
				.toList();
	}

	@Override
	public StructureElements elementsOf(DependencyDescriptor dependency, IJavaProject including, CachedSpringMetamodelIndex cachedIndex,
			AbstractStereotypeCatalog catalog) {
		if (dependency.kind() != DependencyDescriptor.Kind.WORKSPACE_PROJECT) {
			return null;
		}

		return projectFinder.all().stream()
				.filter(project -> project.getElementName().equals(dependency.projectName()))
				.findFirst()
				.map(project -> IndexStructureElements.of(project, cachedIndex, catalog))
				.orElse(null);
	}

}
