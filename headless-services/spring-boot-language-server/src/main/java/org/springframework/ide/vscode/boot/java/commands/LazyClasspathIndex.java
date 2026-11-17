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
import org.jboss.jandex.Indexer;
import org.jboss.jandex.ModuleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The classpath of a project, as an {@link IndexView} that reads a class only once it is looked up
 * by name. The JARs whose own classes are scanned come fully indexed; of every other JAR only the
 * names of its classes are known up front - read from the JAR's directory, without reading any
 * class - and a class is indexed on its own ({@link Index#singleClass}) the first time it is asked
 * for.
 *
 * <p>A scan only ever looks up the annotations and supertypes the scanned classes refer to, and
 * theirs in turn: a few hundred classes out of the tens of thousands a classpath has. Indexing all
 * of them up front, as a combined index would, is what most of a scan's time and memory would go
 * into. See {@code docs/structure-view-dependencies.md}.
 *
 * <p>A class that several JARs have is the first one's in classpath order, as for the JVM. Within
 * a JAR, a class also found under {@code META-INF/versions} is the one listed last, as an
 * {@code Indexer} fed the whole JAR keeps it.
 *
 * <p>Only {@link #getClassByName(DotName)} is supported - the only lookup the JAR scanners make.
 * Everything else would need the whole classpath indexed, and throws. Safe to use from several
 * threads; to be {@link #close() closed} once the scan is done, which closes the JARs.
 *
 * @author Martin Lippert
 */
final class LazyClasspathIndex implements IndexView, AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(LazyClasspathIndex.class);

	private static final String VERSIONS = "META-INF/versions/";

	/**
	 * What one classpath entry contributes - see {@link #indexed} and {@link #listed}.
	 */
	sealed interface Part extends AutoCloseable permits Indexed, Listed {

		@Override
		default void close() {
		}
	}

	/**
	 * A JAR already indexed as a whole.
	 */
	record Indexed(Index index) implements Part {
	}

	/**
	 * A JAR of which only the entry of each class is known, by the class's binary name.
	 */
	record Listed(File file, JarFile jar, Map<String, String> entries) implements Part {

		@Override
		public void close() {
			try {
				jar.close();
			} catch (IOException e) {
				log.debug("cannot close '{}'", file, e);
			}
		}
	}

	static Part indexed(Index index) {
		return new Indexed(index);
	}

	/**
	 * Lists the classes of the JAR, which is kept open to read them from later.
	 */
	static Part listed(File file) throws IOException {
		JarFile jar = new JarFile(file);
		try {
			Map<String, String> entries = new HashMap<>();
			Enumeration<JarEntry> all = jar.entries();
			while (all.hasMoreElements()) {
				String entry = all.nextElement().getName();
				if (entry.endsWith(".class") && !entry.endsWith("module-info.class")) {
					entries.put(classNameOf(entry), entry);
				}
			}
			return new Listed(file, jar, entries);
		} catch (RuntimeException e) {
			jar.close();
			throw e;
		}
	}

	static Part empty() {
		return indexed(new Indexer().complete());
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

	private final List<Part> parts;
	// by binary name: where the class is to be found, the first part on the classpath to have it
	private final Map<String, Part> partOfClass = new HashMap<>();
	private final ConcurrentHashMap<DotName, Optional<ClassInfo>> loaded = new ConcurrentHashMap<>();
	private final AtomicInteger read = new AtomicInteger();
	private volatile boolean closed;

	/**
	 * @param parts in classpath order - taken over, and closed by {@link #close()}
	 */
	LazyClasspathIndex(List<Part> parts) {
		this.parts = new ArrayList<>(parts);
		for (Part part : parts) {
			switch (part) {
				case Indexed indexed -> indexed.index().getKnownClasses().forEach(classInfo -> partOfClass.putIfAbsent(classInfo.name().toString(), part));
				case Listed listed -> listed.entries().keySet().forEach(name -> partOfClass.putIfAbsent(name, part));
			}
		}
	}

	/**
	 * How many classes of JARs not indexed as a whole have been read so far - unreadable ones too.
	 */
	int loadedCount() {
		return read.get();
	}

	@Override
	public ClassInfo getClassByName(DotName className) {
		Optional<ClassInfo> known = loaded.get(className);
		if (known == null) {
			// read outside of the map's locks - two threads asking for the same class at once read it
			// twice, rather than every other lookup waiting on the reading
			known = Optional.ofNullable(load(className));
			Optional<ClassInfo> other = loaded.putIfAbsent(className, known);
			known = other != null ? other : known;
		}
		return known.orElse(null);
	}

	private ClassInfo load(DotName className) {
		if (closed) {
			throw new IllegalStateException("the classpath index is closed - looking up " + className);
		}

		return switch (partOfClass.get(className.toString())) {
			case null -> null; // not on the classpath - a JDK class, most commonly
			case Indexed indexed -> indexed.index().getClassByName(className);
			case Listed listed -> {
				read.incrementAndGet();
				yield read(listed, className);
			}
		};
	}

	private static ClassInfo read(Listed listed, DotName className) {
		String entry = listed.entries().get(className.toString());
		try (InputStream in = listed.jar().getInputStream(listed.jar().getEntry(entry))) {
			return Index.singleClass(in);
		} catch (IOException | RuntimeException e) {
			// a class file the indexer cannot read (a class file version too new for it, say): what
			// it would have contributed is missing from the scan
			log.warn("skipping unreadable class entry '{}' in '{}': {}", entry, listed.file().getName(), e.toString());
			return null;
		}
	}

	@Override
	public void close() {
		closed = true;
		parts.forEach(Part::close);
		loaded.clear();
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
