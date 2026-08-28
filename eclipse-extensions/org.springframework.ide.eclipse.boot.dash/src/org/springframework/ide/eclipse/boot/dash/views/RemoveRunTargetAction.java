/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views;

import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.model.RunTarget;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RunTargetTypes;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;

public class RemoveRunTargetAction extends AbstractBootDashModelAction {

	private BootDashViewModel viewModel;

	public RemoveRunTargetAction(LiveExpression<BootDashModel> sectionSelection, BootDashViewModel viewModel,
			SimpleDIContext ui) {
		super(sectionSelection, ui);

		this.viewModel = viewModel;
		this.setText("Remove Target");
		this.setToolTipText("Remove the connection to the target and its dashboard section.");
		this.setImageDescriptor(BootDashActivator.getImageDescriptor("icons/remove_target.png"));
		this.setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/remove_target_disabled.png"));
	}


	@Override
	public void run() {
		RunTarget target = getRunTargetToRemove();
		viewModel.removeTarget(target, ui());
	}

	@Override
	public void updateEnablement() {
		RunTarget runTargetToRemove = getRunTargetToRemove();
		this.setEnabled(runTargetToRemove != null && runTargetToRemove.canRemove());
	}

	@Override
	public void updateVisibility() {
		RunTarget runTargetToRemove = getRunTargetToRemove();
		setVisible(runTargetToRemove != null && !RunTargetTypes.LOCAL.equals(runTargetToRemove.getType()));
	}

	private RunTarget getRunTargetToRemove() {
		BootDashModel section = sectionSelection.getValue();
		if (section != null) {
			return section.getRunTarget();
		}
		return null;
	}

}
