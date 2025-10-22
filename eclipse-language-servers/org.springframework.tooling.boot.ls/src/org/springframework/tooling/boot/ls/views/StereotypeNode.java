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

import org.eclipse.lsp4j.Location;

/**
 * Represents a node in the logical structure tree.
 * Placeholder implementation for demonstration purposes.
 * 
 * @author Alex Boyko
 */
record StereotypeNode(String id, String text, String icon, Location location, Location reference, Map<String, Object> attributes, StereotypeNode[] children) {
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof StereotypeNode) {
			return id().equals(((StereotypeNode) obj).id());
		}
		return false;
	}

	@Override
	public int hashCode() {
		return id().hashCode();
	}

	@Override
	public String toString() {
		return text;
	}

}

