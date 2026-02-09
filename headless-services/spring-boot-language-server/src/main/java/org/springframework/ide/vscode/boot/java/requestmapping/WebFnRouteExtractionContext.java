/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping;

import java.util.ArrayList;
import java.util.List;

/**
 * Context for tracking nested paths and predicates
 */
public class WebFnRouteExtractionContext {
	
    private List<WebfluxRouteElement> pathPrefixes = new ArrayList<>();
    private List<List<WebfluxRouteElement>> nestedAcceptTypes = new ArrayList<>();
    private List<List<WebfluxRouteElement>> nestedContentTypes = new ArrayList<>();
    private List<WebfluxRouteElement> nestedVersions = new ArrayList<>();
    private List<List<String>> nestedMethods = new ArrayList<>();
    
    private int nestingLevel = 0;
    
    public List<WebfluxRouteElement> getAllPathElements(WebfluxRouteElement routePath) {
        List<WebfluxRouteElement> allElements = new ArrayList<>();
        allElements.addAll(pathPrefixes);
        if (routePath != null && !routePath.getElement().isEmpty()) {
            allElements.add(routePath);
        }
        return allElements;
    }
    
	public List<WebfluxRouteElement> getPathPrefixes() {
		return this.pathPrefixes;
	}
    
    public List<WebfluxRouteElement> getCurrentAcceptTypes() {
        List<WebfluxRouteElement> result = new ArrayList<>();
        for (List<WebfluxRouteElement> types : nestedAcceptTypes) {
            result.addAll(types);
        }
        return result;
    }
    
    public List<WebfluxRouteElement> getCurrentContentTypes() {
        List<WebfluxRouteElement> result = new ArrayList<>();
        for (List<WebfluxRouteElement> types : nestedContentTypes) {
            result.addAll(types);
        }
        return result;
    }
    
    public WebfluxRouteElement getCurrentVersion() {
        // Return the most recent (innermost) version if any
        return nestedVersions.isEmpty() ? null : nestedVersions.get(nestedVersions.size() - 1);
    }
    
    public void pushPathPrefix(WebfluxRouteElement prefix) {
        pathPrefixes.add(prefix);
        nestingLevel++;
    }
    
    public void popPathPrefix() {
        if (!pathPrefixes.isEmpty()) {
            pathPrefixes.remove(pathPrefixes.size() - 1);
            nestingLevel--;
        }
    }
    
    public void pushAcceptTypes(List<WebfluxRouteElement> types) {
        nestedAcceptTypes.add(new ArrayList<>(types));
    }
    
    public void popAcceptTypes() {
        if (!nestedAcceptTypes.isEmpty()) {
            nestedAcceptTypes.remove(nestedAcceptTypes.size() - 1);
        }
    }
    
    public void pushContentTypes(List<WebfluxRouteElement> types) {
        nestedContentTypes.add(new ArrayList<>(types));
    }
    
    public void popContentTypes() {
        if (!nestedContentTypes.isEmpty()) {
            nestedContentTypes.remove(nestedContentTypes.size() - 1);
        }
    }
    
    public void pushVersion(WebfluxRouteElement version) {
        if (version != null) {
            nestedVersions.add(version);
        }
    }
    
    public void popVersion() {
        if (!nestedVersions.isEmpty()) {
            nestedVersions.remove(nestedVersions.size() - 1);
        }
    }
    
    public List<String> getCurrentMethods() {
        List<String> result = new ArrayList<>();
        for (List<String> methods : nestedMethods) {
            result.addAll(methods);
        }
        return result;
    }
    
    public void pushMethods(List<String> methods) {
        nestedMethods.add(new ArrayList<>(methods));
    }
    
    public void popMethods() {
        if (!nestedMethods.isEmpty()) {
            nestedMethods.remove(nestedMethods.size() - 1);
        }
    }

	public int getNestingLevel() {
		return nestingLevel;
	}

}