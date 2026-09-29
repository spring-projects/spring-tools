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
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;
import org.jboss.jandex.VoidType;
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
	 * <p>Left out, matching what {@code StereotypesIndexer} sees in source: annotation types (a
	 * stereotype *definition* candidate, never a tree node of its own), and anonymous and synthetic
	 * classes (an anonymous class is no {@code TypeDeclaration}; synthetic classes have no source
	 * at all).
	 */
	public static List<StereotypeClassElement> ownClassesOf(Set<DotName> ownClasses, Index index) {
		return ownClassesOf(ownClasses, index, new IdentityHashMap<>());
	}

	/**
	 * As {@link #ownClassesOf(Set, Index)}, recording the JDT binding key ({@link JarBindingKeys}) of
	 * every type and method element built - by the element's identity - so that the tree can have
	 * the IDE open it.
	 */
	public static List<StereotypeClassElement> ownClassesOf(Set<DotName> ownClasses, Index index, Map<Object, String> bindingKeys) {
		List<StereotypeClassElement> result = new ArrayList<>();

		for (DotName name : ownClasses) {
			ClassInfo classInfo = index.getClassByName(name);

			if (isPackageInfo(classInfo)) {
				StereotypeClassElement element = packageInfoElementOf(classInfo);
				bindingKeys.put(element, JarBindingKeys.of(classInfo));
				result.add(element);
				continue;
			}

			if (!isSourceLevelType(classInfo)) {
				continue;
			}

			StereotypeClassElement element = new StereotypeClassElement(name.toString(), null,
					supertypesOf(classInfo, index), annotationTypesOf(classInfo, index), null);
			bindingKeys.put(element, JarBindingKeys.of(classInfo));

			for (MethodInfo method : sourceLevelMethodsOf(classInfo)) {
				Set<String> methodAnnotations = annotationTypesOf(method, index);

				if (!methodAnnotations.isEmpty()) {
					StereotypeMethodElement methodElement = new StereotypeMethodElement(sourceName(method), methodLabelOf(method),
							methodSignatureOf(method), null, methodAnnotations, null);
					bindingKeys.put(methodElement, JarBindingKeys.of(method));
					element.addChild(methodElement);
				}
			}

			result.add(element);
		}

		return result;
	}

	/**
	 * {@code package-info.class} - synthetic in bytecode, but a type element of its own on the AST
	 * side, carrying the package's annotations.
	 */
	private static boolean isPackageInfo(ClassInfo classInfo) {
		if (classInfo == null) {
			return false;
		}
		String name = classInfo.name().toString();
		return name.equals("package-info") || name.endsWith(".package-info");
	}

	/**
	 * {@code StereotypesIndexer.createStereotypeElementForPackage}'s type element: named
	 * {@code <package>.package-info}, no supertypes, the package's direct annotations - not
	 * meta-expanded and not filtered, exactly as that method takes them.
	 */
	private static StereotypeClassElement packageInfoElementOf(ClassInfo classInfo) {
		Set<String> annotationTypes = new LinkedHashSet<>();
		for (AnnotationInstance annotation : classInfo.declaredAnnotations()) {
			annotationTypes.add(JdtStyleTypeNames.qualifiedName(annotation.name()));
		}
		return new StereotypeClassElement(classInfo.name().toString(), null, Set.of(), annotationTypes, null);
	}

	/**
	 * Whether the class corresponds to a type declaration in source the way
	 * {@code StereotypesIndexer} sees it.
	 */
	public static boolean isSourceLevelType(ClassInfo classInfo) {
		return classInfo != null && !classInfo.isAnnotation() && !classInfo.isModule() && !classInfo.isSynthetic()
				&& classInfo.nestingType() != ClassInfo.NestingType.ANONYMOUS;
	}

	/**
	 * The class's methods as they appear in source, in declaration order: constructors included
	 * (JDT's {@code TypeDeclaration.getMethods()} includes them), but not the static initializer
	 * and not the synthetic and bridge methods javac adds.
	 */
	public static List<MethodInfo> sourceLevelMethodsOf(ClassInfo classInfo) {
		return classInfo.methodsInDeclarationOrder().stream()
				.filter(method -> !method.isStaticInitializer() && !method.isSynthetic() && !method.isBridge())
				.toList();
	}

	/**
	 * The class's own direct annotations expanded through their meta-annotation chain - but,
	 * unlike {@link #annotationTypesOf(ClassInfo, Index)}, <em>without</em> the annotations of its
	 * supertypes. This is what {@code AnnotationHierarchies.isAnnotatedWith} answers from on the
	 * AST side, which every "is this a component / a {@code @Configuration} class /
	 * {@code @NoRepositoryBean}" decision there uses - a class extending a {@code @Component}
	 * superclass is not a component to it, while {@code annotationTypesOf} (built for stereotype
	 * matching) would say otherwise.
	 */
	public static Set<String> ownAnnotationTypesOf(ClassInfo classInfo, Index index) {
		Set<String> result = new LinkedHashSet<>();
		Set<DotName> visitedMetaAnnotations = new LinkedHashSet<>();

		// unfiltered, unlike the stereotype matching sets: javax.inject.Named has to be seen
		for (AnnotationInstance annotation : classInfo.classAnnotations()) {
			addWithMetaAnnotations(annotation.name(), index, result, visitedMetaAnnotations, false);
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

		for (Type supertype : hierarchyOf(classInfo, index)) {
			ClassInfo supertypeInfo = index.getClassByName(supertype.name());
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
	public static Set<String> annotationTypesOf(MethodInfo method, Index index) {
		Set<String> result = new LinkedHashSet<>();
		Set<DotName> visitedMetaAnnotations = new LinkedHashSet<>();

		for (AnnotationInstance annotation : method.declaredAnnotations()) {
			addWithMetaAnnotations(annotation.name(), index, result, visitedMetaAnnotations);
		}

		return result;
	}

	/**
	 * The annotation type itself and its meta-annotations, recursively -
	 * {@code AnnotationHierarchies.isAnnotatedWith}'s answer for an annotation, which counts the
	 * annotation itself.
	 */
	public static Set<String> metaAnnotationTypesOf(DotName annotationName, Index index) {
		Set<String> result = new LinkedHashSet<>();
		addWithMetaAnnotations(annotationName, index, result, new LinkedHashSet<>(), false);
		return result;
	}

	private static void addWithMetaAnnotations(DotName annotationName, Index index, Set<String> result, Set<DotName> visited) {
		addWithMetaAnnotations(annotationName, index, result, visited, true);
	}

	/**
	 * @param withoutJava whether to leave out the {@code java*} names, as stereotype matching does
	 *        ({@link #addIfNotJavaLang}) - a gate that looks for {@code javax.inject.Named} must not
	 */
	private static void addWithMetaAnnotations(DotName annotationName, Index index, Set<String> result, Set<DotName> visited,
			boolean withoutJava) {
		if (!visited.add(annotationName)) {
			return; // a meta-annotation cycle - nothing further to add
		}

		if (withoutJava) {
			addIfNotJavaLang(annotationName, result);
		}
		else {
			result.add(JdtStyleTypeNames.qualifiedName(annotationName));
		}

		ClassInfo annotationClass = index.getClassByName(annotationName);
		if (annotationClass != null) {
			for (AnnotationInstance metaAnnotation : annotationClass.classAnnotations()) {
				addWithMetaAnnotations(metaAnnotation.name(), index, result, visited, withoutJava);
			}
		}
	}

	private static void addIfNotJavaLang(DotName name, Set<String> result) {
		// StereotypesIndexer takes annotation types' getQualifiedName() - dots for a nested one
		String fqn = JdtStyleTypeNames.qualifiedName(name);
		if (!fqn.startsWith("java")) { // matches StereotypesIndexer.getAnnotationTypes's own filter
			result.add(fqn);
		}
	}

	/**
	 * Every superclass and interface in the class's hierarchy, recursively - by name, whether or not
	 * that type is itself present in {@code index} (a supertype outside of what got indexed, most
	 * commonly a JDK type, still has to count for a stereotype assignment that matches on it).
	 */
	public static Set<String> supertypesOf(ClassInfo classInfo, Index index) {
		Set<String> result = new LinkedHashSet<>();
		for (Type supertype : hierarchyOf(classInfo, index)) {
			// ASTUtils.getHierarchyTypesFqNamesBreadthFirstIterator: the binary name for a
			// supertype referenced with type arguments, the qualified name otherwise
			result.add(supertype.kind() == Type.Kind.PARAMETERIZED_TYPE ? supertype.name().toString() : JdtStyleTypeNames.qualifiedName(supertype.name()));
		}
		return result;
	}

	/**
	 * Every supertype, breadth first, each once, as it is first referenced.
	 */
	private static List<Type> hierarchyOf(ClassInfo classInfo, Index index) {
		List<Type> result = new ArrayList<>();
		Deque<Type> toVisit = new ArrayDeque<>(directSupertypesOf(classInfo));

		Set<DotName> visited = new LinkedHashSet<>();
		while (!toVisit.isEmpty()) {
			Type supertype = toVisit.poll();
			if (!visited.add(supertype.name())) {
				continue;
			}
			result.add(supertype);

			ClassInfo superInfo = index.getClassByName(supertype.name());
			if (superInfo != null) {
				toVisit.addAll(directSupertypesOf(superInfo));
			}
		}

		return result;
	}

	/**
	 * The superclass and interfaces as they are referenced - with their type arguments, which
	 * decide how the name is rendered. An interface's {@code java.lang.Object} superclass is left
	 * out: it only exists in bytecode, JDT has no superclass for an interface.
	 */
	private static List<Type> directSupertypesOf(ClassInfo classInfo) {
		List<Type> result = new ArrayList<>();
		if (classInfo.superClassType() != null && !classInfo.isInterface()) {
			result.add(classInfo.superClassType());
		}
		result.addAll(classInfo.interfaceTypes());
		return result;
	}



	/**
	 * Mirrors {@code ASTUtils.getMethodSignature(method, false)} exactly: the declaring class's
	 * simple name, the method name (a constructor is named after its class, as JDT names it), and
	 * JDT-style simple type names - {@code ClassWithMethods.methodWithAnnotations(String) : void}.
	 */
	public static String methodLabelOf(MethodInfo method) {
		return signature(method, JdtStyleTypeNames.simpleName(method.declaringClass().name()), JdtStyleTypeNames::name);
	}

	/**
	 * Mirrors {@code ASTUtils.getMethodSignature(method, true)}: binary names throughout. Used as
	 * the key a method is matched by - e.g. to the request mapping it declares - so it only has to
	 * be unambiguous among JAR-scanned elements, which it is, even where JDT's own rendering (of a
	 * type variable, say) differs.
	 */
	public static String methodSignatureOf(MethodInfo method) {
		return signature(method, method.declaringClass().name().toString(), JdtStyleTypeNames::binaryName);
	}

	/**
	 * The method's name as source (and JDT) has it - a constructor is named after its class, not
	 * {@code <init>}.
	 */
	public static String sourceName(MethodInfo method) {
		return method.isConstructor() ? JdtStyleTypeNames.simpleName(method.declaringClass().name()) : method.name();
	}

	private static String signature(MethodInfo method, String className, Function<Type, String> typeName) {
		String name = sourceName(method);
		String parameters = method.parameterTypes().stream().map(typeName).collect(Collectors.joining(", "));
		String returnType = method.isConstructor() ? typeName.apply(VoidType.VOID) : typeName.apply(method.returnType());
		return className + "." + name + "(" + parameters + ") : " + returnType;
	}


}
