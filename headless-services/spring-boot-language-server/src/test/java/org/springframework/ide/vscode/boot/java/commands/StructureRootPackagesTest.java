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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;

/**
 * The root packages of a structure tree: the top-most packages that are not empty
 * ({@link StructureViewUtil#identifyRootPackages}).
 *
 * @author Martin Lippert
 */
public class StructureRootPackagesTest {

	@Test
	void theTopMostPackageWithATypeOfItsOwnIsTheRoot() {
		assertEquals(List.of("com.example"), StructureViewUtil.identifyRootPackages(List.of(
				"com.example.Application", "com.example.web.Controller", "com.example.web.api.Endpoint")));
	}

	@Test
	void emptyParentPackagesDoNotCountSoSiblingsAreRootsOfTheirOwn() {
		// nothing in com.acme itself
		assertEquals(List.of("com.acme.a", "com.acme.b.deep"), StructureViewUtil.identifyRootPackages(List.of(
				"com.acme.b.deep.Y", "com.acme.a.X", "com.acme.a.sub.Z")));
	}

	@Test
	void aPackageThatOnlySharesAPrefixIsNotBelowAnother() {
		assertEquals(List.of("com.example", "com.examplefoo"), StructureViewUtil.identifyRootPackages(List.of(
				"com.example.A", "com.examplefoo.B")));
	}

	@Test
	void aTypeInTheDefaultPackageMakesThatTheOneRoot() {
		assertEquals(List.of(""), StructureViewUtil.identifyRootPackages(List.of("Stray", "com.example.A")));
	}

	@Test
	void nestedTypesAndPackageInfoCountForTheirPackage() {
		assertEquals(List.of("com.example"), StructureViewUtil.identifyRootPackages(List.of(
				"com.example.package-info", "com.example.web.Outer$Inner")));
		assertEquals(List.of(), StructureViewUtil.identifyRootPackages(List.of()));
	}

	@Test
	void aTypeIsInAPackageOnPackageNameBoundaries() {
		assertTrue(StructureViewUtil.isInPackage("com.example.A", "com.example"));
		assertTrue(StructureViewUtil.isInPackage("com.example.web.Outer$Inner", "com.example"));
		assertTrue(StructureViewUtil.isInPackage("Stray", ""));
		assertTrue(StructureViewUtil.isInPackage("com.example.A", ""));
		assertFalse(StructureViewUtil.isInPackage("com.examplefoo.A", "com.example"));
		assertFalse(StructureViewUtil.isInPackage("com.A", "com.example"));
	}

	@Test
	void theApplicationKnowsTheRootPackageOfEachType() {
		StereotypePackageElement acme = new StereotypePackageElement("com.acme", null);
		StereotypePackageElement example = new StereotypePackageElement("com.example", null);
		StructureApplication application = new StructureApplication("app", List.of(acme, example));

		assertSame(example, application.rootPackageOf(type("com.example.web.Controller")));
		assertSame(acme, application.rootPackageOf(type("com.acme.Lib")));
		assertNull(application.rootPackageOf(type("org.other.Type")));
	}

	private static StereotypeClassElement type(String name) {
		StereotypeClassElement type = mock(StereotypeClassElement.class);
		when(type.getType()).thenReturn(name);
		return type;
	}

}
