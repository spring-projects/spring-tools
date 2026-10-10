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

import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.jface.viewers.ITreeContentProvider;

/**
 * Content provider for the logical structure tree view.
 * Provides placeholder content for demonstration purposes.
 * 
 * @author Alex Boyko
 */
public class StructureTreeContentProvider implements ITreeContentProvider {

	private final BooleanSupplier hideUnchanged;

	/**
	 * @param hideUnchanged whether to show only the changed nodes, and the path leading to them,
	 *        below the project nodes that have a baseline
	 */
	StructureTreeContentProvider(BooleanSupplier hideUnchanged) {
		this.hideUnchanged = hideUnchanged;
	}

	@Override
	public Object[] getElements(Object input) {
		StereotypeNode[] nodes = (StereotypeNode[]) (input instanceof List ? ((List<?>) input).toArray(StereotypeNode[]::new)
				: getChildren(input));
		Arrays.sort(nodes, (n1, n2) -> n1.text().compareTo(n2.text()));
		return nodes;
	}

	/**
	 * The children to show for a node.
	 * 
	 * With unchanged nodes hidden and a baseline captured for the node's project, only the children
	 * that changed are shown - which is exactly the path towards the changes, since a node
	 * containing a change is reported as changed too. If nothing changed at all since the
	 * baseline, this legitimately hides every child. Projects without a captured baseline are never
	 * filtered, otherwise their whole tree would look empty.
	 */
	@Override
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof StereotypeNode sn) {
			if (hideUnchanged.getAsBoolean() && sn.hasBaseline()) {
				return Arrays.stream(sn.children()).filter(StereotypeNode::containsChanges).toArray();
			}
			return sn.children();
		}
		return new Object[0];
	}

	@Override
	public Object getParent(Object element) {
		return element instanceof StereotypeNode sn ? sn.parent() : null;
	}

	@Override
	public boolean hasChildren(Object element) {
		return getChildren(element).length > 0;
	}
}
