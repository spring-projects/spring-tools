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

import java.util.stream.Collectors;

import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.catalog.StereotypeCatalog;
import org.jmolecules.stereotype.catalog.StereotypeGroup;
import org.jmolecules.stereotype.tooling.LabelProvider;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public class ApplicationModulesLabelProvider implements
		LabelProvider<ApplicationModules, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, NamedInterfaceNode> {

	private final StereotypeCatalog catalog;
	private final IJavaProject project;
	private final SpringMetamodelIndex springIndex;
	private final ApplicationModules modules;
	
	public ApplicationModulesLabelProvider(StereotypeCatalog catalog, IJavaProject project, SpringMetamodelIndex springIndex, ApplicationModules modules) {
		this.catalog = catalog;
		this.project = project;
		this.springIndex = springIndex;
		this.modules = modules;
	}
	
	@Override
	public String getApplicationLabel(ApplicationModules application) {
		return modules.getSystemName().orElse("Application");
	}

	@Override
	public String getPackageLabel(StereotypePackageElement pkg) {
		var name = pkg.getPackageName();
		return modules.getModuleForPackage(name).map(ApplicationModule::getDisplayName).orElse(name);
	}

	@Override
	public String getTypeLabel(StereotypeClassElement type) {
		
		return type.getType();
		
		// TODO: abbreviate with module name
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
	public String getSterotypeLabel(Stereotype stereotype) {

		var groups = catalog.getGroupsFor(stereotype);

		return stereotype.getDisplayName() + (groups.isEmpty() ? ""
				: " " + groups.stream().map(StereotypeGroup::getDisplayName)
						.collect(Collectors.joining(", ", "(", ")")));
	}
}
