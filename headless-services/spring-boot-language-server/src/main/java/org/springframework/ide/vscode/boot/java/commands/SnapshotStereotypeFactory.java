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
import java.util.Map;

import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.api.StereotypeFactory;
import org.jmolecules.stereotype.api.Stereotypes;
import org.springframework.ide.vscode.boot.java.commands.StructureElementSnapshot.SnapshotStereotype;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;

/**
 * A {@link StereotypeFactory} that hands back the stereotypes a {@link StructureSnapshotBuilder}
 * stored for a type or method, rather than detecting them against a catalog - the catalog is never
 * consulted, which is exactly what lets a rebuilt-from-snapshot tree place a type whose stereotype
 * has since disappeared from the catalog the same way the live tree would if that identifier were
 * simply unknown (into "Other" - see the design doc's "stereotypes that disappeared").
 *
 * <p>{@code fromType}/{@code fromMethod} are answered from an identity-keyed lookup built once by
 * {@link SnapshotStructureElements}, since the elements it reconstructs from a snapshot carry no
 * annotations or supertypes of their own to detect anything from.
 *
 * @author Martin Lippert
 */
public class SnapshotStereotypeFactory implements StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> {

	private final Map<Object, Stereotypes> stereotypesByElement;

	public SnapshotStereotypeFactory(Map<Object, Stereotypes> stereotypesByElement) {
		this.stereotypesByElement = stereotypesByElement;
	}

	@Override
	public Stereotypes fromPackage(StereotypePackageElement pkg) {
		// package-level stereotypes are already folded into each type's stored, resolved
		// stereotypes at capture time (IndexBasedStereotypeFactory.fromType), so there is nothing
		// left for a package on its own to contribute
		return Stereotypes.NONE;
	}

	@Override
	public Stereotypes fromType(StereotypeClassElement type) {
		return stereotypesByElement.getOrDefault(type, Stereotypes.NONE);
	}

	@Override
	public Stereotypes fromMethod(StereotypeMethodElement method) {
		return stereotypesByElement.getOrDefault(method, Stereotypes.NONE);
	}

	/**
	 * Reconstructs the {@link Stereotype} a stored identifier refers to, using its stored
	 * definitional data - {@code null} if the snapshot itself carries no definition for that
	 * identifier (a corrupt or hand-edited snapshot; never true of one this codebase wrote).
	 */
	static Stereotype toStereotype(String identifier, Map<String, SnapshotStereotype> definitionsByIdentifier) {
		SnapshotStereotype definition = definitionsByIdentifier.get(identifier);
		return definition == null ? null : new StoredStereotype(definition);
	}

	/**
	 * The {@link Stereotype#isInherited()} this class answers with is never consulted by anything
	 * that renders or groups the structure tree (only by catalog *detection*, which this factory
	 * never does) - it is always {@code false} here for that reason, not because a captured
	 * stereotype is known not to have been inherited.
	 */
	private record StoredStereotype(SnapshotStereotype definition) implements Stereotype {

		@Override
		public String getIdentifier() {
			return definition.identifier();
		}

		@Override
		public String getDisplayName() {
			return definition.displayName();
		}

		@Override
		public List<String> getGroups() {
			return definition.groups();
		}

		@Override
		public int getPriority() {
			return definition.priority();
		}

		@Override
		public boolean isInherited() {
			return false;
		}

		@Override
		public String toDetailedString() {
			return definition.displayName();
		}

		@Override
		public String toString() {
			return definition.displayName();
		}
	}

}
