/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.boot.ls.views;

import java.net.URI;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.lsp4e.LSPEclipseUtils;
import org.eclipse.lsp4j.Location;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerService;
import org.springframework.tooling.boot.ls.BootLanguageServerPlugin;

/**
 * Shows what changed in the source of a node that changed since the baseline, by comparing the
 * file with its last committed version - with the "Compare With HEAD" of EGit, which is run for the
 * file opened in its editor, the same way as from the Team menu. EGit is not required to be
 * installed, a message says so if it is not.
 * 
 * Note this compares with HEAD, not with the commit a structure baseline was captured at. Those
 * are the same as long as baselines follow the git history (which they do by default), and differ
 * only for a baseline pinned manually mid-branch.
 * 
 * @author Martin Lippert
 */
class ShowChangesAction extends Action {

	private static final String TITLE = "Logical Structure";

	static final String COMPARE_WITH_HEAD_COMMAND = "org.eclipse.egit.ui.team.CompareWithHead";

	private final Location location;
	private final Shell shell;

	ShowChangesAction(Shell shell, Location location) {
		super("Show Changes");
		setToolTipText("Compare the file with its last committed version");
		this.shell = shell;
		this.location = location;
	}

	@Override
	public void run() {
		IFile file = findFile(location);
		if (file == null) {
			MessageDialog.openInformation(shell, TITLE, "Cannot show the changes: the file is not in the workspace.");
			return;
		}
		if (!file.exists()) {
			MessageDialog.openInformation(shell, TITLE, "Cannot show the changes: '" + file.getName() + "' does not exist anymore.");
			return;
		}

		Command command = PlatformUI.getWorkbench().getService(ICommandService.class).getCommand(COMPARE_WITH_HEAD_COMMAND);
		if (!command.isDefined()) {
			MessageDialog.openInformation(shell, TITLE, "Cannot show the changes: the Git integration of Eclipse (EGit) is not installed.");
			return;
		}

		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window == null) {
			return;
		}

		// the file in its editor, at the node, which is also what the command works on
		LSPEclipseUtils.openInEditor(location);
		try {
			window.getService(IHandlerService.class).executeCommand(COMPARE_WITH_HEAD_COMMAND, null);
		} catch (NotEnabledException | NotHandledException e) {
			MessageDialog.openInformation(shell, TITLE, "Cannot show the changes: '" + file.getName() + "' is not in a Git repository.");
		} catch (ExecutionException | NotDefinedException e) {
			BootLanguageServerPlugin.getDefault().getLog().error("Failed to compare " + file + " with HEAD", e);
			MessageDialog.openError(shell, TITLE, "Cannot show the changes: " + e.getMessage());
		}
	}

	private static IFile findFile(Location location) {
		try {
			IFile[] files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(URI.create(location.getUri()));
			return files.length > 0 ? files[0] : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

}
