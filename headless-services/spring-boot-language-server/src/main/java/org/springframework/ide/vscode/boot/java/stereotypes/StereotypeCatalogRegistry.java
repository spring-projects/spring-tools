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
package org.springframework.ide.vscode.boot.java.stereotypes;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.jmolecules.stereotype.catalog.support.JsonPathStereotypeCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;

public class StereotypeCatalogRegistry {

	private static final Logger log = LoggerFactory.getLogger(StereotypeCatalogRegistry.class);

	private static final String COMPOSED_KEY_SEPARATOR = "\u0000";

	private final Map<String, AbstractStereotypeCatalog> catalogs;
	
	public StereotypeCatalogRegistry(ProjectObserver projectObserver) {
		this.catalogs = new ConcurrentHashMap<>();
		registerProjectListener(projectObserver);
	}
	
	public AbstractStereotypeCatalog getCatalogOf(IJavaProject project) {
		return catalogs.computeIfAbsent(project.getElementName(), (p) -> createCatalog(project));
	}

	/**
	 * A catalog for the project's structure tree with the given dependencies included - separate
	 * from {@link #getCatalogOf(IJavaProject)}, because the source-defined stereotypes of the
	 * dependencies get registered with it. Registered into the project's own catalog, they would
	 * linger there after the dependencies are excluded again and change the project's own tree
	 * depending on what was included earlier. Reset together with the project's own catalog.
	 *
	 * @param includedDependencyIds identifies the set of included dependencies - the same set
	 *        yields the same catalog, whatever the order
	 */
	public AbstractStereotypeCatalog getCatalogOf(IJavaProject project, Collection<String> includedDependencyIds) {
		if (includedDependencyIds == null || includedDependencyIds.isEmpty()) {
			return getCatalogOf(project);
		}

		String key = project.getElementName() + COMPOSED_KEY_SEPARATOR
				+ includedDependencyIds.stream().sorted().distinct().collect(Collectors.joining(","));
		return catalogs.computeIfAbsent(key, (p) -> createCatalog(project));
	}

	private AbstractStereotypeCatalog createCatalog(IJavaProject project) {
    	var source = new ProjectBasedCatalogSource(project);
		return new JsonPathStereotypeCatalog(source);
	}

	public void reset(IJavaProject project) {
		this.catalogs.remove(project.getElementName());
		this.catalogs.keySet().removeIf(key -> key.startsWith(project.getElementName() + COMPOSED_KEY_SEPARATOR));
		log.info("stereotype catalog registry reset for project '" + project.getElementName() + "'");
	}

	// reset cache when projects change or disappear
	private void registerProjectListener(ProjectObserver projectObserver) {
		projectObserver.addListener(new ProjectObserver.Listener() {
			
			@Override
			public void deleted(IJavaProject project) {
				reset(project);
			}
			
			@Override
			public void created(IJavaProject project) {
			}
			
			@Override
			public void changed(IJavaProject project, boolean clean) {
				reset(project);
			}
		});
	}

}
