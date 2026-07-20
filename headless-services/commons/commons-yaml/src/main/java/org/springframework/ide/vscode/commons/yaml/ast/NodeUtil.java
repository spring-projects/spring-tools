/*******************************************************************************
 * Copyright (c) 2016-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.yaml.ast;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.springframework.ide.vscode.commons.util.text.DocumentRegion;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeId;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

import com.google.common.collect.ImmutableSet;

/**
 * @author Kris De Volder
 */
public class NodeUtil {

	/**
	 * Determines whether a node contains the given offset.
	 * The range a node covers between its begin and end mark is treated
	 * as a half open interval. The start offset is treated as included
	 * in the range but the end offset is excluded.
	 * <p>
	 * This is to avoid ambiguity as node ranges tend to 'join'
	 * together so that the end region of one node coincides with
	 * the start region of the next node. By treating node ranges
	 * as 'half open' intervals every offset is typically only
	 * part of two different nodes if those nodes effectively
	 * have overlapping ranges (i.e. only if one node contains
	 * the other). Thus, an operation like finding the smallest
	 * node that contains an offset is unambgious.
	 */
	public static boolean contains(Node node, int offset) {
		return getStart(node)<=offset && offset<getEnd(node);
	}

	public static int getStart(Node node) {
		return node.getStartMark().getIndex();
	}

	public static int getEnd(Node node) {
		return node.getEndMark().getIndex();
	}

	/**
	 * Retrieve String value of a scalar node.
	 * @return String value or null if node is not a Scalar node.
	 */
	public static String asScalar(Node node) {
		if (node!=null && node.getNodeId()==NodeId.scalar) {
			return ((ScalarNode)node).getValue();
		}
		return null;
	}

	public static MappingNode asMapping(Node node) {
		if (node!=null && node.getNodeId()==NodeId.mapping) {
			return (MappingNode) node;
		}
		return null;
	}

	public static SequenceNode asSequence(Node node) {
		if (node!=null && node.getNodeId()==NodeId.sequence) {
			return (SequenceNode) node;
		}
		return null;
	}

	/**
	 * Get the scalar values of all keys of the given {@link MappingNode} as Strings.
	 * Any non-scalar keys are silently ignored.
	 */
	public static Set<String> getScalarKeys(Node node) {
		if (node instanceof MappingNode) {
			MappingNode mapNode = (MappingNode) node;
			ImmutableSet.Builder<String> builder = ImmutableSet.builder();
			for (NodeTuple entry : mapNode.getValue()) {
				String key = NodeUtil.asScalar(entry.getKeyNode());
				if (key!=null) { //key not a scalar? => something funky so skip it
					builder.add(key);
				}
			}
			return builder.build();
		}
		return Collections.emptySet();
	}

	public static String getScalarProperty(Node node, String propName) {
		if (node instanceof MappingNode) {
			for (NodeTuple entry : ((MappingNode)node).getValue()) {
				String key = NodeUtil.asScalar(entry.getKeyNode());
				if (propName.equals(key)) {
					String value = asScalar(entry.getValueNode());
					if (value!=null) {
						return value;
					}
				}
			}
		}
		return null;
	}

	public static NodeTuple getPropertyTuple(Node node, String propName) {
		if (node instanceof MappingNode) {
			for (NodeTuple entry : ((MappingNode)node).getValue()) {
				String key = NodeUtil.asScalar(entry.getKeyNode());
				if (propName.equals(key)) {
					return entry;
				}
			}
		}
		return null;
	}

	public static Node getProperty(Node node, String propName) {
		if (node instanceof MappingNode) {
			for (NodeTuple entry : ((MappingNode)node).getValue()) {
				String key = NodeUtil.asScalar(entry.getKeyNode());
				if (propName.equals(key)) {
					return entry.getValueNode();
				}
			}
		}
		return null;
	}

	public static DocumentRegion region(IDocument doc, Node node) {
		return new DocumentRegion(doc, node.getStartMark().getIndex(), node.getEndMark().getIndex());
	}

	public static DocumentRegion region(IDocument doc, NodeTuple tup) {
		return new DocumentRegion(doc, tup.getKeyNode().getStartMark().getIndex(), tup.getValueNode().getEndMark().getIndex());
	}

	/**
	 * Detects whether a given map key-value pair is anchored. I.e. corresponds to
	 * a bit of yaml like this example:
	 *
	 * <pre>
	 * some-key: &some-anchor
	 *   blah: blah
	 *   more: blah
	 * </pre>
	 */
	public static boolean isAnchored(NodeTuple entry) {
		if (entry!=null) {
			Node v = entry.getValueNode();
			return v!=null && v.getAnchor()!=null;
		}
		return false;
	}

	/**
	 * YAML anchors/aliases let SnakeYAML's composer produce a Node graph that is
	 * self-referential (e.g. {@code a: &x\n  b: *x}), since an anchor is registered
	 * before its own value finishes composing. Callers that recurse over a composed
	 * Node graph should track nodes currently being visited (by identity, not
	 * {@code equals()}) in a set created here, and stop descending rather than
	 * re-entering a node already on the current path.
	 */
	public static Set<Node> newIdentitySet() {
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}

}
