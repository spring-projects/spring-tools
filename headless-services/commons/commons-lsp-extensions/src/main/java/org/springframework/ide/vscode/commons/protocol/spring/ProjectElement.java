/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.protocol.spring;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProjectElement implements SpringIndexElement {

	private String projectName;
	
	private Map<String, DocumentElement> documents;
	private List<SpringIndexElement> otherElements;

	public ProjectElement(String projectName) {
		this.projectName = projectName;
		this.documents = new ConcurrentHashMap<>();
		this.otherElements = new ArrayList<>();
	}
	
	public String getProjectName() {
		return projectName;
	}

	public void removeDocument(String docURI) {
		this.documents.remove(docURI);
	}

	@Override
	public List<SpringIndexElement> getChildren() {
		List<SpringIndexElement> result = new ArrayList<>();

		result.addAll(documents.values());
		result.addAll(otherElements);
		
		return result;
	}

	@Override
	public void addChild(SpringIndexElement child) {
		if (child instanceof DocumentElement document) {
			documents.put(document.getDocURI(), document);
		}
		else {
			otherElements.add(child);
		}
	}

	@Override
	public void removeChild(SpringIndexElement doc) {
		if (doc instanceof DocumentElement document) {
			documents.remove(document.getDocURI());
		}
		else {
			otherElements.remove(doc);
		}
	}

}
