/*******************************************************************************
 * Copyright (c) 2019, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.ast;

import java.util.Set;

import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

public class AstDumper {

	public static void dump(Node node, int indent) {
		dump(node, indent, NodeUtil.newIdentitySet());
	}

	private static void dump(Node node, int indent, Set<Node> onPath) {
		if (!onPath.add(node)) {
			println(indent, "*** cyclic reference ***");
			return;
		}
		try {
			if (node instanceof MappingNode) {
				for (NodeTuple entry : ((MappingNode)node).getValue()) {
					println(indent, NodeUtil.asScalar(entry.getKeyNode())+":");
					dump(entry.getValueNode(), indent+1, onPath);
				}
			} else if (node instanceof SequenceNode) {
				for (Node el : ((SequenceNode)node).getValue()) {
					println(indent, "[");
					dump(el, indent+1, onPath);
					println(indent, "]");
				}
			} else if (node instanceof ScalarNode) {
				println(indent, NodeUtil.asScalar(node));
			} else {
				println(indent, "???"+node.getClass().getSimpleName());
			}
		} finally {
			onPath.remove(node);
		}
	}

	private static void println(int indent, String string) {
		indent(indent);
		System.out.println(string);
	}

	private static void indent(int indent) {
		for (int i = 0; i < indent; i++) {
			System.out.print("  ");
		}
	}
}
