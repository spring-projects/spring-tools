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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.api.Stereotypes;
import org.springframework.ide.vscode.boot.java.commands.StructureElementSnapshot.SnapshotMember;
import org.springframework.ide.vscode.boot.java.commands.StructureElementSnapshot.SnapshotMethod;
import org.springframework.ide.vscode.boot.java.commands.StructureElementSnapshot.SnapshotStereotype;
import org.springframework.ide.vscode.boot.java.commands.StructureElementSnapshot.SnapshotType;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;

/**
 * Builds a {@link StructureElementSnapshot} from a {@link StructureElements} - normally an
 * {@link IndexStructureElements}, since capturing only ever happens against the live index, never
 * against an already-captured snapshot.
 *
 * <p>Deliberately built against the {@link StructureElements} interface rather than
 * {@link IndexStructureElements} directly: it needs nothing but what that interface already
 * exposes, so it costs nothing extra to keep it decoupled from where the elements come from.
 *
 * @author Martin Lippert
 */
public class StructureSnapshotBuilder {

	private StructureSnapshotBuilder() {
	}

	public static StructureElementSnapshot capture(StructureElements elements) {
		Map<String, SnapshotStereotype> stereotypeDefinitions = new LinkedHashMap<>();

		List<SnapshotType> types = elements.types().stream()
				.map(type -> captureType(elements, type, stereotypeDefinitions))
				.toList();

		return new StructureElementSnapshot(elements.mainApplicationPackage().getPackageName(), types,
				List.copyOf(stereotypeDefinitions.values()));
	}

	private static SnapshotType captureType(StructureElements elements, StereotypeClassElement type,
			Map<String, SnapshotStereotype> stereotypeDefinitions) {

		List<String> stereotypes = captureStereotypes(elements.stereotypeFactory().fromType(type), stereotypeDefinitions);

		List<SnapshotMethod> methods = type.getMethods().stream()
				.map(method -> captureMethod(elements, type, method, stereotypeDefinitions))
				.toList();

		List<SnapshotMember> members = elements.membersOf(type).stream()
				.map(member -> new SnapshotMember(member.label(), member.contentHash()))
				.toList();

		return new SnapshotType(type.getType(), type.getContentHash(), stereotypes, methods, members);
	}

	private static SnapshotMethod captureMethod(StructureElements elements, StereotypeClassElement type,
			StereotypeMethodElement method, Map<String, SnapshotStereotype> stereotypeDefinitions) {

		List<String> stereotypes = captureStereotypes(elements.stereotypeFactory().fromMethod(method), stereotypeDefinitions);
		String label = elements.methodLabel(method, type);

		return new SnapshotMethod(method.getMethodName(), label, method.getMethodSignature(), method.getContentHash(), stereotypes);
	}

	/**
	 * Records the identifiers of the given stereotypes, and - the first time each identifier is
	 * seen - its definitional data too, so {@link SnapshotStereotypeFactory} can reconstruct
	 * equivalent {@link Stereotype} objects later without a catalog lookup.
	 */
	private static List<String> captureStereotypes(Stereotypes stereotypes, Map<String, SnapshotStereotype> stereotypeDefinitions) {
		List<String> identifiers = new ArrayList<>();

		for (Stereotype stereotype : stereotypes) {
			identifiers.add(stereotype.getIdentifier());
			stereotypeDefinitions.putIfAbsent(stereotype.getIdentifier(),
					new SnapshotStereotype(stereotype.getIdentifier(), stereotype.getDisplayName(), stereotype.getPriority(), stereotype.getGroups()));
		}

		return identifiers;
	}

}
