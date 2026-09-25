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
 * <p>{@link IndexStructureElements} - today's only implementation - reads from the live
 * {@code SpringMetamodelIndex}. A second implementation reading from a captured baseline snapshot,
 * used to diff against instead of a rendered tree, is the point of
 * {@code docs/structure-diff-elements.md}; a third, composing several projects' elements, is the
 * point of {@code docs/structure-view-dependencies.md}. Neither exists yet - this interface is the
 * seam both build on.
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
	 * The project's main application package - the tree's root label and the base that type labels
	 * are abbreviated against ({@code StructureViewUtil.abbreviate}).
	 */
	StereotypePackageElement mainApplicationPackage();

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

}
