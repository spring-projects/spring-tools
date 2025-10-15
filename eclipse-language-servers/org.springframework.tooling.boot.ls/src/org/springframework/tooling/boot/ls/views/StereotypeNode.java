/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls.views;

import java.util.Map;


/**
 * Represents a node in the logical structure tree.
 * Placeholder implementation for demonstration purposes.
 * 
 * @author Alex Boyko
 */
record StereotypeNode(StereotypeNode[] children, Map<String, Object> attributes) {
	
	private static final String PROJECT_ID = "projectId";
	
	private static final String LOCATION = "location";
	
	private static final String ICON = "icon";
	private static final String TEXT = "text";
	
	private static final String NODE_ID = "nodeId";
	
	public String getText() {
		return (String) attributes.get(TEXT);
	}
	
	public String getId() {
		return (String) attributes.get(NODE_ID);
	}

	public String getIcon() {
		return (String) attributes.get(ICON);
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof StereotypeNode) {
			return getId().equals(((StereotypeNode) obj).getId());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return getId().hashCode();
	}

	@Override
	public String toString() {
		return getId();
	}

}

