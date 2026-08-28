/*******************************************************************************
 * Copyright (c) 2015, 2017 Pivotal, Inc.
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
import org.springframework.ide.eclipse.boot.dash.model.RunTarget;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RemoteRunTarget;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;

public class RefreshRunTargetAction extends AbstractBootDashModelAction {

	public RefreshRunTargetAction(LiveExpression<BootDashModel> section, SimpleDIContext ui) {
		super(section, ui);
		this.setText("Refresh");
		this.setToolTipText("Manually refresh contents of the section");
		this.setImageDescriptor(BootDashActivator.getImageDescriptor("icons/refresh.png"));
	}

	@Override
	public void run() {
		BootDashModel model = sectionSelection.getValue();
		if (model != null) {
			model.refresh(ui());
		}
	}

	@Override
	public void updateEnablement() {
		super.updateEnablement();
		BootDashModel model = sectionSelection.getValue();
		if (model != null) {
			RunTarget target = model.getRunTarget();
			if (target instanceof RemoteRunTarget<?, ?>) {
				setEnabled(((RemoteRunTarget<?, ?>) target).isConnected());
			}
		}
	}

}
