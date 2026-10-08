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
package org.springframework.ide.vscode.commons.protocol.java;

import java.util.Set;

/**
 * Describes the build of a project.
 *
 * @param type {@link #MAVEN_PROJECT_TYPE} or {@link #GRADLE_PROJECT_TYPE}, <code>null</code> if unknown
 * @param buildFile URI of the build file, <code>null</code> if unknown
 * @param tasks names of the tasks of the build, as far as the IDE knows them without running the build tool.
 * Currently only provided for Gradle. <code>null</code> means that it is not known (not provided or the IDE
 * has not loaded the Gradle model yet), an empty set means that the build has no tasks. A set, such that two
 * builds with the same tasks are equal whatever the order.
 */
public record ProjectBuild(String type, String buildFile, Set<String> tasks)  {
	
	public static final String MAVEN_PROJECT_TYPE = "maven";
	public static final String GRADLE_PROJECT_TYPE = "gradle";
	
	public ProjectBuild(String type, String buildFile) {
		this(type, buildFile, null);
	}
	
	public static ProjectBuild createMavenBuild(String buildFile) {
		return new ProjectBuild(MAVEN_PROJECT_TYPE, buildFile);
	}
	
	public static ProjectBuild createGradleBuild(String buildFile) {
		return new ProjectBuild(GRADLE_PROJECT_TYPE, buildFile);
	}
	
	public static ProjectBuild createGradleBuild(String buildFile, Set<String> tasks) {
		return new ProjectBuild(GRADLE_PROJECT_TYPE, buildFile, tasks);
	}
	
	@Override
	public String toString() {
		return "ProjectBuild [type=" + type + ", buildFile=" + buildFile + ", tasks=" + (tasks == null ? "unknown" : tasks.size()) + "]";
	}

}
