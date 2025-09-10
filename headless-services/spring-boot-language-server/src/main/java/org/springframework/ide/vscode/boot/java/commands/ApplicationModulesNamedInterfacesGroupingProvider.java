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
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.jmolecules.stereotype.tooling.Grouped;
import org.jmolecules.stereotype.tooling.StructureProvider.GroupingStructureProvider;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.boot.modulith.NamedInterface;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public class ApplicationModulesNamedInterfacesGroupingProvider extends ApplicationModulesStructureProvider implements
		GroupingStructureProvider<ApplicationModules, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement, NamedInterfaceNode> {
	
	private final ApplicationModules modules;

	public ApplicationModulesNamedInterfacesGroupingProvider(ApplicationModules modules, IJavaProject project, CachedSpringMetamodelIndex springIndex) {
		super(project, springIndex);
		this.modules = modules;
	}

	@Override
	public Grouped<NamedInterfaceNode, StereotypeClassElement> groupTypes(StereotypePackageElement pkg) {

		var module = modules.getModuleForPackage(pkg.getPackageName()).get();

		var interfaces = module.getNamedInterfaces().stream().collect(Collectors.toMap(
				namedInterface -> new NamedInterfaceNode(namedInterface),
				namedInterface -> getClassElements(namedInterface),
				(l, r) -> r,
				TreeMap::new));

		interfaces.put(NamedInterfaceNode.INTERNAL, getInternalTypes(module));

		return new Grouped<NamedInterfaceNode, StereotypeClassElement>(interfaces);
	}

	private Collection<StereotypeClassElement> getClassElements(NamedInterface namedInterface) {
		return namedInterface.getClasses().stream()
				.map(className -> findClassElement(className, project, springIndex))
				.toList();
	}
	
	private StereotypeClassElement findClassElement(String className, IJavaProject project, CachedSpringMetamodelIndex springIndex) {
		return springIndex.getClassesForProject(project.getElementName()).stream()
			.filter(classElement -> classElement.getType().equals(className))
			.findAny().orElse(new StereotypeClassElement(className, null, Set.of(), Set.of()));
	}

	private Collection<StereotypeClassElement> getInternalTypes(ApplicationModule module) {
		return springIndex.getClassesForProject(project.getElementName()).stream()
				.filter(classElement -> classElement.getType().startsWith(module.getBasePackage()))
				.filter(classElement -> !(module.getNamedInterfaces().stream().filter(namedInterface -> namedInterface.getClasses().contains(classElement.getType())).findAny().isPresent()))
			.toList();
	}
}
