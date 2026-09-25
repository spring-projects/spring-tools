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

/**
 * A dependency of a project that the user can select to include in that project's logical
 * structure tree - what {@code sts/spring-boot/structure/dependencies} hands to the clients to
 * render a picker from, and what the ids in a structure request's {@code dependencies} selection
 * refer to. See {@code docs/structure-view-dependencies.md}.
 *
 * @param id stable identifier the selection is stored and sent as - deliberately version-free, so a
 *        selection survives a version bump: {@code "project:<projectName>"} for a workspace project,
 *        {@code "gav:<groupId>:<artifactId>"} for a JAR with known Maven coordinates,
 *        {@code "jar:<name>"} (the file name without version) for any other JAR
 * @param kind whether the dependency is an open workspace project or a JAR
 * @param displayName what to show the user - the project name, or the artifact id
 * @param groupId the Maven group id, if known (may be null)
 * @param artifactId the Maven artifact id, if known (may be null)
 * @param version the version, if known (may be null)
 * @param projectName the workspace project's name, null for a JAR
 * @param location the workspace project's file system location, or the JAR's path
 *
 * @author Martin Lippert
 */
public record DependencyDescriptor(
		String id,
		Kind kind,
		String displayName,
		String groupId,
		String artifactId,
		String version,
		String projectName,
		String location) {

	public enum Kind { WORKSPACE_PROJECT, JAR }

	public static final String WORKSPACE_PROJECT_ID_PREFIX = "project:";

	public static final String GAV_ID_PREFIX = "gav:";
	public static final String JAR_ID_PREFIX = "jar:";

	public static DependencyDescriptor workspaceProject(String projectName, String location) {
		return new DependencyDescriptor(WORKSPACE_PROJECT_ID_PREFIX + projectName, Kind.WORKSPACE_PROJECT, projectName,
				null, null, null, projectName, location);
	}

	public static DependencyDescriptor jar(String groupId, String artifactId, String version, String path) {
		return new DependencyDescriptor(GAV_ID_PREFIX + groupId + ":" + artifactId, Kind.JAR, artifactId,
				groupId, artifactId, version, null, path);
	}

	/**
	 * A JAR whose Maven coordinates are unknown - identified by its file name alone.
	 */
	public static DependencyDescriptor jar(String name, String path) {
		return new DependencyDescriptor(JAR_ID_PREFIX + name, Kind.JAR, name, null, null, null, null, path);
	}

}
