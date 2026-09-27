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
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeFactory;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;

/**
 * {@link StructureElements} for a selected JAR dependency, backed by {@link JarStereotypeScanner}.
 * See {@code docs/structure-view-dependencies.md}.
 *
 * <p>{@code types()} filters the JAR's scanned classes down to the ones that currently match some
 * stereotype in the given catalog, live, on every call - so a change to the catalog's stereotype
 * definitions (a JSON catalog file edited, a source-defined stereotype added or removed anywhere in
 * the project or an included dependency) is reflected immediately, without needing to detect that
 * change or rescan the JAR: only the (comparatively expensive, and catalog-independent) scan of the
 * JAR's classes into raw elements is cached - by {@link JarDependencySource}, keyed by the JAR's own
 * identity - never the filtered result.
 *
 * @author Martin Lippert
 */
public class JarStructureElements implements StructureElements {

	private final List<StereotypeClassElement> scannedTypes;
	private final JarStereotypeFactory factory;

	/**
	 * @param scannedTypes every class {@link JarStereotypeScanner} found in the JAR, unfiltered -
	 *        see {@link JarDependencySource} for where this comes from and how it is cached
	 * @param catalog the catalog of the tree this JAR is being included in
	 */
	public JarStructureElements(List<StereotypeClassElement> scannedTypes, AbstractStereotypeCatalog catalog) {
		this.scannedTypes = scannedTypes;
		this.factory = new JarStereotypeFactory(catalog);
	}

	@Override
	public List<StereotypeClassElement> types() {
		return scannedTypes.stream().filter(factory::matchesAnyStereotype).toList();
	}

	@Override
	public StereotypePackageElement mainApplicationPackage() {
		// never consulted: a composite never asks a dependency for its main package, only the
		// host's (see CompositeStructureElements) - defensive fallback only
		return new StereotypePackageElement("", null, true);
	}

	@Override
	public StereotypePackageElement packageNode(String packageName) {
		// never consulted either, for the same reason - a composite always uses the host's
		return new StereotypePackageElement(packageName, null);
	}

	@Override
	public String methodLabel(StereotypeMethodElement method, StereotypeClassElement type) {
		// no live index to consult a request-mapping element from - the label computed at scan
		// time is all there is, matching SnapshotStructureElements' own reasoning
		return method.getMethodLabel();
	}

	@Override
	public List<StructureMember> membersOf(StereotypeClassElement type) {
		// no source-backed bean data exists for a type read out of a JAR
		return List.of();
	}

	@Override
	public StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> stereotypeFactory() {
		return factory;
	}

}
