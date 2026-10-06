/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.commands;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jmolecules.stereotype.api.StereotypeFactory;
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexElement;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeFactory;
import org.springframework.ide.vscode.boot.java.stereotypes.JarStereotypeScanner;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.SymbolElement;

/**
 * {@link StructureElements} for a selected JAR dependency, backed by {@link JarStereotypeScanner}.
 * See {@code docs/structure-view-dependencies.md}.
 *
 * <p>{@code types()} filters the JAR's scanned classes down to the ones that currently match some
 * stereotype in the given catalog, live, on every call - so a change to the catalog's stereotype
 * definitions (a JSON catalog file edited, a source-defined stereotype added or removed anywhere in
 * the project or an included dependency) is reflected immediately, without needing to detect that
 * change or rescan the JAR: only the (comparatively expensive, and catalog-independent) scan of the
 * JAR's classes into raw elements is cached - by {@link JarDependencySource}, keyed by the JAR's own
 * identity - never the filtered result.
 *
 * <p>Members and method labels come from the beans {@code JarBeanIndexer} built for the scanned
 * types - the same index elements the AST side builds, turned into members and labels by the same
 * code ({@link StructureMember#of}, {@link StructureViewUtil#getMethodLabel(StereotypeMethodElement,
 * java.util.Collection)}). Unlike stereotype matching, those beans are not catalog-dependent - a
 * fact of the classes' own bytecode - so they are computed once with the scan and only looked up
 * here.
 *
 * @author Martin Lippert
 */
public class JarStructureElements implements StructureElements {

	private final List<StereotypeClassElement> scannedTypes;
	private final Map<StereotypeClassElement, List<Bean>> beans;
	private final Map<Object, String> bindingKeys;
	private final Map<StereotypeMethodElement, StereotypeClassElement> declaringTypes;
	private final JarStereotypeFactory factory;
	private final StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> cachingFactory;

	/**
	 * @param scannedTypes every class {@link JarStereotypeScanner} found in the JAR, unfiltered -
	 *        see {@link JarDependencySource} for where this comes from and how it is cached
	 * @param beans the beans {@code JarBeanIndexer} built for those classes, by identity of the exact
	 *        {@code scannedTypes} instances - a class without any has no entry
	 * @param bindingKeys the JDT binding keys of the scanned types, methods and bean children, by
	 *        identity - what a node for one of them opens it by, having no location
	 * @param catalog the catalog of the tree this JAR is being included in
	 */
	public JarStructureElements(List<StereotypeClassElement> scannedTypes, Map<StereotypeClassElement, List<Bean>> beans,
			Map<Object, String> bindingKeys, AbstractStereotypeCatalog catalog) {
		this.scannedTypes = scannedTypes;
		this.beans = beans;
		this.bindingKeys = bindingKeys;
		this.factory = new JarStereotypeFactory(catalog);
		this.cachingFactory = StereotypeFactory.caching(factory);

		// a method label is looked up by the method's own type, not the contextual one - in a group of
		// methods across types, the contextual type is not the declaring one
		this.declaringTypes = new IdentityHashMap<>();
		scannedTypes.forEach(type -> type.getMethods().forEach(method -> declaringTypes.put(method, type)));
	}

	/**
	 * Every scanned type - which of them are shown is decided by the composite, see
	 * {@link #showsOnlyStereotypedTypes()}.
	 */
	@Override
	public List<StereotypeClassElement> types() {
		return scannedTypes;
	}

	@Override
	public boolean showsOnlyStereotypedTypes() {
		return true;
	}

	/**
	 * How many of the scanned types have a stereotype of their own - for logging only; the composite
	 * also takes the host's package stereotypes and the types' methods into account.
	 */
	public long typesWithOwnStereotypeCount() {
		return scannedTypes.stream().filter(factory::matchesAnyStereotype).count();
	}

	@Override
	public StereotypePackageElement mainApplicationPackage() {
		// never consulted: a composite never asks a dependency for its main package, only the
		// host's (see CompositeStructureElements) - defensive fallback only
		return new StereotypePackageElement("", null, true);
	}

	@Override
	public StereotypePackageElement packageNode(String packageName) {
		// never consulted either, for the same reason - a composite always uses the host's
		return new StereotypePackageElement(packageName, null);
	}

	@Override
	public String methodLabel(StereotypeMethodElement method, StereotypeClassElement type) {
		StereotypeClassElement declaringType = declaringTypes.getOrDefault(method, type);
		List<RequestMappingIndexElement> requestMappings = childrenOf(declaringType).stream()
				.filter(RequestMappingIndexElement.class::isInstance)
				.map(RequestMappingIndexElement.class::cast)
				.toList();

		return StructureViewUtil.getMethodLabel(method, requestMappings);
	}

	@Override
	public List<StructureMember> membersOf(StereotypeClassElement type) {
		return childrenOf(type).stream().map(child -> StructureMember.of(child, null).withBindingKey(bindingKeys.get(child))).toList();
	}

	@Override
	public String bindingKeyOf(StereotypeClassElement type) {
		return bindingKeys.get(type);
	}

	@Override
	public String bindingKeyOf(StereotypeMethodElement method) {
		return bindingKeys.get(method);
	}

	/**
	 * The children of the type's beans that are rendered as members - {@code StructureViewUtil.membersOf}'s
	 * rule for the live index.
	 */
	private List<SymbolElement> childrenOf(StereotypeClassElement type) {
		return beans.getOrDefault(type, List.of()).stream()
				.flatMap(bean -> bean.getChildren().stream())
				.filter(SymbolElement.class::isInstance)
				.map(SymbolElement.class::cast)
				.toList();
	}

	@Override
	public StereotypeFactory<StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> stereotypeFactory() {
		// a tree asks for the stereotypes of the same type once per grouping level
		return cachingFactory;
	}

}
