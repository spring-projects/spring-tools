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
package org.springframework.ide.vscode.boot.java.stereotypes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.jmolecules.stereotype.catalog.support.JsonPathStereotypeCatalog;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public class StereotypeCatalogRegistry {
	
	private final Map<String, AbstractStereotypeCatalog> catalogs;
	
	public StereotypeCatalogRegistry() {
		this.catalogs = new ConcurrentHashMap<>();
	}
	
	public AbstractStereotypeCatalog getCatalogOf(IJavaProject project) {
		return catalogs.computeIfAbsent(project.getElementName(), (p) -> createCatalog(project));
	}

	private AbstractStereotypeCatalog createCatalog(IJavaProject project) {
    	var source = new ProjectBasedCatalogSource(project);
		return new JsonPathStereotypeCatalog(source);
	}

}
