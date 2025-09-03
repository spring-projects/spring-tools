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
package org.springframework.ide.vscode.boot.java.commands;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Node {

	transient final Node parent;
	final Map<String, Object> attributes;
	final List<Node> children;

	Node(Node parent) {
		this.parent = parent;
		this.attributes = new LinkedHashMap<>();
		this.children = new ArrayList<>();
	}

	public Node withAttribute(String key, Object value) {
		this.attributes.put(key, value);
		return this;
	}
}
