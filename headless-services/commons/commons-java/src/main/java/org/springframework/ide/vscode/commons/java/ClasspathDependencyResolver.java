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

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;

/**
 * Finds the workspace projects a project depends on, straight from its classpath.
 *
 * <p>No build-tool specific resolution needed: when the classpath comes from the Java tooling
 * (JDT-LS, or the Eclipse plugin), Maven/Gradle workspace resolution already turns a dependency on
 * an open workspace module into a project reference, which arrives as that project's source folders
 * - not {@link CPE#isOwn() own}, and carrying the referenced project's location (and, from recent
 * tooling versions on, its name) in {@link CPE#getExtra()}. Classpath providers that do no workspace
 * resolution (the standalone language server's Maven/Gradle classpaths) produce no such entries, so
 * no workspace project dependencies are found there.
 *
 * @author Martin Lippert
 */
public class ClasspathDependencyResolver {

	private static final Logger log = LoggerFactory.getLogger(ClasspathDependencyResolver.class);

	private final JavaProjectFinder projectFinder;

	public ClasspathDependencyResolver(JavaProjectFinder projectFinder) {
		this.projectFinder = projectFinder;
	}

	/**
	 * The distinct workspace projects the given project depends on, in classpath order - one per
	 * referenced project, even though each contributes several source folders.
	 */
	public List<WorkspaceProjectDependency> workspaceProjectDependenciesOf(IJavaProject project) {
		Set<WorkspaceProjectDependency> result = new LinkedHashSet<>();

		try {
			for (CPE cpe : project.getClasspath().getClasspathEntries()) {
				if (Classpath.isWorkspaceProjectDependency(cpe)) {
					String name = cpe.getProjectName() != null ? cpe.getProjectName() : nameOfProjectAt(cpe.getProjectLocation()).orElse(null);
					if (name != null && !name.equals(project.getElementName())) {
						result.add(new WorkspaceProjectDependency(name, cpe.getProjectLocation()));
					}
				}
			}
		} catch (Exception e) {
			log.error("cannot determine workspace project dependencies of project: " + project.getElementName(), e);
		}

		return new ArrayList<>(result);
	}

	/**
	 * Fallback for a classpath sent by a version of the tooling that recorded only the location of a
	 * referenced project, not its name.
	 */
	private Optional<String> nameOfProjectAt(String location) {
		Path path = normalize(new File(location).toURI());
		if (path == null) {
			return Optional.empty();
		}
		return projectFinder.all().stream()
				.filter(p -> p.getLocationUri() != null && Objects.equals(path, normalize(p.getLocationUri())))
				.map(IJavaProject::getElementName)
				.findFirst();
	}

	private static Path normalize(URI uri) {
		try {
			return Paths.get(uri).toAbsolutePath().normalize();
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * A workspace project another project depends on.
	 *
	 * @param projectName the dependency project's name - what the index is keyed by
	 * @param location the dependency project's file system location
	 */
	public static record WorkspaceProjectDependency(String projectName, String location) {
	}

}
