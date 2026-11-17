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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.ModuleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.stereotypes.ClassFacts;
import org.springframework.ide.vscode.boot.java.stereotypes.ClassFactsReader;

/**
 * The classpath of a project, read only as far as a scan looks at it. Of every JAR, only the names
 * of its classes are known up front - read from the JAR's directory, without reading any class -
 * and a class is read the first time it is asked for, in one of two ways:
 * <ul>
 * <li>as {@link ClassFacts} ({@link #factsOf}): its names, supertypes and annotations, straight from
 * the class file - all that is needed to work out what a class's elements say, so all that is read
 * of nearly every class;</li>
 * <li>as a Jandex {@code ClassInfo} ({@link #getClassByName}): in full, for the few classes whose
 * methods or members the scan renders.</li>
 * </ul>
 *
 * <p>A scan only ever looks at the classes of the JARs it scans, and the annotations and supertypes
 * those refer to, and theirs in turn: a small part of what the whole classpath has. Reading all of it
 * up front is what most of a scan's time and memory would go into. See
 * {@code docs/structure-view-dependencies.md}.
 *
 * <p>A class that several JARs have is the first one's in classpath order, as for the JVM. Within
 * a JAR, a class also found under {@code META-INF/versions} is the one listed last.
 *
 * <p>Only {@link #getClassByName(DotName)} is supported of the {@link IndexView} - the only lookup
 * the JAR scanners make. Everything else would need the whole classpath indexed, and throws. Safe to
 * use from several threads; to be {@link #close() closed} once the scan is done, which closes the
 * JARs.
 *
 * @author Martin Lippert
 */
final class LazyClasspathIndex implements IndexView, AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(LazyClasspathIndex.class);

	private static final String VERSIONS = "META-INF/versions/";

	/**
	 * One JAR of the classpath: where each of its classes is, by the class's binary name.
	 *
	 * @param jar the open JAR - null if it could not be read
	 * @param ownClassNames the binary names of the classes the JAR itself has - the ones to scan -
	 *        if it was listed as one to scan, else none. Not what {@code entries} has: a class
	 *        found only under {@code META-INF/versions} is no class of the JAR as far as a scan goes.
	 */
	record ClasspathJar(File file, JarFile jar, Map<String, String> entries, List<String> ownClassNames) implements AutoCloseable {

		@Override
		public void close() {
			if (jar != null) {
				try {
					jar.close();
				} catch (IOException e) {
					log.debug("cannot close '{}'", file, e);
				}
			}
		}
	}

	/**
	 * Lists the classes of the JAR, which is kept open to read them from later.
	 *
	 * @param scanned whether the classes of the JAR are to be scanned - see {@link ClasspathJar#ownClassNames}
	 */
	static ClasspathJar list(File file, boolean scanned) throws IOException {
		JarFile jar = new JarFile(file);
		try {
			Map<String, String> entries = new HashMap<>();
			List<String> ownClassNames = new ArrayList<>();
			Enumeration<JarEntry> all = jar.entries();
			while (all.hasMoreElements()) {
				String entry = all.nextElement().getName();
				if (entry.endsWith(".class") && !entry.endsWith("module-info.class")) {
					entries.put(classNameOf(entry), entry);
					if (scanned && !entry.startsWith("META-INF/")) {
						ownClassNames.add(classNameOf(entry));
					}
				}
			}
			return new ClasspathJar(file, jar, entries, ownClassNames);
		} catch (RuntimeException e) {
			jar.close();
			throw e;
		}
	}

	/**
	 * What a JAR that could not be read has: no classes.
	 */
	static ClasspathJar unreadable(File file) {
		return new ClasspathJar(file, null, Map.of(), List.of());
	}

	/**
	 * {@code com/example/Foo$Bar.class}, also under {@code META-INF/versions/<n>/}, is
	 * {@code com.example.Foo$Bar}.
	 */
	private static String classNameOf(String entry) {
		String name = entry;
		if (name.startsWith(VERSIONS)) {
			int slash = name.indexOf('/', VERSIONS.length());
			name = slash < 0 ? name : name.substring(slash + 1);
		}
		return name.substring(0, name.length() - ".class".length()).replace('/', '.');
	}

	private final List<ClasspathJar> jars;
	// by binary name: the JAR the class is to be found in, the first one on the classpath to have it
	private final Map<String, ClasspathJar> jarOfClass = new HashMap<>();
	private final ConcurrentHashMap<String, String> strings = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Optional<ClassFacts>> facts = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<DotName, Optional<ClassInfo>> classInfos = new ConcurrentHashMap<>();
	private final AtomicInteger factsRead = new AtomicInteger();
	private final AtomicInteger classInfosRead = new AtomicInteger();
	private volatile boolean closed;

	/**
	 * @param jars in classpath order - taken over, and closed by {@link #close()}
	 */
	LazyClasspathIndex(List<ClasspathJar> jars) {
		this.jars = new ArrayList<>(jars);
		for (ClasspathJar jar : jars) {
			jar.entries().keySet().forEach(name -> jarOfClass.putIfAbsent(name, jar));
		}
	}

	/**
	 * How many classes have been read as {@link ClassFacts} so far - unreadable ones too.
	 */
	int factsReadCount() {
		return factsRead.get();
	}

	/**
	 * How many classes have been read in full so far - unreadable ones too.
	 */
	int classInfosReadCount() {
		return classInfosRead.get();
	}

	/**
	 * The class's {@link ClassFacts}, null if it is not on the classpath (a JDK class, most
	 * commonly) or cannot be read.
	 */
	ClassFacts factsOf(String binaryName) {
		Optional<ClassFacts> known = facts.get(binaryName);
		if (known == null) {
			// read outside of the map's locks - two threads asking for the same class at once read it
			// twice, rather than every other lookup waiting on the reading
			known = Optional.ofNullable(readFacts(binaryName));
			Optional<ClassFacts> other = facts.putIfAbsent(binaryName, known);
			known = other != null ? other : known;
		}
		return known.orElse(null);
	}

	private ClassFacts readFacts(String binaryName) {
		ClasspathJar jar = locate(binaryName);
		if (jar == null) {
			return null;
		}

		factsRead.incrementAndGet();
		String entry = jar.entries().get(binaryName);
		try (InputStream in = jar.jar().getInputStream(jar.jar().getEntry(entry))) {
			return ClassFactsReader.read(in.readAllBytes(), this::shared);
		} catch (IOException | RuntimeException e) {
			return unreadable(entry, jar, e);
		}
	}

	@Override
	public ClassInfo getClassByName(DotName className) {
		Optional<ClassInfo> known = classInfos.get(className);
		if (known == null) {
			known = Optional.ofNullable(readClassInfo(className));
			Optional<ClassInfo> other = classInfos.putIfAbsent(className, known);
			known = other != null ? other : known;
		}
		return known.orElse(null);
	}

	private ClassInfo readClassInfo(DotName className) {
		ClasspathJar jar = locate(className.toString());
		if (jar == null) {
			return null;
		}

		classInfosRead.incrementAndGet();
		String entry = jar.entries().get(className.toString());
		try (InputStream in = jar.jar().getInputStream(jar.jar().getEntry(entry))) {
			return Index.singleClass(in);
		} catch (IOException | RuntimeException e) {
			return unreadable(entry, jar, e);
		}
	}

	private ClasspathJar locate(String binaryName) {
		if (closed) {
			throw new IllegalStateException("the classpath index is closed - looking up " + binaryName);
		}
		return jarOfClass.get(binaryName); // none: not on the classpath
	}

	/**
	 * One string for every name that is the same - a JAR has thousands of classes with the same few
	 * supertypes and annotations. Not {@code computeIfAbsent}, which locks on every call: this is
	 * asked for every name of every class, from every thread.
	 */
	private String shared(String name) {
		String known = strings.putIfAbsent(name, name);
		return known != null ? known : name;
	}

	private static <T> T unreadable(String entry, ClasspathJar jar, Exception e) {
		// a class file that cannot be read (a class file version too new for the reader, say): what
		// it would have contributed is missing from the scan
		log.warn("skipping unreadable class entry '{}' in '{}': {}", entry, jar.file().getName(), e.toString());
		return null;
	}

	@Override
	public void close() {
		closed = true;
		jars.forEach(ClasspathJar::close);
		facts.clear();
		classInfos.clear();
	}

	// ------------------------------------------------------------------ needs the whole classpath indexed

	private static UnsupportedOperationException unsupported() {
		return new UnsupportedOperationException("the classpath is indexed lazily - only getClassByName is supported");
	}

	@Override
	public Collection<ClassInfo> getKnownClasses() {
		throw unsupported();
	}

	@Override
	public Collection<ClassInfo> getKnownDirectSubclasses(DotName className) {
		throw unsupported();
	}

	@Override
	public Collection<ClassInfo> getAllKnownSubclasses(DotName className) {
		throw unsupported();
	}

	@Override
	public Collection<ClassInfo> getKnownDirectSubinterfaces(DotName interfaceName) {
		throw unsupported();
	}

	@Override
	public Collection<ClassInfo> getAllKnownSubinterfaces(DotName interfaceName) {
		throw unsupported();
	}

	@Override
	public Collection<ClassInfo> getKnownDirectImplementations(DotName interfaceName) {
		throw unsupported();
	}

	@Override
	public Collection<ClassInfo> getAllKnownImplementations(DotName interfaceName) {
		throw unsupported();
	}

	@Override
	public Collection<ClassInfo> getKnownDirectImplementors(DotName interfaceName) {
		throw unsupported();
	}

	@Override
	public Collection<ClassInfo> getAllKnownImplementors(DotName interfaceName) {
		throw unsupported();
	}

	@Override
	public Collection<AnnotationInstance> getAnnotations(DotName annotationName) {
		throw unsupported();
	}

	@Override
	public Collection<AnnotationInstance> getAnnotationsWithRepeatable(DotName annotationName, IndexView index) {
		throw unsupported();
	}

	@Override
	public Collection<AnnotationInstance> getAnnotationsWithRepeatable(DotName annotationName, DotName containerAnnotationName) {
		throw unsupported();
	}

	@Override
	public Collection<ModuleInfo> getKnownModules() {
		throw unsupported();
	}

	@Override
	public ModuleInfo getModuleByName(DotName moduleName) {
		throw unsupported();
	}

	@Override
	public Collection<ClassInfo> getKnownUsers(DotName className) {
		throw unsupported();
	}

	@Override
	public Collection<ClassInfo> getClassesInPackage(DotName packageName) {
		throw unsupported();
	}

	@Override
	public Set<DotName> getSubpackages(DotName packageName) {
		throw unsupported();
	}

}
