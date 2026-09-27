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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.eclipse.lsp4j.Location;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads {@link StereotypeClassElement}s out of a JAR file's classes, mirroring what
 * {@code StereotypesIndexer} computes from Java source (same {@code annotationTypes}/
 * {@code supertypes} semantics), so the exact same stereotype-matching machinery that runs against
 * source-indexed elements also works against these. See {@code docs/structure-view-dependencies.md}.
 *
 * <p>Built directly on the public Jandex API rather than the {@code commons.jandex} package: that
 * package's classpath-wide indexing (JRT modules, on-disk index caching) is built for a different
 * job - resolving types for completion/hover - and its classes are not accessible outside their
 * own package anyway.
 *
 * <p>An annotation's own meta-annotations often live in a different JAR than the annotated class
 * (Spring's own annotations are the textbook example: {@code @RestController} in {@code spring-web}
 * is meta-annotated with {@code @Controller} in {@code spring-context}). So a class's annotations
 * and supertypes are resolved against a combined {@link Index} the caller builds over more than
 * just the one JAR being scanned - see {@link #ownClassesOf}.
 *
 * @author Martin Lippert
 */
public class JarStereotypeScanner {

	private static final Logger log = LoggerFactory.getLogger(JarStereotypeScanner.class);

	/**
	 * Feeds every {@code .class} entry of the given JAR into the indexer, and returns the
	 * {@link DotName}s of the classes that came from this JAR specifically - the indexer itself may
	 * go on to receive classes from other JARs too, so that annotations and supertypes declared
	 * elsewhere on the classpath can still be resolved once the combined {@link Index} is built.
	 */
	public static Set<DotName> indexInto(Indexer indexer, File jarFile) {
		Set<DotName> ownClasses = new LinkedHashSet<>();

		try (JarFile jar = new JarFile(jarFile)) {
			Enumeration<JarEntry> entries = jar.entries();
			while (entries.hasMoreElements()) {
				JarEntry entry = entries.nextElement();
				if (!entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) {
					continue;
				}

				try (InputStream in = jar.getInputStream(entry)) {
					// Indexer.index(InputStream) is overloaded on return type alone (a Jandex 3.x
					// binary-compatibility trick) - not something a normal method call can pick
					// the ClassInfo-returning overload of, so the class name is derived from the
					// entry's own path instead, which is exactly the class's binary name anyway
					indexer.index(in);
					ownClasses.add(dotNameOfClassEntry(entry.getName()));
				} catch (Exception e) {
					log.debug("skipping unreadable class entry '{}' in '{}'", entry.getName(), jarFile, e);
				}
			}
		} catch (IOException e) {
			log.warn("failed to read JAR '{}' for structure dependency scanning", jarFile, e);
		}

		return ownClasses;
	}

	/**
	 * A {@code .class} entry's path is its binary name, slash-separated - e.g.
	 * {@code com/example/Foo$Bar.class} is {@code com.example.Foo$Bar}.
	 */
	private static DotName dotNameOfClassEntry(String entryName) {
		String binaryName = entryName.substring(0, entryName.length() - ".class".length()).replace('/', '.');
		return DotName.createSimple(binaryName);
	}

	/**
	 * The elements a JAR's own classes (identified by {@code ownClasses}, see {@link #indexInto})
	 * contribute - one {@link StereotypeClassElement} per class, unfiltered by any stereotype catalog:
	 * filtering happens later, live, against whichever catalog is current when a tree is built (see
	 * {@code docs/structure-view-dependencies.md}'s discussion of why this is not cached here).
	 *
	 * <p>Annotation types and annotated interfaces themselves are left out, matching what
	 * {@code StereotypesIndexer} does for source: an annotation type is a stereotype *definition*
	 * candidate, never a tree node of its own.
	 */
	public static List<StereotypeClassElement> ownClassesOf(Set<DotName> ownClasses, Index index) {
		List<StereotypeClassElement> result = new ArrayList<>();

		for (DotName name : ownClasses) {
			ClassInfo classInfo = index.getClassByName(name);
			if (classInfo == null || classInfo.isAnnotation() || classInfo.isModule()) {
				continue;
			}

			StereotypeClassElement element = new StereotypeClassElement(name.toString(), null,
					supertypesOf(classInfo, index), annotationTypesOf(classInfo, index), null);

			for (MethodInfo method : classInfo.methods()) {
				Set<String> methodAnnotations = annotationTypesOf(method, index);

				if (!methodAnnotations.isEmpty()) {
					element.addChild(new StereotypeMethodElement(method.name(), methodLabelOf(method), methodSignatureOf(method),
							null, methodAnnotations, null));
				}
			}

			result.add(element);
		}

		return result;
	}

	/**
	 * The class's own direct annotations, expanded through their meta-annotation chain (an
	 * annotation that is itself annotated with another one - {@code @RestController} meta-annotated
	 * with {@code @Controller}, say), plus the direct annotations of every type in its superclass
	 * and interface hierarchy - <em>not</em> meta-expanded for those, matching
	 * {@code StereotypesIndexer.getAnnotationTypes}'s exact (if slightly asymmetric) behavior for
	 * source-indexed types, so a class does not get attributed differently depending on whether it
	 * came from a workspace project or a JAR.
	 */
	static Set<String> annotationTypesOf(ClassInfo classInfo, Index index) {
		Set<String> result = new LinkedHashSet<>();
		Set<DotName> visitedMetaAnnotations = new LinkedHashSet<>();

		for (AnnotationInstance annotation : classInfo.classAnnotations()) {
			addWithMetaAnnotations(annotation.name(), index, result, visitedMetaAnnotations);
		}

		for (String supertype : supertypesOf(classInfo, index)) {
			ClassInfo supertypeInfo = index.getClassByName(DotName.createSimple(supertype));
			if (supertypeInfo != null) {
				for (AnnotationInstance annotation : supertypeInfo.classAnnotations()) {
					addIfNotJavaLang(annotation.name(), result);
				}
			}
		}

		return result;
	}

	/**
	 * A method's own direct annotations, expanded through their meta-annotation chain - the same
	 * expansion {@link #annotationTypesOf(ClassInfo, Index)} does for a class's own annotations, so
	 * a convenience annotation like {@code @GetMapping} (meta-annotated with
	 * {@code @RequestMapping}) matches a stereotype assigned to the base annotation, exactly as it
	 * would from source ({@code StereotypesIndexer.getAnnotationTypes} meta-expands a method's own
	 * annotations too). Unlike a class, a method does <em>not</em> also pick up a superclass or
	 * interface method's annotations - matching source there as well.
	 */
	static Set<String> annotationTypesOf(MethodInfo method, Index index) {
		Set<String> result = new LinkedHashSet<>();
		Set<DotName> visitedMetaAnnotations = new LinkedHashSet<>();

		for (AnnotationInstance annotation : method.declaredAnnotations()) {
			addWithMetaAnnotations(annotation.name(), index, result, visitedMetaAnnotations);
		}

		return result;
	}

	private static void addWithMetaAnnotations(DotName annotationName, Index index, Set<String> result, Set<DotName> visited) {
		if (!visited.add(annotationName)) {
			return; // a meta-annotation cycle - nothing further to add
		}

		addIfNotJavaLang(annotationName, result);

		ClassInfo annotationClass = index.getClassByName(annotationName);
		if (annotationClass != null) {
			for (AnnotationInstance metaAnnotation : annotationClass.classAnnotations()) {
				addWithMetaAnnotations(metaAnnotation.name(), index, result, visited);
			}
		}
	}

	private static void addIfNotJavaLang(DotName name, Set<String> result) {
		String fqn = name.toString();
		if (!fqn.startsWith("java")) { // matches StereotypesIndexer.getAnnotationTypes's own filter
			result.add(fqn);
		}
	}

	/**
	 * Every superclass and interface in the class's hierarchy, recursively - by name, whether or not
	 * that type is itself present in {@code index} (a supertype outside of what got indexed, most
	 * commonly a JDK type, still has to count for a stereotype assignment that matches on it).
	 */
	static Set<String> supertypesOf(ClassInfo classInfo, Index index) {
		Set<String> result = new LinkedHashSet<>();
		Deque<DotName> toVisit = new ArrayDeque<>();

		if (classInfo.superName() != null) {
			toVisit.add(classInfo.superName());
		}
		toVisit.addAll(List.of(classInfo.interfaces()));

		Set<DotName> visited = new LinkedHashSet<>();
		while (!toVisit.isEmpty()) {
			DotName name = toVisit.poll();
			if (name == null || !visited.add(name)) {
				continue;
			}

			result.add(name.toString());

			ClassInfo superInfo = index.getClassByName(name);
			if (superInfo != null) {
				if (superInfo.superName() != null) {
					toVisit.add(superInfo.superName());
				}
				toVisit.addAll(List.of(superInfo.interfaces()));
			}
		}

		return result;
	}

	/**
	 * Mirrors {@code ASTUtils.getMethodSignature(method, false)}'s label shape closely enough for
	 * display - the declaring class's simple name, then simple type names throughout, not the fully
	 * qualified ones a signature (used for identity, not display) would need.
	 */
	private static String methodLabelOf(MethodInfo method) {
		StringBuilder label = new StringBuilder(simpleName(method.declaringClass().name())).append('.').append(method.name()).append('(');
		for (int i = 0; i < method.parametersCount(); i++) {
			if (i > 0) {
				label.append(", ");
			}
			label.append(simpleName(method.parameterType(i).name()));
		}
		return label.append(") : ").append(simpleName(method.returnType().name())).toString();
	}

	/**
	 * A signature stable enough for identity - unlike {@link #methodLabelOf}, uses fully qualified
	 * parameter type names so two overloads are never confused with one another.
	 */
	private static String methodSignatureOf(MethodInfo method) {
		StringBuilder signature = new StringBuilder(method.declaringClass().name().toString()).append('.').append(method.name()).append('(');
		for (int i = 0; i < method.parametersCount(); i++) {
			if (i > 0) {
				signature.append(", ");
			}
			signature.append(method.parameterType(i).name().toString());
		}
		return signature.append(')').toString();
	}

	private static String simpleName(DotName name) {
		return name.local();
	}

}
