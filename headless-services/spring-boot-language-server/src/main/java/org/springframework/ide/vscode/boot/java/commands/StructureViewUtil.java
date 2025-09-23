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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.atteo.evo.inflector.English;
import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.catalog.StereotypeCatalog;
import org.jmolecules.stereotype.catalog.StereotypeGroup;
import org.jmolecules.stereotype.catalog.StereotypeGroup.Type;
import org.jmolecules.stereotype.catalog.StereotypeGroups;
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.jmolecules.stereotype.tooling.LabelUtils;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.commons.java.IJavaProject;

import com.google.common.collect.Streams;

public class StructureViewUtil {

	public static List<String[]> identifyGroupers(AbstractStereotypeCatalog catalog, List<String> selectedGroups) {
		
//		List<String[]> allGroupsWithSpecificOrder = Arrays.asList(
//			new String[] {"architecture"},
//			new String[] {"ddd", "event", "spring", "jpa", "java"}
//		);
//		
//		return allGroupsWithSpecificOrder;
		
		
		StereotypeGroups groups = catalog.getGroups();

        var architectureIds = groups.streamByType(StereotypeGroup.Type.ARCHITECTURE)
                .map(StereotypeGroup::getIdentifier)
                .filter(selectedGroups::contains)
                .toList();

        var designIds = groups.streamByType(StereotypeGroup.Type.DESIGN)
                .map(StereotypeGroup::getIdentifier)
                .filter(selectedGroups::contains);
        
        var customIds = new ArrayList<String>().stream();

        var technologyIds = groups.streamByType(StereotypeGroup.Type.TECHNOLOGY)
                .map(StereotypeGroup::getIdentifier)
                .filter(selectedGroups::contains);
        
        ArrayList<String[]> result = new ArrayList<String[]>();
        result.add(architectureIds.toArray(String[]::new));
        result.add(Streams.concat(designIds, customIds, technologyIds)
        		.toArray(String[]::new));
        
        return result;
	}
	
	public static String getPackageLabel(StereotypePackageElement p) {
		String packageName = p.getPackageName();
		if (p.isMainPackage() && (packageName == null || packageName.isEmpty())) {
			return "(no main application package identified)";
		}
		else {
			return packageName;
		}
	}

	public static String abbreviate(StereotypePackageElement mainApplicationPackage, StereotypeClassElement it) {
		if (mainApplicationPackage == null || mainApplicationPackage.getPackageName() == null || mainApplicationPackage.getPackageName().isBlank()) {
			return it.getType();
		}
		else {
			return LabelUtils.abbreviate(it.getType(), mainApplicationPackage.getPackageName());
		}
	}
	
	public static String getMethodLabel(IJavaProject project, CachedSpringMetamodelIndex springIndex, StereotypeMethodElement method, StereotypeClassElement clazz) {
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
	
	public static StereotypePackageElement identifyMainApplicationPackage(IJavaProject project, CachedSpringMetamodelIndex springIndex) {
		List<StereotypeClassElement> classNodes = springIndex.getClassesForProject(project.getElementName());
		
		Optional<StereotypePackageElement> packageElement = classNodes.stream()
			.filter(node -> node.isAnnotatedWith(Annotations.BOOT_APP))
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
	
	public static String getPackage(String fullyQualifiedClassName) {
		return ModulithService.getPackageNameFromTypeFQName(fullyQualifiedClassName);
	}
	
	public static StereotypePackageElement findPackageNode(String packageName, IJavaProject project, CachedSpringMetamodelIndex springIndex) {
		StereotypePackageElement packageElement = springIndex.findPackageNode(packageName, project.getElementName());
		return packageElement != null ? packageElement : new StereotypePackageElement(packageName, null);
	}
	
	public static boolean hasSourceDefinedStereotypesEnabled() {
		return System.getProperty("disable-source-defined-stereotypes") == null;
	}
	
	public static boolean hasModulithStructureViewEnabled() {
		return System.getProperty("disable-modulith-structure-view") == null;
	}
	
	public static boolean hasNamedInterfaceNodesEnabled() {
		return System.getProperty("enable-named-interface-nodes") != null;
	}

	private static final List<String> EXCLUSIONS = List.of("Application", "Properties", "Mappings", "Hints");

	static Function<Stereotype, String> getStereotypeLabeler(StereotypeCatalog catalog) {

		return stereotype -> {

			var groups = catalog.getGroupsFor(stereotype);
			var name = stereotype.getDisplayName();

			var doNotPluralize = groups.stream().anyMatch(it -> it.hasType(Type.ARCHITECTURE))
					|| EXCLUSIONS.stream().anyMatch(name::endsWith);

			var plural = doNotPluralize ? name : English.plural(name);

			return plural + (groups.isEmpty() ? ""
					: " " + groups.stream().map(StereotypeGroup::getDisplayName)
							.collect(Collectors.joining(", ", "(", ")")));
		};
	}
}
