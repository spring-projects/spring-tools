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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.lsp4j.WorkDoneProgressBegin;
import org.eclipse.lsp4j.WorkDoneProgressEnd;
import org.eclipse.lsp4j.WorkDoneProgressReport;
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.jmolecules.stereotype.catalog.support.CatalogSource;
import org.jmolecules.stereotype.catalog.support.JsonPathStereotypeCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.boot.java.stereotypes.JarFixtureBuilder;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.commons.java.ClasspathDependencyResolver;
import org.springframework.ide.vscode.commons.java.IClasspath;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.ProgressService;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.STS4LanguageClient;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;

/**
 * @author Martin Lippert
 */
public class JarDependencySourceTest {

	@TempDir
	Path tempDir;

	@Test
	void elementsOfAJarAreScannedAndMatchedAgainstTheGivenCatalog() throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "fixture", Map.of(
				"com.example.Marked", "package com.example; @Marker public class Marked {}",
				"com.example.Marker", "package com.example; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface Marker {}",
				"com.example.NotMarked", "package com.example; public class NotMarked {}"
		), Map.of());

		IJavaProject including = project(jar);
		JarDependencySource source = new JarDependencySource(new ClasspathDependencyResolver(mock(JavaProjectFinder.class)));

		StructureElements elements = source.elementsOf(DependencyDescriptor.jar("fixture", jar.getAbsolutePath()), including,
				null, catalogWithMarker());

		// every scanned type is offered - the composite keeps the stereotyped ones, see CompositeStructureElementsTest
		assertEquals(List.of("com.example.Marked", "com.example.NotMarked"), elements.types().stream().map(StereotypeClassElement::getType).sorted().toList());
		assertEquals(1, ((JarStructureElements) elements).typesWithOwnStereotypeCount());
	}

	@Test
	void aNonJarDependencyIsNotHandled() throws Exception {
		JarDependencySource source = new JarDependencySource(new ClasspathDependencyResolver(mock(JavaProjectFinder.class)));

		StructureElements elements = source.elementsOf(DependencyDescriptor.workspaceProject("other", "/ws/other"),
				mock(IJavaProject.class), null, catalogWithMarker());

		assertNull(elements);
	}

	@Test
	void aMissingJarFileIsHandledGracefullyRatherThanThrowing() throws Exception {
		JarDependencySource source = new JarDependencySource(new ClasspathDependencyResolver(mock(JavaProjectFinder.class)));

		StructureElements elements = source.elementsOf(DependencyDescriptor.jar("missing", "/does/not/exist.jar"),
				mock(IJavaProject.class), null, catalogWithMarker());

		assertNull(elements);
	}

	/**
	 * The expensive part - reading the JAR's classes - is cached by the JAR's own identity, so a
	 * second selection of the very same JAR (by the same or a different project) does not rescan
	 * it. Proven by the scanned {@link StereotypeClassElement} instances themselves being the exact
	 * same objects across two calls - a fresh scan would build entirely new ones, even reading the
	 * exact same bytes.
	 */
	@Test
	void scanningTheSameJarTwiceOnlyReadsItOnce() throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "fixture", Map.of(
				"com.example.Marked", "package com.example; @Marker public class Marked {}",
				"com.example.Marker", "package com.example; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface Marker {}"
		), Map.of());

		IJavaProject including = project(jar);
		JarDependencySource source = new JarDependencySource(new ClasspathDependencyResolver(mock(JavaProjectFinder.class)));
		DependencyDescriptor dependency = DependencyDescriptor.jar("fixture", jar.getAbsolutePath());

		StructureElements first = source.elementsOf(dependency, including, null, catalogWithMarker());
		StructureElements second = source.elementsOf(dependency, including, null, catalogWithMarker());

		assertEquals(1, first.types().size());
		assertSame(first.types().get(0), second.types().get(0));
	}

	@Test
	void aJarNotOnTheIncludingProjectsClasspathIsStillScannedInIsolation() throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "fixture", Map.of(
				"com.example.Marked", "package com.example; @Marker public class Marked {}",
				"com.example.Marker", "package com.example; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface Marker {}"
		), Map.of());

		IJavaProject including = project(); // no classpath entries at all
		JarDependencySource source = new JarDependencySource(new ClasspathDependencyResolver(mock(JavaProjectFinder.class)));

		StructureElements elements = source.elementsOf(DependencyDescriptor.jar("fixture", jar.getAbsolutePath()), including,
				null, catalogWithMarker());

		assertNotNull(elements);
		assertEquals(List.of("com.example.Marked"), elements.types().stream().map(StereotypeClassElement::getType).toList());
	}

	/**
	 * A scan that could not resolve against the including project's classpath - here: the JAR is not
	 * on it - may lack meta-annotations; it is used, but not kept for later requests.
	 */
	@Test
	void anIncompleteScanIsNotCached() throws Exception {
		File jar = markedJar("fixture");
		JarDependencySource source = new JarDependencySource(new ClasspathDependencyResolver(mock(JavaProjectFinder.class)));
		DependencyDescriptor dependency = DependencyDescriptor.jar("fixture", jar.getAbsolutePath());

		StructureElements first = source.elementsOf(dependency, project(), null, catalogWithMarker());
		StructureElements second = source.elementsOf(dependency, project(), null, catalogWithMarker());

		assertNotSame(first.types().get(0), second.types().get(0));
	}

	@Test
	void aChangedJarIsScannedAgain() throws Exception {
		File jar = markedJar("fixture");
		IJavaProject including = project(jar);
		JarDependencySource source = new JarDependencySource(new ClasspathDependencyResolver(mock(JavaProjectFinder.class)));
		DependencyDescriptor dependency = DependencyDescriptor.jar("fixture", jar.getAbsolutePath());

		StructureElements before = source.elementsOf(dependency, including, null, catalogWithMarker());
		assertTrue(jar.setLastModified(jar.lastModified() + 60_000));
		StructureElements after = source.elementsOf(dependency, including, null, catalogWithMarker());

		assertNotSame(before.types().get(0), after.types().get(0));
	}

	/**
	 * A scan is dropped once no project's latest tree includes the JAR any more - but not while
	 * another project still does.
	 */
	@Test
	void aScanIsDroppedOnceNoProjectIncludesTheJarAnyMore() throws Exception {
		File jar = markedJar("fixture");
		IJavaProject projectA = project(jar);
		IJavaProject projectB = project(jar);
		when(projectA.getElementName()).thenReturn("a");
		when(projectB.getElementName()).thenReturn("b");
		JarDependencySource source = new JarDependencySource(new ClasspathDependencyResolver(mock(JavaProjectFinder.class)));
		DependencyDescriptor dependency = DependencyDescriptor.jar("fixture", jar.getAbsolutePath());

		StructureElements scanned = source.elementsOf(dependency, projectA, null, catalogWithMarker());
		source.retainOnly(projectA, List.of(dependency));
		source.retainOnly(projectB, List.of(dependency));

		source.retainOnly(projectA, List.of());
		assertTrue(source.isReady(dependency, projectA), "project b still includes it");
		assertSame(scanned.types().get(0), source.elementsOf(dependency, projectA, null, catalogWithMarker()).types().get(0));

		source.retainOnly(projectB, List.of());
		assertFalse(source.isReady(dependency, projectA), "no project includes it any more");
		assertNotSame(scanned.types().get(0), source.elementsOf(dependency, projectA, null, catalogWithMarker()).types().get(0));
	}

	/**
	 * Every type and method element of the scan has the binding key its node opens it by.
	 */
	@Test
	void typesAndMethodsAreScannedWithTheirBindingKeys() throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "fixture", Map.of(
				"com.example.Marked", "package com.example; public class Marked { @Marker public void handle(String event, int[] ids) {} }",
				"com.example.Marker", "package com.example; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface Marker {}"
		), Map.of());
		JarDependencySource source = new JarDependencySource(new ClasspathDependencyResolver(mock(JavaProjectFinder.class)));

		JarDependencySource.ScanResult scan = source.scan(project(jar), jar);

		StereotypeClassElement type = scan.types().get(0);
		assertEquals("Lcom/example/Marked;", scan.bindingKeys().get(type));
		assertEquals("Lcom/example/Marked;.handle(Ljava/lang/String;[I)V", scan.bindingKeys().get(type.getMethods().get(0)));
	}

	/**
	 * Scanning JARs indexes the including project's whole classpath, which can take a while - so the
	 * JARs selected for a project are scanned together, indexing that classpath once, and reported
	 * as one progress for the project that names each JAR as it gets to it, the way indexing a
	 * project's sources is reported. What is scanned already has nothing to report.
	 */
	@Test
	void theJarsOfAProjectAreScannedTogetherAsOneProgressNamingEachJar() throws Exception {
		File first = markedJar("first");
		File second = markedJar("second");
		STS4LanguageClient client = mock(STS4LanguageClient.class);
		when(client.createProgress(any())).thenReturn(CompletableFuture.completedFuture(null));
		JarDependencySource source = new JarDependencySource(new ClasspathDependencyResolver(mock(JavaProjectFinder.class)),
				ProgressService.create(() -> client));
		List<DependencyDescriptor> dependencies = List.of(DependencyDescriptor.jar("first", first.getAbsolutePath()),
				DependencyDescriptor.jar("second", second.getAbsolutePath()));
		IJavaProject including = project(first, second);

		source.prepare(including, dependencies);

		// the begin is sent once the client has created the progress - asynchronously, so not
		// necessarily before the reports
		verify(client, timeout(5000)).notifyProgress(argThat(params -> params.getValue().getLeft() instanceof WorkDoneProgressBegin begin
				&& begin.getTitle().contains("including")));
		for (File jar : List.of(first, second)) {
			verify(client, timeout(5000).atLeastOnce()).notifyProgress(argThat(params -> params.getValue().getLeft() instanceof WorkDoneProgressReport report
					&& jar.getName().equals(report.getMessage())));
		}
		verify(client, timeout(5000)).notifyProgress(argThat(params -> params.getValue().getLeft() instanceof WorkDoneProgressEnd));
		verify(client, times(1)).createProgress(any());

		dependencies.forEach(dependency -> assertTrue(source.isReady(dependency, including)));

		source.prepare(including, dependencies);
		source.elementsOf(dependencies.get(0), including, null, catalogWithMarker());
		verify(client, times(1)).createProgress(any());
	}

	@Test
	void inTheBackgroundTheJarsAreScannedAndTheProjectIsToldOnceTheyAreReady() throws Exception {
		File jar = markedJar("fixture");
		JarDependencySource source = new JarDependencySource(new ClasspathDependencyResolver(mock(JavaProjectFinder.class)));
		DependencyDescriptor dependency = DependencyDescriptor.jar("fixture", jar.getAbsolutePath());
		IJavaProject including = project(jar);
		assertFalse(source.isReady(dependency, including));

		CompletableFuture<Void> ready = new CompletableFuture<>();
		source.prepareInBackground(including, List.of(dependency), () -> ready.complete(null));

		ready.get(30, TimeUnit.SECONDS);
		assertTrue(source.isReady(dependency, including));
	}

	/**
	 * The classpath's JARs are indexed in parallel and then put together in classpath order: a
	 * class that two JARs have is the first one's, as for the JVM. Here the selected JAR's class
	 * gets the stereotype of its supertype from whichever JAR comes first.
	 */
	@Test
	void aClassThatTwoJarsHaveIsTheFirstOnesOnTheClasspath() throws Exception {
		String marker = "package com.example; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface Marker {}";

		File markedBase = jarOf("marked-base", Map.of("com.example.Marker", marker,
				"com.example.Base", "package com.example; @Marker public class Base {}"), List.of("com.example.Marker", "com.example.Base"));
		File plainBase = jarOf("plain-base", Map.of("com.example.Base", "package com.example; public class Base {}"), List.of("com.example.Base"));
		File sub = jarOf("sub", Map.of("com.example.Marker", marker,
				"com.example.Base", "package com.example; public class Base {}",
				"com.example.Sub", "package com.example; public class Sub extends Base {}"), List.of("com.example.Sub"));
		DependencyDescriptor dependency = DependencyDescriptor.jar("sub", sub.getAbsolutePath());

		JarDependencySource source = new JarDependencySource(new ClasspathDependencyResolver(mock(JavaProjectFinder.class)));
		JarStructureElements markedFirst = (JarStructureElements) source.elementsOf(dependency, project(markedBase, plainBase, sub), null, catalogWithMarker());
		assertEquals(1, markedFirst.typesWithOwnStereotypeCount());

		JarDependencySource other = new JarDependencySource(new ClasspathDependencyResolver(mock(JavaProjectFinder.class)));
		JarStructureElements plainFirst = (JarStructureElements) other.elementsOf(dependency, project(plainBase, markedBase, sub), null, catalogWithMarker());
		assertEquals(0, plainFirst.typesWithOwnStereotypeCount());
	}

	private File jarOf(String name, Map<String, String> sources, List<String> packaged) throws Exception {
		Path directory = Files.createDirectories(tempDir.resolve(name));
		Path classes = JarFixtureBuilder.compileAll(directory, sources);
		return JarFixtureBuilder.packageJar(classes, directory, name, packaged, Map.of());
	}

	private File markedJar(String name) throws Exception {
		return JarFixtureBuilder.buildJar(tempDir, name, Map.of(
				"com.example.Marked", "package com.example; @Marker public class Marked {}",
				"com.example.Marker", "package com.example; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface Marker {}"
		), Map.of());
	}

	private static IJavaProject project(File... jars) throws Exception {
		List<CPE> entries = List.of(jars).stream().map(jar -> CPE.binary(jar.getAbsolutePath())).toList();

		IClasspath classpath = mock(IClasspath.class);
		when(classpath.getClasspathEntries()).thenReturn(entries);

		IJavaProject project = mock(IJavaProject.class);
		when(project.getClasspath()).thenReturn(classpath);
		when(project.getElementName()).thenReturn("including");
		return project;
	}

	private AbstractStereotypeCatalog catalogWithMarker() throws Exception {
		Path file = Files.createTempFile(tempDir, "catalog", ".json");
		Files.writeString(file, """
				{ "stereotypes": { "custom": { "assignments": ["@com.example.Marker"] } } }
				""", StandardCharsets.UTF_8);
		URL url = file.toUri().toURL();

		return new JsonPathStereotypeCatalog(new CatalogSource() {
			@Override
			public Stream<URL> getSources() {
				return Stream.of(url);
			}
		});
	}

}
