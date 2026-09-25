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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.commons.java.ClasspathDependencyResolver.WorkspaceProjectDependency;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;

/**
 * @author Martin Lippert
 */
public class ClasspathDependencyResolverTest {

	private static final File APP = new File("/ws/app");
	private static final File SHARED = new File("/ws/shared");
	private static final File OTHER = new File("/ws/other");

	@Test
	void findsEachReferencedWorkspaceProjectOnceAndIgnoresEverythingElse() throws Exception {
		IJavaProject app = project("app", APP, List.of(
				ownSource(APP, "src/main/java"),
				ownSource(APP, "src/test/java"),
				dependencySource(SHARED, "shared", "src/main/java"),
				dependencySource(SHARED, "shared", "src/main/resources"),
				dependencySource(OTHER, "other", "src/main/java"),
				CPE.binary("/repo/some-lib-1.0.jar"),
				system("/jdk/lib/rt.jar"),
				legacySource("/ws/elsewhere/src")));

		List<WorkspaceProjectDependency> dependencies = new ClasspathDependencyResolver(finder()).workspaceProjectDependenciesOf(app);

		assertEquals(List.of(
				new WorkspaceProjectDependency("shared", SHARED.getAbsolutePath()),
				new WorkspaceProjectDependency("other", OTHER.getAbsolutePath())), dependencies);
	}

	@Test
	void resolvesTheNameByLocationWhenTheClasspathCarriesNone() throws Exception {
		IJavaProject shared = project("shared", SHARED, List.of());
		IJavaProject app = project("app", APP, List.of(
				ownSource(APP, "src/main/java"),
				source(SHARED, "src/main/java", Map.of(CPE.EXTRA_PROJECT_LOCATION, SHARED.getAbsolutePath()))));

		List<WorkspaceProjectDependency> dependencies = new ClasspathDependencyResolver(finder(app, shared)).workspaceProjectDependenciesOf(app);

		assertEquals(List.of(new WorkspaceProjectDependency("shared", SHARED.getAbsolutePath())), dependencies);
	}

	@Test
	void dropsADependencyWithoutANameWhoseProjectIsNotOpen() throws Exception {
		IJavaProject app = project("app", APP, List.of(
				source(SHARED, "src/main/java", Map.of(CPE.EXTRA_PROJECT_LOCATION, SHARED.getAbsolutePath()))));

		assertEquals(List.of(), new ClasspathDependencyResolver(finder(app)).workspaceProjectDependenciesOf(app));
	}

	@Test
	void aClasspathWithoutProjectReferencesHasNoWorkspaceProjectDependencies() throws Exception {
		// what the standalone language server's Maven/Gradle classpaths look like - no workspace
		// resolution, so a sibling module arrives as a plain jar
		IJavaProject app = project("app", APP, List.of(
				ownSource(APP, "src/main/java"),
				CPE.binary("/repo/shared-1.0.jar")));

		assertEquals(List.of(), new ClasspathDependencyResolver(finder(app)).workspaceProjectDependenciesOf(app));
	}

	private static CPE ownSource(File project, String folder) {
		CPE cpe = source(project, folder, extras(project, project.getName()));
		cpe.setOwn(true);
		return cpe;
	}

	private static CPE dependencySource(File project, String name, String folder) {
		return source(project, folder, extras(project, name));
	}

	/** A source folder with no project information at all, as some classpath providers produce. */
	private static CPE legacySource(String folder) {
		return CPE.source(new File(folder), new File(folder, "../bin"));
	}

	private static CPE source(File project, String folder, Map<String, String> extra) {
		return CPE.source(new File(project, folder), new File(project, "target/classes"), extra);
	}

	private static Map<String, String> extras(File project, String name) {
		return Map.of(CPE.EXTRA_PROJECT_LOCATION, project.getAbsolutePath(), CPE.EXTRA_PROJECT_NAME, name);
	}

	private static CPE system(String path) {
		CPE cpe = CPE.binary(path);
		cpe.setSystem(true);
		return cpe;
	}

	private static IJavaProject project(String name, File location, Collection<CPE> entries) throws Exception {
		IClasspath classpath = mock(IClasspath.class);
		when(classpath.getClasspathEntries()).thenReturn(entries);
		when(classpath.getName()).thenReturn(name);

		IJavaProject project = mock(IJavaProject.class);
		when(project.getClasspath()).thenReturn(classpath);
		when(project.getElementName()).thenReturn(name);
		when(project.getLocationUri()).thenReturn(location.toURI());
		return project;
	}

	private static JavaProjectFinder finder(IJavaProject... projects) {
		JavaProjectFinder finder = mock(JavaProjectFinder.class);
		when(finder.all()).thenAnswer(invocation -> List.of(projects));
		return finder;
	}

}
