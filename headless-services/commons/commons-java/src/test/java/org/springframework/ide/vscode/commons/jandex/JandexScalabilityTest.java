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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ide.vscode.commons.java.ClasspathData;
import org.springframework.ide.vscode.commons.java.IType;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.util.BasicFileObserver;

import com.google.common.collect.ImmutableList;

import reactor.util.function.Tuple2;

/**
 * Performance and memory analysis of the Jandex type index for workspaces with many projects
 * and many dependencies.
 * <p>
 * This is not a regular unit test: it is skipped unless {@code -Djandex.perf=true} is set. It
 * indexes real jars (sampled deterministically from a local Maven repository), so the results
 * reflect real-world class counts and jar sizes. The user home is redirected to a temp folder so
 * the persistent {@code ~/.sts-jandex} cache is neither used nor polluted.
 * <p>
 * Tunables (system properties):
 * <ul>
 * <li>{@code jandex.perf.repo} - folder to sample jars from (default {@code ~/.m2/repository})</li>
 * <li>{@code jandex.perf.deps} - number of distinct dependencies in the workspace (default 2000)</li>
 * <li>{@code jandex.perf.projects} - number of projects in the multi-project scenario (default 50)</li>
 * <li>{@code jandex.perf.depsPerProject} - classpath size of each of those projects (default 200)</li>
 * <li>{@code jandex.perf.sharedCore} - how many of a project's deps come from a common core shared by
 * all projects, like spring-core and friends (default 80)</li>
 * <li>{@code jandex.perf.report} - report file to write (default {@code target/jandex-perf-report.txt})</li>
 * </ul>
 * Run, e.g. with: {@code JAVA_TOOL_OPTIONS=-Xmx3g ../mvnw test -pl commons-java -Dtest=JandexScalabilityTest -Djandex.perf=true}
 *
 * @author Martin Lippert
 */
@EnabledIfSystemProperty(named = "jandex.perf", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class JandexScalabilityTest {

	private static final int DEPS = Integer.getInteger("jandex.perf.deps", 2000);
	private static final int PROJECTS = Integer.getInteger("jandex.perf.projects", 50);
	private static final int DEPS_PER_PROJECT = Integer.getInteger("jandex.perf.depsPerProject", 200);
	private static final int SHARED_CORE = Integer.getInteger("jandex.perf.sharedCore", 80);

	/** fat jars (shaded SDKs, executable jars) of 100+ MB would dominate the sample; real classpaths rarely have many of them */
	private static final long MAX_JAR_BYTES = Long.getLong("jandex.perf.maxJarMB", 50L) << 20;

	private static String originalUserHome;
	private static Path fakeHome;
	private static List<File> jars;
	private static final StringBuilder report = new StringBuilder();

	@BeforeAll
	static void setup() throws Exception {
		originalUserHome = System.getProperty("user.home");
		fakeHome = Files.createTempDirectory("jandex-perf-home");
		System.setProperty("user.home", fakeHome.toString());
		System.setProperty("jandex.cache.cleanup", "false");

		Path repo = Path.of(System.getProperty("jandex.perf.repo", originalUserHome + "/.m2/repository"));
		jars = sampleJars(repo, DEPS);
		assertTrue(jars.size() == DEPS, "Only found " + jars.size() + " usable jars in " + repo + ", need " + DEPS);

		long totalBytes = jars.stream().mapToLong(File::length).sum();
		line("=== Jandex scalability analysis ===");
		line("JVM: %s, max heap %d MB, %d CPUs", System.getProperty("java.version"),
				Runtime.getRuntime().maxMemory() >> 20, Runtime.getRuntime().availableProcessors());
		line("Workspace: %d unique dependency jars (%.0f MB on disk, avg %.0f KB, max %.1f MB) sampled from %s",
				jars.size(), totalBytes / 1048576.0, totalBytes / 1024.0 / jars.size(),
				jars.stream().mapToLong(File::length).max().orElse(0) / 1048576.0, repo);
		line("largest jars: %s", jars.stream().sorted((a, b) -> Long.compare(b.length(), a.length())).limit(5)
				.map(f -> f.getName() + " (" + (f.length() >> 20) + " MB)").collect(Collectors.joining(", ")));
		line("Multi-project scenario: %d projects x %d deps (%d from shared core of popular jars)", PROJECTS,
				DEPS_PER_PROJECT, SHARED_CORE);
		line("");
	}

	@AfterAll
	static void teardown() throws IOException {
		System.setProperty("user.home", originalUserHome);
		System.clearProperty("jandex.cache.cleanup");
		String out = report.toString();
		Path reportFile = Path.of(System.getProperty("jandex.perf.report", "target/jandex-perf-report.txt"));
		if (reportFile.getParent() != null) {
			Files.createDirectories(reportFile.getParent());
		}
		Files.writeString(reportFile, out);
		deleteRecursively(fakeHome);
	}

	/**
	 * One project with all dependencies and nothing cached yet: this is what a first start on a fresh machine costs.
	 */
	@Test
	@Order(1)
	void singleProjectColdIndex() throws Exception {
		line("--- 1. single project, %d deps, COLD (no .jdx files yet) ---", DEPS);
		Measurement m = Measurement.start();
		JandexClasspath cp = classpath("big-project", jars);
		assertNull(cp.findType("does.not.Exist")); // a miss has to consult every jar => forces indexing of all of them
		m.stop("cold index of all jars");
		m.print();
		line("on-disk .jdx cache: %.0f MB in %d files", dirSize(indexFolder()) / 1048576.0, fileCount(indexFolder()));
		line("");
		java.lang.ref.Reference.reachabilityFence(cp);
	}

	/**
	 * Same project again, now with the .jdx files on disk: this is the cost of every subsequent start.
	 */
	@Test
	@Order(2)
	void singleProjectWarmIndex() throws Exception {
		line("--- 2. single project, %d deps, WARM (.jdx cache on disk) ---", DEPS);
		int fdsBefore = openFileDescriptors();
		Measurement m = Measurement.start();
		JandexClasspath cp = classpath("big-project", jars);
		assertNull(cp.findType("does.not.Exist"));
		m.stop("load of all cached indexes");
		m.print();
		line("open file descriptors: %d before -> %d after load",
				fdsBefore, openFileDescriptors());

		// query latency on the fully loaded index
		List<IType> sample = cp.fuzzySearchTypes("Exception", true, true).take(200).map(Tuple2::getT1).collectList().block();
		assertNotNull(sample);
		assertTrue(!sample.isEmpty());
		List<String> names = sample.stream().map(IType::getFullyQualifiedName).collect(Collectors.toList());

		long t = System.nanoTime();
		int hits = 0;
		for (int i = 0; i < 200; i++) {
			for (String n : names) {
				if (cp.findType(n) != null) {
					hits++;
				}
			}
		}
		double perHit = (System.nanoTime() - t) / 1e3 / (200.0 * names.size());
		t = System.nanoTime();
		for (int i = 0; i < 2000; i++) {
			cp.findType("does.not.Exist" + i);
		}
		double perMiss = (System.nanoTime() - t) / 1e3 / 2000;
		assertTrue(hits > 0);
		line("findType hit : %.1f us/call   findType miss: %.1f us/call (linear scan over %d jars)", perHit, perMiss, DEPS);

		t = System.nanoTime();
		long matches = cp.fuzzySearchTypes("Logger", true, true).count().block();
		line("fuzzy type search 'Logger': %d ms (%d matches)", (System.nanoTime() - t) / 1_000_000, matches);
		t = System.nanoTime();
		matches = cp.fuzzySearchTypes("Exception", true, true).count().block();
		line("fuzzy type search 'Exception': %d ms (%d matches)", (System.nanoTime() - t) / 1_000_000, matches);
		t = System.nanoTime();
		matches = cp.fuzzySearchPackages("springframework", true, true).count().block();
		line("fuzzy package search 'springframework': %d ms (%d matches)", (System.nanoTime() - t) / 1_000_000, matches);

		IType exception = sample.stream().filter(x -> !x.isInterface()).findFirst().orElse(sample.get(0));
		t = System.nanoTime();
		matches = cp.allSubtypesOf(exception.getFullyQualifiedName(), false, false).count().block();
		line("allSubtypesOf(%s): %d ms (%d subtypes)", exception.getFullyQualifiedName(),
				(System.nanoTime() - t) / 1_000_000, matches);
		line("");
		java.lang.ref.Reference.reachabilityFence(cp);
	}

	/**
	 * Many projects with overlapping dependencies, once with an index per project and jar (the current behaviour) and
	 * once with one shared index per jar.
	 */
	@Test
	@Order(3)
	void manyProjectsWithOverlappingDependencies() throws Exception {
		for (boolean shared : new boolean[] { false, true }) {
			sharing(shared);
			line("--- 3. %d projects x %d deps, warm .jdx cache, %s ---", PROJECTS, DEPS_PER_PROJECT, mode(shared));
			List<List<File>> classpaths = projectClasspaths();
			Set<File> unique = new HashSet<>();
			classpaths.forEach(unique::addAll);
			line("%d classpath entries in total, %d distinct jars (each jar referenced by %.1f projects on average)",
					classpaths.stream().mapToLong(List::size).sum(), unique.size(),
					(double) classpaths.stream().mapToLong(List::size).sum() / unique.size());

			Measurement m = Measurement.start();
			List<JandexClasspath> all = new ArrayList<>();
			for (int i = 0; i < classpaths.size(); i++) {
				JandexClasspath cp = classpath("project-" + i, classpaths.get(i));
				assertNull(cp.findType("does.not.Exist"));
				all.add(cp);
			}
			m.stop("index load of all projects (sequential)");
			m.print();
			line("");
			java.lang.ref.Reference.reachabilityFence(all);
		}
		sharing(true);
	}

	/**
	 * What opening the workspace costs when the projects get indexed in parallel (which is what a project-by-project
	 * background indexer would do), including the transient memory peak.
	 */
	@Test
	@Order(4)
	void manyProjectsIndexedInParallel() throws Exception {
		for (boolean shared : new boolean[] { false, true }) {
			sharing(shared);
			line("--- 4. same %d projects, warm cache, indexed in parallel (%d threads), %s ---", PROJECTS,
					Runtime.getRuntime().availableProcessors(), mode(shared));
			List<List<File>> classpaths = projectClasspaths();
			Measurement m = Measurement.start();
			List<JandexClasspath> all = Collections.synchronizedList(new ArrayList<>());
			java.util.stream.IntStream.range(0, classpaths.size()).parallel().forEach(i -> {
				JandexClasspath cp = classpath("project-" + i, classpaths.get(i));
				cp.findType("does.not.Exist");
				all.add(cp);
			});
			m.stop("index load of all projects (parallel)");
			m.print();
			line("");
			java.lang.ref.Reference.reachabilityFence(all);
		}
		sharing(true);
	}

	/**
	 * Cold index of the whole multi-project workspace, i.e. the very first start where nothing is cached. Uses a
	 * separate empty cache folder per mode. Projects are indexed in parallel, so without sharing several threads
	 * create (and write the .jdx file of) the same jar at the same time.
	 */
	@Test
	@Order(5)
	void manyProjectsColdIndex() throws Exception {
		for (boolean shared : new boolean[] { false, true }) {
			sharing(shared);
			line("--- 5. %d projects x %d deps, COLD (empty .jdx cache), parallel, %s ---", PROJECTS, DEPS_PER_PROJECT,
					mode(shared));
			Path coldHome = Files.createTempDirectory("jandex-perf-cold");
			String previous = System.getProperty("user.home");
			System.setProperty("user.home", coldHome.toString());
			try {
				List<List<File>> classpaths = projectClasspaths();
				Measurement m = Measurement.start();
				List<JandexClasspath> all = Collections.synchronizedList(new ArrayList<>());
				java.util.stream.IntStream.range(0, classpaths.size()).parallel().forEach(i -> {
					JandexClasspath cp = classpath("project-" + i, classpaths.get(i));
					cp.findType("does.not.Exist");
					all.add(cp);
				});
				m.stop("cold index of all projects (parallel)");
				m.print();
				line("on-disk .jdx cache: %.0f MB in %d files", dirSize(indexFolder()) / 1048576.0, fileCount(indexFolder()));
				line("");
				java.lang.ref.Reference.reachabilityFence(all);
			} finally {
				System.setProperty("user.home", previous);
				deleteRecursively(coldHome);
			}
		}
		sharing(true);
	}

	/**
	 * Correctness under concurrency with sharing enabled: many threads, each working on its own project, hammer the
	 * jar indexes that all those projects share (cold, so loading races as well) and have to see exactly the same
	 * results as a single threaded run.
	 */
	@Test
	@Order(6)
	void sharedIndexesAreSafeUnderConcurrentAccess() throws Exception {
		sharing(true);
		line("--- 6. concurrency check of shared indexes (cold cache, %d threads) ---", PROJECTS);
		Path coldHome = Files.createTempDirectory("jandex-perf-concurrent");
		String previous = System.getProperty("user.home");
		System.setProperty("user.home", coldHome.toString());
		try {
			List<List<File>> classpaths = projectClasspaths();
			// expected results come from one project in isolation, computed single threaded without sharing
			sharing(false);
			JandexClasspath reference = classpath("reference", classpaths.get(0));
			long expectedLogger = reference.fuzzySearchTypes("Logger", true, true).count().block();
			long expectedException = reference.fuzzySearchTypes("Exception", true, true).count().block();
			List<String> names = reference.fuzzySearchTypes("Exception", true, true).take(300)
					.map(t -> t.getT1().getFullyQualifiedName()).collectList().block();
			sharing(true);

			List<JandexClasspath> all = Collections.synchronizedList(new ArrayList<>());
			java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(PROJECTS);
			java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
			List<java.util.concurrent.Future<long[]>> futures = new ArrayList<>();
			for (int i = 0; i < classpaths.size(); i++) {
				List<File> entries = classpaths.get(i);
				int p = i;
				futures.add(pool.submit(() -> {
					JandexClasspath cp = classpath("project-" + p, entries);
					all.add(cp);
					go.await(); // all threads start loading at the same moment
					long found = 0;
					for (String n : names) {
						if (cp.findType(n) != null) {
							found++;
						}
					}
					long logger = cp.fuzzySearchTypes("Logger", true, true).count().block();
					long exception = cp.fuzzySearchTypes("Exception", true, true).count().block();
					return new long[] { found, logger, exception };
				}));
			}
			go.countDown();
			long[] first = futures.get(0).get();
			long expectedFound = names.stream().filter(n -> reference.findType(n) != null).count();
			assertTrue(first[0] == expectedFound, "findType results differ from single threaded run");
			assertTrue(first[1] == expectedLogger, "Logger search differs: " + first[1] + " vs " + expectedLogger);
			assertTrue(first[2] == expectedException, "Exception search differs: " + first[2] + " vs " + expectedException);
			for (java.util.concurrent.Future<long[]> f : futures) {
				f.get(); // propagate exceptions
			}
			pool.shutdown();
			line("%d threads finished without errors; project 0 results identical to single threaded reference", PROJECTS);

			// every project has to use the very same index instance for a given jar (the same type name may legitimately
			// come from different jars in different projects, so group by jar)
			int checked = 0;
			java.util.Map<File, Set<Object>> instancesPerJar = new java.util.HashMap<>();
			for (String n : names) {
				for (JandexClasspath cp : all) {
					org.springframework.ide.vscode.commons.java.IJavaModuleData c = cp.findClasspathResourceContainer(n);
					if (c != null) {
						instancesPerJar.computeIfAbsent(c.getContainer(),
								k -> java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>())).add(c);
						checked++;
					}
				}
			}
			instancesPerJar.forEach((jar, instances) -> assertTrue(instances.size() == 1,
					jar + " is held in " + instances.size() + " different index instances"));
			line("%d lookups across all projects, %d jars involved, exactly one shared index instance per jar", checked, instancesPerJar.size());

			// every .jdx written under contention has to be readable and complete
			sharing(false);
			JandexClasspath reread = classpath("reread", classpaths.get(0));
			assertTrue(reread.fuzzySearchTypes("Logger", true, true).count().block() == expectedLogger,
					"index files written concurrently are corrupt");
			line("index files written during the concurrent cold start are intact");
			line("");
		} finally {
			sharing(true);
			System.setProperty("user.home", previous);
			deleteRecursively(coldHome);
		}
	}

	// ------------------------------------------------------------------------------------------------------------

	private static void sharing(boolean enabled) {
		System.setProperty("jandex.sharedJarIndexes", Boolean.toString(enabled));
	}

	private static String mode(boolean shared) {
		return shared ? "SHARED index per jar" : "index per project (current)";
	}

	private static JandexClasspath classpath(String name, List<File> entries) {
		List<CPE> cpes = entries.stream().map(f -> CPE.binary(f.getAbsolutePath())).collect(Collectors.toList());
		return new JandexClasspath(new ClasspathData(name, ImmutableList.copyOf(cpes), null), new BasicFileObserver(), null);
	}

	/**
	 * Projects consist of a core shared by all (think spring-core, spring-boot, logging, ...) plus a project specific
	 * random selection from the whole workspace dependency pool. Deterministic.
	 */
	private static List<List<File>> projectClasspaths() {
		Random random = new Random(42);
		List<File> core = jars.subList(0, Math.min(SHARED_CORE, jars.size()));
		List<List<File>> result = new ArrayList<>();
		for (int p = 0; p < PROJECTS; p++) {
			Set<File> cp = new java.util.LinkedHashSet<>(core);
			while (cp.size() < DEPS_PER_PROJECT) {
				cp.add(jars.get(random.nextInt(jars.size())));
			}
			result.add(new ArrayList<>(cp));
		}
		return result;
	}

	/**
	 * Deterministic sample of one jar per artifact (no sources/javadoc/test jars, no tiny jars), so that the
	 * workspace consists of {@code count} *distinct* libraries like a real dependency tree would.
	 */
	private static List<File> sampleJars(Path repo, int count) throws IOException {
		List<File> candidates = new ArrayList<>();
		Set<Path> seenArtifacts = new HashSet<>();
		List<Path> found = new ArrayList<>();
		Files.walkFileTree(repo, new java.nio.file.SimpleFileVisitor<Path>() {
			@Override
			public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
				return dir.getFileName() != null && dir.getFileName().toString().equals(".locks")
						? java.nio.file.FileVisitResult.SKIP_SUBTREE : java.nio.file.FileVisitResult.CONTINUE;
			}

			@Override
			public java.nio.file.FileVisitResult visitFile(Path p, java.nio.file.attribute.BasicFileAttributes attrs) {
				String n = p.getFileName().toString();
				if (n.endsWith(".jar") && !n.endsWith("-sources.jar") && !n.endsWith("-javadoc.jar")
						&& !n.endsWith("-tests.jar") && !n.endsWith("-test-sources.jar")) {
					found.add(p);
				}
				return java.nio.file.FileVisitResult.CONTINUE;
			}

			@Override
			public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException exc) {
				return java.nio.file.FileVisitResult.CONTINUE; // repository may be modified concurrently
			}
		});
		Collections.sort(found);
		for (Path p : found) {
			Path versionDir = p.getParent();
			Path artifactDir = versionDir == null ? null : versionDir.getParent();
			if (artifactDir != null && p.toFile().length() > 4096 && p.toFile().length() <= MAX_JAR_BYTES && seenArtifacts.add(artifactDir)) {
				candidates.add(p.toFile());
			}
		}
		Collections.shuffle(candidates, new Random(1234));
		return new ArrayList<>(candidates.subList(0, Math.min(count, candidates.size())));
	}

	private static File indexFolder() {
		return JandexIndex.getIndexFolder();
	}

	private static long dirSize(File dir) {
		try (Stream<Path> s = Files.walk(dir.toPath())) {
			return s.filter(Files::isRegularFile).mapToLong(p -> p.toFile().length()).sum();
		} catch (IOException e) {
			return -1;
		}
	}

	private static long fileCount(File dir) {
		try (Stream<Path> s = Files.walk(dir.toPath())) {
			return s.filter(Files::isRegularFile).count();
		} catch (IOException e) {
			return -1;
		}
	}

	private static int openFileDescriptors() {
		String[] fds = new File("/proc/self/fd").list();
		return fds == null ? -1 : fds.length;
	}

	private static void deleteRecursively(Path dir) throws IOException {
		if (Files.exists(dir)) {
			try (Stream<Path> s = Files.walk(dir)) {
				s.sorted(Collections.reverseOrder()).forEach(p -> p.toFile().delete());
			}
		}
	}

	private static void line(String format, Object... args) {
		String text = String.format(format, args) + "\n";
		report.append(text);
		System.out.print(text); // immediately visible, even if the JVM dies with an OutOfMemoryError later on
		System.out.flush();
	}

	private static long usedHeapAfterGc() {
		for (int i = 0; i < 4; i++) {
			System.gc();
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		Runtime rt = Runtime.getRuntime();
		return rt.totalMemory() - rt.freeMemory();
	}

	/**
	 * Wall clock, GC time, transient peak heap (sampled) and retained heap (live after full GC) of a block of work.
	 * The caller must keep the results reachable (reachabilityFence) until after {@link #stop(String)}.
	 */
	private static class Measurement {
		long baseline;
		long start;
		long gcBase;
		String label;
		long millis;
		long gcMillis;
		long peakBytes;
		long retainedBytes;
		volatile boolean sampling = true;
		Thread sampler;
		volatile long peak;

		static Measurement start() {
			Measurement m = new Measurement();
			m.baseline = usedHeapAfterGc();
			m.gcBase = gcTime();
			m.peak = m.baseline;
			m.sampler = new Thread(() -> {
				Runtime rt = Runtime.getRuntime();
				while (m.sampling) {
					long used = rt.totalMemory() - rt.freeMemory();
					if (used > m.peak) {
						m.peak = used;
					}
					try {
						Thread.sleep(5);
					} catch (InterruptedException e) {
						return;
					}
				}
			}, "heap-sampler");
			m.sampler.setDaemon(true);
			m.sampler.start();
			m.start = System.nanoTime();
			return m;
		}

		void stop(String what) {
			millis = (System.nanoTime() - start) / 1_000_000;
			sampling = false;
			gcMillis = gcTime() - gcBase;
			peakBytes = peak - baseline;
			this.label = what;
			retainedBytes = usedHeapAfterGc() - baseline;
		}

		void print() {
			line("%s: %d ms (%.1f s), of which GC %d ms", label, millis, millis / 1000.0, gcMillis);
			line("heap: retained (live after GC) %d MB, sampled transient peak +%d MB over baseline", retainedBytes >> 20,
					peakBytes >> 20);
		}

		private static long gcTime() {
			long t = 0;
			for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
				t += Math.max(0, gc.getCollectionTime());
			}
			return t;
		}
	}

}
