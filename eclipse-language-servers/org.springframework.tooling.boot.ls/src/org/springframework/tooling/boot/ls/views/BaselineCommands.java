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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.ListDialog;
import org.springframework.tooling.boot.ls.views.StructureClient.BaselineHistoryEntry;

/**
 * The commands of the Logical Structure view that deal with the baseline a project's tree is
 * compared against: capturing one, clearing it, and choosing which of the retained ones to compare
 * against.
 * 
 * @author Martin Lippert
 */
class BaselineCommands {

	private static final String TITLE = "Logical Structure";

	/**
	 * A choice in the dialog selecting the baseline.
	 * 
	 * @param snapshotKey identifies the snapshot, null for "whichever is the most recent"
	 */
	record BaselineChoice(String label, String snapshotKey) {}

	private final LogicalStructureView view;
	private final StructureClient client;
	private final StructureViewState state;

	BaselineCommands(LogicalStructureView view, StructureClient client, StructureViewState state) {
		this.view = view;
		this.client = client;
		this.state = state;
	}

	void capture(String projectName) {
		client.captureBaseline(projectName).whenComplete((result, error) -> view.runInUI(() -> {
			if (error != null) {
				failed("Failed to capture the logical structure baseline for '" + projectName + "'", error);
			} else {
				// the tree is fetched again so that change markers left over from a previous baseline disappear
				view.fetchStructure(null, false);
				view.setStatusMessage("Captured the logical structure baseline for '" + projectName + "' (" + result.elementCount()
						+ " element(s)). Changes since this point are highlighted in the Logical Structure view.");
			}
		}));
	}

	void clear(String projectName) {
		client.clearBaseline(projectName).whenComplete((result, error) -> view.runInUI(() -> {
			if (error != null) {
				failed("Failed to clear the logical structure baseline for '" + projectName + "'", error);
			} else {
				// the tree is fetched again so that leftover change markers disappear (a git-backed
				// project may get a fresh baseline right back on this same refresh, which is expected)
				view.fetchStructure(null, false);
				view.setStatusMessage(result.hadBaseline()
						? "Cleared the logical structure baseline for '" + projectName + "'."
						: "Project '" + projectName + "' had no logical structure baseline to clear.");
			}
		}));
	}

	void select(String projectName) {
		client.baselineHistory(projectName).whenComplete((history, error) -> view.runInUI(() -> {
			if (error != null) {
				failed("Failed to load the baseline history for '" + projectName + "'", error);
			} else if (history.isEmpty()) {
				MessageDialog.openInformation(shell(), TITLE, "Project '" + projectName + "' has no captured logical structure baseline yet.");
			} else {
				select(projectName, history);
			}
		}));
	}

	private void select(String projectName, List<BaselineHistoryEntry> history) {
		String currentKey = state.getCompareAgainst(projectName);

		List<BaselineChoice> choices = new ArrayList<>();
		choices.add(new BaselineChoice("Most recent snapshot - always compare against the newest captured snapshot", null));
		BaselineChoice current = choices.get(0);
		String currentDescription = "most recent snapshot";
		for (BaselineHistoryEntry entry : history) {
			boolean isGit = entry.commitSha() != null && !entry.commitSha().isEmpty();
			// a manually captured snapshot has no commit: it is normally taken over uncommitted work,
			// so only its capture time says anything about it
			String label = isGit
					? BaselineLabels.describe(entry.commitSha(), entry.commitMessage(), entry.capturedAt()) + "  (" + BaselineLabels.formatCapturedAt(entry.capturedAt()) + ")"
					: "Manual snapshot - " + BaselineLabels.formatCapturedAt(entry.capturedAt());
			BaselineChoice choice = new BaselineChoice(label, entry.capturedAt());
			choices.add(choice);
			if (entry.capturedAt().equals(currentKey)) {
				current = choice;
				currentDescription = BaselineLabels.describe(entry.commitSha(), entry.commitMessage(), entry.capturedAt());
			}
		}

		ListDialog dialog = new ListDialog(shell());
		dialog.setTitle("Select Baseline to Compare Against");
		dialog.setMessage("Select the baseline to compare '" + projectName + "' against (currently: " + currentDescription + ")");
		dialog.setContentProvider(ArrayContentProvider.getInstance());
		dialog.setLabelProvider(new LabelProvider() {
			@Override
			public String getText(Object element) {
				return ((BaselineChoice) element).label();
			}
		});
		dialog.setInput(choices);
		dialog.setInitialSelections(current);
		dialog.setHeightInChars(Math.min(Math.max(choices.size(), 4), 15));
		dialog.setWidthInChars(80);

		if (dialog.open() == ListDialog.OK && dialog.getResult().length == 1) {
			state.setCompareAgainst(projectName, ((BaselineChoice) dialog.getResult()[0]).snapshotKey());
			view.fetchStructure(null, false);
		}
	}

	private void failed(String message, Throwable error) {
		Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
		MessageDialog.openError(shell(), TITLE, message + ": " + cause.getMessage());
	}

	private Shell shell() {
		return view.getSite().getShell();
	}

}
