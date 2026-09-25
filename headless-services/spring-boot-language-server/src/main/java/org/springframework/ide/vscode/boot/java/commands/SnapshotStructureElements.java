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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.api.StereotypeFactory;
import org.jmolecules.stereotype.api.Stereotypes;
import org.springframework.ide.vscode.boot.java.commands.StructureElementSnapshot.SnapshotMethod;
import org.springframework.ide.vscode.boot.java.commands.StructureElementSnapshot.SnapshotStereotype;
import org.springframework.ide.vscode.boot.java.commands.StructureElementSnapshot.SnapshotType;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;

/**
 * {@link StructureElements} reconstructed from a {@link StructureElementSnapshot}, so a baseline
 * can be diffed by rebuilding the exact same kind of tree the live index builds, with the
 * *current* presentation settings - the whole point of
 * {@code docs/structure-diff-elements.md}'s rework.
 *
 * <p>The reconstructed {@link StereotypeClassElement}/{@link StereotypeMethodElement} instances
 * carry no location, no supertypes and no annotations - the elements a snapshot restores stand for
 * source that may no longer exist in that exact form, so there is nothing genuine to give them
 * there. Nothing in the tree-building path needs any of that, since stereotypes come from
 * {@link #stereotypeFactory()} - never from catalog detection against annotations - and every
 * caller of {@link StereotypeClassElement#getLocation()}/{@link StereotypeMethodElement#getLocation()}
 * along this path only ever feeds it into {@code toComparableNode}, which drops locations anyway.
 *
 * @author Martin Lippert
 */
public class SnapshotStructureElements implements StructureElements {

	private final List<StereotypeClassElement> types;
	private final Map<StereotypeClassElement, List<StructureMember>> membersByType;
	private final StereotypePackageElement mainApplicationPackage;
	private final SnapshotStereotypeFactory factory;

	public SnapshotStructureElements(StructureElementSnapshot snapshot) {
		Map<String, SnapshotStereotype> definitionsByIdentifier = new LinkedHashMap<>();
		for (SnapshotStereotype definition : snapshot.stereotypeDefinitions()) {
			definitionsByIdentifier.put(definition.identifier(), definition);
		}

		Map<Object, Stereotypes> stereotypesByElement = new IdentityHashMap<>();
		Map<StereotypeClassElement, List<StructureMember>> membersByType = new IdentityHashMap<>();
		List<StereotypeClassElement> types = new ArrayList<>();

		for (SnapshotType snapshotType : snapshot.types()) {
			StereotypeClassElement type = new StereotypeClassElement(snapshotType.fqn(), null, Set.of(), Set.of(), snapshotType.contentHash());
			stereotypesByElement.put(type, toStereotypes(snapshotType.stereotypes(), definitionsByIdentifier));

			for (SnapshotMethod snapshotMethod : snapshotType.methods()) {
				StereotypeMethodElement method = new StereotypeMethodElement(snapshotMethod.name(), snapshotMethod.label(),
						snapshotMethod.signature(), null, Set.of(), snapshotMethod.contentHash());
				type.addChild(method);
				stereotypesByElement.put(method, toStereotypes(snapshotMethod.stereotypes(), definitionsByIdentifier));
			}

			membersByType.put(type, snapshotType.members().stream()
					.map(member -> new StructureMember(member.label(), null, member.contentHash()))
					.toList());

			types.add(type);
		}

		this.types = List.copyOf(types);
		this.membersByType = membersByType;
		this.mainApplicationPackage = new StereotypePackageElement(snapshot.mainApplicationPackage(), null, true);
		this.factory = new SnapshotStereotypeFactory(stereotypesByElement);
	}

	private static Stereotypes toStereotypes(List<String> identifiers, Map<String, SnapshotStereotype> definitionsByIdentifier) {
		List<Stereotype> stereotypes = new ArrayList<>();

		for (String identifier : identifiers) {
			Stereotype stereotype = SnapshotStereotypeFactory.toStereotype(identifier, definitionsByIdentifier);
			if (stereotype != null) {
				stereotypes.add(stereotype);
			}
		}

		return new Stereotypes(stereotypes);
	}

	@Override
	public List<StereotypeClassElement> types() {
		return types;
	}

	@Override
	public StereotypePackageElement mainApplicationPackage() {
		return mainApplicationPackage;
	}

	@Override
	public StereotypePackageElement packageNode(String packageName) {
		// no stereotype information was ever stored for packages (see StructureElementSnapshot) -
		// matches IndexStructureElements' own fallback for an unknown package
		return new StereotypePackageElement(packageName, null);
	}

	@Override
	public String methodLabel(StereotypeMethodElement method, StereotypeClassElement type) {
		// the label was already resolved and stored as rendered - see StructureSnapshotBuilder
		return method.getMethodLabel();
	}

	@Override
	public List<StructureMember> membersOf(StereotypeClassElement type) {
		return membersByType.getOrDefault(type, List.of());
	}

	@Override
	public StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> stereotypeFactory() {
		return factory;
	}

}
