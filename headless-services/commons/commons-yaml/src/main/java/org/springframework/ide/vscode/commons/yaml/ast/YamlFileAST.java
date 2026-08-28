/*******************************************************************************
 * Copyright (c) 2016-2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.yaml.ast;

import static org.springframework.ide.vscode.commons.yaml.ast.NodeUtil.contains;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.ide.vscode.commons.util.Collector;
import org.springframework.ide.vscode.commons.util.IRequestor;
import org.springframework.ide.vscode.commons.util.RememberLast;
import org.springframework.ide.vscode.commons.util.text.IDocument;
import org.springframework.ide.vscode.commons.yaml.ast.NodeRef.RootRef;
import org.springframework.ide.vscode.commons.yaml.ast.NodeRef.SeqRef;
import org.springframework.ide.vscode.commons.yaml.ast.NodeRef.TupleKeyRef;
import org.springframework.ide.vscode.commons.yaml.ast.NodeRef.TupleValueRef;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * Represents a parsed yml file.
 *
 * @author Kris De Volder
 */
public class YamlFileAST {

	private static final List<NodeRef<?>> NO_CHILDREN = Collections.emptyList();
	private final List<Node> nodes;
	private final IDocument doc;

	public YamlFileAST(IDocument doc, List<Node> nodes) {
		this.doc = doc;
		this.nodes = nodes;
	}

	public List<NodeRef<?>> findPath(int offset) {
		Collector<NodeRef<?>> path = new Collector<NodeRef<?>>();
		findPath(offset, path);
		return path.get();
	}

	/**
	 * Find 'smallest' ast node that contains offset. The pathRequestor will
	 * be called as the search progresses down the AST on all nodes on the
	 * path to the smallest node. If no node in the tree contains the offset
	 * the requestor will not be called at all.
	 */
	public void findPath(int offset, IRequestor<NodeRef<?>> pathRequestor) {
		for (int i = 0; i < nodes.size(); i++) {
			Node node = nodes.get(i);
			if (contains(node, offset)) {
				pathRequestor.accept(new RootRef(this, i) );
				Set<Node> onPath = NodeUtil.newIdentitySet();
				onPath.add(node);
				findPath(node, offset, pathRequestor, onPath);
				return;
			}
		}
	}

	/**
	 * Find smallest node that is a child of 'n' that contains 'offset'. Each visited
	 * node containing the offset, from the down to the found node are
	 * passed to the pathRequestor.
	 * <p>
	 * A YAML anchor/alias can make a node its own descendant (see {@link NodeUtil#newIdentitySet()}).
	 * 'onPath' tracks nodes on the current descent so that case stops instead of recursing forever -
	 * the node already found is then reported as the smallest match.
	 */
	private void findPath(Node n, int offset, IRequestor<NodeRef<?>> pathRequestor, Set<Node> onPath) {
		//TODO: avoid lots of garbage production by not using 'getChildren'
		// but inling getChildren (i.e a switch-case that visits
		// the children without putting them into temporary collections.)
		// By doing this it should be possible to avoid creaing lots of temporary
		// array lists and NodeRef objects and only create NodeRef objects for
		// the nodes we actually care about (i.e. the ones on the path).
		List<NodeRef<?>> children = getChildren(n);
		for (int i = 0; i < children.size(); i++) {
			NodeRef<?> c = children.get(i);
			Node childNode = c.get();
			if (contains(childNode, offset)) {
				if (!onPath.add(childNode)) {
					return;
				}
				pathRequestor.accept(c);
				findPath(childNode, offset, pathRequestor, onPath);
				return;
			}
		}
	}

	public static List<NodeRef<?>> getChildren(Node n) {
		switch (n.getNodeId()) {
		case scalar:
			return NO_CHILDREN;
		case sequence:
			return getChildren((SequenceNode)n);
		case mapping:
			return getChildren((MappingNode)n);
		case anchor:
			//TODO: is this right? maybe we should visit down into 'realnode'
			// but do we then potentially visit the same node twice?
			return NO_CHILDREN;
		}
		return null;
	}

	public List<Node> getNodes() {
		return nodes;
	}

	private static List<NodeRef<?>> getChildren(SequenceNode seq) {
		int nodes = seq.getValue().size();
		ArrayList<NodeRef<?>> children = new ArrayList<NodeRef<?>>(nodes);
		for (int i = 0; i < nodes; i++) {
			children.add(new SeqRef(seq, i));
		}
		return children;
	}

	private static List<NodeRef<?>> getChildren(MappingNode map) {
		int entries = map.getValue().size();
		ArrayList<NodeRef<?>> children = new ArrayList<NodeRef<?>>(entries*2);
		for (int i = 0; i < entries; i++) {
			children.add(new TupleKeyRef(map, i));
			children.add(new TupleValueRef(map, i));
		}
		return children;
	}

	public NodeRef<?> findNodeRef(int offset) {
		RememberLast<NodeRef<?>> lastNode = new RememberLast<NodeRef<?>>();
		findPath(offset, lastNode);
		return lastNode.get();
	}

	public Node findNode(int offset) {
		NodeRef<?> ref = findNodeRef(offset);
		if (ref != null) {
			return ref.get();
		}
		return null;
	}

	public Node get(int index) {
		return nodes.get(index);
	}

	public void put(int index, Node value) {
		nodes.set(index, value);
	}

	public IDocument getDocument() {
		return doc;
	}
}
