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

import java.util.Collection;
import java.util.TreeSet;

import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.api.StereotypeFactory;
import org.jmolecules.stereotype.api.Stereotypes;
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.jmolecules.stereotype.catalog.support.StereotypeDetector.AnalysisLevel;
import org.jmolecules.stereotype.catalog.support.StereotypeMatcher;

/**
 * A {@link StereotypeFactory} that matches {@link JarStereotypeScanner}-derived elements against a
 * catalog by annotation/{@code implements} assignment - the same detection
 * {@link IndexBasedStereotypeFactory} does for source-indexed elements, since a JAR element's
 * {@code annotationTypes}/{@code supertypes} are computed to mean the same thing (see
 * {@link JarStereotypeScanner}).
 *
 * <p>No package-level detection: a JAR element's package is never queried for its own stereotypes
 * here - the host's package of the same name is consulted instead, one level up, by
 * {@code CompositeStructureElements}, since that is the package a composed tree actually renders.
 *
 * @author Martin Lippert
 */
public class JarStereotypeFactory implements StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> {

	// mirrors IndexBasedStereotypeFactory.STEREOTYPE_MATCHER exactly - kept separate rather than
	// shared, since these two factories otherwise have nothing else in common (no live index)
	private static final StereotypeMatcher<StereotypeClassElement, StereotypeAnnotatedElement> STEREOTYPE_MATCHER = StereotypeMatcher
			.<StereotypeClassElement, StereotypeAnnotatedElement> isAnnotatedWith((element, fqn) -> element.isAnnotatedWith(fqn))
			.orImplements((type, fqn) -> type.doesImplement(fqn));

	private final AbstractStereotypeCatalog catalog;

	public JarStereotypeFactory(AbstractStereotypeCatalog catalog) {
		this.catalog = catalog;
	}

	@Override
	public Stereotypes fromPackage(StereotypePackageElement pkg) {
		return Stereotypes.NONE;
	}

	@Override
	public Stereotypes fromType(StereotypeClassElement type) {
		return new Stereotypes(stereotypesOfType(type));
	}

	@Override
	public Stereotypes fromMethod(StereotypeMethodElement method) {
		return new Stereotypes(catalog.getAnnotationBasedStereotypes(method, AnalysisLevel.DIRECT, STEREOTYPE_MATCHER));
	}

	/**
	 * A type's stereotypes come from two, independently-queried catalog buckets - definitions
	 * assigned by {@code implements} and definitions assigned by annotation - mirroring
	 * {@code IndexBasedStereotypeFactory.fromTypeInternal} exactly; missing either half would leave
	 * whichever kind of assignment silently unmatched.
	 */
	private Collection<Stereotype> stereotypesOfType(StereotypeClassElement type) {
		Collection<Stereotype> result = new TreeSet<>();
		result.addAll(catalog.getTypeBasedStereotypes(type, AnalysisLevel.DIRECT, STEREOTYPE_MATCHER));
		result.addAll(catalog.getAnnotationBasedStereotypes(type, AnalysisLevel.DIRECT, STEREOTYPE_MATCHER));
		return result;
	}

	/**
	 * Whether the given type matches some stereotype in the catalog - used to filter which of a
	 * JAR's classes are worth turning into tree nodes at all (see {@code JarStructureElements}).
	 */
	public boolean matchesAnyStereotype(StereotypeClassElement type) {
		return !stereotypesOfType(type).isEmpty();
	}

}
