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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.protocol.java.Gav;

/**
 * Finds the dependencies of a project - the workspace projects and the JARs it depends on -
 * straight from its classpath.
 *
 * <p>No build-tool specific resolution needed: when the classpath comes from the Java tooling
 * (JDT-LS, or the Eclipse plugin), Maven/Gradle workspace resolution already turns a dependency on
 * an open workspace module into a project reference, which arrives as that project's source folders
 * - not {@link CPE#isOwn() own}, and carrying the referenced project's location (and, from recent
 * tooling versions on, its name) in {@link CPE#getExtra()}. Classpath providers that do no workspace
 * resolution (the standalone language server's Maven/Gradle classpaths) produce no such entries, so
 * no workspace project dependencies are found there.
 *
 * <p>A JAR's Maven coordinates come from its classpath entry when the classpath provider recorded
 * them (the Java tooling's Maven integration, the standalone Maven classpath), and otherwise from
 * its location in the Gradle cache, whose layout encodes them - which covers Gradle projects in the
 * Java tooling, which records none, and the standalone Gradle classpath.
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
	 * The distinct JARs the given project depends on, in classpath order - one per Maven group and
	 * artifact id where those are known, one per file otherwise. Leaves out the JRE and whatever the
	 * classpath provider marks as test-only.
	 */
	public List<JarDependency> jarDependenciesOf(IJavaProject project) {
		Map<String, JarDependency> result = new LinkedHashMap<>();

		try {
			for (CPE cpe : project.getClasspath().getClasspathEntries()) {
				if (Classpath.isBinary(cpe) && isJar(cpe) && !cpe.isSystem() && !cpe.isTest() && !"test".equals(cpe.getScope())) {
					Gav gav = cpe.getGav() != null ? cpe.getGav() : gavFromGradleCachePath(cpe.getPath());
					JarDependency jar = new JarDependency(gav, cpe.getName(), cpe.getPath());
					result.putIfAbsent(gav != null ? gav.groupId() + ":" + gav.artifactId() : cpe.getPath(), jar);
				}
			}
		} catch (Exception e) {
			log.error("cannot determine jar dependencies of project: " + project.getElementName(), e);
		}

		return new ArrayList<>(result.values());
	}

	/**
	 * A binary classpath entry can also be a class folder (a Gradle build directory, a library
	 * folder) - not a JAR, and nothing to offer as one.
	 */
	private static boolean isJar(CPE cpe) {
		return cpe.getPath() != null && cpe.getPath().toLowerCase(Locale.ROOT).endsWith(".jar");
	}

	/**
	 * The Maven coordinates encoded in a path of the Gradle dependency cache,
	 * {@code .../files-2.1/<group>/<artifact>/<version>/<hash>/<file>} - null for any other path.
	 */
	static Gav gavFromGradleCachePath(String path) {
		if (path == null) {
			return null;
		}

		Path p = Paths.get(path);
		for (int i = 0; i < p.getNameCount(); i++) {
			if ("files-2.1".equals(p.getName(i).toString()) && p.getNameCount() == i + 6) {
				return new Gav(p.getName(i + 1).toString(), p.getName(i + 2).toString(), p.getName(i + 3).toString());
			}
		}
		return null;
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

	/**
	 * A JAR a project depends on.
	 *
	 * @param gav the JAR's Maven coordinates, null if unknown
	 * @param name the JAR's file name without version and extension
	 * @param path the JAR's file system location
	 */
	public static record JarDependency(Gav gav, String name, String path) {
	}

}
