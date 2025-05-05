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

import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.dialogs.ToggleFiltersDialogModel;
import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelection;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.ToggleFiltersModel;

/**
 * @author Kris De Volder
 */
@SuppressWarnings("restriction")
public class OpenToggleFiltersDialogAction extends AbstractBootDashAction {

	/**
	 * Represents the filters in the view (i.e. the ones currently in effect when dlg opens).
	 */
	private ToggleFiltersModel viewModel;

	public OpenToggleFiltersDialogAction(ToggleFiltersModel model, MultiSelection<BootDashElement> selection, SimpleDIContext ui) {
		super(ui);
		this.viewModel = model;
		setText("Filters...");
		setImageDescriptor(JavaPluginImages.DESC_ELCL_FILTER);
	}

	@Override
	public void run() {
		ToggleFiltersDialogModel dlg = new ToggleFiltersDialogModel(viewModel);
		ui().openDialog(dlg);
	}

}
