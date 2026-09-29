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
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.springframework.ide.vscode.boot.java.stereotypes.IndexBasedStereotypeFactory;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * {@link StructureElements} backed by the live, in-memory Spring index (via
 * {@link CachedSpringMetamodelIndex}) - the one every live structure tree is built from, and each
 * part of a {@link CompositeStructureElements}.
 *
 * <p>Deliberately a thin, side-effect-free view: the stereotype factory it hands back
 * ({@link #stereotypeFactory()}) is supplied already built - and, if applicable, already had its
 * source-defined stereotype definitions registered - by whoever constructs this class
 * (usually via {@link #of}), so that decision (and the
 * {@code disable-source-defined-stereotypes} toggle behind it) stays in one place rather than
 * being duplicated here.
 *
 * @author Martin Lippert
 */
public class IndexStructureElements implements StructureElements {

	private final IJavaProject project;
	private final CachedSpringMetamodelIndex springIndex;
	private final StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> factory;

	public IndexStructureElements(IJavaProject project, CachedSpringMetamodelIndex springIndex,
			StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> factory) {
		this.project = project;
		this.springIndex = springIndex;
		this.factory = factory;
	}

	/**
	 * The elements of the given project, with a stereotype factory on the given catalog - which, if
	 * enabled, gets the project's source-defined stereotype definitions registered right away.
	 */
	public static IndexStructureElements of(IJavaProject project, CachedSpringMetamodelIndex springIndex, AbstractStereotypeCatalog catalog) {
		var factory = new IndexBasedStereotypeFactory(catalog, project, springIndex);

		if (StructureViewUtil.hasSourceDefinedStereotypesEnabled()) {
			factory.registerStereotypeDefinitions();
		}

		return new IndexStructureElements(project, springIndex, factory);
	}

	@Override
	public List<StereotypeClassElement> types() {
		return springIndex.getClassesForProject(project.getElementName());
	}

	@Override
	public StereotypePackageElement mainApplicationPackage() {
		return StructureViewUtil.identifyMainApplicationPackage(project, springIndex);
	}

	@Override
	public StereotypePackageElement packageNode(String packageName) {
		return StructureViewUtil.findPackageNode(packageName, project, springIndex);
	}

	@Override
	public String methodLabel(StereotypeMethodElement method, StereotypeClassElement type) {
		return StructureViewUtil.getMethodLabel(project, springIndex, method, type);
	}

	@Override
	public List<StructureMember> membersOf(StereotypeClassElement type) {
		if (type.getLocation() == null) {
			return List.of();
		}

		String docUri = type.getLocation().getUri();

		return StructureViewUtil.membersOf(springIndex, type).stream()
				.map(symbolElement -> StructureMember.of(symbolElement, docUri))
				.toList();
	}

	@Override
	public StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> stereotypeFactory() {
		return factory;
	}

}
