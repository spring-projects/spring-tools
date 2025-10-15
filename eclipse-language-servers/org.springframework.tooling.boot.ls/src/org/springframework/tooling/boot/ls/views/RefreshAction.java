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

class RefreshAction extends Action {
	
	private final LogicalStructureView logicalStructureView;

	@SuppressWarnings("restriction")
	RefreshAction(LogicalStructureView logicalStructureView) {
		super("Refresh");
		this.logicalStructureView = logicalStructureView;
		setToolTipText("Refresh Structure");
		JavaPluginImages.setLocalImageDescriptors(this, "refresh.svg");
	}
	
	@Override
	public void run() {
		this.logicalStructureView.fetchStructure(true);
	}
}