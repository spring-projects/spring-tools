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

/**
 * A project's logical structure, captured as the elements it is built from rather than as a
 * rendered tree - see {@code docs/structure-diff-elements.md}.
 *
 * <p>Not yet wired into baseline capture or diffing (that is the plan's "R4" step); for now this is
 * built and consumed only by {@link StructureSnapshotBuilder} and {@link SnapshotStructureElements},
 * and round-tripped in their tests.
 *
 * <p>Deliberately excludes application, package, stereotype-group and named-interface nodes - those
 * are derived when a tree is built, on both the live and the rebuilt-from-snapshot side, from the
 * *current* presentation settings (groups, catalog, Modulith metadata). Only {@code types} carries
 * everything the project's own code contributes; {@code stereotypeDefinitions} exists purely to
 * reconstruct the {@link org.jmolecules.stereotype.api.Stereotype} objects
 * {@link SnapshotStereotypeFactory} hands back, since those can no longer be looked up from the
 * catalog once an identifier is gone from it (see the design doc's "stereotypes that disappeared").
 *
 * @param mainApplicationPackage the project's main application package at capture time - the
 *        rebuilt tree's root label and abbreviation base
 * @param types all of the project's indexed types at capture time, unfiltered, in index order
 * @param stereotypeDefinitions the definitional data (display name, priority, groups) for every
 *        stereotype identifier referenced by any {@link SnapshotType} or {@link SnapshotMethod}
 *        above, one entry per distinct identifier
 *
 * @author Martin Lippert
 */
public record StructureElementSnapshot(
		String mainApplicationPackage,
		List<SnapshotType> types,
		List<SnapshotStereotype> stereotypeDefinitions) {

	/**
	 * @param fqn the type's fully qualified name - unabbreviated; abbreviation happens at render
	 *        time, relative to whatever main application package is current then
	 * @param contentHash a hash of the type's source, excluding any child nodes counted separately
	 *        (see {@code ASTUtils.contentHash}'s exclusion parameter)
	 * @param stereotypes the type's resolved stereotype identifiers - already folded in from
	 *        package-level and source-defined stereotypes, exactly as rendered
	 * @param methods the type's {@code StereotypeMethodElement} children
	 * @param members what {@link StructureViewUtil#membersOf} selects for the type
	 */
	public record SnapshotType(
			String fqn,
			String contentHash,
			List<String> stereotypes,
			List<SnapshotMethod> methods,
			List<SnapshotMember> members) {
	}

	/**
	 * @param name the method's plain name
	 * @param label the label rendered for the method - its request-mapping label when it is a
	 *        mapping handler ({@code StructureViewUtil#getMethodLabel}), its plain method label
	 *        otherwise; stored as rendered so a rebuilt tree matches the live one label-for-label
	 * @param signature the method's signature - its identity for matching across captures
	 * @param contentHash a hash of the method's source
	 * @param stereotypes the method's resolved stereotype identifiers
	 */
	public record SnapshotMethod(
			String name,
			String label,
			String signature,
			String contentHash,
			List<String> stereotypes) {
	}

	/**
	 * @param label the member's label (a {@code DocumentSymbol} name)
	 * @param contentHash a hash of the member's source
	 */
	public record SnapshotMember(
			String label,
			String contentHash) {
	}

	/**
	 * @param identifier the stereotype's identifier
	 * @param displayName the stereotype's human-readable name
	 * @param priority the stereotype's ordering priority among others a target can carry
	 * @param groups the identifiers of the catalog groups the stereotype belongs to
	 */
	public record SnapshotStereotype(
			String identifier,
			String displayName,
			int priority,
			List<String> groups) {
	}

}
