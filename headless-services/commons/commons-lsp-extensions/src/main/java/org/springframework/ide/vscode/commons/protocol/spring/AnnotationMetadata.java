/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.protocol.spring;

import java.util.Map;

import org.eclipse.lsp4j.Location;

/**
 * @author Martin Lippert
 */
public class AnnotationMetadata {
	
	private final String annotationName;
	private final String annotationType;

	private final boolean isMetaAnnotation;
	private final Location location;
	private final Map<String, AnnotationAttributeValue[]> attributes;
	
	public AnnotationMetadata(String annotationType, boolean isMetaAnnotation, Location location, Map<String, AnnotationAttributeValue[]> attributes) {
		this(annotationType, annotationType, isMetaAnnotation, location, attributes);
	}
	
	public AnnotationMetadata(String annotationName, String annotationType, boolean isMetaAnnotation, Location location, Map<String, AnnotationAttributeValue[]> attributes) {
		this.annotationName = annotationName;
		this.annotationType = annotationType;
		this.isMetaAnnotation = isMetaAnnotation;
		this.location = location;
		this.attributes = attributes;
	}
	
	public String getAnnotationName() {
		return annotationName;
	}
	
	public String getAnnotationType() {
		return annotationType;
	}

	public boolean isMetaAnnotation() {
		return isMetaAnnotation;
	}
	
	public Location getLocation() {
		return location;
	}

	public Map<String, AnnotationAttributeValue[]> getAttributes() {
		return attributes;
	}
	
}
