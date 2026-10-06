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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.beans.SpringBootApplicationIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;

public class CachedSpringMetamodelIndex {
	
	private final SpringMetamodelIndex springIndex;
	private final ConcurrentMap<String, ProjectCache> cache;
	private final ConcurrentMap<String, Map<String, String>> requestMappingLabels;
	
	public CachedSpringMetamodelIndex (SpringMetamodelIndex springIndex) {
		this.springIndex = springIndex;
		this.cache = new ConcurrentHashMap<>();
		this.requestMappingLabels = new ConcurrentHashMap<>();
	}

	public List<StereotypeClassElement> getClassesForProject(String projectName) {
		return this.cache.computeIfAbsent(projectName, pn -> createProjectCache(pn)).classes;
	}

	public StereotypePackageElement findPackageNode(String packageName, String projectName) {
		return this.cache.computeIfAbsent(projectName, pn -> createProjectCache(pn)).packages.get(packageName);
	}
	
	public List<SpringBootApplicationIndexElement> getSpringBootApplicationElementsForProject(String projectName) {
		return this.cache.computeIfAbsent(projectName, pn -> createProjectCache(pn)).springBootAppElements;
	}
	
	private ProjectCache createProjectCache(String projectName) {
		var classes = this.springIndex.getNodesOfType(projectName, StereotypeClassElement.class);

		var packages = new ConcurrentHashMap<String, StereotypePackageElement>();
		List<StereotypePackageElement> packageNodes = this.springIndex.getNodesOfType(projectName, StereotypePackageElement.class);
		for (StereotypePackageElement packageNode : packageNodes) {
			packages.put(packageNode.getPackageName(), packageNode);
		}

		var springBootAppElements = this.springIndex.getNodesOfType(projectName, SpringBootApplicationIndexElement.class);

		return new ProjectCache(classes, packages, springBootAppElements);
	}
	
	/**
	 * The label of the project's request mapping declared by the method with the given signature, or
	 * {@code null} if there is none - the first one in index order, if several share the signature.
	 *
	 * <p>Looked up once per project, rather than walking the whole project index again for every
	 * method a structure tree labels.
	 */
	public String getRequestMappingLabel(String projectName, String methodSignature) {
		if (methodSignature == null) {
			return null;
		}
		return this.requestMappingLabels.computeIfAbsent(projectName, pn -> createRequestMappingLabels(pn)).get(methodSignature);
	}

	private Map<String, String> createRequestMappingLabels(String projectName) {
		Map<String, String> labels = new HashMap<>();
		for (RequestMappingIndexElement mapping : this.springIndex.getNodesOfType(projectName, RequestMappingIndexElement.class)) {
			if (mapping.getMethodSignature() != null) {
				labels.putIfAbsent(mapping.getMethodSignature(), mapping.getDocumentSymbol().getName());
			}
		}
		return labels;
	}

	public <T extends SpringIndexElement> List<T> getNodesOfType(String projectName, Class<T> type) {
		return springIndex.getNodesOfType(projectName, type);
	}
	
	public Bean[] getBeansOfDocument(String docUri) {
		return springIndex.getBeansOfDocument(docUri);
	}
	
	private record ProjectCache(List<StereotypeClassElement> classes, ConcurrentMap<String, StereotypePackageElement> packages,
			List<SpringBootApplicationIndexElement> springBootAppElements) {}

}
