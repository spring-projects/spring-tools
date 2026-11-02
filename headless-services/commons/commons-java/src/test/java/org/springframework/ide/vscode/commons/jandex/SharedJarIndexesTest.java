/*******************************************************************************
 * Copyright (c) 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.jandex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.jboss.jandex.IndexReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.commons.java.ClasspathData;
import org.springframework.ide.vscode.commons.java.IJavaModuleData;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.util.BasicFileObserver;

import com.google.common.collect.ImmutableList;

/**
 * Tests for sharing Jandex jar indexes between projects and for writing index files safely.
 * Jars are compiled on the fly, so the tests don't depend on the content of a local Maven repository.
 *
 * @author Martin Lippert
 */
public class SharedJarIndexesTest {

	@TempDir
	Path tmp;

	private String originalUserHome;
	private Path home;

	@BeforeEach
	void setup() throws IOException {
		originalUserHome = System.getProperty("user.home");
		home = Files.createDirectories(tmp.resolve("home"));
		System.setProperty("user.home", home.toString()); // index files go to <user.home>/.sts-jandex
		System.setProperty("jandex.cache.cleanup", "false"); // no background clean-up racing with @TempDir deletion
	}

	@AfterEach
	void teardown() {
		System.setProperty("user.home", originalUserHome);
		System.clearProperty("jandex.sharedJarIndexes");
		System.clearProperty("jandex.cache.cleanup");
	}

	@Test
	void sameJarIsSharedBetweenProjects() throws Exception {
		File jar = jar("lib.jar", Map.of("a.A", "package a; public class A {}"));
		JandexClasspath p1 = classpath(jar);
		JandexClasspath p2 = classpath(jar);

		IJavaModuleData m1 = p1.findClasspathResourceContainer("a.A");
		assertNotNull(m1);
		assertSame(m1, p2.findClasspathResourceContainer("a.A"));
	}

	@Test
	void sharingCanBeSwitchedOff() throws Exception {
		System.setProperty("jandex.sharedJarIndexes", "false");
		File jar = jar("lib.jar", Map.of("a.A", "package a; public class A {}"));

		IJavaModuleData m1 = classpath(jar).findClasspathResourceContainer("a.A");
		IJavaModuleData m2 = classpath(jar).findClasspathResourceContainer("a.A");
		assertNotNull(m1);
		assertNotNull(m2);
		assertNotSame(m1, m2);
	}

	@Test
	void rebuiltJarGetsAFreshIndex() throws Exception {
		File jar = jar("lib.jar", Map.of("a.A", "package a; public class A {}"));
		JandexClasspath before = classpath(jar);
		assertNotNull(before.findType("a.A"));

		jar("lib.jar", Map.of("a.A", "package a; public class A {}", "a.B", "package a; public class B {}"));
		assertTrue(jar.setLastModified(jar.lastModified() + 10_000)); // file systems with coarse timestamps

		JandexClasspath after = classpath(jar);
		assertNotNull(after.findType("a.B"));
		assertNotSame(before.findClasspathResourceContainer("a.A"), after.findClasspathResourceContainer("a.A"));
		assertNull(before.findType("a.B")); // existing projects keep their consistent (old) view until they get a new classpath
	}

	@Test
	void indexIsReleasedWhenNoProjectUsesItAnymore() throws Exception {
		File jar = jar("lib.jar", Map.of("a.A", "package a; public class A {}"));
		JandexClasspath project = classpath(jar);
		WeakReference<IJavaModuleData> ref = new WeakReference<>(project.findClasspathResourceContainer("a.A"));
		assertNotNull(ref.get());

		project = null;
		for (int i = 0; i < 50 && ref.get() != null; i++) {
			System.gc();
			Thread.sleep(20);
		}
		assertNull(ref.get(), "shared cache must not keep indexes alive on its own");

		// and a new project simply indexes it again
		assertNotNull(classpath(jar).findType("a.A"));
	}

	@Test
	void concurrentColdStartIndexesEachJarOnceAndWritesIntactFiles() throws Exception {
		List<File> jars = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			Map<String, String> sources = new LinkedHashMap<>();
			for (int c = 0; c < 20; c++) {
				sources.put("p" + i + ".C" + c, "package p" + i + "; public class C" + c + " {}");
			}
			jars.add(jar("lib" + i + ".jar", sources));
		}

		int threads = 32;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		List<JandexClasspath> projects = Collections.synchronizedList(new ArrayList<>());
		try {
			CountDownLatch go = new CountDownLatch(1);
			List<Future<?>> futures = new ArrayList<>();
			for (int t = 0; t < threads; t++) {
				futures.add(pool.submit(() -> {
					JandexClasspath cp = classpath(jars.toArray(new File[0]));
					projects.add(cp);
					go.await();
					for (int i = 0; i < 5; i++) {
						assertNotNull(cp.findType("p" + i + ".C7"));
					}
					return null;
				}));
			}
			go.countDown();
			for (Future<?> f : futures) {
				f.get();
			}
		} finally {
			pool.shutdownNow();
		}

		for (int i = 0; i < 5; i++) {
			Set<IJavaModuleData> modules = Collections.newSetFromMap(new IdentityHashMap<>());
			for (JandexClasspath cp : projects) {
				modules.add(cp.findClasspathResourceContainer("p" + i + ".C0"));
			}
			assertEquals(1, modules.size(), "every project has to use the same index for lib" + i);
		}

		List<Path> files = indexFiles();
		assertEquals(5, files.stream().filter(f -> f.toString().endsWith(".jdx")).count(), files.toString());
		assertTrue(files.stream().noneMatch(f -> f.toString().endsWith(".tmp")), "temp files left over: " + files);
		for (Path f : files.stream().filter(f -> f.toString().endsWith(".jdx")).collect(Collectors.toList())) {
			try (InputStream in = Files.newInputStream(f)) {
				assertEquals(20, new IndexReader(in).read().getKnownClasses().size());
			}
		}
	}

	@Test
	void brokenJarLeavesNoFilesBehindAndDoesNotAffectOtherJars() throws Exception {
		File good = jar("good.jar", Map.of("a.A", "package a; public class A {}"));
		File broken = tmp.resolve("broken.jar").toFile();
		Files.writeString(broken.toPath(), "this is not a zip file");

		JandexClasspath cp = classpath(broken, good);
		assertNotNull(cp.findType("a.A"));
		assertNull(cp.findType("does.not.Exist"));

		List<Path> files = indexFiles();
		assertTrue(files.stream().noneMatch(f -> f.getFileName().toString().startsWith("broken.jar")),
				"no index file expected for the broken jar: " + files);
	}

	@Test
	void failedIndexWriteKeepsExistingIndexFileAndCleansUp() throws Exception {
		File target = home.resolve("some.jdx").toFile();
		Files.writeString(target.toPath(), "existing");

		assertThrows(IOException.class, () -> IndexRoutines.writeAtomically(target, tmpFile -> {
			Files.writeString(tmpFile.toPath(), "half written");
			throw new IOException("disk full");
		}));

		assertEquals("existing", Files.readString(target.toPath()));
		try (Stream<Path> s = Files.list(home)) {
			assertEquals(List.of(target.toPath()), s.filter(Files::isRegularFile).collect(Collectors.toList()));
		}
	}

	@Test
	void packageSearchCopesWithDefaultPackageClasses() throws Exception {
		File jar = jar("lib.jar", Map.of(
				"Dflt", "public class Dflt {}",
				"pkg.one.X", "package pkg.one; public class X {}"));
		JandexClasspath cp = classpath(jar);

		List<String> packages = cp.fuzzySearchPackages("pkg", true, true).map(t -> t.getT1()).collectList().block();
		assertEquals(List.of("pkg.one"), packages);
		assertFalse(cp.fuzzySearchPackages("", true, true).map(t -> t.getT1()).collectList().block().contains(""));
		assertNotNull(cp.findType("Dflt"));
	}

	@Test
	void indexFilesAreClosedAfterReading() throws Exception {
		File fdDir = new File("/proc/self/fd");
		assumeTrue(fdDir.isDirectory(), "needs /proc to count open files");

		List<File> jars = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			jars.add(jar("lib" + i + ".jar", Map.of("p" + i + ".A", "package p" + i + "; public class A {}")));
		}
		File[] entries = jars.toArray(new File[0]);
		assertNull(classpath(entries).findType("does.not.Exist")); // creates all index files

		System.setProperty("jandex.sharedJarIndexes", "false"); // force reading every index file again
		int before = fdDir.list().length;
		JandexClasspath cp = classpath(entries);
		assertNull(cp.findType("does.not.Exist"));
		int after = fdDir.list().length;
		assertTrue(after - before < 10, "open files grew from " + before + " to " + after);
		assertNotNull(cp.findType("p39.A"));
	}

	@Test
	void loadingAnIndexMarksItAsUsed() throws Exception {
		File jar = jar("lib.jar", Map.of("a.A", "package a; public class A {}"));
		assertNotNull(classpath(jar).findType("a.A"));
		Path indexFile = indexFiles().get(0);
		long longAgo = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(90);
		assertTrue(indexFile.toFile().setLastModified(longAgo));

		System.setProperty("jandex.sharedJarIndexes", "false"); // force reading the index file again
		assertNotNull(classpath(jar).findType("a.A"));

		assertTrue(indexFile.toFile().lastModified() > longAgo + java.util.concurrent.TimeUnit.DAYS.toMillis(89),
				"index in use must not look unused to the cache clean-up");
	}

	// -------------------------------------------------------------------------------------------------

	private JandexClasspath classpath(File... jars) {
		List<CPE> cpes = Stream.of(jars).map(f -> CPE.binary(f.getAbsolutePath())).collect(Collectors.toList());
		return new JandexClasspath(new ClasspathData("test", ImmutableList.copyOf(cpes), null), new BasicFileObserver(), null);
	}

	private List<Path> indexFiles() throws IOException {
		try (Stream<Path> s = Files.walk(home.resolve(".sts-jandex"))) {
			return s.filter(Files::isRegularFile).collect(Collectors.toList());
		}
	}

	/**
	 * Compiles the given sources (fully qualified name -> source) and writes them into a jar with the given name.
	 */
	private File jar(String name, Map<String, String> sources) throws IOException {
		Path work = Files.createTempDirectory(tmp, "build");
		Path src = Files.createDirectories(work.resolve("src"));
		Path classes = Files.createDirectories(work.resolve("classes"));
		List<String> args = new ArrayList<>(List.of("--release", "17", "-d", classes.toString()));
		for (Map.Entry<String, String> e : sources.entrySet()) {
			Path file = src.resolve(e.getKey().replace('.', '/') + ".java");
			Files.createDirectories(file.getParent());
			Files.writeString(file, e.getValue(), StandardCharsets.UTF_8);
			args.add(file.toString());
		}
		JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
		assertNotNull(javac, "tests need a JDK");
		assertEquals(0, javac.run(null, null, null, args.toArray(new String[0])), "compilation failed");

		File jar = tmp.resolve(name).toFile();
		try (OutputStream out = Files.newOutputStream(jar.toPath());
				JarOutputStream jarOut = new JarOutputStream(out);
				Stream<Path> files = Files.walk(classes)) {
			for (Path f : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
				jarOut.putNextEntry(new JarEntry(classes.relativize(f).toString().replace(File.separatorChar, '/')));
				try (FileInputStream in = new FileInputStream(f.toFile())) {
					in.transferTo(jarOut);
				}
				jarOut.closeEntry();
			}
		}
		return jar;
	}

}
