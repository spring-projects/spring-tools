/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.util;

import java.util.concurrent.CompletableFuture;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

public class UiUtil {

	public static Shell getShell() {
		try {
			IWorkbench wb = PlatformUI.getWorkbench();
			CompletableFuture<Shell> shell = new CompletableFuture<>();
			Display d = wb.getDisplay();
			d.syncExec(() -> {
				try {
					IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
					if (win != null) {
						shell.complete(win.getShell());
					} else {
						shell.complete(d.getActiveShell());
					}
				} catch (Throwable e) {
					shell.completeExceptionally(e);
				}
			});
			return shell.get();
		} catch (Exception e) {
			throw ExceptionUtil.unchecked(e);
		}
	}
}
