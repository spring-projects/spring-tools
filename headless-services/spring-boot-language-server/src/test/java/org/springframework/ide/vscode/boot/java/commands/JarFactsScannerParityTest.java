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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.boot.java.beans.JarBeanIndexer;
import org.springframework.ide.vscode.boot.java.stereotypes.JarFactsScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.JarFixtureBuilder;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.JarType;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;

/**
 * {@link JarFactsScanner} and {@link JarStereotypeScanner#ownClassesOf} are two implementations of
 * the same thing, and have to come to the same answers: here they are held to it, over JARs built
 * to have every construct that matters, and over real ones - what a scan of a real project meets.
 * The elements of the Jandex-based one are the reference.
 *
 * @author Martin Lippert
 */
class JarFactsScannerParityTest {

	@TempDir
	Path tempDir;

	private static final String MARKER = "package com.example; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface Marker {}";
	private static final String COMPOSED = "package com.example; import java.lang.annotation.*; @Marker @Retention(RetentionPolicy.RUNTIME) public @interface Composed {}";

	@Test
	void theElementsOfEveryKindOfClassAreTheSame() throws Exception {
		File meta = jarOf("meta", Map.of("com.example.Marker", MARKER, "com.example.Composed", COMPOSED));

		File jar = jarOf("kinds", List.of(meta), new LinkedHashMap<>(Map.ofEntries(
				Map.entry("com.example.Base", "package com.example; @Composed public class Base<T> implements java.util.function.Supplier<T>, java.io.Serializable {"
						+ " @Composed public T get() { return null; } }"),
				Map.entry("com.example.Sub", "package com.example; import java.util.List; public class Sub extends Base<String> implements Comparable<Sub> {"
						+ " @Marker void m(List<? extends Number> numbers, String[] arrays) {} @Deprecated @Marker void both() {} @Deprecated void onlyJava() {}"
						+ " public int compareTo(Sub other) { return 0; } }"),
				Map.entry("com.example.Api", "package com.example; @Marker public interface Api<K> extends java.util.Map<K, String> { @Marker void call(); }"),
				Map.entry("com.example.Color", "package com.example; public enum Color { RED { @Marker void f() {} }, BLUE; @Marker void g() {} }"),
				Map.entry("com.example.Point", "package com.example; @Marker public record Point(int x, @Marker String name) {}"),
				Map.entry("com.example.Outer", "package com.example; public class Outer { class Inner {} @Marker static class Nested<T> extends Outer {}"
						+ " @Marker <X extends Number & Comparable<X>> X generic(X x) { new Object() { @Marker void inAnonymous() {} }; return x; }"
						+ " class Sub2 extends Nested<String> { @Marker Sub2(String s) {} } }"),
				Map.entry("com.example.Plain", "package com.example; public class Plain extends Sub implements Runnable { public void run() {} }"),
				Map.entry("com.example.package-info", "@Marker @Deprecated package com.example;"))));
		List<File> classpath = List.of(meta, jar);

		Comparison comparison = compare(classpath, classpath);

		assertEquals(comparison.reference(), comparison.actual());
		assertTrue(comparison.reference().stream().anyMatch(line -> line.startsWith("com.example.Sub ") && line.contains("com.example.Marker")), "the fixture has to have something to compare");
		assertTrue(comparison.reference().stream().anyMatch(line -> line.contains("M ") && line.contains("generic")), "the fixture has to have methods to compare");
		assertEquals(0, comparison.skipped());
	}

	/**
	 * The classes of the JARs everything is made of: Spring's, Boot's, Guava, LSP4J - the stuff of a
	 * real project's classpath, with all its generics, nesting and meta-annotations. Everything
	 * is scanned, and everything is looked up in everything else.
	 */
	@Test
	void theElementsOfRealJarsAreTheSame() throws Exception {
		List<File> classpath = new ArrayList<>();
		for (String className : List.of("org.springframework.core.SpringVersion", "org.springframework.beans.factory.BeanFactory",
				"org.springframework.context.annotation.Configuration", "org.springframework.web.bind.annotation.RestController",
				"org.springframework.boot.SpringApplication", "org.springframework.boot.autoconfigure.SpringBootApplication",
				"org.springframework.data.repository.Repository", "com.google.common.collect.ImmutableList", "org.eclipse.lsp4j.Location",
				"org.jboss.jandex.Index", "org.junit.jupiter.api.Test")) {
			File jar = jarOf(className);
			if (jar != null && !classpath.contains(jar)) {
				classpath.add(jar);
			}
		}
		assertTrue(classpath.size() > 3, "found only " + classpath);

		Comparison comparison = compare(classpath, classpath);

		assertTrue(comparison.reference().size() > 5000, "scanned only " + comparison.reference().size() + " classes");
		assertEquals(comparison.reference(), comparison.actual());
		assertEquals(0, comparison.skipped());
	}

	/**
	 * Only some of the classpath is scanned, the rest is looked up - as in a scan of a few selected
	 * JARs.
	 */
	@Test
	void theElementsOfSomeJarsAreTheSameWhenTheRestOfTheClasspathIsOnlyLookedUp() throws Exception {
		File meta = jarOf("meta", Map.of("com.example.Marker", MARKER, "com.example.Composed", COMPOSED));
		File jar = jarOf("scanned", List.of(meta), Map.of(
				"com.example.Base", "package com.example; @Composed public class Base {}",
				"com.example.Sub", "package com.example; public class Sub extends Base {}"));

		Comparison comparison = compare(List.of(meta, jar), List.of(jar));

		assertEquals(comparison.reference(), comparison.actual());
		// the class's own annotations are meta-expanded, a supertype's are not
		assertTrue(comparison.reference().stream().anyMatch(line -> line.startsWith("com.example.Base ") && line.contains("com.example.Marker")));
		assertTrue(comparison.reference().stream().anyMatch(line -> line.startsWith("com.example.Sub ") && line.contains("com.example.Composed")
				&& !line.contains("com.example.Marker")));
	}

	/**
	 * What decides which classes are read in full for their beans must not leave one out that has
	 * any - every class the Jandex-based scan gets a bean for is one the gate lets through.
	 */
	@Test
	void everyClassThatHasABeanPassesTheGate() throws Exception {
		List<File> classpath = new ArrayList<>();
		for (String className : List.of("org.springframework.context.annotation.Configuration", "org.springframework.web.bind.annotation.RestController",
				"org.springframework.boot.autoconfigure.SpringBootApplication", "org.springframework.boot.SpringApplication",
				"org.springframework.data.repository.Repository", "org.springframework.core.SpringVersion", "org.springframework.beans.factory.BeanFactory")) {
			File jar = jarOf(className);
			if (jar != null && !classpath.contains(jar)) {
				classpath.add(jar);
			}
		}

		List<IndexView> indexes = new ArrayList<>();
		Map<File, Set<DotName>> own = new LinkedHashMap<>();
		for (File file : classpath) {
			Indexer indexer = new Indexer();
			own.put(file, JarStereotypeScanner.indexInto(indexer, file));
			indexes.add(indexer.complete());
		}
		IndexView index = CompositeIndex.create(indexes);

		List<LazyClasspathIndex.ClasspathJar> jars = new ArrayList<>();
		for (File file : classpath) {
			jars.add(LazyClasspathIndex.list(file, true));
		}

		int withBeans = 0;
		try (LazyClasspathIndex lazy = new LazyClasspathIndex(jars)) {
			for (File file : classpath) {
				Map<Object, String> keys = new IdentityHashMap<>();
				JarFactsScanner scanner = new JarFactsScanner(lazy::factsOf, lazy);
				Map<String, JarFactsScanner.ScannedClass> scanned = new LinkedHashMap<>();
				scanner.ownClassesOf(jars.get(classpath.indexOf(file)).ownClassNames(), keys).forEach(s -> scanned.put(s.element().getType(), s));

				for (StereotypeClassElement element : JarStereotypeScanner.ownClassesOf(own.get(file), index)) {
					ClassInfo classInfo = index.getClassByName(DotName.createSimple(element.getType()));
					JarType type = new JarType(classInfo, element, JarStereotypeScanner.ownAnnotationTypesOf(classInfo, index), index,
							new Location("jar:x", new Range(new Position(0, 0), new Position(0, 0))), file, new IdentityHashMap<>());
					if (!JarBeanIndexer.beansOf(type).isEmpty()) {
						withBeans++;
						JarFactsScanner.ScannedClass s = scanned.get(element.getType());
						assertTrue(s != null && s.facts() != null, element.getType() + " is not scanned");
						assertTrue(JarBeanIndexer.mayHaveBeans(s.facts(), s.element(), scanner.ownAnnotationTypesOf(s.facts())),
								element.getType() + " has a bean, but does not pass the gate");
					}
				}
			}
		}
		assertTrue(withBeans > 20, "only " + withBeans + " classes with a bean to check");
	}

	// ------------------------------------------------------------------------------------------------

	private record Comparison(List<String> reference, List<String> actual, int skipped) {
	}

	/**
	 * Both ways of scanning the {@code scanned} JARs' classes against the {@code classpath}, as the
	 * elements' descriptions, one per class, sorted. Description: the class's supertypes and
	 * annotations, its methods' labels, signatures and annotations, and the binding keys.
	 */
	private Comparison compare(List<File> classpath, List<File> scanned) throws Exception {
		// the reference: indexed as a whole, as a scan used to
		List<IndexView> indexes = new ArrayList<>();
		Map<File, Set<DotName>> own = new LinkedHashMap<>();
		for (File file : classpath) {
			Indexer indexer = new Indexer();
			own.put(file, JarStereotypeScanner.indexInto(indexer, file));
			indexes.add(indexer.complete());
		}
		IndexView index = CompositeIndex.create(indexes);

		List<String> reference = new ArrayList<>();
		for (File file : scanned) {
			Map<Object, String> keys = new IdentityHashMap<>();
			for (StereotypeClassElement element : JarStereotypeScanner.ownClassesOf(own.get(file), index, keys)) {
				reference.add(describe(file, element, keys));
			}
		}

		// the facts
		List<LazyClasspathIndex.ClasspathJar> jars = new ArrayList<>();
		for (File file : classpath) {
			jars.add(LazyClasspathIndex.list(file, scanned.contains(file)));
		}

		List<String> actual = new ArrayList<>();
		int skipped = 0;
		try (LazyClasspathIndex lazy = new LazyClasspathIndex(jars)) {
			for (File file : scanned) {
				Map<Object, String> keys = new IdentityHashMap<>();
				JarFactsScanner scanner = new JarFactsScanner(lazy::factsOf, lazy);
				for (JarFactsScanner.ScannedClass scannedClass : scanner.ownClassesOf(jars.get(classpath.indexOf(file)).ownClassNames(), keys)) {
					actual.add(describe(file, scannedClass.element(), keys));
				}
				skipped += scanner.skipped();
			}
		}

		return new Comparison(reference.stream().sorted().toList(), actual.stream().sorted().toList(), skipped);
	}

	private static String describe(File jar, StereotypeClassElement element, Map<Object, String> keys) {
		StringBuilder result = new StringBuilder(element.getType()).append(" | ").append(new TreeSet<>(element.getSupertypes()))
				.append(" | ").append(new TreeSet<>(element.getAnnotationTypes())).append(" | ").append(keys.get(element));
		List<String> methods = new ArrayList<>();
		for (StereotypeMethodElement method : element.getMethods()) {
			methods.add("M " + method.getMethodName() + " | " + method.getMethodLabel() + " | " + method.getMethodSignature() + " | "
					+ new TreeSet<>(method.getAnnotationTypes()) + " | " + keys.get(method));
		}
		return result + methods.stream().sorted().map(line -> "\n    " + line).reduce("", String::concat) + " [" + jar.getName() + "]";
	}

	private File jarOf(String name, Map<String, String> sources) throws Exception {
		return jarOf(name, List.of(), sources);
	}

	private File jarOf(String name, List<File> classpath, Map<String, String> sources) throws Exception {
		Path directory = Files.createDirectories(tempDir.resolve(name));
		Path classes = JarFixtureBuilder.compileAll(directory, sources, classpath);
		return JarFixtureBuilder.packageJar(classes, directory, name, new LinkedHashSet<>(sources.keySet()), Map.of());
	}

	/**
	 * The JAR a class of the test's own classpath comes from, if it comes from one.
	 */
	private static File jarOf(String className) throws URISyntaxException {
		try {
			Class<?> type = Class.forName(className, false, JarFactsScannerParityTest.class.getClassLoader());
			File location = new File(type.getProtectionDomain().getCodeSource().getLocation().toURI());
			return location.isFile() ? location : null;
		} catch (ClassNotFoundException | LinkageError e) {
			return null; // not on the test's classpath
		}
	}

}
