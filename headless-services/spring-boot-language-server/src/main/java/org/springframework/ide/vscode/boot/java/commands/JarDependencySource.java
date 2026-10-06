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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.beans.JarBeanIndexer;
import org.springframework.ide.vscode.boot.java.stereotypes.JarBindingKeys;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.JarType;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.commons.java.ClasspathDependencyResolver;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.PercentageProgressTask;
import org.springframework.ide.vscode.commons.languageserver.ProgressService;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;

/**
 * Offers the JARs a project depends on, as found on its classpath by
 * {@link ClasspathDependencyResolver}, and reads the stereotype elements a selected one
 * contributes via {@link JarStereotypeScanner}. See {@code docs/structure-view-dependencies.md}.
 *
 * <p>Only a JAR that is actually selected for some project's tree is ever scanned - never one
 * merely offered in a picker. Scanning is cached by the JAR's own identity (path, size and last
 * modified time), so the same JAR selected for several projects, or reselected later, is scanned
 * once. What is <em>not</em> cached is which of a JAR's classes currently match a stereotype: that
 * is decided fresh every time against whichever catalog is current, in
 * {@link JarStructureElements}, so a change to the project's stereotype definitions is reflected
 * immediately rather than only after the JAR happens to be rescanned.
 *
 * @author Martin Lippert
 */
public class JarDependencySource implements StructureDependencySource {

	private static final Logger log = LoggerFactory.getLogger(JarDependencySource.class);

	private static final String SCAN_JARS_TASK_ID = "scan-structure-dependency-jars-task-";

	private final ClasspathDependencyResolver resolver;
	private final ProgressService progressService;
	// by the JAR's absolute path - one entry per JAR, replaced when the JAR changes, rather than one
	// per version of it that happened to be scanned
	private final ConcurrentHashMap<String, CachedScan> scannedJars = new ConcurrentHashMap<>();

	// the absolute paths of the JARs the latest tree of each project (by name) includes
	private final Map<String, Set<String>> includedJars = new HashMap<>();

	/**
	 * How many JARs are indexed at once - half the cores, at most 8: a scan reads and parses every
	 * class of the classpath, which scales with cores, while the language server has other work to
	 * do too. See {@link #scanAll}.
	 */
	private static final int INDEXING_THREADS = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() / 2));

	private final ExecutorService indexing = Executors.newFixedThreadPool(INDEXING_THREADS, new ThreadFactory() {
		private final AtomicInteger count = new AtomicInteger();

		@Override
		public Thread newThread(Runnable runnable) {
			Thread thread = new Thread(runnable, "structure-dependency-indexing-" + count.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		}
	});

	// see prepareInBackground
	private final ExecutorService backgroundScans = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "structure-dependency-scan");
		thread.setDaemon(true);
		return thread;
	});
	private final Set<String> scheduledProjects = ConcurrentHashMap.newKeySet();

	public JarDependencySource(ClasspathDependencyResolver resolver) {
		this(resolver, ProgressService.NO_PROGRESS);
	}

	/**
	 * @param progressService reports the scan of a JAR to the client while it runs - the part of
	 *        including a JAR the user may have to wait for, since it indexes the including project's
	 *        whole classpath (see {@link #scanAll}); a cached scan reports nothing
	 */
	public JarDependencySource(ClasspathDependencyResolver resolver, ProgressService progressService) {
		this.resolver = resolver;
		this.progressService = progressService;
	}

	@Override
	public List<DependencyDescriptor> discover(IJavaProject project) {
		return resolver.jarDependenciesOf(project).stream()
				.map(jar -> jar.gav() != null
						? DependencyDescriptor.jar(jar.gav().groupId(), jar.gav().artifactId(), jar.gav().version(), jar.path())
						: DependencyDescriptor.jar(jar.name(), jar.path()))
				.toList();
	}

	@Override
	public StructureElements elementsOf(DependencyDescriptor dependency, IJavaProject including, CachedSpringMetamodelIndex cachedIndex,
			AbstractStereotypeCatalog catalog) {

		File jarFile = jarFileOf(dependency);
		if (jarFile == null) {
			return null;
		}

		ScanResult scanned = scanned(including, jarFile);
		JarStructureElements elements = new JarStructureElements(scanned.types(), scanned.beans(), scanned.bindingKeys(), catalog);

		// worth an INFO on every request, not just on failure: this is the only place that shows
		// whether a JAR selection is actually doing something - scanned classes found none matching
		// the current catalog looks identical to "elementsOf was never even called" otherwise
		log.info("structure dependency '{}': {} of {} scanned class(es) in '{}' have a stereotype of their own", dependency.id(),
				elements.typesWithOwnStereotypeCount(), scanned.types().size(), jarFile.getName());

		return elements;
	}

	/**
	 * Drops the cached scan of every JAR that the latest tree of no project includes. A scan can be
	 * large, and one for a JAR nobody includes any more would otherwise stay until the server is
	 * restarted. Including the JAR again scans it again.
	 */
	@Override
	public synchronized void retainOnly(IJavaProject including, List<DependencyDescriptor> dependencies) {
		Set<String> jars = dependencies.stream()
				.filter(dependency -> dependency.kind() == DependencyDescriptor.Kind.JAR)
				.map(dependency -> new File(dependency.location()).getAbsolutePath())
				.collect(Collectors.toSet());
		includedJars.put(including.getElementName(), jars);

		Set<String> included = includedJars.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
		scannedJars.keySet().removeIf(jarPath -> {
			boolean evict = !included.contains(jarPath);
			if (evict) {
				log.info("structure dependency JAR '{}' is not included in any project's tree any more - dropping its scan", jarPath);
			}
			return evict;
		});
	}

	/**
	 * Ready once the JAR is scanned - also by an incomplete scan (see {@link #scanned}), which
	 * scanning again in the background would only repeat, and repeat for every refresh it triggers.
	 */
	@Override
	public boolean isReady(DependencyDescriptor dependency, IJavaProject including) {
		File jarFile = jarFileOf(dependency);
		if (jarFile == null) {
			return true; // nothing to scan - elementsOf answers right away
		}
		CachedScan cached = scannedJars.get(jarFile.getAbsolutePath());
		return cached != null && cached.isFor(jarFile);
	}

	/**
	 * Scans the JARs on a thread of its own, one project after the other - each scan indexes the
	 * project's whole classpath, which is not something to do for several projects at once - with a
	 * single progress for the project. A project whose scan is still to come or running is not
	 * scheduled a second time; JARs selected for it meanwhile are scanned with the refresh that
	 * {@code onReady} triggers.
	 */
	@Override
	public void prepareInBackground(IJavaProject including, List<DependencyDescriptor> dependencies, Runnable onReady) {
		if (!scheduledProjects.add(including.getElementName())) {
			return;
		}

		backgroundScans.execute(() -> {
			try {
				prepare(including, dependencies);
			}
			catch (Exception e) {
				log.error("cannot scan the structure dependencies of project '{}'", including.getElementName(), e);
			}
			finally {
				scheduledProjects.remove(including.getElementName());
				onReady.run();
			}
		});
	}

	/**
	 * Scans all of the given JARs not scanned yet together - indexing the including project's
	 * classpath once for all of them - and caches the results.
	 */
	@Override
	public void prepare(IJavaProject including, List<DependencyDescriptor> dependencies) {
		List<File> toScan = dependencies.stream()
				.filter(dependency -> !isReady(dependency, including))
				.map(JarDependencySource::jarFileOf)
				.filter(Objects::nonNull)
				.distinct()
				.toList();

		if (!toScan.isEmpty()) {
			scanAll(including, toScan).forEach(this::cache);
		}
	}

	private static File jarFileOf(DependencyDescriptor dependency) {
		if (dependency.kind() != DependencyDescriptor.Kind.JAR) {
			return null;
		}

		File jarFile = new File(dependency.location());
		if (!jarFile.isFile()) {
			log.warn("cannot scan structure dependency '{}': '{}' is not a file", dependency.id(), jarFile);
			return null;
		}
		return jarFile;
	}

	/**
	 * The cached scan of the JAR, or a fresh one if there is none, the JAR has changed since, or the
	 * cached one is incomplete. A scan that could not resolve against the including project's
	 * classpath - the classpath could not be read, or the JAR is not on it - may lack
	 * meta-annotations a complete one would resolve: asked for directly, it is tried again.
	 */
	private ScanResult scanned(IJavaProject including, File jarFile) {
		CachedScan cached = scannedJars.get(jarFile.getAbsolutePath());
		if (cached != null && cached.isFor(jarFile) && cached.result().complete()) {
			return cached.result();
		}

		ScanResult fresh = scan(including, jarFile);
		cache(jarFile, fresh);
		return fresh;
	}

	private void cache(File jarFile, ScanResult result) {
		scannedJars.put(jarFile.getAbsolutePath(), new CachedScan(jarFile.lastModified(), jarFile.length(), result));
	}

	ScanResult scan(IJavaProject including, File jarFile) {
		return scanAll(including, List.of(jarFile)).get(jarFile);
	}

	/**
	 * Scans the given JARs' own classes, resolving their annotations and supertypes against a
	 * combined index built over every JAR on {@code including}'s classpath - not just the JARs in
	 * isolation, since an annotation's own meta-annotations are often declared several JARs away
	 * from where the annotation itself is used (Spring's own annotations are the textbook example:
	 * {@code @RestController} is meta-annotated with {@code @Controller}, which lives in a different
	 * JAR). Every JAR {@code including} depends on is guaranteed to be resolvable on its own
	 * classpath, so this is always sufficient. The index is built once, for all of them.
	 *
	 * <p>The classpath's JARs are indexed in parallel, one index each on {@link #indexing}, and then
	 * put together in classpath order: a class that more than one JAR has is the first one's, as for
	 * the JVM. Reading the given JARs' own classes from that index runs in parallel too, one JAR
	 * each.
	 *
	 * <p>Reported as one progress for the project, naming the JAR last done.
	 */
	private Map<File, ScanResult> scanAll(IJavaProject including, List<File> jarFiles) {
		Set<File> wanted = jarFiles.stream().map(File::getAbsoluteFile).collect(Collectors.toSet());

		Collection<CPE> classpathEntries;
		try {
			// IClasspath.getClasspathEntries() declares a checked Exception that every caller in
			// this codebase catches locally rather than propagating - matching that here too
			classpathEntries = including.getClasspath().getClasspathEntries();
		} catch (Exception e) {
			log.error("cannot read the classpath of '{}' to scan structure dependency JARs {}", including.getElementName(), jarFiles, e);
			classpathEntries = null;
		}
		boolean classpathRead = classpathEntries != null;

		// each JAR once, in classpath order
		Set<File> toIndex = new LinkedHashSet<>();
		if (classpathRead) {
			classpathEntries.stream()
					.filter(cpe -> Classpath.isBinary(cpe) && !cpe.isSystem())
					.map(cpe -> IClasspathUtil.binaryLocation(cpe).getAbsoluteFile())
					.filter(File::isFile)
					.forEach(toIndex::add);
		}

		// the JARs not found among including's own classpath entries at all - a stale selection, or a
		// discovery/classpath mismatch: indexed along with it, so something is still shown, even
		// though cross-JAR meta-annotations may not all resolve
		Set<File> isolated = new LinkedHashSet<>();
		for (File jarFile : wanted) {
			if (toIndex.add(jarFile)) {
				log.warn("structure dependency JAR '{}' is not on the classpath of '{}' - scanning it in isolation", jarFile,
						including.getElementName());
				isolated.add(jarFile);
			}
		}

		// one step per JAR to index, and one per JAR to read its own classes from
		PercentageProgressTask progress = progressService.createPercentageProgressTask(SCAN_JARS_TASK_ID + including.getElementName(),
				toIndex.size() + jarFiles.size(), "Spring Tools: Indexing Libraries for the Logical Structure of '"
						+ including.getElementName() + "'");
		long scanStart = System.currentTimeMillis();
		try {
			Map<File, Future<IndexedJar>> indexing = new LinkedHashMap<>();
			for (File file : toIndex) {
				indexing.put(file, this.indexing.submit(() -> {
					IndexedJar indexed = indexedJar(file, including);
					stepDone(progress, file);
					return indexed;
				}));
			}

			List<IndexView> indexes = new ArrayList<>();
			Map<File, Set<DotName>> ownClasses = new LinkedHashMap<>();
			for (Map.Entry<File, Future<IndexedJar>> entry : indexing.entrySet()) {
				IndexedJar indexed = await(entry.getValue(), entry.getKey(), new IndexedJar(new Indexer().complete(), Set.of()));
				indexes.add(indexed.index());
				if (wanted.contains(entry.getKey())) {
					ownClasses.put(entry.getKey(), indexed.ownClasses());
				}
			}

			IndexView index = CompositeIndex.create(indexes);

			Map<File, Future<ScanResult>> reading = new LinkedHashMap<>();
			for (File jarFile : jarFiles) {
				File key = jarFile.getAbsoluteFile();
				boolean complete = classpathRead && !isolated.contains(key);
				reading.put(jarFile, this.indexing.submit(() -> {
					ScanResult scanned = scannedJar(jarFile, ownClasses.getOrDefault(key, Set.of()), index, complete, including);
					stepDone(progress, jarFile);
					return scanned;
				}));
			}

			Map<File, ScanResult> result = new LinkedHashMap<>();
			for (Map.Entry<File, Future<ScanResult>> entry : reading.entrySet()) {
				result.put(entry.getKey(), await(entry.getValue(), entry.getKey(), new ScanResult(List.of(), Map.of(), Map.of(), false)));
			}

			log.info("scanned {} structure dependency JAR(s) for '{}', indexing {} classpath entries on up to {} threads, in {} ms",
					jarFiles.size(), including.getElementName(), toIndex.size(), INDEXING_THREADS, System.currentTimeMillis() - scanStart);
			return result;
		}
		finally {
			progress.done();
		}
	}

	private record IndexedJar(Index index, Set<DotName> ownClasses) {
	}

	/**
	 * One JAR's own index - an {@link Indexer} is not to be shared between threads, a finished
	 * {@link Index} is safe to read from any number of them.
	 */
	private static IndexedJar indexedJar(File file, IJavaProject including) {
		long start = System.currentTimeMillis();
		Indexer indexer = new Indexer();
		Set<DotName> classes = JarStereotypeScanner.indexInto(indexer, file);
		Index index = indexer.complete();
		log.info("indexed '{}' for the logical structure of '{}': {} class file(s) in {} ms", file.getName(),
				including.getElementName(), classes.size(), System.currentTimeMillis() - start);
		return new IndexedJar(index, classes);
	}

	private static ScanResult scannedJar(File jarFile, Set<DotName> ownClasses, IndexView index, boolean complete, IJavaProject including) {
		long start = System.currentTimeMillis();

		Map<Object, String> bindingKeys = new IdentityHashMap<>();
		List<StereotypeClassElement> scanned = JarStereotypeScanner.ownClassesOf(ownClasses, index, bindingKeys);
		Map<StereotypeClassElement, List<Bean>> beans = beansOf(scanned, index, jarFile, bindingKeys);

		log.info("scanned structure dependency JAR '{}' for '{}': {} of its {} class(es) found (annotation types, module-info excluded) in {} ms",
				jarFile.getName(), including.getElementName(), scanned.size(), ownClasses.size(), System.currentTimeMillis() - start);

		return new ScanResult(scanned, beans, bindingKeys, complete);
	}

	/**
	 * The progress is not made to be told from several threads at once.
	 */
	private static void stepDone(PercentageProgressTask progress, File jarFile) {
		synchronized (progress) {
			progress.increment(jarFile.getName());
		}
	}

	/**
	 * The result of one JAR's task - or, when that failed on something unexpected, the given
	 * stand-in, so that one JAR does not cost all the others theirs.
	 */
	private static <T> T await(Future<T> task, File jarFile, T failed) {
		try {
			return task.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted while scanning structure dependency JAR " + jarFile, e);
		} catch (ExecutionException e) {
			log.warn("failed to scan structure dependency JAR '{}'", jarFile, e.getCause());
			return failed;
		}
	}

	/**
	 * The beans each scanned type contributes, with their children - see {@link JarBeanIndexer}.
	 * Computed once here, alongside the rest of the (JAR-identity-cached, catalog-independent) scan:
	 * unlike stereotype matching, whether a class is a component, a repository or a
	 * {@code @ConfigurationProperties} class is a fact of its own bytecode, not of what stereotypes
	 * happen to be defined right now.
	 */
	private static Map<StereotypeClassElement, List<Bean>> beansOf(List<StereotypeClassElement> scanned, IndexView index, File jarFile,
			Map<Object, String> bindingKeys) {
		Map<StereotypeClassElement, List<Bean>> result = new IdentityHashMap<>();

		for (StereotypeClassElement element : scanned) {
			ClassInfo classInfo = index.getClassByName(DotName.createSimple(element.getType()));
			if (classInfo == null) {
				continue;
			}

			JarType type = new JarType(classInfo, element, JarStereotypeScanner.ownAnnotationTypesOf(classInfo, index), index,
					placeholderLocation(jarFile, classInfo), jarFile, bindingKeys);

			try {
				List<Bean> beans = JarBeanIndexer.beansOf(type);
				if (!beans.isEmpty()) {
					result.put(element, beans);
				}
			}
			catch (RuntimeException e) {
				// one class's unexpected bytecode must not cost the whole JAR its members
				log.warn("skipping the members of '{}' in structure dependency JAR '{}'", element.getType(), jarFile, e);
			}
		}

		return result;
	}

	/**
	 * The class entry inside the JAR, with an empty range: index elements need a location, but a
	 * JAR-scanned one is never sent to a client as such - those navigate differently.
	 */
	private static Location placeholderLocation(File jarFile, ClassInfo classInfo) {
		String entry = classInfo.name().toString().replace('.', '/') + ".class";
		return new Location("jar:" + jarFile.toURI() + "!/" + entry, new Range(new Position(0, 0), new Position(0, 0)));
	}

	/**
	 * @param bindingKeys the JDT binding key of every type, method and member element of the scan,
	 *        by the element's identity - see {@link JarBindingKeys}
	 * @param complete whether the scan resolved against the including project's whole classpath
	 */
	record ScanResult(List<StereotypeClassElement> types, Map<StereotypeClassElement, List<Bean>> beans, Map<Object, String> bindingKeys,
			boolean complete) {
	}

	private record CachedScan(long lastModified, long length, ScanResult result) {

		boolean isFor(File jarFile) {
			return lastModified == jarFile.lastModified() && length == jarFile.length();
		}
	}

}
