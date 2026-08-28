/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui.util;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.viewers.ITreeContentProvider;

import com.google.common.collect.ImmutableSet;

/**
 * Wraps another ITreeContentProvider to ensure that all nodes have proper 'getParent'
 * result, even when the wrapped {@link ITreeContentProvider} does not implement
 * a proper 'getParent' and may have nodes that occur multiple times within the same
 * tree with a different parent.
 * <p>
 * This is accomplished by wrapping each element of the original provider into
 * a 'TreeNode' element that properly keeps track of the parent.
 */
public class TreeElementWrappingContentProvider implements ITreeContentProvider {
	
	private Map<Object, TreeNode> rootNodes = new HashMap<>();

	private static final TreeNode[] NO_NODES = {};
	
	private final ITreeContentProvider base;

	public class TreeNode {
		private final Object value;
		private final TreeNode parent;
		private TreeNode[] children;
		private TreeNode(Object value, TreeNode parent) {
			this.parent = parent;
			this.value = value;
		}

		@Override
		public String toString() {
			return value.toString();
		}

		public TreeNode[] getChildren() {
			if (children == null) {
				children = wrap(base.getChildren(value), this);
			}
			return children;
		}

		public TreeNode getParent() {
			return parent;
		}

		public Object getWrappedValue() {
			return value;
		}

		public boolean hasChildren() {
			if (children == null) {
				//Try to avoid creating children just to check if they exist.
				return base.hasChildren(value);
			} else {
				return children.length > 0;
			}
		}
	}

	public TreeElementWrappingContentProvider(ITreeContentProvider base) {
		this.base = base;
	}

	@Override
	public synchronized Object[] getElements(Object inputElement) {
		//Note: To avoid tree being 'flashy' on refreshes, try as much as possible
		// to reuse the root node wrappers if the root elements are the same as last time.
		
		Object[] objects = base.getElements(inputElement);
		if (objects == null || objects.length == 0) {
			rootNodes.clear();
			return NO_NODES;
		}
		
		TreeNode[] nodes = new TreeNode[objects.length];
		for (int i = 0; i < nodes.length; i++) {
			Object object = objects[i];
			nodes[i] = rootNodes.get(object);
			if (nodes[i] == null) {
				rootNodes.put(object, nodes[i] = new TreeNode(object, null));
			}
		}
		rootNodes.keySet().retainAll(ImmutableSet.copyOf(objects));
		return nodes;
	}

	private TreeNode[] wrap(Object[] elements, TreeNode parent) {
		if (elements != null && elements.length > 0) {
			TreeNode[] wrapped = new TreeNode[elements.length];
			for (int i = 0; i < elements.length; i++) {
				wrapped[i] = new TreeNode(elements[i], parent);
			}
			return wrapped;
		}
		return NO_NODES;
	}

	@Override
	public Object[] getChildren(Object _parent) {
		TreeNode parent = (TreeNode) _parent;
		return parent.getChildren();
	}

	@Override
	public Object getParent(Object element) {
		return ((TreeNode)element).getParent();
	}

	@Override
	public boolean hasChildren(Object element) {
		return ((TreeNode)element).hasChildren();
	}

}