/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.commands;

import java.util.Collection;

import org.jmolecules.stereotype.tooling.StructureProvider.SimpleStructureProvider;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;

/**
 * The structure of the tree built from stereotypes: the application delivers its root packages,
 * and each root package the types in or below it.
 */
public class ToolsStructureProvider implements
		SimpleStructureProvider<StructureApplication, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> {

	private final StructureElements elements;

	public ToolsStructureProvider(StructureElements elements) {
		this.elements = elements;
	}

	@Override
	public Collection<StereotypePackageElement> extractPackages(StructureApplication application) {
		return application.rootPackages();
	}

	@Override
	public Collection<StereotypeMethodElement> extractMethods(StereotypeClassElement type) {
		return type.getMethods();
	}

	@Override
	public Collection<StereotypeClassElement> extractTypes(StereotypePackageElement pkg) {
		// root packages are never below one another, so every type ends up under exactly one
		return elements.types().stream()
			.filter(element -> StructureViewUtil.isInPackage(element.getType(), pkg.getPackageName()))
			.toList();
	}
}
