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

import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * One way of getting at a project's dependencies for the logical structure view - open workspace
 * projects ({@link WorkspaceProjectDependencySource}) today, JAR dependencies later. See
 * {@code docs/structure-view-dependencies.md}.
 *
 * <p>Only the discovery half so far: which dependencies can be offered for selection. The half
 * that supplies a selected dependency's elements to the tree builder comes with the step of the
 * plan that actually includes them in the tree.
 *
 * @author Martin Lippert
 */
public interface StructureDependencySource {

	/**
	 * The dependencies of the given project this source can offer for inclusion.
	 */
	List<DependencyDescriptor> discover(IJavaProject project);

}
