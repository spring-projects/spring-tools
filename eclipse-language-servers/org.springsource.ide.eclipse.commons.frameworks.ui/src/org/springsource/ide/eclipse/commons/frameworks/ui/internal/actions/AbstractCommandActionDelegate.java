/*******************************************************************************
 * Copyright (c) 2012 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.ui.internal.actions;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.PixelConverter;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.wizard.WizardDialog;
import org.springsource.ide.eclipse.commons.frameworks.core.internal.commands.IFrameworkCommand;
import org.springsource.ide.eclipse.commons.frameworks.ui.internal.wizard.GenericCommandWizard;


/**
 * @author Nieraj Singh
 * @author Kris De Volder
 */
public abstract class AbstractCommandActionDelegate extends
		AbstractActionDelegate {
	
	private IFrameworkCommand selectedCommand = null;

	public void selectionChanged(IAction action, ISelection sel) {
		debug("Selection Changed: "+sel);
		if (sel != null) {
			//It seems we receive "null" selection when the Key shortcut popup disapears (Linux). This happens despite the fact
			//that the selection in the workbench has not been changed. This causes problems (command gets deactivated or
			//work on wrong selection, so we ignore any "null" selections.
			super.selectionChanged(action, sel);
			selectedCommand = getSelectedCommand(action, sel);
		}
	}

	public void run(IAction action) {
		List<IProject> projects = getSelectedProjects();
		IProject project = null;
		if (projects != null) {
			project = getSelectedProjects().get(0);
		}
		if (project != null) {
			LinkedHashSet<IProject> allProjects = new LinkedHashSet<IProject>();

			// Add the selected Project first
			allProjects.add(project);

			// Add any remaining projects that should appear as selectable
			// projects in the wizard
			Collection<IProject> selectionProjects = getSelectionProjects(project);
			if (selectionProjects != null) {
				allProjects.addAll(selectionProjects);
			}

			GenericCommandWizard wizard = getCommandWizard(allProjects,
					selectedCommand);

			if (wizard == null) {
				MessageDialog
						.openError(
								getShell(),
								"Unable to find command wizard",
								"No command wizard found. This action requires a command wizard. Please contact STS support.");
				return;
			}

			WizardDialog dialog = new WizardDialog(getShell(), wizard);

			PixelConverter converter = new PixelConverter(
					JFaceResources.getDialogFont());
			dialog.setPageSize(
					converter.convertWidthInCharsToPixels(70),
					converter.convertHeightInCharsToPixels(20));
			dialog.create();

			if (Dialog.OK == dialog.open()) {
				//The call below is superfluous, performFinish was already called automatically when user clicks finish button! 
				//				wizard.performFinish();
			}
		}
	}

	/**
	 * Return a list of projects to select. The project argument is the
	 * currently selected project by the context menu action.
	 * <p>
	 * Note that the selected project need NOT be added to the list, as it is
	 * already selected. This list should have any other project that would also
	 * be valid selections.
	 * </p>
	 * 
	 * @param selectedProject
	 *           project currently selected by context menu action
	 * @return list of OTHER projects that are also valid selections. Can be
	 *        null or empty.
	 */
	protected Collection<IProject> getSelectionProjects(IProject selectedProject) {
		return null;
	}

	/**
	 * Optional. Based on the action and selection, it is possible to create a
	 * command INSTANCE that is prepopulated with parameter values. Note that
	 * these are not necessarily default values for the parameters, but
	 * prepopulated values specific to the action or selection. Can be null.
	 * 
	 * @param action
	 *           currently selected action
	 * @param selection
	 *           selection in workbench
	 * @return a pre-filled command instance, or null if not required.
	 */
	protected IFrameworkCommand getSelectedCommand(IAction action,
			ISelection selection) {
		return null;
	}

	/**
	 * Get the command wizard that allows a user to select commands and
	 * configure a command instance.
	 * 
	 * @param projects
	 * @param command
	 * @return
	 */
	abstract protected GenericCommandWizard getCommandWizard(Collection<IProject> projects, IFrameworkCommand command);

}
