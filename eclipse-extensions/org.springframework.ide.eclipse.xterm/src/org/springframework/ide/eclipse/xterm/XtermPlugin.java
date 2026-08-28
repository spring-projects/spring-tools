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
package org.springframework.ide.eclipse.xterm;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;
import org.springframework.ide.eclipse.xterm.views.TerminalView;

public class XtermPlugin extends AbstractUIPlugin {
	
	private static XtermPlugin plugin;
	
	private static final String PLUGIN_ID = "org.springframework.ide.eclipse.xterm";
	
	public static final String BG_COLOR = "org.springframework.ide.eclipse.xterm.background"; 
	public static final String FG_COLOR = "org.springframework.ide.eclipse.xterm.foreground"; 
	public static final String SELECTION_COLOR = "org.springframework.ide.eclipse.xterm.selection"; 
	public static final String CURSOR_COLOR = "org.springframework.ide.eclipse.xterm.cursor"; 
	public static final String CURSOR_ACCENT_COLOR = "org.springframework.ide.eclipse.xterm.cursorAccent";
	public static final String FONT = "org.springframework.ide.eclipse.xterm.font";
	
	public static final String PREFS_DEFAULT_SHELL_CMD = "org.springframework.ide.eclipse.xterm.defaultShellCmd";
	
	private static final List<String> NO_SHOW_EXCEPTIONS = Arrays.asList(
			"ValueParseException",
			"CoreException"
	);
	
	private XtermServiceProcessManager serviceManager = new XtermServiceProcessManager();
	
	@Override
	public void start(BundleContext bundle) throws Exception {
		plugin = this;
		
		// TODO: Workaround for SWT browser not able to open HTTP URL while in runtime workbench
//		if (Platform.OS_MACOSX.equals(Platform.getOS())) {
//		    NSDictionary allowNonHttps = NSDictionary.dictionaryWithObject(
//		            NSNumber.numberWithBool(true),
//		            NSString.stringWith("NSAllowsArbitraryLoads"));
//		    NSBundle.mainBundle().infoDictionary().setValue(
//		            allowNonHttps, NSString.stringWith("NSAppTransportSecurity"));
//		}
	}

	@Override
	public void stop(BundleContext bundle) throws Exception {
		serviceManager.stopService();
		plugin = null;
	}
	
	public static XtermPlugin getDefault() {
		return plugin;
	}

	public static void log(String m, Throwable t) {
		getDefault().getLog().error(m, t);
	}
	
	public void openTerminalView(String cmd, String cwd) {
		try {
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			String terminalId = UUID.randomUUID().toString();
			TerminalView terminalView = (TerminalView) page.showView(TerminalView.ID, terminalId, IWorkbenchPage.VIEW_ACTIVATE);
			terminalView.startTerminal(terminalId, cmd, cwd);
		} catch (Exception e) {
			XtermPlugin.log(e);
		}
	}

	public CompletableFuture<String> xtermUrl(long timeout) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return  serviceManager.serviceUrl(Duration.ofSeconds(10));
			} catch (Throwable t) {
				throw new CompletionException(t);
			}
		});
	}
	
	public static void log(Throwable e) {
		if (isCancelation(e)) {
			//Don't log canceled operations, those aren't real errors.
			return;
		}
		try {
			XtermPlugin.getDefault().getLog().log(status(e));
		} catch (NullPointerException npe) {
			//Can happen if errors are trying to be logged during Eclipse's shutdown
			e.printStackTrace();
		}
	}
	
	private static boolean isCancelation(Throwable e) {
		if (e == null) {
			return false;
		}
		Throwable cause = e.getCause();
		boolean isCancel = (
				e instanceof OperationCanceledException ||
				e instanceof InterruptedException ||
				e instanceof CancellationException ||
				(
						e instanceof CoreException &&
						((CoreException)e).getStatus().getSeverity() == IStatus.CANCEL
				)
		);
		return isCancel || (
				cause != null && //avoid npe's on recursive check
				cause != e && //avoid infinite recursion on e == cause
				isCancelation(cause)
		);
	}

	private static IStatus status(Throwable e) {
		if (isCancelation(e)) {
			return Status.CANCEL_STATUS;
		}
		return status(IStatus.ERROR, e);
	}
	
	private static IStatus status(int severity, Throwable e) {
		if (e instanceof CoreException) {
			IStatus status = ((CoreException) e).getStatus();
			if (status != null && status.getSeverity() == severity) {
				Throwable ee = status.getException();
				if (ee != null) {
					return status;
				}
			}
		}
		return new Status(severity, XtermPlugin.PLUGIN_ID, getMessage(e), e);
	}
	
	private static String getMessage(Throwable e) {
		// The message of nested exception is usually more interesting than the
		// one on top.
		Throwable cause = getDeepestCause(e);
		String errorType = cause.getClass().getSimpleName();
		String msg = cause.getMessage();
		if (NO_SHOW_EXCEPTIONS.contains(errorType) && msg != null) {
			return msg;
		}
		return errorType + ": " + msg;
	}

	private static Throwable getDeepestCause(Throwable e) {
		Throwable cause = e;
		Throwable parent = e.getCause();
		while (parent != null && parent != e) {
			cause = parent;
			parent = cause.getCause();
		}
		return cause;
	}

}
