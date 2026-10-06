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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.catalog.StereotypeDefinition;
import org.jmolecules.stereotype.catalog.StereotypeDefinition.Assignment;
import org.jmolecules.stereotype.catalog.StereotypeDefinition.Assignment.Type;
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.jmolecules.stereotype.catalog.support.StereotypeDetector.AnalysisLevel;

/**
 * The stereotypes of a catalog by what they are assigned to - the annotation an element has to
 * carry, or the type it has to be or implement - so that resolving an element's stereotypes takes
 * a lookup per annotation and supertype the element has, rather than matching the element against
 * every definition of the catalog, which {@link AbstractStereotypeCatalog#getDefinitions()} hands
 * out as a freshly sorted copy on every call.
 *
 * <p>Finds exactly what {@code getAnnotationBasedStereotypes} and {@code getTypeBasedStereotypes}
 * of the catalog find on {@link AnalysisLevel#DIRECT}, with the matcher both
 * {@link IndexBasedStereotypeFactory} and {@link JarStereotypeFactory} use:
 * {@link StereotypeAnnotatedElement#isAnnotatedWith} and {@link StereotypeClassElement#doesImplement}.
 *
 * <p>Built from the catalog on first use, so it contains whatever got registered with the catalog
 * until then - the source-defined stereotypes of every part of a tree are registered before the
 * tree is built - but nothing registered afterwards.
 *
 * @author Martin Lippert
 */
public class StereotypeAssignmentIndex {

	private final AbstractStereotypeCatalog catalog;
	private volatile Assignments assignments;

	public StereotypeAssignmentIndex(AbstractStereotypeCatalog catalog) {
		this.catalog = catalog;
	}

	/**
	 * The stereotypes assigned to an annotation the given element carries.
	 *
	 * <p>Collected the way {@link org.jmolecules.stereotype.api.Stereotypes} keep them, sorted and
	 * told apart by their natural order - never by {@code hashCode}, which a stereotype computes from
	 * its groups, and which fails for one without groups and without a dot in its identifier.
	 */
	public Collection<Stereotype> annotationBased(StereotypeAnnotatedElement element) {
		return lookup(assignments().byAnnotation(), element.getAnnotationTypes());
	}

	/**
	 * The stereotypes assigned to the given type itself, or to one of its supertypes - collected as
	 * {@link #annotationBased} collects them.
	 */
	public Collection<Stereotype> typeBased(StereotypeClassElement type) {
		Set<Stereotype> result = new TreeSet<>(lookup(assignments().byImplements(), List.of(type.getType())));
		result.addAll(lookup(assignments().byImplements(), type.getSupertypes()));
		return result;
	}

	private static Collection<Stereotype> lookup(Map<String, List<Stereotype>> stereotypes, Collection<String> keys) {
		if (keys == null || keys.isEmpty()) {
			return List.of();
		}

		Set<Stereotype> result = new TreeSet<>();
		for (String key : keys) {
			List<Stereotype> found = stereotypes.get(key);
			if (found != null) {
				result.addAll(found);
			}
		}
		return result;
	}

	private Assignments assignments() {
		Assignments result = assignments;
		if (result == null) {
			synchronized (this) {
				result = assignments;
				if (result == null) {
					result = Assignments.of(catalog);
					assignments = result;
				}
			}
		}
		return result;
	}

	private record Assignments(Map<String, List<Stereotype>> byAnnotation, Map<String, List<Stereotype>> byImplements) {

		static Assignments of(AbstractStereotypeCatalog catalog) {
			Map<String, List<Stereotype>> byAnnotation = new HashMap<>();
			Map<String, List<Stereotype>> byImplements = new HashMap<>();

			for (StereotypeDefinition definition : catalog.getDefinitions()) {
				for (Assignment assignment : definition.getAssignments()) {
					if (assignment.hasType(Type.IS_ANNOTATED)) {
						byAnnotation.computeIfAbsent(assignment.getTarget(), target -> new ArrayList<>()).add(definition.getStereotype());
					}
					else if (assignment.hasType(Type.IMPLEMENTS)) {
						byImplements.computeIfAbsent(assignment.getTarget(), target -> new ArrayList<>()).add(definition.getStereotype());
					}
				}
			}

			return new Assignments(byAnnotation, byImplements);
		}
	}

}
