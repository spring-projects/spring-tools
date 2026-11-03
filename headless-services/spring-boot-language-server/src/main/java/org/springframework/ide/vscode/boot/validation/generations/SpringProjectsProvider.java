/*******************************************************************************
 * Copyright (c) 2021, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.validation.generations;

import org.springframework.ide.vscode.boot.validation.generations.json.ResolvedSpringProject;

public interface SpringProjectsProvider {

	/**
	 * 
	 * @param Project slug. E.g. "spring-boot"
	 * @return the project, or <code>null</code> if no project with that slug is available
	 *         (callers must handle this)
	 * @throws Exception if the project information could not be fetched
	 */
	ResolvedSpringProject getProject(String projectSlug) throws Exception;

}