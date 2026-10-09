/*******************************************************************************
 * Copyright (c) 2022, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.java;

import java.net.URI;
import java.util.Set;

public interface IProjectBuild {
	
	String getType();
	
	URI getBuildFile();
	
	/**
	 * Names of the tasks of the build, as far as the IDE knows them without running the build tool
	 * (currently Gradle only).
	 *
	 * @return the task names, <code>null</code> if not known. An empty set means that there are no tasks.
	 */
	default Set<String> getTasks() {
		return null;
	}
	
	static IProjectBuild create(String type, URI buildFile) {
		return create(type, buildFile, null);
	}
	
	static IProjectBuild create(String type, URI buildFile, Set<String> tasks) {
		return new DefaultProjectBuild(type, buildFile, tasks == null ? null : Set.copyOf(tasks));
	}

}
