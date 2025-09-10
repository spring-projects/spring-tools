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

import org.jmolecules.stereotype.tooling.StructureProvider;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public abstract class ApplicationModulesStructureProvider
		implements StructureProvider<ApplicationModules, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> {

	protected final IJavaProject project;
	protected final CachedSpringMetamodelIndex springIndex;
	
	public ApplicationModulesStructureProvider(IJavaProject project, CachedSpringMetamodelIndex springIndex) {
		this.project = project;
		this.springIndex = springIndex;
	}

	@Override
	public Collection<StereotypePackageElement> extractPackages(ApplicationModules application) {
		
		return application.stream()
				.map(ApplicationModule::getBasePackage)
				.map(pkg -> StructureViewUtil.findPackageNode(pkg, project, springIndex))
				.toList();
	}

	@Override
	public Collection<StereotypeMethodElement> extractMethods(StereotypeClassElement type) {
		return type.getMethods();
	}

}
