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

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jmolecules.stereotype.api.StereotypeFactory;
import org.jmolecules.stereotype.api.Stereotypes;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;

/**
 * A project's own {@link StructureElements} together with those of the dependencies included in its
 * tree - so that the tree builders see the dependencies' types as if they were the project's own.
 * See {@code docs/structure-view-dependencies.md}.
 *
 * <p>Only ever used for display: a composed tree is never diffed, so this never meets
 * {@link StructureSnapshotBuilder}.
 *
 * <p>Whatever describes the tree as a whole - the main application package (the root label and the
 * base type labels are abbreviated against) and package nodes - comes from the host project. Whatever
 * describes a single type - its members, its method labels, its stereotypes - comes from the part the
 * type came from, since those are looked up in that part's project; a dependency's type additionally
 * gets the stereotypes of the host's package of the same name, if any. All parts are expected to
 * resolve stereotypes against the same catalog, so the dependencies' types get grouped by the same
 * stereotypes as the host's.
 *
 * @author Martin Lippert
 */
public class CompositeStructureElements implements StructureElements {

	private final StructureElements host;
	private final List<StereotypeClassElement> types;

	// by identity: the tree builders hand back the very instances types() returned
	private final Map<Object, StructureElements> owners;

	private final StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> factory;

	public CompositeStructureElements(StructureElements host, List<StructureElements> dependencies) {
		this.host = host;
		this.types = new ArrayList<>();
		this.owners = new IdentityHashMap<>();

		List<StructureElements> parts = new ArrayList<>();
		parts.add(host);
		parts.addAll(dependencies);

		for (StructureElements part : parts) {
			for (StereotypeClassElement type : part.types()) {
				if (owners.putIfAbsent(type, part) == null) {
					types.add(type);
					type.getMethods().forEach(method -> owners.putIfAbsent(method, part));
				}
			}
		}

		this.factory = new CompositeStereotypeFactory();
	}

	@Override
	public List<StereotypeClassElement> types() {
		return Collections.unmodifiableList(types);
	}

	@Override
	public StereotypePackageElement mainApplicationPackage() {
		return host.mainApplicationPackage();
	}

	@Override
	public StereotypePackageElement packageNode(String packageName) {
		return host.packageNode(packageName);
	}

	@Override
	public String methodLabel(StereotypeMethodElement method, StereotypeClassElement type) {
		// by the method first: the contextual type isn't necessarily the one declaring it - in a
		// group of methods across types, for example
		StructureElements owner = owners.containsKey(method) ? owners.get(method) : ownerOf(type);
		return owner.methodLabel(method, type);
	}

	@Override
	public List<StructureMember> membersOf(StereotypeClassElement type) {
		return ownerOf(type).membersOf(type);
	}

	@Override
	public StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> stereotypeFactory() {
		return factory;
	}

	private StructureElements ownerOf(Object element) {
		return owners.getOrDefault(element, host);
	}

	private class CompositeStereotypeFactory implements StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> {

		@Override
		public Stereotypes fromPackage(StereotypePackageElement pkg) {
			return host.stereotypeFactory().fromPackage(pkg);
		}

		/**
		 * A type's own stereotypes, plus those of its package - which, for a dependency's type, can
		 * also be declared by the host (a package split across both, with the host's
		 * {@code package-info} carrying the stereotype).
		 */
		@Override
		public Stereotypes fromType(StereotypeClassElement type) {
			StructureElements owner = ownerOf(type);
			Stereotypes stereotypes = owner.stereotypeFactory().fromType(type);

			if (owner != host) {
				StereotypePackageElement hostPackage = host.packageNode(StructureViewUtil.getPackage(type.getType()));

				// no annotation types: a package the host doesn't have - just a placeholder
				if (hostPackage != null && hostPackage.getAnnotationTypes() != null) {
					stereotypes = stereotypes.and(host.stereotypeFactory().fromPackage(hostPackage));
				}
			}

			return stereotypes;
		}

		@Override
		public Stereotypes fromMethod(StereotypeMethodElement method) {
			return ownerOf(method).stereotypeFactory().fromMethod(method);
		}

	}

}
