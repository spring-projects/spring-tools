/*******************************************************************************
 * Copyright (c) 2016 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views;

import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.dialogs.EditTemplateDialogModel;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStoreApi;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;

public class CustmomizeTargetLabelAction extends AbstractBootDashModelAction {

	protected CustmomizeTargetLabelAction(LiveExpression<BootDashModel> section, SimpleDIContext context) {
		super(section, context);
		setText("Customize Label...");
	}

	@Override
	public void updateEnablement() {
		this.setEnabled(isApplicable(sectionSelection.getValue()));
	}

	public void updateVisibility() {
		this.setVisible(isApplicable(sectionSelection.getValue()));
	}

	private boolean isApplicable(BootDashModel section) {
		if (section != null) {
			PropertyStoreApi props = section.getRunTarget().getType().getPersistentProperties();
			//Not all target types provide persistent properties yet. This feature only works on
			// those target types that do.
			return props != null;
		}
		return false;
	}

	@Override
	public void run() {
		final BootDashModel section = sectionSelection.getValue();
		if (isApplicable(section)) {
			EditTemplateDialogModel model = CustomizeTargetLabelDialogModel.create(section);
			ui().openEditTemplateDialog(model);
		}
	}

}
