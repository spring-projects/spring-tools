/*******************************************************************************
 * Copyright (c) 2020, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.console;

import org.eclipse.debug.internal.ui.DebugPluginImages;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.action.Action;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IConsoleView;
import org.springsource.ide.eclipse.commons.core.SpringCoreUtils;

@SuppressWarnings("restriction")
public class RemoveConsoleAction extends Action {

	private static final String REMOVED_CONSTANT_FOR_IMG_DLCL_REMOVE = "IMG_DLCL_REMOVE";
	private static final String DEBUG_UI_BUNDLE_ID = "org.eclipse.debug.ui";

	private IConsoleView consoleView;
	private IConsoleManager consoleManager;

	public RemoveConsoleAction(IConsoleView consoleView, IConsoleManager consoleManager) {
		this.consoleView = consoleView;
		this.consoleManager = consoleManager;
		setImageDescriptor(DebugPluginImages.getImageDescriptor(IDebugUIConstants.IMG_LCL_REMOVE));

		if (!SpringCoreUtils.isEclipseBundleSameOrNewer(DEBUG_UI_BUNDLE_ID, 3, 22)) {
			setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(REMOVED_CONSTANT_FOR_IMG_DLCL_REMOVE));
		}

		setEnabled(true);
	}

	@Override
	public void run() {
		ApplicationLogConsole applicationConsole = getApplicationConsole();
		consoleManager.removeConsoles(new IConsole[] { applicationConsole });
	}

	private ApplicationLogConsole getApplicationConsole() {
		if (consoleView != null) {
			IConsole console = consoleView.getConsole();
			if (console instanceof ApplicationLogConsole) {
				return (ApplicationLogConsole) console;
			}
		}
		return null;

	}

}
