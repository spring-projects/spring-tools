/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
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
import java.util.List;

import org.jmolecules.stereotype.tooling.StructureProvider.SimpleStructureProvider;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public class ToolsStructureProvider implements
		SimpleStructureProvider<StereotypePackageElement, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> {

	private final CachedSpringMetamodelIndex springIndex;
	private final IJavaProject project;

	public ToolsStructureProvider(CachedSpringMetamodelIndex springIndex, IJavaProject project) {
		this.springIndex = springIndex;
		this.project = project;
	}

	@Override
	public Collection<StereotypePackageElement> extractPackages(StereotypePackageElement pkg) {
		return List.of(pkg);
	}

	@Override
	public Collection<StereotypeMethodElement> extractMethods(StereotypeClassElement type) {
		return type.getMethods();
	}

	@Override
	public Collection<StereotypeClassElement> extractTypes(StereotypePackageElement pkg) {
		return springIndex.getClassesForProject(project.getElementName()).stream()
			.filter(element -> element.getType().startsWith(pkg.getPackageName()))
			.toList();
	}
}