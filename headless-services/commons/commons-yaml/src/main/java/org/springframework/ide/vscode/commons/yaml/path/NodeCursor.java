/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.path;

import java.util.stream.Stream;

import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.yaml.ast.NodeMergeSupport;
import org.springframework.ide.vscode.commons.yaml.ast.NodeUtil;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * Pointer to a specific {@link Node} in Snake yaml parse tree. Supports navigation
 * using {@link YamlPath}s, including support for 'ambiguous' steps like 
 * {@link YamlPathSegment}.anyChild()
 * 
 * @author Kris De Volder
 */
public class NodeCursor extends ASTCursor {
	
	private final Node currentNode;
	
	private NodeMergeSupport nodeMerger = new NodeMergeSupport(IProblemCollector.NULL);
	
	public NodeCursor(Node node) {
		Assert.isNotNull(node);
		this.currentNode = node;
	}
	
	

	@Override
	public Stream<ASTCursor> traverseAmbiguously(YamlPathSegment s) {
		switch (s.getType()) {
		case KEY_AT_KEY: {
			String key = s.toPropString();
			MappingNode mappingNode = asMapping(getNode());
			if (mappingNode != null) {
				return mappingNode.getValue().stream()
						.filter((c) -> key.equals(NodeUtil.asScalar(c.getKeyNode())))
						.map((c) -> new NodeCursor(c.getKeyNode()));
						
			}
			return Stream.empty();
		}
		case ANY_CHILD: {
			MappingNode mappingNode = asMapping(getNode());
			if (mappingNode != null) {
				return mappingNode.getValue().stream()
						.map((c) -> new NodeCursor(c.getValueNode()));
			}
			SequenceNode sequenceNode = NodeUtil.asSequence(getNode());
			if (sequenceNode != null) {
				return sequenceNode.getValue().stream().map(NodeCursor::new);
			}
			return Stream.empty();
		}
		case VAL_AT_INDEX: {
			SequenceNode seq = NodeUtil.asSequence(getNode());
			int index = s.toIndex();
			int size = seq.getValue().size();
			if (index < size && index >= 0) {
				return Stream.of(new NodeCursor(seq.getValue().get(index)));
			}
			return Stream.empty();
		}
		case VAL_AT_KEY: {
			MappingNode mappingNode = asMapping(getNode());
			if (mappingNode != null) {
				String key = s.toPropString();
				return mappingNode.getValue().stream()
						.filter((c) -> key.equals(NodeUtil.asScalar(c.getKeyNode())))
						.map((c) -> new NodeCursor(c.getValueNode()));
			}
			return Stream.empty();
		}
		default:
			Assert.isLegal(false, "Bug? Missing switch case?");
			return Stream.empty();
		}
	}



	private MappingNode asMapping(Node node) {
		MappingNode mapping = NodeUtil.asMapping(node);
		if (mapping != null) {
			nodeMerger.flattenMapping(mapping);
		}
		return mapping;
	}

	@Override
	public Node getNode() {
		return currentNode;
	}
	
}
