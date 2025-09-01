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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.commands.ToolsJsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexElement;
import org.springframework.ide.vscode.boot.java.stereotypes.IndexBasedStereotypeFactory;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

import com.google.gson.JsonPrimitive;

public class SpringIndexCommands {
	
	private static final String SPRING_STRUCTURE_CMD = "sts/spring-boot/structure";
	private static final Logger log = LoggerFactory.getLogger(SpringIndexCommands.class);
	
	private final SpringMetamodelIndex springIndex;
	
	public SpringIndexCommands(SimpleLanguageServer server, SpringMetamodelIndex springIndex, JavaProjectFinder projectFinder, StereotypeCatalogRegistry stereotypeCatalogRegistry) {
		this.springIndex = springIndex;
		
		server.onCommand(SPRING_STRUCTURE_CMD, params -> server.getAsync().invoke(() -> {
			boolean updateMetadata =
					params.getArguments() != null
					&& params.getArguments().size() == 1
					&& params.getArguments().get(0) instanceof JsonPrimitive
					? ((JsonPrimitive) params.getArguments().get(0)).getAsBoolean() : false;
			
			return projectFinder.all().stream()
					.map(project -> nodeFrom(stereotypeCatalogRegistry, springIndex, project, updateMetadata))
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
		}));
	}
	
	private Node nodeFrom(StereotypeCatalogRegistry stereotypeCatalogRegistry, SpringMetamodelIndex springIndex, IJavaProject project, boolean updateMetadata) {
		log.info("create structural view tree information for project: " + project.getElementName());
		
		if (updateMetadata) {
			stereotypeCatalogRegistry.reset(project);
			log.info("stereotype registry reset for project: " + project.getElementName());
		}
		
		var catalog = stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, springIndex);
		factory.registerStereotypeDefinitions();

		StereotypePackageElement mainApplicationPackage = identifyMainApplicationPackage(project, springIndex);
		
		var labelProvider = new SimpleLabelProvider<>(StereotypePackageElement::getPackageName, StereotypePackageElement::getPackageName, StereotypeClassElement::getType,
				(StereotypeMethodElement m, StereotypeClassElement __) -> m.getMethodName(), Object::toString)
				.withTypeLabel(it -> abbreviate(mainApplicationPackage, it))
				.withMethodLabel((m, c) -> getMethodLabel(project, m, c))
				.withPackageLabel((p) -> getPackageLabel(p))
				.withApplicationLabel((p) -> getPackageLabel(p));

		var structureProvider = new ToolsStructureProvider(springIndex, project);
		
		// json output
		BiConsumer<Node, Object> consumer = (node, c) -> {
			node.withAttribute(HierarchicalNodeHandler.TEXT, labelProvider.getCustomLabel(c))
			 .withAttribute(ToolsJsonNodeHandler.ICON, "fa-named-interface");
		};
		
		var jsonHandler = new ToolsJsonNodeHandler(labelProvider, consumer);

		var jsonTree = new ProjectTree<>(factory, catalog, jsonHandler)
				.withStructureProvider(structureProvider)
				.withGrouper("org.jmolecules.architecture")
				.withGrouper("org.jmolecules.ddd", "org.jmolecules.event", "spring", "jpa", "java");
		
		jsonTree.process(mainApplicationPackage);

		return jsonHandler.getRoot();
	}

	private String getPackageLabel(StereotypePackageElement p) {
		String packageName = p.getPackageName();
		if (p.isMainPackage() && (packageName == null || packageName.isEmpty())) {
			return "(no main application package identified)";
		}
		else {
			return packageName;
		}
	}

	private String abbreviate(StereotypePackageElement mainApplicationPackage, StereotypeClassElement it) {
		if (mainApplicationPackage == null || mainApplicationPackage.getPackageName() == null || mainApplicationPackage.getPackageName().isBlank()) {
			return it.getType();
		}
		else {
			return LabelUtils.abbreviate(it.getType(), mainApplicationPackage.getPackageName());
		}
	}
	
	private String getMethodLabel(IJavaProject project, StereotypeMethodElement method, StereotypeClassElement clazz) {
		// TODO: special treatment for methods that have specific index elements with specific labels (e.g. mapping methods)
		
		Optional<RequestMappingIndexElement> mapping = springIndex.getNodesOfType(project.getElementName(), RequestMappingIndexElement.class).stream()
			.filter(mappingElement -> mappingElement.getMethodSignature() != null && mappingElement.getMethodSignature().equals(method.getMethodSignature()))
			.findAny();
		
		if (mapping.isPresent()) {
			return mapping.get().getDocumentSymbol().getName();
		}
		else {
			return method.getMethodLabel();
		}
		
	}
	
	public StereotypePackageElement identifyMainApplicationPackage(IJavaProject project, SpringMetamodelIndex springIndex) {
		List<StereotypeClassElement> classNodes = springIndex.getNodesOfType(project.getElementName(), StereotypeClassElement.class);
		
		Optional<StereotypePackageElement> packageElement = classNodes.stream()
			.filter(node -> node.getAnnotationTypes().contains(Annotations.BOOT_APP))
			.map(node -> getPackage(node.getType()))
			.map(packageName -> findPackageNode(packageName, project, springIndex))
			.findFirst();
		
		if (packageElement.isPresent()) {
			return new StereotypePackageElement(packageElement.get().getPackageName(), packageElement.get().getAnnotationTypes(), true);
		}
		else {
			return new StereotypePackageElement("", null, true);
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
