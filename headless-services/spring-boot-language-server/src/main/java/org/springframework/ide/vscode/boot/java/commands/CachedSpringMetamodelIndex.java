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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypePackageElement;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;

public class CachedSpringMetamodelIndex {
	
	private final SpringMetamodelIndex springIndex;
	private final ConcurrentMap<String, ProjectCache> cache;
	
	public CachedSpringMetamodelIndex (SpringMetamodelIndex springIndex) {
		this.springIndex = springIndex;
		this.cache = new ConcurrentHashMap<>();
	}

	public List<StereotypeClassElement> getClassesForProject(String projectName) {
		return this.cache.computeIfAbsent(projectName, pn -> createProjectCache(pn)).classes;
	}

	public StereotypePackageElement findPackageNode(String packageName, String projectName) {
		return this.cache.computeIfAbsent(projectName, pn -> createProjectCache(pn)).packages.get(packageName);
	}
	
	private ProjectCache createProjectCache(String projectName) {
		var classes = this.springIndex.getNodesOfType(projectName, StereotypeClassElement.class);

		var packages = new ConcurrentHashMap<String, StereotypePackageElement>();
		List<StereotypePackageElement> packageNodes = this.springIndex.getNodesOfType(projectName, StereotypePackageElement.class);
		for (StereotypePackageElement packageNode : packageNodes) {
			packages.put(packageNode.getPackageName(), packageNode);
		}

		return new ProjectCache(classes, packages);
	}
	
	public <T extends SpringIndexElement> List<T> getNodesOfType(String projectName, Class<T> type) {
		return springIndex.getNodesOfType(projectName, type);
	}
	
	private record ProjectCache(List<StereotypeClassElement> classes, ConcurrentMap<String, StereotypePackageElement> packages) {}

}
