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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
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
