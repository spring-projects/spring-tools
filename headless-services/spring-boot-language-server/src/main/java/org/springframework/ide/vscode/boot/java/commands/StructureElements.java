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

import java.util.List;

import org.jmolecules.stereotype.api.StereotypeFactory;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;

/**
 * Everything the structure view's tree builders ({@link JMoleculesStructureView},
 * {@link ModulithStructureView} and the providers/label providers they use) read about a project's
 * stereotype elements, abstracted from where those elements actually come from.
 *
 * <p>{@link IndexStructureElements} reads from the live {@code SpringMetamodelIndex};
 * {@link SnapshotStructureElements} from a captured baseline snapshot, diffed against instead of a
 * rendered tree ({@code docs/structure-diff-elements.md}); {@link CompositeStructureElements}
 * composes a project's elements with those of its included dependencies
 * ({@code docs/structure-view-dependencies.md}).
 *
 * @author Martin Lippert
 */
public interface StructureElements {

	/**
	 * All of the project's indexed types, unfiltered - the tree builders apply their own
	 * package-prefix filtering on top, so this deliberately doesn't take a package to filter by.
	 */
	List<StereotypeClassElement> types();

	/**
	 * The project's main application package - the package of its {@code @SpringBootApplication}
	 * class. What the Spring Modulith tree abbreviates type labels against, and what a baseline
	 * snapshot records; the tree built from stereotypes is rooted in {@link #rootPackages()} instead.
	 */
	StereotypePackageElement mainApplicationPackage();

	/**
	 * The packages the tree built from stereotypes has below its application node - the top-most
	 * packages that are not empty among {@link #types()} (see
	 * {@link StructureViewUtil#identifyRootPackages}), ordered by name. Every type is in or below
	 * exactly one of them.
	 */
	default List<StereotypePackageElement> rootPackages() {
		List<String> typeNames = types().stream().map(StereotypeClassElement::getType).toList();
		return StructureViewUtil.identifyRootPackages(typeNames).stream().map(this::packageNode).toList();
	}

	/**
	 * The package node for the given package name - used to render package nodes, and to resolve an
	 * application module's base package to one.
	 */
	StereotypePackageElement packageNode(String packageName);

	/**
	 * The label to render for the given method - its request-mapping label when it is a mapping
	 * handler, its plain method label otherwise. See {@code StructureViewUtil.getMethodLabel}.
	 */
	String methodLabel(StereotypeMethodElement method, StereotypeClassElement type);

	/**
	 * The members the given type contributes beyond its own {@link StereotypeMethodElement}s. See
	 * {@link StructureViewUtil#membersOf}.
	 */
	List<StructureMember> membersOf(StereotypeClassElement type);

	/**
	 * The stereotype factory to resolve a package's, type's or method's stereotypes with.
	 */
	StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> stereotypeFactory();

	/**
	 * Whether only those of {@link #types()} that end up with a stereotype are to be shown - true
	 * for a JAR dependency, so a library's many plain classes don't flood the tree's "Others", where
	 * a project's own unmatched types go. Decided by whoever composes the tree
	 * ({@link CompositeStructureElements}), since a type's stereotypes can come from outside the
	 * part it belongs to (the host's package of the same name).
	 */
	default boolean showsOnlyStereotypedTypes() {
		return false;
	}

	/**
	 * The JDT binding key of a type that has no location of its own - one read from a JAR - by
	 * which the IDE's Java tooling can find and open it instead; {@code null} otherwise.
	 */
	default String bindingKeyOf(StereotypeClassElement type) {
		return null;
	}

	/**
	 * As {@link #bindingKeyOf(StereotypeClassElement)}, for a method.
	 */
	default String bindingKeyOf(StereotypeMethodElement method) {
		return null;
	}

}
