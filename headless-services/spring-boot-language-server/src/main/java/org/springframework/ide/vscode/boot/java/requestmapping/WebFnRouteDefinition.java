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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Range;

public class WebFnRouteDefinition {

	private WebfluxRouteElement path;
	private List<WebfluxRouteElement> pathElements = new ArrayList<>();

	private List<WebfluxRouteElement> httpMethods = new ArrayList<>();
	private List<WebfluxRouteElement> acceptTypes = new ArrayList<>();
	private List<WebfluxRouteElement> contentTypes = new ArrayList<>();
	private WebfluxRouteElement version;

	private String handlerClass;
	private String handlerMethod;

	private Map<String, WebfluxRouteElement> predicates = new HashMap<>();
	private Range range;

	private int nestingLevel = 0;

	public WebfluxRouteElement getPath() {
		return path;
	}

	public void setPath(WebfluxRouteElement path) {
		this.path = path;
	}

	public WebfluxRouteElement[] getPathElements() {
		return pathElements.toArray(new WebfluxRouteElement[0]);
	}

	public void addPathElement(WebfluxRouteElement pathElement) {
		this.pathElements.add(pathElement);
	}

	public WebfluxRouteElement[] getHttpMethods() {
		return httpMethods.toArray(new WebfluxRouteElement[0]);
	}

	public void addHttpMethod(WebfluxRouteElement httpMethod) {
		this.httpMethods.add(httpMethod);
	}

	public WebfluxRouteElement[] getAcceptTypes() {
		return acceptTypes.toArray(new WebfluxRouteElement[0]);
	}

	public void addAcceptType(WebfluxRouteElement acceptType) {
		this.acceptTypes.add(acceptType);
	}
	
	public void resetAcceptTypes() {
		this.acceptTypes.clear();
	}

	public WebfluxRouteElement[] getContentTypes() {
		return contentTypes.toArray(new WebfluxRouteElement[0]);
	}

	public void addContentType(WebfluxRouteElement contentType) {
		this.contentTypes.add(contentType);
	}

	public void resetContentTypes() {
		this.contentTypes.clear();
	}

	public WebfluxRouteElement getVersion() {
		return version;
	}

	public void setVersion(WebfluxRouteElement version) {
		this.version = version;
	}

	public String getHandlerClass() {
		return handlerClass;
	}

	public void setHandlerClass(String handlerClass) {
		this.handlerClass = handlerClass;
	}

	public String getHandlerMethod() {
		return handlerMethod;
	}

	public void setHandlerMethod(String handlerMethod) {
		this.handlerMethod = handlerMethod;
	}

	public Map<String, WebfluxRouteElement> getPredicates() {
		return predicates;
	}

	public void addPredicate(String key, WebfluxRouteElement value) {
		this.predicates.put(key, value);
	}

	public Range getRange() {
		return range;
	}

	public void setRange(Range range) {
		this.range = range;
	}

	public int getNestingLevel() {
		return nestingLevel;
	}

	public void setNestingLevel(int level) {
		this.nestingLevel = level;
	}
	
	//
	// for testing purposes
	//

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("  ".repeat(nestingLevel)); // Indent by nesting level
		sb.append("Route:\n");

		if (!httpMethods.isEmpty()) {
			String methodsStr = httpMethods.stream().map(WebfluxRouteElement::getElement).reduce((a, b) -> a + ", " + b)
					.orElse("N/A");
			sb.append("  ".repeat(nestingLevel + 1)).append("Methods:  ").append(methodsStr).append("\n");
		} else {
			sb.append("  ".repeat(nestingLevel + 1)).append("Methods:  N/A\n");
		}

		sb.append("  ".repeat(nestingLevel + 1)).append("Path:     ").append(path != null ? path.getElement() : "N/A")
				.append("\n");

		if (!pathElements.isEmpty()) {
			String pathStr = pathElements.stream().map(WebfluxRouteElement::getElement).reduce((a, b) -> a + b)
					.orElse("");
			sb.append("  ".repeat(nestingLevel + 1)).append("FullPath: ").append(pathStr).append("\n");
		}

		if (!acceptTypes.isEmpty()) {
			String acceptStr = acceptTypes.stream().map(WebfluxRouteElement::getElement).reduce((a, b) -> a + ", " + b)
					.orElse("");
			sb.append("  ".repeat(nestingLevel + 1)).append("Accept:   ").append(acceptStr).append("\n");
		}

		if (!contentTypes.isEmpty()) {
			String contentStr = contentTypes.stream().map(WebfluxRouteElement::getElement)
					.reduce((a, b) -> a + ", " + b).orElse("");
			sb.append("  ".repeat(nestingLevel + 1)).append("Content:  ").append(contentStr).append("\n");
		}

		if (version != null) {
			sb.append("  ".repeat(nestingLevel + 1)).append("Version:  ").append(version.getElement()).append("\n");
		}

		String handlerStr = "N/A";
		if (handlerClass != null && handlerMethod != null) {
			handlerStr = handlerClass + "::" + handlerMethod;
		} else if (handlerClass != null) {
			handlerStr = handlerClass;
		} else if (handlerMethod != null) {
			handlerStr = handlerMethod;
		}
		sb.append("  ".repeat(nestingLevel + 1)).append("Handler:  ").append(handlerStr).append("\n");

		return sb.toString();
	}

}