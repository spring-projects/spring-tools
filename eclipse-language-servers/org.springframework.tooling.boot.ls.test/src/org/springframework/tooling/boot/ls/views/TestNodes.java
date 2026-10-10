/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls.views;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds the nodes the tests work with.
 * 
 * @author Martin Lippert
 */
final class TestNodes {

	private TestNodes() {
	}

	static StereotypeNode node(String id, Map<String, Object> attributes, StereotypeNode... children) {
		return new StereotypeNode(id, id, null, null, null, attributes, children);
	}

	static StereotypeNode node(String id, StereotypeNode... children) {
		return node(id, Map.of(), children);
	}

	static StereotypeNode changed(String id, String change, StereotypeNode... children) {
		return node(id, Map.of(StereotypeNode.CHANGE, change), children);
	}

	/**
	 * The root of a project's tree.
	 */
	static StereotypeNode project(String name, StereotypeNode... children) {
		return node(name, Map.of(StereotypeNodeDeserializer.PROJECT_ID, name), children);
	}

	static StereotypeNode projectWithBaseline(String name, boolean hasBaseline, StereotypeNode... children) {
		Map<String, Object> attributes = new HashMap<>();
		attributes.put(StereotypeNodeDeserializer.PROJECT_ID, name);
		attributes.put(StereotypeNode.HAS_BASELINE, hasBaseline);
		return node(name, attributes, children);
	}

}
