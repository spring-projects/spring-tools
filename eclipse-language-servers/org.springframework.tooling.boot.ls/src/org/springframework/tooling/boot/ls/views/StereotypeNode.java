/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom, Inc.
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
 *
 * The node knows its parent, which is what the attributes that only the root of a project's tree
 * carries (the baseline information) are looked up through.
 *
 * @author Alex Boyko
 */
final class StereotypeNode {

	/**
	 * How a node itself changed since the baseline of its project - only set for the node a change
	 * actually happened to, never for the packages and groups above it.
	 */
	enum Change {
		ADDED, REMOVED, MODIFIED;

		static Change parse(Object value) {
			if (value instanceof String s) {
				switch (s) {
				case "added":
					return ADDED;
				case "removed":
					return REMOVED;
				case "modified":
					return MODIFIED;
				}
			}
			return null;
		}
	}

	/**
	 * What a node for an element read from a JAR dependency carries instead of a location: the
	 * element's JDT binding key and the project whose classpath has the JAR.
	 */
	record JavaElementReference(String projectUri, String bindingKey) {}

	static final String CHANGE = "change";
	static final String HAS_BASELINE = "hasBaseline";
	static final String COMPARED_AGAINST_SHA = "comparedAgainstSha";
	static final String COMPARED_AGAINST_MESSAGE = "comparedAgainstMessage";
	static final String COMPARED_AGAINST_CAPTURED_AT = "comparedAgainstCapturedAt";
	static final String JAVA_ELEMENT = "javaElement";
	static final String HOVER = "hover";
	static final String KIND = "kind";

	private final String id;
	private final String text;
	private final String icon;
	private final Location location;
	private final Location reference;
	private final Map<String, Object> attributes;
	private final StereotypeNode[] children;

	private StereotypeNode parent;

	StereotypeNode(String id, String text, String icon, Location location, Location reference,
			Map<String, Object> attributes, StereotypeNode[] children) {
		this.id = id;
		this.text = text;
		this.icon = icon;
		this.location = location;
		this.reference = reference;
		this.attributes = attributes == null ? Map.of() : attributes;
		this.children = children == null ? new StereotypeNode[0] : children;
		for (StereotypeNode child : this.children) {
			child.parent = this;
		}
	}

	String id() {
		return id;
	}

	String text() {
		return text;
	}

	String icon() {
		return icon;
	}

	Location location() {
		return location;
	}

	Location reference() {
		return reference;
	}

	Map<String, Object> attributes() {
		return attributes;
	}

	StereotypeNode[] children() {
		return children;
	}

	StereotypeNode parent() {
		return parent;
	}

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

	public String getProjectId() {
		return attributes.containsKey(StereotypeNodeDeserializer.PROJECT_ID)
				? (String) attributes.get(StereotypeNodeDeserializer.PROJECT_ID)
				: null;
	}

	/**
	 * Whether this is the root node of a project's tree.
	 */
	boolean isProject() {
		return getProjectId() != null;
	}

	/**
	 * How this node itself changed since the baseline of its project, if it did.
	 */
	Change change() {
		return Change.parse(attributes.get(CHANGE));
	}

	/**
	 * Whether this node changed, or anything below it did. This is what keeps the path to a change
	 * visible while unchanged nodes are hidden, even though the containers along it are not
	 * highlighted themselves.
	 */
	boolean containsChanges() {
		return attributes.get(CHANGE) != null;
	}

	/**
	 * Whether the tree this node belongs to says anything about baselines at all - it does not when
	 * it was built with dependencies included, which are never compared against a baseline. Only
	 * the root of a project's tree carries the baseline attributes.
	 */
	boolean hasBaselineInformation() {
		return root().attributes.get(HAS_BASELINE) != null;
	}

	/**
	 * Whether a baseline was captured for the project this node belongs to, regardless of whether
	 * anything changed since then.
	 */
	boolean hasBaseline() {
		return Boolean.TRUE.equals(root().attributes.get(HAS_BASELINE));
	}

	/**
	 * The commit sha of the baseline the project's tree was compared against, if it has one.
	 */
	String comparedAgainstSha() {
		return rootString(COMPARED_AGAINST_SHA);
	}

	String comparedAgainstMessage() {
		return rootString(COMPARED_AGAINST_MESSAGE);
	}

	/**
	 * When the baseline the project's tree was compared against was captured - the only thing
	 * identifying a manually captured one, which has no commit.
	 */
	String comparedAgainstCapturedAt() {
		return rootString(COMPARED_AGAINST_CAPTURED_AT);
	}

	/**
	 * The element this node stands for when it has no location - one read from a JAR dependency.
	 */
	JavaElementReference javaElement() {
		Object value = attributes.get(JAVA_ELEMENT);
		if (value instanceof Map<?, ?> map && map.get("bindingKey") instanceof String bindingKey) {
			Object projectUri = map.get("projectUri");
			return new JavaElementReference(projectUri instanceof String s ? s : null, bindingKey);
		}
		return null;
	}

	String hover() {
		return attributes.get(HOVER) instanceof String s ? s : null;
	}

	private StereotypeNode root() {
		StereotypeNode node = this;
		while (node.parent != null) {
			node = node.parent;
		}
		return node;
	}

	private String rootString(String key) {
		return root().attributes.get(key) instanceof String s ? s : null;
	}

}
