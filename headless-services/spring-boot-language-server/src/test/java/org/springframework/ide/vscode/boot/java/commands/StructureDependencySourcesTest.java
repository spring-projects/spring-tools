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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.commons.java.ClasspathDependencyResolver;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;

/**
 * @author Martin Lippert
 */
public class StructureDependencySourcesTest {

	@Test
	void workspaceProjectsAreOnlyOfferedWhenTheLanguageServerKnowsThem() throws Exception {
		IJavaProject shared = project("shared", List.of());
		IJavaProject app = project("app", List.of(dependencySource("shared"), dependencySource("not-open")));

		JavaProjectFinder finder = finder(app, shared);
		WorkspaceProjectDependencySource source = new WorkspaceProjectDependencySource(new ClasspathDependencyResolver(finder), finder);

		List<DependencyDescriptor> offered = source.discover(app);

		assertEquals(List.of(DependencyDescriptor.workspaceProject("shared", location("shared"))), offered);
		assertEquals("project:shared", offered.get(0).id());
	}

	@Test
	void jarsAreOfferedByMavenCoordinatesWhenKnownAndByFileNameOtherwise() throws Exception {
		CPE withGav = CPE.binary("/repo/spring-web-7.0.1.jar");
		withGav.setExtra(Map.of(CPE.EXTRA_GROUP_ID, "org.springframework", CPE.EXTRA_ARTIFACT_ID, "spring-web", CPE.EXTRA_VERSION, "7.0.1"));
		CPE withoutGav = CPE.binary("/libs/local-helper-1.2.jar");

		IJavaProject app = project("app", List.of(withGav, withoutGav));
		JarDependencySource source = new JarDependencySource(new ClasspathDependencyResolver(finder(app)));

		assertEquals(List.of(
				DependencyDescriptor.jar("org.springframework", "spring-web", "7.0.1", "/repo/spring-web-7.0.1.jar"),
				DependencyDescriptor.jar("local-helper", "/libs/local-helper-1.2.jar")), source.discover(app));
		assertEquals("gav:org.springframework:spring-web", source.discover(app).get(0).id());
		assertEquals("jar:local-helper", source.discover(app).get(1).id());
	}

	@Test
	void discoverAllMergesSourcesDedupedByIdAndSortsProjectsFirstThenByName() {
		IJavaProject app = mock(IJavaProject.class);
		DependencyDescriptor zeta = DependencyDescriptor.workspaceProject("zeta", "/ws/zeta");
		DependencyDescriptor alpha = DependencyDescriptor.workspaceProject("Alpha", "/ws/alpha");
		DependencyDescriptor jar = new DependencyDescriptor("gav:com.example:aaa", DependencyDescriptor.Kind.JAR, "aaa",
				"com.example", "aaa", "1.0", null, "/repo/aaa-1.0.jar");

		StructureDependencySources sources = new StructureDependencySources(List.of(
				offering(jar, zeta),
				offering(alpha, zeta)));

		assertEquals(List.of(alpha, zeta, jar), sources.discoverAll(app));
	}

	@Test
	void resolveKeepsOnlySelectedIdsThatAreStillOffered() {
		IJavaProject app = mock(IJavaProject.class);
		DependencyDescriptor shared = DependencyDescriptor.workspaceProject("shared", "/ws/shared");
		StructureDependencySources sources = new StructureDependencySources(List.of(offering(shared)));

		assertEquals(List.of(shared), sources.resolve(app, List.of("project:shared", "project:closed-meanwhile")));
		assertEquals(List.of(), sources.resolve(app, List.of()));
		assertEquals(List.of(), sources.resolve(app, null));
	}

	@Test
	void aFailingSourceDoesNotHideTheOthers() {
		IJavaProject app = mock(IJavaProject.class);
		DependencyDescriptor shared = DependencyDescriptor.workspaceProject("shared", "/ws/shared");
		StructureDependencySources sources = new StructureDependencySources(List.of(
				failing(),
				offering(shared)));

		assertEquals(List.of(shared), sources.discoverAll(app));
	}

	private static StructureDependencySource offering(DependencyDescriptor... dependencies) {
		StructureDependencySource source = mock(StructureDependencySource.class);
		when(source.discover(any())).thenReturn(List.of(dependencies));
		return source;
	}

	private static StructureDependencySource failing() {
		StructureDependencySource source = mock(StructureDependencySource.class);
		when(source.discover(any())).thenThrow(new IllegalStateException("broken source"));
		return source;
	}

	private static CPE dependencySource(String projectName) {
		String location = location(projectName);
		return CPE.source(new File(location, "src/main/java"), new File(location, "target/classes"),
				Map.of(CPE.EXTRA_PROJECT_LOCATION, location, CPE.EXTRA_PROJECT_NAME, projectName));
	}

	private static String location(String projectName) {
		return new File("/ws", projectName).getAbsolutePath();
	}

	private static IJavaProject project(String name, List<CPE> entries) throws Exception {
		IClasspath classpath = mock(IClasspath.class);
		when(classpath.getClasspathEntries()).thenReturn(entries);

		IJavaProject project = mock(IJavaProject.class);
		when(project.getClasspath()).thenReturn(classpath);
		when(project.getElementName()).thenReturn(name);
		when(project.getLocationUri()).thenReturn(new File(location(name)).toURI());
		return project;
	}

	private static JavaProjectFinder finder(IJavaProject... projects) {
		JavaProjectFinder finder = mock(JavaProjectFinder.class);
		when(finder.all()).thenAnswer(invocation -> List.of(projects));
		return finder;
	}

}
