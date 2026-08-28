/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.xterm;

import org.eclipse.core.runtime.Platform;
import org.springframework.ide.eclipse.boot.dash.api.App;
import org.springframework.ide.eclipse.boot.dash.docker.runtarget.DockerContainer;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.remote.GenericRemoteAppElement;
import org.springframework.ide.eclipse.boot.dash.views.AbstractBootDashElementsAction;
import org.springframework.ide.eclipse.xterm.XtermPlugin;

public class OpenDockerTerminal extends AbstractBootDashElementsAction {

	public OpenDockerTerminal(Params params) {
		super(params);
		this.setText("Open Shell");
		this.setToolTipText("Open Shell in selected container");
		this.setImageDescriptor(XtermPlugin.imageDescriptorFromPlugin(XtermPlugin.getDefault().getBundle().getSymbolicName(), "icons/terminal.png"));
//		this.setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/open_console_disabled.png"));
	}
	
	@Override
	public void run() {
		DockerContainer c = getDockerContainer();
		String id = c.getName();
		if (Platform.OS_WIN32.equals(Platform.getOS())) {
			XtermPlugin.getDefault().openTerminalView("docker exec -it "+id+" /bin/bash", null);
		} else {
			XtermPlugin.getDefault().openTerminalView("/usr/local/bin/docker exec -it "+id+" /bin/bash", null);
		}
	}


	/**
	 * Subclass can override to compuet enablement differently.
	 * The default implementation enables if a single element is selected.
	 */
	public void updateEnablement() {
		DockerContainer c = getDockerContainer();
		this.setEnabled(c != null && c.fetchRunState().isActive());
	}

	private DockerContainer getDockerContainer() {
		BootDashElement e = getSingleSelectedElement();
		if (e instanceof GenericRemoteAppElement) {
			App app = ((GenericRemoteAppElement) e).getAppData();
			if (app instanceof DockerContainer) {
				return (DockerContainer) app;
			}
		}
		return null;
	}

	public void updateVisibility() {
		this.setVisible(getDockerContainer() != null);
	}

	
}
