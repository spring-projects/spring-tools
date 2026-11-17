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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.springframework.ide.vscode.commons.protocol.spring.CanonicalNameSets;

/**
 * The same {@link StereotypeClassElement}s as {@link JarStereotypeScanner#ownClassesOf}, worked out
 * from {@link ClassFacts} instead of Jandex {@code ClassInfo}s: what a class's elements say about
 * its annotations and supertypes needs nothing but names, so the great majority of a JAR's classes
 * never have to be read in full. This is what a scan spends nearly all of its time on.
 *
 * <p>A {@code ClassInfo} (from the {@code IndexView} given) is only asked for where a class has
 * annotated methods - their labels and signatures are rendered by {@link JarStereotypeScanner}, one
 * implementation for both - so the classes that need it are loaded there, on demand.
 *
 * <p>Same semantics as {@link JarStereotypeScanner}, rule for rule (meta-annotation expansion, the
 * {@code java*} and Lombok filters, the supertype walk with its asymmetry, which classes are left
 * out); {@code JarFactsScannerParityTest} holds the two to the same answers over real JARs.
 *
 * <p>One instance per JAR scan: it shares what it works out between the classes, and is not for
 * several threads.
 *
 * @author Martin Lippert
 */
public class JarFactsScanner {

	private static final String OBJECT = Object.class.getName();

	/**
	 * @param facts null if the class is not found (or not readable)
	 */
	public record ScannedClass(StereotypeClassElement element, ClassFacts facts) {
	}

	private final Function<String, ClassFacts> classes;
	private final IndexView index;
	private final JarStereotypeScanner.ScanCache methodCache = new JarStereotypeScanner.ScanCache();

	private final Map<String, String> qualifiedNames = new HashMap<>();
	private final Map<String, String> names = new HashMap<>();
	private final Map<String, Set<String>> supertypes = new HashMap<>();
	private final Map<String, Set<String>> supertypeAnnotations = new HashMap<>();

	private int skipped;

	/**
	 * @param classes the class facts by binary name, across the whole classpath scanned against
	 * @param index for the classes that have annotated methods - see above
	 */
	public JarFactsScanner(Function<String, ClassFacts> classes, IndexView index) {
		this.classes = classes;
		this.index = index;
	}

	/**
	 * How many of the classes given to {@link #ownClassesOf} were not found, or not readable.
	 */
	public int skipped() {
		return skipped;
	}

	/**
	 * As {@link JarStereotypeScanner#ownClassesOf(Set, IndexView, Map)}, for the classes with the
	 * given binary names - one element per class, unfiltered by any stereotype catalog, with the
	 * facts it was made from. The JDT binding key of every type and method element is recorded
	 * in {@code bindingKeys}.
	 */
	public List<ScannedClass> ownClassesOf(Collection<String> ownClasses, Map<Object, String> bindingKeys) {
		List<ScannedClass> result = new ArrayList<>(ownClasses.size());

		for (String name : ownClasses) {
			ClassFacts facts = classes.apply(name);
			if (facts == null) {
				skipped++;
				continue;
			}

			if (isPackageInfo(facts.name())) {
				Set<String> annotationTypes = new LinkedHashSet<>();
				for (String annotation : facts.annotations()) {
					annotationTypes.add(qualifiedName(annotation));
				}
				StereotypeClassElement element = new StereotypeClassElement(facts.name(), null, Set.of(), annotationTypes, null);
				bindingKeys.put(element, JarBindingKeys.ofClass(facts.name()));
				result.add(new ScannedClass(element, null));
				continue;
			}

			if (!isSourceLevelType(facts)) {
				continue;
			}

			StereotypeClassElement element = new StereotypeClassElement(name, null, supertypesWithoutObjectOf(facts), annotationTypesOf(facts), null);
			bindingKeys.put(element, JarBindingKeys.ofClass(facts.name()));

			if (facts.hasAnnotatedMethod()) {
				ClassInfo classInfo = index.getClassByName(DotName.createSimple(facts.name()));
				if (classInfo != null) {
					JarStereotypeScanner.addAnnotatedMethods(element, classInfo, index, methodCache, bindingKeys);
				}
			}

			result.add(new ScannedClass(element, facts));
		}

		return result;
	}

	private static boolean isPackageInfo(String name) {
		return name.equals("package-info") || name.endsWith(".package-info");
	}

	/**
	 * {@link JarStereotypeScanner#isSourceLevelType}: not an annotation type, not a module, not
	 * synthetic and not anonymous.
	 */
	public static boolean isSourceLevelType(ClassFacts facts) {
		return (facts.flags() & (ClassFacts.ACC_ANNOTATION | ClassFacts.ACC_MODULE | ClassFacts.ACC_SYNTHETIC)) == 0 && !facts.anonymous();
	}

	/**
	 * The class's own direct annotations expanded through their meta-annotation chain, without
	 * those of its supertypes and nothing filtered -
	 * {@link JarStereotypeScanner#ownAnnotationTypesOf(ClassInfo, IndexView)}.
	 */
	public Set<String> ownAnnotationTypesOf(ClassFacts facts) {
		Set<String> result = new LinkedHashSet<>();
		Set<String> visited = new HashSet<>();
		for (String annotation : facts.annotations()) {
			addWithMetaAnnotations(annotation, result, visited, false);
		}
		return result;
	}

	private Set<String> annotationTypesOf(ClassFacts facts) {
		Set<String> result = new LinkedHashSet<>();
		Set<String> visited = new HashSet<>();
		for (String annotation : facts.annotations()) {
			addWithMetaAnnotations(annotation, result, visited, true);
		}
		result.addAll(hierarchyContributionOf(facts, supertypeAnnotations, (supertype, parameterized, collected) -> {
			ClassFacts supertypeFacts = classes.apply(supertype);
			if (supertypeFacts != null) {
				for (String annotation : supertypeFacts.annotations()) {
					addIfNotJavaLang(annotation, collected);
				}
			}
		}));
		return result;
	}

	private void addWithMetaAnnotations(String annotation, Set<String> result, Set<String> visited, boolean withoutJava) {
		if (!visited.add(annotation)) {
			return; // a meta-annotation cycle - nothing further to add
		}

		if (withoutJava) {
			addIfNotJavaLang(annotation, result);
		}
		else {
			result.add(qualifiedName(annotation));
		}

		ClassFacts annotationFacts = classes.apply(annotation);
		if (annotationFacts != null) {
			for (String metaAnnotation : annotationFacts.annotations()) {
				addWithMetaAnnotations(metaAnnotation, result, visited, withoutJava);
			}
		}
	}

	private void addIfNotJavaLang(String annotation, Set<String> result) {
		String fqn = qualifiedName(annotation);
		if (!fqn.startsWith("java") && !JarStereotypeScanner.LOMBOK_GENERATED.equals(fqn)) {
			result.add(fqn);
		}
	}

	private Set<String> supertypesWithoutObjectOf(ClassFacts facts) {
		return hierarchyContributionOf(facts, supertypes, (supertype, parameterized, collected) -> {
			if (!OBJECT.equals(supertype)) {
				// the binary name for a supertype referenced with type arguments, the qualified name otherwise
				collected.add(parameterized ? name(supertype) : qualifiedName(supertype));
			}
		});
	}

	private interface Contribution {
		void accept(String supertype, boolean parameterized, Set<String> collected);
	}

	/**
	 * What the class's whole hierarchy contributes, each class working it out once -
	 * {@code JarStereotypeScanner.hierarchyContributionOf}.
	 */
	private Set<String> hierarchyContributionOf(ClassFacts facts, Map<String, Set<String>> known, Contribution contribution) {
		Set<String> result = known.get(facts.name());
		if (result != null) {
			return result;
		}

		known.put(facts.name(), Set.of()); // what a (corrupt) hierarchy that includes itself comes upon
		Set<String> collected = new HashSet<>();

		// an interface's java.lang.Object superclass is left out: only in bytecode, JDT has none
		if (facts.superName() != null && !facts.isInterface()) {
			contributionOf(facts.superName(), facts.superParameterized(), known, contribution, collected);
		}
		for (int i = 0; i < facts.interfaces().length; i++) {
			contributionOf(facts.interfaces()[i], facts.interfaceParameterized()[i], known, contribution, collected);
		}

		result = CanonicalNameSets.of(collected);
		known.put(facts.name(), result);
		return result;
	}

	private void contributionOf(String supertype, boolean parameterized, Map<String, Set<String>> known, Contribution contribution,
			Set<String> collected) {
		contribution.accept(supertype, parameterized, collected);

		ClassFacts supertypeFacts = classes.apply(supertype);
		if (supertypeFacts != null) {
			collected.addAll(hierarchyContributionOf(supertypeFacts, known, contribution));
		}
	}

	private String qualifiedName(String binaryName) {
		String known = qualifiedNames.get(binaryName);
		if (known == null) {
			known = name(JdtStyleTypeNames.qualifiedName(binaryName));
			qualifiedNames.put(binaryName, known);
		}
		return known;
	}

	private String name(String name) {
		return names.computeIfAbsent(name, key -> key);
	}

}
