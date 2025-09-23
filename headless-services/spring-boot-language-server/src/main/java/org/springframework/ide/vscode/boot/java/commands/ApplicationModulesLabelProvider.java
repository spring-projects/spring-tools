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

import java.util.Collections;

import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.catalog.StereotypeCatalog;
import org.jmolecules.stereotype.tooling.LabelProvider;
import org.jmolecules.stereotype.tooling.LabelUtils;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public class ApplicationModulesLabelProvider implements
		LabelProvider<ApplicationModules, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, NamedInterfaceNode> {

	private final StereotypeCatalog catalog;
	private final IJavaProject project;
	private final CachedSpringMetamodelIndex springIndex;
	private final ApplicationModules modules;
	
	public ApplicationModulesLabelProvider(StereotypeCatalog catalog, IJavaProject project, CachedSpringMetamodelIndex springIndex, ApplicationModules modules) {
		this.catalog = catalog;
		this.project = project;
		this.springIndex = springIndex;
		this.modules = modules;
	}
	
	@Override
	public String getApplicationLabel(ApplicationModules application) {
		
		var mainPackage = StructureViewUtil.identifyMainApplicationPackage(project, springIndex);
		
		return modules.getSystemName()	.orElse(project.getElementName()) + " (" + mainPackage.getPackageName() + ")";
	}

	@Override
	public String getPackageLabel(StereotypePackageElement pkg) {

		var name = pkg.getPackageName();

		return modules.getModuleForPackage(name)
				.map(ApplicationModule::getDisplayName)
				.map(it -> it + " (" + LabelUtils.abbreviate(name) +")")
				.orElse(name);
	}

	@Override
	public String getTypeLabel(StereotypeClassElement type) {

		var result = modules.getModuleByType(type)
				.map(it -> it.getBasePackage())
				.map(it -> new StereotypePackageElement(it, Collections.emptySet()))
				.map(it -> StructureViewUtil.abbreviate(it, type))
				.orElseGet(type::getType);

		return !StructureViewUtil.hasNamedInterfaceNodesEnabled()
				? result + modules.getModuleByType(type)
						.filter(it -> it.isExposed(type.getType()))
						.map(__ -> " (API)")
						.orElse(" (internal)")
				: result;
	}

	@Override
	public String getMethodLabel(StereotypeMethodElement method, StereotypeClassElement contextual) {
		return StructureViewUtil.getMethodLabel(project, springIndex, method, contextual);
	}

	@Override
	public String getCustomLabel(NamedInterfaceNode ni) {
		return ni.toString();
	}

	@Override
	public String getStereotypeLabel(Stereotype stereotype) {
		return StructureViewUtil.getStereotypeLabeler(catalog).apply(stereotype);
	}
}
