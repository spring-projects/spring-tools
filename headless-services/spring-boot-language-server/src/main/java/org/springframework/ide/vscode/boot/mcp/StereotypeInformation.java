/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.mcp;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jmolecules.stereotype.api.Stereotypes;
import org.jmolecules.stereotype.catalog.StereotypeDefinition;
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.CachedSpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.stereotypes.IndexBasedStereotypeFactory;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.stereotype.Component;

/**
 * @author Martin Lippert
 */
@Component
public class StereotypeInformation {

	private final JavaProjectFinder projectFinder;
	private final SpringMetamodelIndex springIndex;
	private final StereotypeCatalogRegistry stereotypeCatalogRegistry;

	public StereotypeInformation(JavaProjectFinder projectFinder, SpringMetamodelIndex springIndex,
			StereotypeCatalogRegistry stereotypeCatalogRegistry) {
		this.projectFinder = projectFinder;
		this.springIndex = springIndex;
		this.stereotypeCatalogRegistry = stereotypeCatalogRegistry;
	}

	@Tool(description = """
			This function provides information about all the stereotype definitions that are defined and available in the given project
			""")
	public Set<StereotypeDefinition> getStereotypesList(
			@ToolParam(description = "the name of the project in the workspace of the user") String projectName)
			throws Exception {

		IJavaProject project = getProject(projectName);
		AbstractStereotypeCatalog catalog = this.stereotypeCatalogRegistry.getCatalogOf(project);

		return catalog.getDefinitions();
	}

	@Tool(description = """
			This function returns a list of classes or components from the given project and lists the stereotypes that each class or component has.
			This way you can identify, for example, all components from a specific stereotype (e.g. all data repositories, all services, all entities, and so on)
			""")
	public List<ComponentWithStereotypes> getListOfComponentsAndTheirStereotypes(
			@ToolParam(description = "the name of the project in the workspace of the user") String projectName)
			throws Exception {

		IJavaProject project = getProject(projectName);
		
		var catalog = stereotypeCatalogRegistry.getCatalogOf(project);
		var cachedIndex = new CachedSpringMetamodelIndex(springIndex);
		var factory = new IndexBasedStereotypeFactory(catalog, project, cachedIndex);

		List<StereotypeClassElement> classNodes = this.springIndex.getNodesOfType(project.getElementName(), StereotypeClassElement.class);
		return classNodes.stream()
			.map(classNode -> createComponent(classNode, factory))
			.filter(component -> component != null)
			.filter(component -> component.stereotypes.size() > 0)
			.toList();
		
	}

	public static record ComponentWithStereotypes(String name, List<String> stereotypes) {
	};
	
	private ComponentWithStereotypes createComponent(StereotypeClassElement classElement, IndexBasedStereotypeFactory factory) {
		Stereotypes stereotypes = factory.fromType(classElement);
		
		List<String> stereotypeList = stereotypes.stream()
			.map(stereotype -> stereotype.getDisplayName())
			.toList();
		
		return new ComponentWithStereotypes(classElement.getType(), stereotypeList);
	}

	//
	//
	//

	private IJavaProject getProject(String projectName) throws Exception {
		Optional<? extends IJavaProject> found = projectFinder.all().stream()
				.filter(project -> project.getElementName().toLowerCase().equals(projectName.toLowerCase()))
				.findFirst();

		if (found.isEmpty()) {
			throw new Exception("project with name " + projectName + " not found");
		} else {
			return found.get();
		}
	}

}
