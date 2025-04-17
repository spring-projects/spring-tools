/*******************************************************************************
 * Copyright (c) 2023, 2024 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.protocol.spring;

import org.eclipse.lsp4j.Location;

/**
 * @author Martin Lippert
 */
public class InjectionPoint {
	
	private final String name;
	private final String type;
	private final Location location;
	private final AnnotationMetadata[] annotations;
	
	public InjectionPoint(String name, String type, Location location, AnnotationMetadata[] annotations) {
		super();
		
		this.name = name;
		this.type = type;
		this.location = location;
		
		if (annotations != null && annotations.length == 0) {
			this.annotations = null;
		}
		else {
			this.annotations = annotations;
		}
	}
	
	public String getName() {
		return name;
	}
	
	public String getType() {
		return type;
	}
	
	public Location getLocation() {
		return location;
	}
	
	public AnnotationMetadata[] getAnnotations() {
		return annotations == null ? DefaultValues.EMPTY_ANNOTATIONS : annotations;
	}

}
