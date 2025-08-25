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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.jmolecules.stereotype.tooling.HierarchicalNodeHandler;
import org.jmolecules.stereotype.tooling.LabelUtils;
import org.jmolecules.stereotype.tooling.ProjectTree;
import org.jmolecules.stereotype.tooling.SimpleLabelProvider;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.commands.ToolsJsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.stereotypes.IndexBasedStereotypeFactory;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

public class SpringIndexCommands {
	
	private static final String SPRING_STRUCTURE_CMD = "sts/spring-boot/structure";
	private static final String SPRING_STRUCTURE_CMD_2 = "sts/spring-boot/structure2";
	
	public SpringIndexCommands(SimpleLanguageServer server, SpringMetamodelIndex springIndex, JavaProjectFinder projectFinder, StereotypeCatalogRegistry stereotypeCatalogRegistry) {
		server.onCommand(SPRING_STRUCTURE_CMD, params -> server.getAsync().invoke(() -> springIndex.getProjects()));
		server.onCommand(SPRING_STRUCTURE_CMD_2, params -> server.getAsync().invoke(() -> {
			return projectFinder.all().stream().map(project -> nodeFrom(stereotypeCatalogRegistry, springIndex, project)).filter(Objects::nonNull).collect(Collectors.toList());
		}));
	}
	
	private Node nodeFrom(StereotypeCatalogRegistry stereotypeCatalogRegistry, SpringMetamodelIndex springIndex, IJavaProject project) {
		var catalog = stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, springIndex);
		factory.registerStereotypeDefinitions();

		StereotypePackageElement mainApplicationPackage = identifyMainApplicationPackage(project, springIndex);
		
		var labelProvider = new SimpleLabelProvider<>(StereotypePackageElement::getPackageName, StereotypePackageElement::getPackageName, StereotypeClassElement::getType,
				(StereotypeMethodElement m, StereotypeClassElement __) -> m.getMethodName(), Object::toString)
				.withTypeLabel(it -> abbreviate(mainApplicationPackage, it))
				.withMethodLabel((m, c) -> getMethodLabel(m, c));

		var structureProvider = new ToolsStructureProvider(springIndex, project);
		
		// json output
		BiConsumer<Node, Object> consumer = (node, c) -> {
			node.withAttribute(HierarchicalNodeHandler.TEXT, labelProvider.getCustomLabel(c))
			 .withAttribute("icon", "fa-named-interface");
		};
		
		var jsonHandler = new ToolsJsonNodeHandler(labelProvider, consumer);

		var jsonTree = new ProjectTree<>(factory, catalog, jsonHandler)
				.withStructureProvider(structureProvider)
				.withGrouper("org.jmolecules.architecture")
				.withGrouper("org.jmolecules.ddd", "org.jmolecules.event", "spring", "jpa", "java");
		
		jsonTree.process(mainApplicationPackage);

		return jsonHandler.getRoot();
	}

	private String abbreviate(StereotypePackageElement mainApplicationPackage, StereotypeClassElement it) {
		if (mainApplicationPackage == null || mainApplicationPackage.getPackageName() == null || mainApplicationPackage.getPackageName().isBlank()) {
			return it.getType();
		}
		else {
			return LabelUtils.abbreviate(it.getType(), mainApplicationPackage.getPackageName());
		}
	}
	
	private String getMethodLabel(StereotypeMethodElement method, StereotypeClassElement clazz) {
		// TODO: special treatment for methods that have specific index elements with specific labels (e.g. mapping methods)
		return method.getMethodLabel();
	}
	
	public StereotypePackageElement identifyMainApplicationPackage(IJavaProject project, SpringMetamodelIndex springIndex) {
		List<StereotypeClassElement> classNodes = springIndex.getNodesOfType(project.getElementName(), StereotypeClassElement.class);
		
		Optional<StereotypePackageElement> packageElement = classNodes.stream()
			.filter(node -> node.getAnnotationTypes().contains(Annotations.BOOT_APP))
			.map(node -> getPackage(node.getType()))
			.map(packageName -> findPackageNode(packageName, project, springIndex))
			.findFirst();
		
		if (packageElement.isPresent()) {
			return packageElement.get();
		}
		else {
			return new StereotypePackageElement("", null);
		}
	}
	
	private String getPackage(String fullyQualifiedClassName) {
		return ModulithService.getPackageNameFromTypeFQName(fullyQualifiedClassName);
	}
	
	private StereotypePackageElement findPackageNode(String packageName, IJavaProject project, SpringMetamodelIndex springIndex) {
		List<StereotypePackageElement> packageNodes = springIndex.getNodesOfType(project.getElementName(), StereotypePackageElement.class);

		Optional<StereotypePackageElement> found = packageNodes.stream()
			.filter(packageNode -> packageNode.getPackageName().equals(packageName))
			.findAny();
		
		if (found.isPresent()) {
			return found.get();
		}
		else {
			return new StereotypePackageElement(packageName, null);
		}
	}
	
}
