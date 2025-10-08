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
package org.springframework.ide.vscode.boot.java.reconcilers;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigPropertiesIndexer;
import org.springframework.ide.vscode.commons.java.IJavaProject;

public class ReconcilingIndex {
	
	private Map<String, Object> cache = new ConcurrentHashMap<>();
	
	public ReconcilingIndex() {
	}
	
	@SuppressWarnings("unchecked")
	public List<WebConfigIndexElement> getWebConfigProperties(IJavaProject project) {
		return (List<WebConfigIndexElement>) cache.computeIfAbsent("webproperties - " + project.getElementName(),
				(n) -> new WebConfigPropertiesIndexer().findWebConfigFromProperties(project));
	}
	
}
