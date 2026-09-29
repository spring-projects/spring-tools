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
import org.springframework.ide.vscode.commons.java.ClasspathDependencyResolver.JarDependency;
import org.springframework.ide.vscode.commons.java.ClasspathDependencyResolver.WorkspaceProjectDependency;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.protocol.java.Gav;

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

	@Test
	void findsJarsWithTheirMavenCoordinatesAndLeavesOutTheJreAndTestOnlyJars() throws Exception {
		IJavaProject app = project("app", APP, List.of(
				ownSource(APP, "src/main/java"),
				dependencySource(SHARED, "shared", "src/main/java"),
				jar("/repo/org/springframework/spring-web/7.0.1/spring-web-7.0.1.jar", "org.springframework", "spring-web", "7.0.1", "compile"),
				jar("/repo/org/junit/junit-jupiter/5.12.0/junit-jupiter-5.12.0.jar", "org.junit", "junit-jupiter", "5.12.0", "test"),
				testJar("/repo/org/assertj/assertj-core/3.27.0/assertj-core-3.27.0.jar"),
				system("/jdk/lib/rt.jar")));

		List<JarDependency> jars = new ClasspathDependencyResolver(finder(app)).jarDependenciesOf(app);

		assertEquals(List.of(new JarDependency(new Gav("org.springframework", "spring-web", "7.0.1"), "spring-web",
				"/repo/org/springframework/spring-web/7.0.1/spring-web-7.0.1.jar")), jars);
	}

	@Test
	void takesTheCoordinatesOfAJarFromItsLocationInTheGradleCacheWhenTheClasspathHasNone() throws Exception {
		String inGradleCache = "/home/me/.gradle/caches/modules-2/files-2.1/org.springframework/spring-core/7.0.1/0a1b2c/spring-core-7.0.1.jar";
		IJavaProject app = project("app", APP, List.of(CPE.binary(inGradleCache), CPE.binary("/libs/local-helper-1.2.jar")));

		List<JarDependency> jars = new ClasspathDependencyResolver(finder(app)).jarDependenciesOf(app);

		assertEquals(List.of(
				new JarDependency(new Gav("org.springframework", "spring-core", "7.0.1"), "spring-core", inGradleCache),
				new JarDependency(null, "local-helper", "/libs/local-helper-1.2.jar")), jars);
	}

	/**
	 * A binary classpath entry can be a class folder - not a JAR, and nothing to offer as one.
	 */
	@Test
	void aClassFolderIsNotOfferedAsAJar() throws Exception {
		IJavaProject app = project("app", APP, List.of(CPE.binary("/ws/other/build/classes/java/main"), CPE.binary("/repo/lib-1.0.jar")));

		assertEquals(List.of("/repo/lib-1.0.jar"), new ClasspathDependencyResolver(finder(app)).jarDependenciesOf(app).stream()
				.map(JarDependency::path).toList());
	}

	@Test
	void listsAJarOnlyOncePerGroupAndArtifact() throws Exception {
		IJavaProject app = project("app", APP, List.of(
				jar("/repo/a/lib-1.0.jar", "com.example", "lib", "1.0", "compile"),
				jar("/repo/b/lib-2.0.jar", "com.example", "lib", "2.0", "runtime")));

		List<JarDependency> jars = new ClasspathDependencyResolver(finder(app)).jarDependenciesOf(app);

		assertEquals(1, jars.size());
		assertEquals("1.0", jars.get(0).gav().version());
	}

	@Test
	void recognizesOnlyTheExactGradleCacheLayout() {
		assertEquals(new Gav("g", "a", "1"), ClasspathDependencyResolver.gavFromGradleCachePath("/x/files-2.1/g/a/1/hash/a-1.jar"));
		assertEquals(null, ClasspathDependencyResolver.gavFromGradleCachePath("/x/files-2.1/g/a/1/a-1.jar"));
		assertEquals(null, ClasspathDependencyResolver.gavFromGradleCachePath("/repo/g/a/1/a-1.jar"));
		assertEquals(null, ClasspathDependencyResolver.gavFromGradleCachePath(null));
	}

	private static CPE jar(String path, String groupId, String artifactId, String version, String scope) {
		CPE cpe = CPE.binary(path);
		cpe.setExtra(Map.of(CPE.EXTRA_GROUP_ID, groupId, CPE.EXTRA_ARTIFACT_ID, artifactId, CPE.EXTRA_VERSION, version,
				CPE.EXTRA_SCOPE, scope));
		return cpe;
	}

	private static CPE testJar(String path) {
		CPE cpe = CPE.binary(path);
		cpe.setTest(true);
		return cpe;
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
