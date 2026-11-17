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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.boot.java.stereotypes.ClassFacts;
import org.springframework.ide.vscode.boot.java.stereotypes.JarFixtureBuilder;

/**
 * {@link LazyClasspathIndex} on its own - its use in a scan is covered by {@link JarDependencySourceTest}.
 *
 * @author Martin Lippert
 */
class LazyClasspathIndexTest {

	private static final DotName BASE = DotName.createSimple("com.example.Base");
	private static final DotName OTHER = DotName.createSimple("com.example.Other");

	@TempDir
	Path tempDir;

	@Test
	void aClassIsReadOnlyOnceLookedUp() throws Exception {
		File jar = jarOf("lib", Map.of(
				"com.example.Base", "package com.example; @Deprecated public class Base {}",
				"com.example.Other", "package com.example; public class Other {}"));

		try (LazyClasspathIndex index = new LazyClasspathIndex(List.of(LazyClasspathIndex.list(jar, false)))) {
			assertEquals(0, index.classInfosReadCount());
			assertEquals(0, index.factsReadCount());

			ClassInfo base = index.getClassByName(BASE);
			assertNotNull(base);
			assertNotNull(base.declaredAnnotation(DotName.createSimple(Deprecated.class.getName())));
			assertEquals(1, index.classInfosReadCount());

			assertSame(base, index.getClassByName(BASE));
			assertEquals(1, index.classInfosReadCount());
			assertEquals(0, index.factsReadCount()); // the two are read independently of each other
		}
	}

	@Test
	void theFactsOfAClassAreReadOnlyOnceLookedUp() throws Exception {
		File jar = jarOf("lib", Map.of(
				"com.example.Base", "package com.example; @Deprecated public class Base implements Runnable { public void run() {} }",
				"com.example.Other", "package com.example; public class Other {}"));

		try (LazyClasspathIndex index = new LazyClasspathIndex(List.of(LazyClasspathIndex.list(jar, false)))) {
			ClassFacts base = index.factsOf("com.example.Base");
			assertEquals("com.example.Base", base.name());
			assertEquals("java.lang.Object", base.superName());
			assertEquals(List.of("java.lang.Runnable"), List.of(base.interfaces()));
			assertEquals(List.of("java.lang.Deprecated"), List.of(base.annotations()));
			assertEquals(1, index.factsReadCount());

			assertSame(base, index.factsOf("com.example.Base"));
			assertEquals(1, index.factsReadCount());
			assertEquals(0, index.classInfosReadCount());
		}
	}

	@Test
	void aClassNotOnTheClasspathIsNotFound() throws Exception {
		File jar = jarOf("lib", Map.of("com.example.Base", "package com.example; public class Base {}"));

		try (LazyClasspathIndex index = new LazyClasspathIndex(List.of(LazyClasspathIndex.list(jar, false)))) {
			assertNull(index.getClassByName(DotName.createSimple("java.lang.String")));
			assertNull(index.getClassByName(OTHER));
			assertNull(index.factsOf("java.lang.String"));
			assertNull(index.factsOf("com.example.Other"));
			assertEquals(0, index.classInfosReadCount());
			assertEquals(0, index.factsReadCount());
		}
	}

	@Test
	void aClassThatSeveralJarsHaveIsTheFirstOnes() throws Exception {
		File first = jarOf("first", Map.of("com.example.Base", "package com.example; public class Base implements java.io.Serializable {}"));
		File second = jarOf("second", Map.of("com.example.Base", "package com.example; public class Base {}",
				"com.example.Other", "package com.example; public class Other {}"));

		try (LazyClasspathIndex index = new LazyClasspathIndex(List.of(LazyClasspathIndex.list(first, false), LazyClasspathIndex.list(second, false)))) {
			assertEquals(1, index.getClassByName(BASE).interfaceNames().size());
			assertEquals(1, index.factsOf("com.example.Base").interfaces().length);
			assertNotNull(index.getClassByName(OTHER));
			assertNotNull(index.factsOf("com.example.Other"));
		}

		try (LazyClasspathIndex index = new LazyClasspathIndex(List.of(LazyClasspathIndex.list(second, false), LazyClasspathIndex.list(first, false)))) {
			assertTrue(index.getClassByName(BASE).interfaceNames().isEmpty());
			assertEquals(0, index.factsOf("com.example.Base").interfaces().length);
		}
	}

	/**
	 * What a scanned JAR has of its own is what its entries say - not a class that is only there
	 * under {@code META-INF/versions}, which the JAR does not have as far as a scan goes.
	 */
	@Test
	void aScannedJarListsItsOwnClasses() throws Exception {
		Path directory = Files.createDirectories(tempDir.resolve("lib"));
		Path classes = JarFixtureBuilder.compileAll(directory, Map.of(
				"com.example.Base", "package com.example; public class Base { class Inner {} }",
				"com.example.Other", "package com.example; public class Other {}"));
		File jar = tempDir.resolve("versions.jar").toFile();
		try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar.toPath()))) {
			for (String entry : List.of("com/example/Base.class", "com/example/Base$Inner.class", "com/example/Other.class",
					"META-INF/versions/9/com/example/Other.class", "META-INF/versions/9/com/example/OnlyThere.class")) {
				out.putNextEntry(new JarEntry(entry));
				out.write(Files.readAllBytes(classes.resolve(entry.substring(entry.indexOf("com/example/")).replace("OnlyThere", "Other"))));
				out.closeEntry();
			}
		}

		LazyClasspathIndex.ClasspathJar scanned = LazyClasspathIndex.list(jar, true);
		assertEquals(List.of("com.example.Base", "com.example.Base$Inner", "com.example.Other"), scanned.ownClassNames().stream().sorted().toList());
		assertTrue(scanned.entries().containsKey("com.example.OnlyThere")); // to be found, not to be scanned
		scanned.close();

		LazyClasspathIndex.ClasspathJar notScanned = LazyClasspathIndex.list(jar, false);
		assertTrue(notScanned.ownClassNames().isEmpty());
		notScanned.close();
	}

	@Test
	void anUnreadableClassIsNotFound() throws Exception {
		File jar = jarOf("lib", Map.of("com.example.Base", "package com.example; public class Base {}"));
		Path broken = tempDir.resolve("broken.jar");
		try (var out = new JarOutputStream(Files.newOutputStream(broken))) {
			out.putNextEntry(new JarEntry("com/example/Other.class"));
			out.write(new byte[] { 1, 2, 3 });
			out.closeEntry();
		}

		try (LazyClasspathIndex index = new LazyClasspathIndex(List.of(LazyClasspathIndex.list(broken.toFile(), false), LazyClasspathIndex.list(jar, false)))) {
			assertNull(index.getClassByName(OTHER));
			assertNull(index.factsOf("com.example.Other"));
			assertNotNull(index.getClassByName(BASE));
			assertNotNull(index.factsOf("com.example.Base"));
		}
	}

	@Test
	void aJarThatCannotBeReadHasNoClasses() throws Exception {
		try (LazyClasspathIndex index = new LazyClasspathIndex(List.of(LazyClasspathIndex.unreadable(new File("missing.jar"))))) {
			assertNull(index.getClassByName(BASE));
			assertNull(index.factsOf("com.example.Base"));
		}
	}

	@Test
	void onlyLookupsByNameAreSupportedAndNotOnceClosed() throws Exception {
		File jar = jarOf("lib", Map.of("com.example.Base", "package com.example; public class Base {}"));

		LazyClasspathIndex index = new LazyClasspathIndex(List.of(LazyClasspathIndex.list(jar, false)));
		assertThrows(UnsupportedOperationException.class, () -> index.getKnownClasses());
		assertThrows(UnsupportedOperationException.class, () -> index.getAllKnownSubclasses(BASE));

		index.close();
		assertThrows(IllegalStateException.class, () -> index.getClassByName(BASE));
		assertThrows(IllegalStateException.class, () -> index.factsOf("com.example.Base"));
	}

	private File jarOf(String name, Map<String, String> sources) throws Exception {
		Path directory = Files.createDirectories(tempDir.resolve(name));
		Path classes = JarFixtureBuilder.compileAll(directory, sources);
		return JarFixtureBuilder.packageJar(classes, directory, name, sources.keySet(), Map.of());
	}

}
