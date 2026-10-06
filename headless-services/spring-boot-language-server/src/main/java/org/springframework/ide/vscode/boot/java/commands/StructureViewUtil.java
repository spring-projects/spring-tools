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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
import org.springframework.ide.vscode.boot.java.beans.SpringBootApplicationIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.protocol.spring.SymbolElement;

import com.google.common.collect.Streams;

public class StructureViewUtil {

	public static List<String[]> identifyGroupers(AbstractStereotypeCatalog catalog, Collection<String> selectedGroups) {
		
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
		else if (packageName == null || packageName.isEmpty()) {
			// a root package of types in the default package
			return "(default package)";
		}
		else {
			return packageName;
		}
	}

	/**
	 * The root packages of a structure tree with the given types: the top-most packages that are not
	 * empty - those that hold a type of their own, and are not below another one that does. For the
	 * types of one project or one JAR that is mostly a single package, but it can be several (types
	 * in {@code com.acme.a} and {@code com.acme.b}, but none in {@code com.acme}), and including
	 * dependencies in a tree adds theirs - unless they are below one of the project's.
	 *
	 * <p>A type in the default package makes that the one root package: every package is below it.
	 *
	 * @param typeNames binary type names - {@code com.example.Outer$Inner}, as the tree's types have
	 * @return the root packages' names, ordered by name, without one being below another
	 */
	public static List<String> identifyRootPackages(Collection<String> typeNames) {
		List<String> packages = typeNames.stream()
				.map(StructureViewUtil::packageOf)
				.distinct()
				.sorted(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))
				.toList();

		// shortest first: a package's ancestors among them are known when it is looked at
		List<String> roots = new ArrayList<>();
		for (String pkg : packages) {
			if (roots.stream().noneMatch(root -> isSubPackage(pkg, root))) {
				roots.add(pkg);
			}
		}

		return roots.stream().sorted().toList();
	}

	/**
	 * Whether the given binary type name is in the given package or below it - on package name
	 * boundaries, so {@code com.examplefoo.Type} is not in {@code com.example}.
	 */
	public static boolean isInPackage(String typeName, String packageName) {
		return isSubPackage(packageOf(typeName), packageName);
	}

	/**
	 * The package of a binary type name: everything before the last dot - {@code com.example} for
	 * {@code com.example.Outer$Inner}, empty for a type in the default package.
	 */
	static String packageOf(String typeName) {
		int lastDot = typeName.lastIndexOf('.');
		return lastDot < 0 ? "" : typeName.substring(0, lastDot);
	}

	/**
	 * Whether the package is the given ancestor package or below it - on package name boundaries.
	 */
	static boolean isSubPackage(String pkg, String ancestor) {
		return ancestor.isEmpty() || pkg.equals(ancestor) || pkg.startsWith(ancestor + ".");
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
		String mappingLabel = springIndex.getRequestMappingLabel(project.getElementName(), method.getMethodSignature());
		return mappingLabel != null ? mappingLabel : method.getMethodLabel();
	}

	/**
	 * A method's label: the route label of the request mapping it declares, if any, matched by
	 * method signature, otherwise its plain method label. Shared between the live index's request
	 * mappings ({@code IndexStructureElements}) and a JAR's ({@code JarStructureElements}), so the
	 * two can't label the same kind of method differently.
	 */
	public static String getMethodLabel(StereotypeMethodElement method, Collection<RequestMappingIndexElement> requestMappings) {
		Optional<RequestMappingIndexElement> mapping = requestMappings.stream()
			.filter(mappingElement -> mappingElement.getMethodSignature() != null && mappingElement.getMethodSignature().equals(method.getMethodSignature()))
			.findAny();
		
		if (mapping.isPresent()) {
			return mapping.get().getDocumentSymbol().getName();
		}
		else {
			return method.getMethodLabel();
		}

	}

	/**
	 * The members a type contributes to the structure view beyond its own {@link StereotypeMethodElement}s
	 * - the symbols of whatever bean matches the type, e.g. an event listener implementation or a
	 * plain event-publishing method, neither of which is a stereotype-annotated method of its own.
	 *
	 * <p>Shared between {@link JsonNodeHandler#createTypeSubnotes} (which turns the result into
	 * member nodes) and the structure snapshot builder that is to come (which will turn it into the
	 * members a baseline snapshot stores for the type) - one rule, so a baseline can never show a
	 * different set of members than the tree it is diffed against.
	 *
	 * @return empty when the type has no known source location (true of every type reconstructed
	 *         from a baseline snapshot) - there is no document to look its beans up by
	 */
	public static List<SymbolElement> membersOf(CachedSpringMetamodelIndex springIndex, StereotypeClassElement type) {
		if (type.getLocation() == null) {
			return Collections.emptyList();
		}

		String docUri = type.getLocation().getUri();

		return Arrays.stream(springIndex.getBeansOfDocument(docUri))
				.filter(bean -> bean.getType().equals(type.getType()))
				.flatMap(bean -> bean.getChildren().stream())
				.filter(child -> child instanceof SymbolElement)
				.map(child -> (SymbolElement) child)
				.toList();
	}

	public static StereotypePackageElement identifyMainApplicationPackage(IJavaProject project, CachedSpringMetamodelIndex springIndex) {
		List<SpringBootApplicationIndexElement> mainAppNodes = springIndex.getSpringBootApplicationElementsForProject(project.getElementName());
		
		Optional<StereotypePackageElement> packageElement = mainAppNodes.stream()
			.sorted(new Comparator<SpringBootApplicationIndexElement>() {
				@Override
				public int compare(SpringBootApplicationIndexElement o1, SpringBootApplicationIndexElement o2) {
					if (o1.isClassDeclaration() && o2.isClassDeclaration()) return 0;
					if (o1.isAnnotationDeclaration() && o2.isAnnotationDeclaration()) return 0;
					
					if (o1.isClassDeclaration() && o2.isAnnotationDeclaration()) return -1;
					if (o1.isAnnotationDeclaration() && o2.isClassDeclaration()) return 1;
					
					return 0;
				}
			})
			.map(element -> element.getPackageName())
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
