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

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.boot.java.stereotypes.JarFixtureBuilder;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;

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
	void aClassOfAListedJarIsReadOnlyOnceLookedUp() throws Exception {
		File jar = jarOf("lib", Map.of(
				"com.example.Base", "package com.example; @Deprecated public class Base {}",
				"com.example.Other", "package com.example; public class Other {}"));

		try (LazyClasspathIndex index = new LazyClasspathIndex(List.of(LazyClasspathIndex.listed(jar)))) {
			assertEquals(0, index.loadedCount());

			ClassInfo base = index.getClassByName(BASE);
			assertNotNull(base);
			assertNotNull(base.declaredAnnotation(DotName.createSimple(Deprecated.class.getName())));
			assertEquals(1, index.loadedCount());

			assertSame(base, index.getClassByName(BASE));
			assertSame(base, index.getClassByName("com.example.Base"));
			assertEquals(1, index.loadedCount());
		}
	}

	@Test
	void aClassNotOnTheClasspathIsNotFound() throws Exception {
		File jar = jarOf("lib", Map.of("com.example.Base", "package com.example; public class Base {}"));

		try (LazyClasspathIndex index = new LazyClasspathIndex(List.of(LazyClasspathIndex.listed(jar)))) {
			assertNull(index.getClassByName(DotName.createSimple("java.lang.String")));
			assertNull(index.getClassByName(OTHER));
		}
	}

	@Test
	void aClassThatSeveralJarsHaveIsTheFirstOnes() throws Exception {
		File first = jarOf("first", Map.of("com.example.Base", "package com.example; public class Base implements java.io.Serializable {}"));
		File second = jarOf("second", Map.of("com.example.Base", "package com.example; public class Base {}",
				"com.example.Other", "package com.example; public class Other {}"));

		Indexer indexer = new Indexer();
		JarStereotypeScanner.indexInto(indexer, second);
		Index indexed = indexer.complete();

		// listed first, indexed second
		try (LazyClasspathIndex index = new LazyClasspathIndex(List.of(LazyClasspathIndex.listed(first), LazyClasspathIndex.indexed(indexed)))) {
			assertEquals(1, index.getClassByName(BASE).interfaceNames().size());
			assertNotNull(index.getClassByName(OTHER));
		}

		// indexed first, listed second
		try (LazyClasspathIndex index = new LazyClasspathIndex(List.of(LazyClasspathIndex.indexed(indexed), LazyClasspathIndex.listed(first)))) {
			assertTrue(index.getClassByName(BASE).interfaceNames().isEmpty());
			assertEquals(0, index.loadedCount()); // from the indexed JAR, nothing to read
		}
	}

	@Test
	void anUnreadableClassIsNotFound() throws Exception {
		File jar = jarOf("lib", Map.of("com.example.Base", "package com.example; public class Base {}"));
		Path broken = tempDir.resolve("broken.jar");
		try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(broken))) {
			out.putNextEntry(new java.util.jar.JarEntry("com/example/Other.class"));
			out.write(new byte[] { 1, 2, 3 });
			out.closeEntry();
		}

		try (LazyClasspathIndex index = new LazyClasspathIndex(List.of(LazyClasspathIndex.listed(broken.toFile()), LazyClasspathIndex.listed(jar)))) {
			assertNull(index.getClassByName(OTHER));
			assertNotNull(index.getClassByName(BASE));
		}
	}

	@Test
	void onlyLookupsByNameAreSupportedAndNotOnceClosed() throws Exception {
		File jar = jarOf("lib", Map.of("com.example.Base", "package com.example; public class Base {}"));

		LazyClasspathIndex index = new LazyClasspathIndex(List.of(LazyClasspathIndex.listed(jar)));
		assertThrows(UnsupportedOperationException.class, () -> index.getKnownClasses());
		assertThrows(UnsupportedOperationException.class, () -> index.getAllKnownSubclasses(BASE));

		index.close();
		assertThrows(IllegalStateException.class, () -> index.getClassByName(BASE));
	}

	private File jarOf(String name, Map<String, String> sources) throws Exception {
		Path directory = Files.createDirectories(tempDir.resolve(name));
		Path classes = JarFixtureBuilder.compileAll(directory, sources);
		return JarFixtureBuilder.packageJar(classes, directory, name, sources.keySet(), Map.of());
	}

}
