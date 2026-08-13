/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.DocumentElement;
import org.springframework.ide.vscode.commons.protocol.spring.ProjectElement;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElementUtils;

public class SpringMetamodelIndex {
	
	private final ConcurrentMap<String, ProjectElement> projectRootElements;

	public SpringMetamodelIndex() {
		projectRootElements = new ConcurrentHashMap<>();
	}
	
	public void updateElements(String projectName, String docURI, SpringIndexElement[] elements) {
		ProjectElement project = this.projectRootElements.computeIfAbsent(projectName, name -> new ProjectElement(name));
		project.removeDocument(docURI);
		
		if (elements != null && elements.length > 0) {
			DocumentElement document = new DocumentElement(docURI);
			for (SpringIndexElement bean : elements) {
				document.addChild(bean);
			}
			
			project.addChild(document);
		}
	}

	public void removeElements(String projectName, String docURI) {
		ProjectElement project = projectRootElements.get(projectName);
		if (project != null) {
			project.removeDocument(docURI);
		}
	}
	
	public void removeProject(String projectName) {
		projectRootElements.remove(projectName);
	}
	
	public Collection<ProjectElement> getProjects() {
		return Collections.unmodifiableCollection(this.projectRootElements.values());
	}

	public DocumentElement getDocument(String docURI) {
		for (ProjectElement project : this.projectRootElements.values()) {
			DocumentElement document = project.getDocument(docURI);
			if (document != null) {
				return document;
			}
		}

		return null;
	}

	public <T extends SpringIndexElement> List<T> getNodesOfType(Class<T> type) {
		List<SpringIndexElement> rootNodes = new ArrayList<SpringIndexElement>(this.projectRootElements.values());
		return SpringIndexElementUtils.getNodesOfType(type, rootNodes);
	}

	public <T extends SpringIndexElement> List<T> getNodesOfType(String projectName, Class<T> type) {
		ProjectElement project = this.projectRootElements.get(projectName);
		return project == null ? List.of() : SpringIndexElementUtils.getNodesOfType(type, List.of(project));
	}
	
	public Bean[] getBeans() {
		List<Bean> result = new ArrayList<>();
		for (ProjectElement project : this.projectRootElements.values()) {
			result.addAll(project.getBeans());
		}
		return result.toArray(Bean[]::new);
	}

	public Bean[] getBeansOfProject(String projectName) {
		ProjectElement project = this.projectRootElements.get(projectName);
		if (project != null) {
			return project.getBeans().toArray(Bean[]::new);
		}
		else {
			return new Bean[0];
		}
	}
	
	public Bean[] getBeansOfDocument(String docURI) {
		DocumentElement document = getDocument(docURI);
		if (document != null) {
			return SpringIndexElementUtils.getNodesOfType(Bean.class, List.of(document)).toArray(Bean[]::new);
		}
		else {
			return new Bean[0];
		}
	}
	
	public Bean[] getBeansOfDocument(String docURI, String name) {
		DocumentElement document = getDocument(docURI);
		if (document != null) {
			return SpringIndexElementUtils.getNodesOfType(Bean.class, List.of(document), bean -> bean.getName().equals(name)).toArray(Bean[]::new);
		}
		else {
			return new Bean[0];
		}
	}
	
	public Bean[] getBeansWithName(String projectName, String name) {
		ProjectElement project = this.projectRootElements.get(projectName);
		if (project != null) {
			return project.getBeans().stream().filter(bean -> bean.getName().equals(name)).toArray(Bean[]::new);
		}
		else {
			return new Bean[0];
		}
	}

	public Bean[] getBeansWithType(String projectName, String type) {
		ProjectElement project = this.projectRootElements.get(projectName);
		if (project != null) {
			return project.getBeans().stream().filter(bean -> bean.getType().equals(type)).toArray(Bean[]::new);
		}
		else {
			return new Bean[0];
		}
	}
	
	public Bean getParentBean(Bean bean) {
		Bean[] beansOfDocument = getBeansOfDocument(bean.getLocation().getUri());
		
		for (Bean candidateBean : beansOfDocument) {
			if (candidateBean.getChildren().contains(bean)) {
				return candidateBean;
			}
		}
		
		return null;
	}

	public Bean[] getMatchingBeans(String projectName, String matchType) {
		ProjectElement project = this.projectRootElements.get(projectName);
		if (project != null) {
			return project.getBeans().stream().filter(bean -> bean.isTypeCompatibleWith(matchType)).toArray(Bean[]::new);
		}
		else {
			return new Bean[0];
		}
	}

	//
	// for test purposes
	//
	
	public void updateBeans(String projectName, Bean[] beanDefinitions) {
		ProjectElement projectRoot = new ProjectElement(projectName);
		
		Map<String, DocumentElement> documents = new HashMap<>();
		for (Bean bean : beanDefinitions) {
			String docURI = bean.getLocation() != null ? bean.getLocation().getUri() : null;
			
			if (docURI != null) {
				
				DocumentElement document = documents.computeIfAbsent(docURI, uri -> {
					DocumentElement newDocument = new DocumentElement(uri);
					projectRoot.addChild(newDocument);
					return newDocument;
				});

				document.addChild(bean);
			}
			else {
				projectRoot.addChild(bean);
			}
		}
		
		projectRootElements.put(projectName, projectRoot);
	}

}
