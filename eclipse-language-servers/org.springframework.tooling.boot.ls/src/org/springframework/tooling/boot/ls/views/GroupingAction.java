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
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.lsp4e.ui.UI;

class GroupingAction extends Action {
	
	private LogicalStructureView structureView;

	@SuppressWarnings("restriction")
	public GroupingAction(LogicalStructureView structureView) {
		super("Grouping...", JavaPluginImages.DESC_ELCL_FILTER);
		this.structureView = structureView;
	}
	
	@Override
	public void run() {
		GroupingDialog dialog = new GroupingDialog(UI.getActiveShell(), structureView::fetchGroups, structureView::getGroupings);
		if (dialog.open() == IDialogConstants.OK_ID) {
			structureView.setGroupings(dialog.getResult());
			structureView.fetchStructure(false);
		}
	}

}
