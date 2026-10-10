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

import java.util.List;
import java.util.concurrent.CompletionException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Shell;
import org.springframework.tooling.boot.ls.views.StructureClient.DependencyDescriptor;

/**
 * Selecting which dependencies of a project are included in its tree.
 * 
 * @author Martin Lippert
 */
class DependencyCommands {

	private static final String TITLE = "Logical Structure";

	private final LogicalStructureView view;
	private final StructureClient client;
	private final StructureViewState state;

	DependencyCommands(LogicalStructureView view, StructureClient client, StructureViewState state) {
		this.view = view;
		this.client = client;
		this.state = state;
	}

	void select(String projectName) {
		client.dependencies(projectName).whenComplete((offered, error) -> view.runInUI(() -> {
			if (error != null) {
				Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
				MessageDialog.openError(shell(), TITLE, "Failed to load the dependencies of '" + projectName + "': " + cause.getMessage());
			} else if (offered.isEmpty()) {
				MessageDialog.openInformation(shell(), TITLE, "Project '" + projectName + "' has no dependencies to include.");
			} else {
				select(projectName, offered);
			}
		}));
	}

	private void select(String projectName, List<DependencyDescriptor> offered) {
		List<String> selected = state.getSelectedDependencies(projectName);
		DependencySelectionDialog dialog = new DependencySelectionDialog(shell(), projectName,
				DependencyChoices.orderForDisplay(offered, selected), selected);
		if (dialog.open() != DependencySelectionDialog.OK) {
			return;
		}

		List<String> picked = dialog.getSelectedIds();
		state.setSelectedDependencies(projectName, DependencyChoices.selectionAfterPicking(selected, offered, picked));
		if (!picked.isEmpty() && !state.isIncludeDependencies()) {
			// picking something and seeing nothing happen would be confusing - the tree is fetched
			// again by the change of mode
			state.setIncludeDependencies(true);
		} else {
			view.fetchStructure(null, false);
		}
	}

	private Shell shell() {
		return view.getSite().getShell();
	}

}
