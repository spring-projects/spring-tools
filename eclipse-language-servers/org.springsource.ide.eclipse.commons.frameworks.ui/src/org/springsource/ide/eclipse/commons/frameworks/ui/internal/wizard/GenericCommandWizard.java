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
package org.springsource.ide.eclipse.commons.frameworks.ui.internal.wizard;

import java.util.Collection;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWorkbench;
import org.springsource.ide.eclipse.commons.frameworks.core.internal.commands.CommandFactory;
import org.springsource.ide.eclipse.commons.frameworks.core.internal.commands.ICommandListener;
import org.springsource.ide.eclipse.commons.frameworks.core.internal.commands.ICommandParameter;
import org.springsource.ide.eclipse.commons.frameworks.core.internal.commands.IFrameworkCommand;
import org.springsource.ide.eclipse.commons.frameworks.core.internal.commands.IFrameworkCommandDescriptor;
import org.springsource.ide.eclipse.commons.frameworks.core.internal.commands.JavaParameterDescriptor;
import org.springsource.ide.eclipse.commons.frameworks.ui.FrameworkUIActivator;
import org.springsource.ide.eclipse.commons.frameworks.ui.internal.contentassist.JavaContentAssistUIAdapter;
import org.springsource.ide.eclipse.commons.frameworks.ui.internal.utils.ProjectFilter;
import org.springsource.ide.eclipse.commons.frameworks.ui.internal.utils.SelectionUtils;


/**
 * A generic wizard that lists commands to execute and configure before
 * executing. It contains at least 2 pages, the first has a list of commands
 * that can be executed or configured, and the second contains the parameters
 * page for a selected command
 * <p>
 * If a selected command has no parameters, the wizard will only have one page,
 * and will indicate to the user that the wizard can be finished directly from
 * the command list page.
 * </p>
 * <p>
 * If a selected command has mandatory parameters, the wizard cannot be
 * completed from the first page, and instead the user must click next to the
 * parameters page of that command to fill in any mandatory values. The wizard
 * will only complete after all mandatory values have been filed.
 * </p>
 * <p>
 * If a selected command has option parameters, the user has the option to
 * complete the wizard directly from the first page, or proceed to the second
 * page to set optional parameter values.
 * </p>
 * <p>
 * Commands can be added prior to opening the wizard, as well as after the
 * wizard opens, meaning that the list of commands in the command list page can
 * potentially increase even after the wizard opens.
 * </p>
 * <p>
 * The wizard also has the option of being created with a command argument. In
 * this case, when the wizard opens, if the command is already present in the
 * list of commands that appear in the command list page, the wizard will open
 * directly to the
 * @author Nieraj Singh
 * @author Christian Dupuis
 * @author Kris De Volder
 */
public abstract class GenericCommandWizard extends Wizard implements ICommandListener {

	protected GenericWizardCommandListPage commandListPage;
	protected GenericWizardCommandParametersPage parameterPage;
	private String wizardTitle;
	private boolean orderRequiredParameters = true;

	/**
	 * This is the command instance with values set that can be executed.
	 */
	private IFrameworkCommand commandInstance;

	private Collection<IProject> projects;
	private IProject selectedProject;

	/**
	 * This constructor accepts a pre-filled command instance. The title,
	 * description, and image location refer to the wizard properties that
	 * should be set by the caller.
	 * <p>
	 * The list of projects indicates the selection of projects that are
	 * available for the commands. The first selection in the project will
	 * always be selected. To pre-select a particular project, be sure that
	 * project appears as the first selection in the list.
	 * </p>
	 * 
	 * @param command
	 * @param title
	 * @param description
	 * @param imageLocation
	 * @param projects
	 */
	public GenericCommandWizard(IFrameworkCommand command, String title,
			String description, String imageLocation,
			Collection<IProject> projects) {
		super();
		this.commandInstance = command;
		this.projects = projects;
		wizardTitle = title;
		setWindowTitle(wizardTitle);
		setDefaultPageImageDescriptor(FrameworkUIActivator
				.getImageDescriptor(imageLocation));
		setNeedsProgressMonitor(true);

		// Create the page now as it may be accessed before the wizard opens
		commandListPage = createCommandListPage();
		commandListPage.setWizard(this);

		setForcePreviousAndNextButtons(true);
	}

	/**
	 * This method is called by a 'newWizard' action (action inside the 'new' menu). Default implementation
	 * sets the selected project based on the selection. Subclasses may override to initialize some of
	 * the command parameters based on the selection.
	 */
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		List<IProject> selectedProjects = SelectionUtils.getProjects(selection, getProjectFilter());
		if (selectedProjects.size() > 0) {
			setSelectedProject(selectedProjects.get(0));
		}
	}
	
	/**
	 * Project filter is used to determine whether a project selected by the user is 'interesting' to
	 * the wizard. Subclasses should override this if they wish to use the wizard in a 'newWizards'
	 * extension point. The default implementation considers any project interesting.
	 * <p>
	 * Typically only some projects should be interesting to a particular type of wizard.
	 * For example, grails wizards are only interested in Grails projects.
	 */
	protected ProjectFilter getProjectFilter() {
		return ProjectFilter.anyProject;
	}

	/**
	 * This constructor accepts a pre-filled command instance. The title,
	 * description, and image location refer to the wizard properties that
	 * should be set by the caller.
	 * <p>
	 * The list of projects indicates the selection of projects that are
	 * available for the commands. The first selection in the project will
	 * always be selected. To pre-select a particular project, be sure that
	 * project appears as the first selection in the list.
	 * </p>
	 * 
	 * @param command
	 * @param title
	 * @param description
	 * @param imageLocation
	 * @param projects
	 * @param orderRequiredParameters
	 *           true if required paramters should appear grouped together and
	 *           appear first. Default is false
	 */
	public GenericCommandWizard(IFrameworkCommand command, String title,
			String description, String imageLocation,
			Collection<IProject> projects, boolean orderRequiredParameters) {
		this(command, title, description, imageLocation, projects);
		this.orderRequiredParameters = orderRequiredParameters;
	}

	protected GenericWizardCommandListPage createCommandListPage() {
		return new GenericWizardCommandListPage(getWindowTitle());
	}

	public void setSelectedProject(IProject project) {
		if (project.equals(selectedProject)) return;
		selectedProject = project;
		if (this.commandListPage != null) {
			commandListPage.setProjectSelectionInPage();
		}
		if (this.parameterPage != null) {
			parameterPage.setProjectSelectionInPage();
		}
	}

	public IProject getSelectedProject() {
		return selectedProject;
	}

	public Collection<IProject> getProjectList() {
		return projects;
	}

	public GenericCommandWizard(String title, String description,
			String imageLocation) {
		this(null, title, description, imageLocation, null);
	}

	public GenericCommandWizard(String title, String description,
			String imageLocation, Collection<IProject> projects) {
		this(null, title, description, imageLocation, projects);
	}

	/**
	 * Get command instance selected whose parameter values have been
	 * configured.
	 * <p>
	 * This is what is needed to execute the command
	 * </p>
	 * 
	 * @return
	 */
	public IFrameworkCommand getCommandInstance() {
		return commandInstance;
	}

	public void addPages() {
		addPage(commandListPage);
	}

	public IWizardPage getStartingPage() {

		// If a command instance already exists, open the second page first
		if (commandListPage != null && commandInstance != null) {

			IFrameworkCommandDescriptor descriptor = commandInstance
					.getCommandDescriptor();
			commandListPage.addCommandDescriptor(descriptor);
			commandListPage.selectCommandInViewer(descriptor);
			parameterPage = createParameterPage(commandInstance);
			return parameterPage;
		}

		return super.getStartingPage();
	}

	protected void resizeWizard(Control control) {

		Point size = control.computeSize(SWT.DEFAULT, SWT.DEFAULT);
		IWizardContainer container = getContainer();
		if (container != null) {
			container.getShell().setSize(size);
		}
	}

	/**
	 * Requires an instance of the command, as the parameters page may fill the
	 * editor controls with a pre-filled version of a command instance.
	 * 
	 * @param command
	 *           instance to edit in the parameters page
	 * @return parameter page or null if unable to create.
	 */
	protected GenericWizardCommandParametersPage createParameterPage(
			IFrameworkCommand command) {
		if (command == null) {
			return null;
		}

		GenericWizardCommandParametersPage page = new GenericWizardCommandParametersPage(
				command);
		page.setWizard(this);
		return page;
	}

	public boolean canFinish() {
		// Handle the case of the parameter Page, as it is not permanently
		// added to the wizard
		if (parameterPage != null
				&& getContainer().getCurrentPage() == parameterPage) {
			return parameterPage.isPageComplete();
		} else if (isConfiguredCommandSelected()) {
			return true;
		} else {
			return super.canFinish();
		}
	}

	/**
	 * True if command instance is selected and configured, meaning any
	 * mandatory values are entered. False otherwise
	 * 
	 * @return true if command is selected and configured, false otherwise
	 */
	protected boolean isConfiguredCommandSelected() {
		if (commandInstance == null) {
			return false;
		}

		List<ICommandParameter> parameters = commandInstance.getParameters();
		if (parameters != null) {

			for (ICommandParameter parameter : parameters) {
				if (parameter.getParameterDescriptor().isMandatory()
						&& !parameter.hasValue()) {
					return false;
				}
			}
		}
		return true;

	}

	public IWizardPage getNextPage(IWizardPage page) {
		if (page == commandListPage) {
			if (parameterPage != null
					&& parameterPage.getCommand() == commandInstance) {
				return parameterPage;
			} else if (commandInstance != null) {
				parameterPage = createParameterPage(commandInstance);
				return parameterPage;
			}
		}
		return super.getNextPage(page);
	}

	/**
	 * Internal call only for wizard pages only. Should not be called outside
	 * the wizard. Sets a command instance only if there is no command instance
	 * currently present in the wizard OR the descriptor for the current command
	 * instance does not equal the descriptor argument.
	 * <p>
	 * If a command instance for the descriptor already exists, nothing happens
	 * and false is returned
	 * </p>
	 * 
	 * @param descriptor
	 *           create and set CommandInstance for the given descriptor, if
	 *           one does not already exist for the given descriptor.
	 * @return true if new command instance set. False otherwise
	 */
	public boolean setCommandInstance(IFrameworkCommandDescriptor descriptor) {
		if (commandInstance != null
				&& commandInstance.getCommandDescriptor().equals(descriptor)) {
			return false;
		}
		commandInstance = createCommand(descriptor);
		return true;
	}

	/**
	 * Create the command instance based on the given descriptor. Users can
	 * override to return a domain-specific command instance, or further
	 * configure the created command.
	 * 
	 * @param descriptor
	 *           to create a command instance
	 * @return Command instance. If null, parameter page is disabled.
	 */
	protected IFrameworkCommand createCommand(
			IFrameworkCommandDescriptor descriptor) {
		return CommandFactory.createCommandInstance(descriptor);
	}

	public IWizardPage getPreviousPage(IWizardPage page) {
		if (page == parameterPage) {
			return commandListPage;
		}
		return super.getPreviousPage(page);
	}

	/**
	 * Add a command descriptor to the wizard. If the wizard already has a
	 * command of the same type, the wizard will use the existing command. It is
	 * not possible to override an existing command already present in the
	 * wizard
	 */
	public void addCommandDescriptor(
			IFrameworkCommandDescriptor commandDescriptor) {
		// Make sure this always executes in UI thread
		final IFrameworkCommandDescriptor commandDesc = commandDescriptor;
		Display.getDefault().syncExec(new Runnable() {

			public void run() {

				if (commandListPage != null
						&& !commandListPage
								.containsCommandDescriptor(commandDesc)) {
					commandListPage.addCommandDescriptor(commandDesc);
				}
			}
		});
	}


	/**
	 * Subclasses can override to implement command execution while the wizard
	 * is finishing. This method ONLY gets called if all required parameter
	 * values for a given command have values and the wizard can complete
	 * successfully. The list of all values, option or required, are passed as
	 * an argument to this method.
	 * 
	 * @param command
	 * @param parameterValues
	 */
	protected void executeCommand(IFrameworkCommand command) {
		// do nothing. Subclasses can override
	}

	public boolean performFinish() {
		boolean canFinish = canFinish();

		if (canFinish) {
			executeCommand(commandInstance);
		}
		return canFinish;
	}

	/**
	 * Answers a request for a java content assist UI adapter given a Java
	 * parameter. If null, no Java content assist is added to the corresponding
	 * UI controls for the parameter.
	 * 
	 * @param parameter
	 *           Java parameter requiring content assist and Java type browsing
	 * @return content assist adapter. If null, no content assist support is
	 *        added to editor controls
	 */
	public JavaContentAssistUIAdapter getJavaContentAssistUIAdapter(
			JavaParameterDescriptor parameter) {
		return new JavaContentAssistUIAdapter(parameter);
	}

	/**
	 * If true group mandatory/required parameters first. If false create the
	 * parameter controls in the order that they appear in the command
	 * descriptor. This does NOT rearrange the order of the parameters in the
	 * command. It only affects the order of the controls in the UI, but the
	 * order of the parameters in the command itself is unchanged.
	 * <p>
	 * Default value is true.
	 * </p>
	 * 
	 * @return true if group mandatory parameters. False if parameter order is
	 *        to be preserved.
	 */
	protected boolean groupRequiredParameters() {
		return orderRequiredParameters;
	}

}
