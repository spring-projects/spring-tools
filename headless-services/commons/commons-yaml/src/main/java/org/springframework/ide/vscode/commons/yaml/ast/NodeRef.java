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

import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * A node reference represents a 'pointer' to a location where a Node is stored.
 * The concept is useful because when looking for a 'path' inside an Yaml AST a
 * NodeRef makes it explicit where the node is with respect to some 'container'.
 * For example it allows distinguishing between a reference to Node which is
 * obtained from map key versus a map value.
 *
 * @author Kris De Volder
 */
public abstract class NodeRef<Parent> {

	public static enum Kind {
		ROOT, SEQ, KEY, VAL
	}

	private Parent parent;

	public NodeRef(Parent parent) {
		this.parent = parent;
	}

	public Parent getParent() {
		return parent;
	}

	public abstract Node get();
	public abstract void put(Node value);

	public abstract String toString();

	public abstract Kind getKind();

	/**
	 * Represents a reference to a root node, contained directly
	 * inside a {@link YamlFileAST}
	 */
	public static class RootRef extends NodeRef<YamlFileAST> {
		private int index;

		public RootRef(YamlFileAST file, int index) {
			super(file);
			this.index = index;
		}
		@Override
		public Node get() {
			return getParent().get(index);
		}

		@Override
		public void put(Node value) {
			getParent().put(index, value);
		}

		@Override
		public String toString() {
			return "ROOT["+index+"]";
		}
		@Override
		public Kind getKind() {
			return Kind.ROOT;
		}
		public int getIndex() {
			return index;
		}
	}

	public static class SeqRef extends NodeRef<SequenceNode> {
		private int index;
		public SeqRef(SequenceNode seq, int index) {
			super(seq);
			this.index = index;
		}
		@Override
		public Node get() {
			return getParent().getValue().get(index);
		}
		@Override
		public void put(Node value) {
			getParent().getValue().set(index, value);
		}
		@Override
		public String toString() {
			return "["+index+"]";
		}
		@Override
		public Kind getKind() {
			return Kind.SEQ;
		}
		public int getIndex() {
			return index;
		}
	}

	/**
	 * Abstract,  represent reference to either a key or
	 * value inside a map tuple. Concrete subclasses define whether
	 * key or value is being accessed.
	 */
	public static abstract class TupleRef extends NodeRef<MappingNode> {
		protected int index;
		public TupleRef(MappingNode map, int index) {
			super(map);
			this.index = index;
		}

		public NodeTuple getTuple() {
			return getParent().getValue().get(index);
		}

		public void putTuple(NodeTuple value) {
			getParent().getValue().set(index, value);
		}
	}

	/**
	 * References a key of a map entry
	 */
	public static class TupleKeyRef extends TupleRef {
		public TupleKeyRef(MappingNode parent, int index) {
			super(parent, index);
		}

		@Override
		public Node get() {
			return getTuple().getKeyNode();
		}

		@Override
		public void put(Node newKey) {
			NodeTuple tuple = getTuple();
			putTuple(new NodeTuple(newKey, tuple.getValueNode()));
		}

		@Override
		public String toString() {
			return "@key["+index+"]";
		}

		@Override
		public Kind getKind() {
			return Kind.KEY;
		}
	}

	/**
	 * References a value of a map entry
	 */
	public static class TupleValueRef extends TupleRef {
		public TupleValueRef(MappingNode parent, int index) {
			super(parent, index);
		}

		@Override
		public Node get() {
			return getTuple().getValueNode();
		}

		@Override
		public void put(Node newValue) {
			NodeTuple t = getTuple();
			putTuple(new NodeTuple(t.getKeyNode(), newValue));
		}

		@Override
		public String toString() {
			String keyString = NodeUtil.asScalar(getTuple().getKeyNode());
			if (keyString != null) {
				//more readable to use the key value than the index of the tuple
				return "@val['"+keyString+"']";
			}
			return "@val["+index+"]";
		}

		@Override
		public Kind getKind() {
			return Kind.VAL;
		}

		public Node getKey() {
			return getTuple().getKeyNode();
		}
	}
}
