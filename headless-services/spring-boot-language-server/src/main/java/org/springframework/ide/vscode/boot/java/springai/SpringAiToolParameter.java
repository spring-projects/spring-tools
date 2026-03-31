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

import org.eclipse.lsp4j.Location;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;

/**
 * Represents a single parameter of a Spring AI tool method (e.g. a method annotated
 * with {@code @Tool} or {@code @McpTool}), capturing the information needed to
 * reconstruct the tool's input schema.
 *
 * @author Martin Lippert
 */
public final class SpringAiToolParameter {

	private final String name;
	private final String type;
	private final String description;
	private final Location location;
	private final AnnotationMetadata[] annotations;

	public SpringAiToolParameter(String name, String type, String description, Location location,
			AnnotationMetadata[] annotations) {
		this.name = name;
		this.type = type;
		this.description = description;
		this.location = location;
		this.annotations = annotations != null && annotations.length == 0 ? null : annotations;
	}

	public String getName() {
		return name;
	}

	public String getType() {
		return type;
	}

	/**
	 * A human-readable description of the parameter, extracted from a parameter-level
	 * annotation when present. May be {@code null} if no description annotation was found.
	 */
	public String getDescription() {
		return description;
	}

	public Location getLocation() {
		return location;
	}

	public AnnotationMetadata[] getAnnotations() {
		return annotations != null ? annotations : new AnnotationMetadata[0];
	}

}
