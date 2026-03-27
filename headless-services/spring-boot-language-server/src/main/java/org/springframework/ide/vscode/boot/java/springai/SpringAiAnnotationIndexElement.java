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
package org.springframework.ide.vscode.boot.java.springai;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.SymbolKind;
import org.springframework.ide.vscode.commons.protocol.spring.AbstractSpringIndexElement;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.SymbolElement;

/**
 * Generic index element for Spring AI method-level annotations:
 * {@code @Tool}, {@code @McpTool}, {@code @McpPrompt}, {@code @McpResource},
 * {@code @McpComplete}, {@code @McpElicitation}, {@code @McpSampling}.
 * <p>
 * Use {@link #getAnnotationType()} to determine the specific annotation.
 *
 * @author Martin Lippert
 */
public class SpringAiAnnotationIndexElement extends AbstractSpringIndexElement implements SymbolElement {

	public enum AnnotationType {
		TOOL, MCP_TOOL, MCP_PROMPT, MCP_RESOURCE, MCP_COMPLETE, MCP_ELICITATION, MCP_SAMPLING
	}

	private final AnnotationType annotationType;
	private final String name;
	private final String description;
	private final String methodSignature;
	private final Location location;
	private final String containerBeanType;
	private final AnnotationMetadata[] annotations;

	public SpringAiAnnotationIndexElement(AnnotationType annotationType, String name, String description,
			String methodSignature, Location location, String containerBeanType, AnnotationMetadata[] annotations) {
		this.annotationType = annotationType;
		this.name = name;
		this.description = description;
		this.methodSignature = methodSignature;
		this.location = location;
		this.containerBeanType = containerBeanType;
		this.annotations = annotations;
	}

	public AnnotationType getAnnotationType() {
		return annotationType;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getMethodSignature() {
		return methodSignature;
	}

	public Location getLocation() {
		return location;
	}

	public String getContainerBeanType() {
		return containerBeanType;
	}

	public AnnotationMetadata[] getAnnotations() {
		return annotations;
	}

	@Override
	public DocumentSymbol getDocumentSymbol() {
		String prefix = switch (annotationType) {
			case TOOL -> "@Tool ";
			case MCP_TOOL -> "@McpTool ";
			case MCP_PROMPT -> "@McpPrompt ";
			case MCP_RESOURCE -> "@McpResource ";
			case MCP_COMPLETE -> "@McpComplete ";
			case MCP_ELICITATION -> "@McpElicitation ";
			case MCP_SAMPLING -> "@McpSampling ";
		};
		DocumentSymbol symbol = new DocumentSymbol();
		symbol.setName(prefix + name);
		symbol.setKind(SymbolKind.Function);
		symbol.setRange(location.getRange());
		symbol.setSelectionRange(location.getRange());
		return symbol;
	}

}
