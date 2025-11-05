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

import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jface.action.Action;

class ExpandCollapseAction extends Action {
	
	private final LogicalStructureView logicalStructureView;
	private final boolean expand;

	@SuppressWarnings("restriction")
	ExpandCollapseAction(LogicalStructureView logicalStructureView, boolean expand) {
		super(expand ? "Expand All" : "Collapse All");
		this.logicalStructureView = logicalStructureView;
		this.expand = expand;
		setToolTipText(expand ? "Expand All" : "Collapse All");
		JavaPluginImages.setLocalImageDescriptors(this, expand ? "expandall.svg" : "collapseall.svg");
	}
	
	@Override
	public void run() {
		if (expand) {
			this.logicalStructureView.expandAll();
		} else {
			this.logicalStructureView.collapseAll();
		}
	}
}

