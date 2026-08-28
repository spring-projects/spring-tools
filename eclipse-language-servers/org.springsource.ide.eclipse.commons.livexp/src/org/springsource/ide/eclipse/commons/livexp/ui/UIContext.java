package org.springsource.ide.eclipse.commons.livexp.ui;

import java.util.concurrent.CompletableFuture;

import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

public interface UIContext {
	UIContext DEFAULT = () -> {
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
	};

	Shell getShell();
}