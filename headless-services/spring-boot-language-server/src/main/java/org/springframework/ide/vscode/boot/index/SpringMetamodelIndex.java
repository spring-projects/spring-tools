/*******************************************************************************
 * Copyright (c) 2023, 2025 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.index;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.DocumentElement;
import org.springframework.ide.vscode.commons.protocol.spring.ProjectElement;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;

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

	public DocumentElement getDocument(String docURI) {
		List<SpringIndexElement> rootNodes = new ArrayList<SpringIndexElement>(this.projectRootElements.values());
		List<DocumentElement> documents = getNodesOfType(DocumentElement.class, rootNodes, document -> document.getDocURI().equals(docURI));

		if (documents.size() == 1) {
			return documents.get(0);
		}
		else {
			return null;
		}
	}

	public <T extends SpringIndexElement> List<T> getNodesOfType(Class<T> type) {
		List<SpringIndexElement> rootNodes = new ArrayList<SpringIndexElement>(this.projectRootElements.values());
		return getNodesOfType(type, rootNodes);
	}

	public Bean[] getBeans() {
		List<SpringIndexElement> rootNodes = new ArrayList<SpringIndexElement>(this.projectRootElements.values());
		return getNodesOfType(Bean.class, rootNodes).toArray(Bean[]::new);
	}

	public Bean[] getBeansOfProject(String projectName) {
		ProjectElement project = this.projectRootElements.get(projectName);
		if (project != null) {
			return getNodesOfType(Bean.class, List.of(project)).toArray(Bean[]::new);
		}
		else {
			return new Bean[0];
		}
	}
	
	public Bean[] getBeansOfDocument(String docURI) {
		DocumentElement document = getDocument(docURI);
		if (document != null) {
			return getNodesOfType(Bean.class, List.of(document)).toArray(Bean[]::new);
		}
		else {
			return new Bean[0];
		}
	}
	
	public Bean[] getBeansWithName(String projectName, String name) {
		ProjectElement project = this.projectRootElements.get(projectName);
		if (project != null) {
			return getNodesOfType(Bean.class, List.of(project), bean -> bean.getName().equals(name)).toArray(Bean[]::new);
		}
		else {
			return new Bean[0];
		}
	}

	public Bean[] getBeansWithType(String projectName, String type) {
		ProjectElement project = this.projectRootElements.get(projectName);
		if (project != null) {
			return getNodesOfType(Bean.class, List.of(project), bean -> bean.getType().equals(type)).toArray(Bean[]::new);
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
			return getNodesOfType(Bean.class, List.of(project), bean -> bean.isTypeCompatibleWith(matchType)).toArray(Bean[]::new);
		}
		else {
			return new Bean[0];
		}
	}

	public static <T extends SpringIndexElement> List<T> getNodesOfType(Class<T> type, Collection<SpringIndexElement> rootNodes) {
		return getNodesOfType(type, rootNodes, element -> true);
	}

	public static <T extends SpringIndexElement> List<T> getNodesOfType(Class<T> type, Collection<SpringIndexElement> rootNodes, Predicate<T> predicate) {
		List<T> result = new ArrayList<>();
		
		ArrayDeque<SpringIndexElement> elementsToVisit = new ArrayDeque<>();
		elementsToVisit.addAll(rootNodes);
		
		while (!elementsToVisit.isEmpty()) {
			SpringIndexElement element = elementsToVisit.pop();

			if (type.isInstance(element) && predicate.test(type.cast(element))) {
				result.add(type.cast(element));
			}
			
			elementsToVisit.addAll(element.getChildren());
		}
		
		return result;
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
