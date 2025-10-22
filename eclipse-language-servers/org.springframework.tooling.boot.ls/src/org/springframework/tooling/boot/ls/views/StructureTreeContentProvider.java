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
package org.springframework.tooling.boot.ls.views;

import java.util.List;

import org.eclipse.jface.viewers.ITreeContentProvider;

/**
 * Content provider for the logical structure tree view.
 * Provides placeholder content for demonstration purposes.
 * 
 * @author Alex Boyko
 */
public class StructureTreeContentProvider implements ITreeContentProvider {

	@Override
	public Object[] getElements(Object input) {
		if (input instanceof List) {
			return ((List<?>) input).toArray();
		}
		return getChildren(input);
	}

	@Override
	public Object[] getChildren(Object parentElement) {
		if (parentElement instanceof StereotypeNode) {
			StereotypeNode node = (StereotypeNode) parentElement;
			return node.children();
		}
		return new Object[0];
	}

	@Override
	public Object getParent(Object element) {
		return null;
	}

	@Override
	public boolean hasChildren(Object element) {
		return getChildren(element).length > 0;
	}
}

