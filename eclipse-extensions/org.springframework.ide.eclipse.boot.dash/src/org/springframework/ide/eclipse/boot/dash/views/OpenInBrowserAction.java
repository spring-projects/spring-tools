/*******************************************************************************
 * Copyright (c) 2015, 2017 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views;

import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;

public class OpenInBrowserAction extends AbstractBootDashElementsAction {

	public OpenInBrowserAction(Params params) {
		super(params);
		this.setText("Open Web Browser");
		this.setToolTipText("Open a Web Browser on the default URL");
		this.setImageDescriptor(BootDashActivator.getImageDescriptor("icons/open_browser.png"));
		this.setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/open_browser_disabled.png"));
	}

	@Override
	public void updateEnablement() {
		String url = getUrl();
		setEnabled(url != null);
	}

	private String getUrl() {
		BootDashElement el = getSingleSelectedElement();
		if (el != null) {
			return el.getUrl();
		}
		return null;
	}

	@Override
	public void run() {
		String url = getUrl();
		if (url != null) {
			ui().openUrl(url);
		}
	}

	@Override
	public void dispose() {
		super.dispose();
	}

}
