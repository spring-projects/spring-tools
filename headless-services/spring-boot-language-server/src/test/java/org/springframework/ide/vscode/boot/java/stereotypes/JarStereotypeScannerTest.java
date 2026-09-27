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
package org.springframework.ide.vscode.boot.java.stereotypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * @author Martin Lippert
 */
public class JarStereotypeScannerTest {

	@TempDir
	Path tempDir;

	/**
	 * The scenario this whole design turns on: {@code @RestController}-in-spring-web being
	 * meta-annotated with {@code @Controller}-in-spring-context is the textbook case of an
	 * annotation's own meta-annotations living in a different JAR than the annotated class - see
	 * {@code docs/structure-view-dependencies.md}. Reproduced here with two small fixture JARs
	 * instead of real Spring JARs, so the test has no other dependency.
	 */
	@Test
	void metaAnnotationsAreResolvedAcrossJarsWhenBothAreIndexedTogether() throws Exception {
		Path classesDir = JarFixtureBuilder.compileAll(tempDir, Map.of(
				"a.Meta", "package a; import java.lang.annotation.*; @Target(ElementType.ANNOTATION_TYPE) @Retention(RetentionPolicy.RUNTIME) public @interface Meta {}",
				"b.Direct", "package b; import a.Meta; import java.lang.annotation.*; @Meta @Retention(RetentionPolicy.RUNTIME) public @interface Direct {}",
				"b.Annotated", "package b; @Direct public class Annotated {}"
		));

		File metaJar = JarFixtureBuilder.packageJar(classesDir, tempDir, "meta", List.of("a.Meta"), Map.of());
		File ownJar = JarFixtureBuilder.packageJar(classesDir, tempDir, "own", List.of("b.Direct", "b.Annotated"), Map.of());

		Indexer indexer = new Indexer();
		Set<DotName> ownClasses = JarStereotypeScanner.indexInto(indexer, ownJar);
		JarStereotypeScanner.indexInto(indexer, metaJar);
		Index index = indexer.complete();

		List<StereotypeClassElement> elements = JarStereotypeScanner.ownClassesOf(ownClasses, index);
		StereotypeClassElement annotated = elements.stream().filter(e -> e.getType().equals("b.Annotated")).findFirst().orElseThrow();

		assertTrue(annotated.isAnnotatedWith("b.Direct"), "the class's own direct annotation");
		assertTrue(annotated.isAnnotatedWith("a.Meta"), "resolved across the two jars via b.Direct's own meta-annotation");
	}

	@Test
	void metaAnnotationsInAMissingJarAreSilentlySkippedRatherThanFailingTheScan() throws Exception {
		File ownJar = JarFixtureBuilder.buildJar(tempDir, "own", Map.of(
				"b.Direct", "package b; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface Direct {}",
				"b.Annotated", "package b; @Direct public class Annotated {}"
		), Map.of());

		Indexer indexer = new Indexer();
		Set<DotName> ownClasses = JarStereotypeScanner.indexInto(indexer, ownJar);
		// the annotation class @Direct itself is indexed (it's in ownJar), so its own (absent)
		// meta-annotations resolve to nothing - no exception, no missing data for the rest
		Index index = indexer.complete();

		List<StereotypeClassElement> elements = JarStereotypeScanner.ownClassesOf(ownClasses, index);
		StereotypeClassElement annotated = elements.stream().filter(e -> e.getType().equals("b.Annotated")).findFirst().orElseThrow();

		assertTrue(annotated.isAnnotatedWith("b.Direct"));
	}

	@Test
	void supertypesAreCollectedRecursivelyByNameEvenWhenNotThemselvesIndexed() throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "hierarchy", Map.of(
				"c.Base", "package c; public class Base implements java.io.Serializable {}",
				"c.Middle", "package c; public class Middle extends Base {}",
				"c.Leaf", "package c; public class Leaf extends Middle {}"
		), Map.of());

		Indexer indexer = new Indexer();
		Set<DotName> ownClasses = JarStereotypeScanner.indexInto(indexer, jar);
		Index index = indexer.complete();

		List<StereotypeClassElement> elements = JarStereotypeScanner.ownClassesOf(ownClasses, index);
		StereotypeClassElement leaf = elements.stream().filter(e -> e.getType().equals("c.Leaf")).findFirst().orElseThrow();

		assertTrue(leaf.doesImplement("c.Middle"));
		assertTrue(leaf.doesImplement("c.Base"));
		assertTrue(leaf.doesImplement("java.io.Serializable"), "a supertype outside the indexed jar still counts by name");
		assertFalse(leaf.doesImplement("c.Unrelated"), "an unrelated type is not a supertype");
	}

	@Test
	void annotationTypesAndModulesAreNotThemselvesTreatedAsElements() throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "own", Map.of(
				"d.SomeAnnotation", "package d; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface SomeAnnotation {}",
				"d.Marked", "package d; @SomeAnnotation public class Marked {}"
		), Map.of());

		Indexer indexer = new Indexer();
		Set<DotName> ownClasses = JarStereotypeScanner.indexInto(indexer, jar);
		Index index = indexer.complete();

		List<StereotypeClassElement> elements = JarStereotypeScanner.ownClassesOf(ownClasses, index);

		assertEquals(List.of("d.Marked"), elements.stream().map(StereotypeClassElement::getType).toList());
	}

	@Test
	void methodsWithoutAnyAnnotationAreLeftOutButAnnotatedOnesAreKept() throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "methods", Map.of(
				"e.MethodAnnotation", "package e; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface MethodAnnotation {}",
				"e.WithMethods", """
						package e;
						public class WithMethods {
							public void plain() {}
							@MethodAnnotation
							public String annotated(String s) { return s; }
						}
						"""
		), Map.of());

		Indexer indexer = new Indexer();
		Set<DotName> ownClasses = JarStereotypeScanner.indexInto(indexer, jar);
		Index index = indexer.complete();

		List<StereotypeClassElement> elements = JarStereotypeScanner.ownClassesOf(ownClasses, index);
		StereotypeClassElement type = elements.stream().filter(e -> e.getType().equals("e.WithMethods")).findFirst().orElseThrow();

		assertEquals(1, type.getMethods().size());
		assertEquals("annotated", type.getMethods().get(0).getMethodName());
	}

	/**
	 * A method's own annotations are meta-expanded exactly like a class's - not doing so was a real
	 * bug: a convenience annotation such as {@code @GetMapping} (meta-annotated with
	 * {@code @RequestMapping}) has to resolve to the base annotation for a "Request Mappings"-style
	 * stereotype assignment on {@code @RequestMapping} to match a JAR-scanned handler method, the
	 * same way it already does for a workspace project's source-indexed one.
	 */
	@Test
	void methodAnnotationsAreMetaExpandedJustLikeClassAnnotations() throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "mapping", Map.of(
				"f.Mapping", "package f; import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) public @interface Mapping {}",
				"f.GetMapping", "package f; import java.lang.annotation.*; @Mapping @Retention(RetentionPolicy.RUNTIME) public @interface GetMapping {}",
				"f.Controller", """
						package f;
						public class Controller {
							@GetMapping
							public String greeting() { return "hi"; }
						}
						"""
		), Map.of());

		Indexer indexer = new Indexer();
		Set<DotName> ownClasses = JarStereotypeScanner.indexInto(indexer, jar);
		Index index = indexer.complete();

		StereotypeClassElement type = JarStereotypeScanner.ownClassesOf(ownClasses, index).get(0);
		StereotypeMethodElement method = type.getMethods().get(0);

		assertTrue(method.isAnnotatedWith("f.GetMapping"), "the method's own direct annotation");
		assertTrue(method.isAnnotatedWith("f.Mapping"), "resolved through @GetMapping's own meta-annotation");
	}

}
