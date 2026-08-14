/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.mcp;

import java.util.Collection;
import java.util.Optional;

import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.stereotype.Component;

/**
 * Resolves workspace projects by name on behalf of the MCP tools.
 *
 * <p>MCP tools identify a project by its IDE project name (as returned by {@code getProjectList}),
 * matched case-insensitively, rather than by document URI. This component centralizes that lookup
 * so the individual tool classes do not each repeat it.
 *
 * @author Martin Lippert
 */
@Component
public class ProjectLookup {

	private final JavaProjectFinder projectFinder;

	public ProjectLookup(JavaProjectFinder projectFinder) {
		this.projectFinder = projectFinder;
	}

	/**
	 * All Java projects currently known in the workspace.
	 */
	public Collection<? extends IJavaProject> all() {
		return projectFinder.all();
	}

	/**
	 * Finds the project with the given name, matching case-insensitively.
	 *
	 * @param projectName the IDE project name to look for
	 * @return the matching project, or empty if no project has that name
	 */
	public Optional<? extends IJavaProject> find(String projectName) {
		return projectFinder.all().stream()
				.filter(project -> project.getElementName().equalsIgnoreCase(projectName))
				.findFirst();
	}

	/**
	 * Same as {@link #find(String)}, but fails instead of returning empty when no project matches.
	 *
	 * @param projectName the IDE project name to look for
	 * @return the matching project, never null
	 * @throws Exception if no project with that name exists
	 */
	public IJavaProject get(String projectName) throws Exception {
		Optional<? extends IJavaProject> found = find(projectName);

		if (found.isEmpty()) {
			throw new Exception("project with name " + projectName + " not found");
		}
		return found.get();
	}

}
