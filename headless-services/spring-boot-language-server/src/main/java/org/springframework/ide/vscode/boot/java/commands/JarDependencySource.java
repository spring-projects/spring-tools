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
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.Indexer;
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.beans.JarConfigurationPropertiesScanner;
import org.springframework.ide.vscode.boot.java.data.JarDataRepositoryScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.commons.java.ClasspathDependencyResolver;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.commons.protocol.java.Classpath.CPE;

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

	private final ClasspathDependencyResolver resolver;
	private final ConcurrentHashMap<String, ScanResult> scannedJars = new ConcurrentHashMap<>();

	public JarDependencySource(ClasspathDependencyResolver resolver) {
		this.resolver = resolver;
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

		if (dependency.kind() != DependencyDescriptor.Kind.JAR) {
			return null;
		}

		File jarFile = new File(dependency.location());
		if (!jarFile.isFile()) {
			log.warn("cannot scan structure dependency '{}': '{}' is not a file", dependency.id(), jarFile);
			return null;
		}

		ScanResult scanned = scannedJars.computeIfAbsent(cacheKey(jarFile), key -> scan(including, jarFile));
		JarStructureElements elements = new JarStructureElements(scanned.types(), scanned.members(), catalog);

		// worth an INFO on every request, not just on failure: this is the only place that shows
		// whether a JAR selection is actually doing something - scanned classes found none matching
		// the current catalog looks identical to "elementsOf was never even called" otherwise
		int matching = elements.types().size();
		log.info("structure dependency '{}': {} of {} scanned class(es) in '{}' match the current catalog", dependency.id(), matching,
				scanned.types().size(), jarFile.getName());

		return elements;
	}

	/**
	 * Scans {@code jarFile}'s own classes, resolving their annotations and supertypes against a
	 * combined index built over every JAR on {@code including}'s classpath - not just
	 * {@code jarFile} in isolation, since an annotation's own meta-annotations are often declared
	 * several JARs away from where the annotation itself is used (Spring's own annotations are the
	 * textbook example: {@code @RestController} is meta-annotated with {@code @Controller}, which
	 * lives in a different JAR). Every JAR {@code including} depends on is guaranteed to be
	 * resolvable on its own classpath, so this is always sufficient.
	 */
	private ScanResult scan(IJavaProject including, File jarFile) {
		Indexer indexer = new Indexer();
		Set<DotName> ownClasses = null;
		Set<File> indexed = new HashSet<>();

		Collection<CPE> classpathEntries;
		try {
			// IClasspath.getClasspathEntries() declares a checked Exception that every caller in
			// this codebase catches locally rather than propagating - matching that here too
			classpathEntries = including.getClasspath().getClasspathEntries();
		} catch (Exception e) {
			log.error("cannot read the classpath of '{}' to scan structure dependency JAR '{}'", including.getElementName(), jarFile, e);
			classpathEntries = List.of();
		}

		for (CPE cpe : classpathEntries) {
			if (!Classpath.isBinary(cpe) || cpe.isSystem()) {
				continue;
			}

			File file = IClasspathUtil.binaryLocation(cpe).getAbsoluteFile();
			if (!file.isFile() || !indexed.add(file)) {
				continue;
			}

			Set<DotName> classesOfThisFile = JarStereotypeScanner.indexInto(indexer, file);
			if (file.equals(jarFile.getAbsoluteFile())) {
				ownClasses = classesOfThisFile;
			}
		}

		if (ownClasses == null) {
			// the JAR wasn't found among including's own classpath entries at all - a stale
			// selection, or a discovery/classpath mismatch; index it directly so something is
			// still shown, even though cross-JAR meta-annotations may not all resolve
			log.warn("structure dependency JAR '{}' is not on the classpath of '{}' - scanning it in isolation", jarFile,
					including.getElementName());
			ownClasses = JarStereotypeScanner.indexInto(indexer, jarFile);
		}

		Index index = indexer.complete();
		List<StereotypeClassElement> scanned = JarStereotypeScanner.ownClassesOf(ownClasses, index);
		log.info("scanned structure dependency JAR '{}': {} of its {} class(es) found (annotation types, module-info excluded)",
				jarFile.getName(), scanned.size(), ownClasses.size());

		return new ScanResult(scanned, membersOf(scanned, index));
	}

	/**
	 * The "members" (properties, query methods) some of the scanned types contribute beyond their
	 * own stereotype-matched methods - see {@link JarConfigurationPropertiesScanner} and
	 * {@link JarDataRepositoryScanner}, the JAR/bytecode counterparts of
	 * {@code ConfigurationPropertiesIndexer} and {@code DataRepositoryIndexer}. Computed once here,
	 * alongside the rest of the (JAR-identity-cached, catalog-independent) scan: unlike stereotype
	 * matching, neither of these depends on the tree's catalog at all - a class either is a
	 * {@code @ConfigurationProperties} class or a repository, a fact of its own bytecode, not of
	 * what stereotypes happen to be defined right now.
	 */
	private Map<StereotypeClassElement, List<StructureMember>> membersOf(List<StereotypeClassElement> scanned, Index index) {
		Map<StereotypeClassElement, List<StructureMember>> result = new IdentityHashMap<>();

		for (StereotypeClassElement element : scanned) {
			ClassInfo classInfo = index.getClassByName(DotName.createSimple(element.getType()));
			if (classInfo == null) {
				continue;
			}

			List<StructureMember> members = new ArrayList<>();
			members.addAll(JarConfigurationPropertiesScanner.membersOf(classInfo, element.getAnnotationTypes()));
			members.addAll(JarDataRepositoryScanner.membersOf(classInfo, element));

			if (!members.isEmpty()) {
				result.put(element, members);
			}
		}

		return result;
	}

	private static String cacheKey(File jarFile) {
		return jarFile.getAbsolutePath() + "@" + jarFile.lastModified() + ":" + jarFile.length();
	}

	private record ScanResult(List<StereotypeClassElement> types, Map<StereotypeClassElement, List<StructureMember>> members) {
	}

}
