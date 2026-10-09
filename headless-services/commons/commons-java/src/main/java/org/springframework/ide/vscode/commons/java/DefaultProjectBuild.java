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
package org.springframework.ide.vscode.commons.java;

import java.net.URI;
import java.util.Set;

/**
 * Value based implementation, such that a project build can be compared with the previous one.
 *
 * @author Alex Boyko
 */
record DefaultProjectBuild(String type, URI buildFile, Set<String> tasks) implements IProjectBuild {

	@Override
	public String getType() {
		return type;
	}

	@Override
	public URI getBuildFile() {
		return buildFile;
	}

	@Override
	public Set<String> getTasks() {
		return tasks;
	}

}
