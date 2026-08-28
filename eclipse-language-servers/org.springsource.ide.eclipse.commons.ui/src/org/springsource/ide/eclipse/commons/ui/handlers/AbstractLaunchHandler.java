/*******************************************************************************
 * Copyright (c) 2014 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.ui.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.springsource.ide.eclipse.commons.frameworks.core.ExceptionUtil;
import org.springsource.ide.eclipse.commons.internal.core.CorePlugin;
import org.springsource.ide.eclipse.commons.ui.launch.LaunchList;

/**
 * Handler for launch-related actions.
 *
 * @author Alex Boyko
 *
 */
public abstract class AbstractLaunchHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		LaunchList.Item l = getLaunchItem();
		IWorkbenchWindow window = PlatformUI.isWorkbenchRunning() ? PlatformUI.getWorkbench().getActiveWorkbenchWindow() : null;
		if (l != null) {
			try {
				performOperation(l);
			} catch (DebugException e) {
				CorePlugin.log(e);
				if (window != null) {
					MessageDialog.openError(window.getShell(), "Error relaunching",
							ExceptionUtil.getMessage(e)
					);
				}
			}
		} else if (window != null) {
			MessageDialog.openError(window.getShell(), "No Processes Found",
					"Couldn't "+ getOperationName()+": no active processes"
			);
		}
		return null;
	}

	abstract protected LaunchList.Item getLaunchItem();

	abstract protected void performOperation(LaunchList.Item l) throws DebugException;

	abstract protected String getOperationName();

}
