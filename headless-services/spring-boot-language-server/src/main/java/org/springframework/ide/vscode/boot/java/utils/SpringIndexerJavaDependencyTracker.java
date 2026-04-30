/*******************************************************************************
 * Copyright (c) 2019, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.commons.java.IJavaProject;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;

public class SpringIndexerJavaDependencyTracker {
	
	private static final Logger log = LoggerFactory.getLogger(SpringIndexerJavaDependencyTracker.class);

	private Map<String, Multimap<String, String>> dependenciesByProject = new ConcurrentHashMap<>();
	
	public void dump(IJavaProject project) {
		Multimap<String, String> dependencies = getDependenciesForProject(project);
		log.info("=== Dependencies for project: {} ===", project.getElementName());
		for (String sourceFile : dependencies.keySet()) {
			Collection<String> values = dependencies.get(sourceFile);
			if (!values.isEmpty()) {
				log.info("{}=> ", sourceFile);
				for (String v : values) {
					log.info("   {}", v);
				}
			}
		}
		log.info("======================");
	}

	public Multimap<String, String> getAllDependencies(IJavaProject project) {
		return Multimaps.unmodifiableMultimap(getDependenciesForProject(project));
	}

	public Set<String> getDependenciesForFile(IJavaProject project, String file) {
		return Set.copyOf(getDependenciesForProject(project).get(file));
	}

	public void addDependencies(IJavaProject project, String file, Iterable<String> dependencies) {
		if (dependencies != null) {
			getDependenciesForProject(project).putAll(file, dependencies);
		}
	}

	public void update(IJavaProject project, String file, Set<String> dependenciesForFile) {
		getDependenciesForProject(project).replaceValues(file, dependenciesForFile);
	}

	public void restore(IJavaProject project, Multimap<String, String> deps) {
		Multimap<String, String> copy = MultimapBuilder.hashKeys().hashSetValues().build();
		if (deps != null) {
			copy.putAll(deps);
		}

		dependenciesByProject.put(project.getElementName(), copy);
	}
	
	public void removeProject(IJavaProject project) {
		dependenciesByProject.remove(project.getElementName());
	}

	public void removeFiles(IJavaProject project, String[] absolutePaths) {
		if (absolutePaths == null || absolutePaths.length == 0) {
			return;
		}

		Multimap<String, String> deps = dependenciesByProject.get(project.getElementName());
		if (deps != null) {
			for (String file : absolutePaths) {
				deps.removeAll(file);
			}
		}
	}
	
	private Multimap<String, String> getDependenciesForProject(IJavaProject project) {
		return dependenciesByProject.computeIfAbsent(
			project.getElementName(), 
			k -> MultimapBuilder.hashKeys().hashSetValues().build()
		);
	}
}