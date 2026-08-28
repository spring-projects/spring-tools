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

import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.IWorkbenchWindowActionDelegate;
import org.springsource.ide.eclipse.commons.frameworks.ui.internal.utils.ProjectFilter;
import org.springsource.ide.eclipse.commons.frameworks.ui.internal.utils.SelectionUtils;


/**
 * @author Nieraj Singh
 * @author Kris De Volder
 */
public abstract class AbstractActionDelegate implements IObjectActionDelegate,
		IWorkbenchWindowActionDelegate {
	
	private static final String DEBUG_CLASS = null;
	//"org.grails.ide.eclipse.ui.internal.actions.OpenGrailsPluginsManagerActionDelegate";

	protected void debug(String msg) {
		if (this.getClass().getName().equals(DEBUG_CLASS)) {
			System.err.println(msg);
		}
	}
	
	public void dispose() {
	}

	public void init(IWorkbenchWindow window) {
	}

	protected List<IProject> selectedProjects;
	protected IWorkbenchPart part;

	public void setActivePart(IAction action, IWorkbenchPart part) {
		this.part = part;
	}

	/**
	 * Gets the workbench. It may be null if it cannot be resolved.
	 * 
	 * @return workbench or null if it cannot be resolved
	 */
	protected IWorkbench getWorkbench() {
		if (part != null) {
			IWorkbenchSite site = part.getSite();
			if (site != null) {
				return site.getWorkbenchWindow().getWorkbench();
			}
		}
		return null;
	}

	public void selectionChanged(IAction action, ISelection sel) {
		if (sel == null) return; //ignore spurrious null selections
		
		List<IProject> newSelectedProjects = SelectionUtils.getProjects(sel, new ProjectFilter() {
			@Override
			public boolean isAcceptable(IProject project) {
				return shouldAddToProjectList(project);
			}
		});

		//Try to hold on to valid selection as long as possible, so only replace selectedProjects if
		// we have some actual new selections.
		if (!newSelectedProjects.isEmpty()) {
			debug(this + ":" + newSelectedProjects.toString());
			selectedProjects = newSelectedProjects; 
		}
		if (selectedProjects == null || selectedProjects.isEmpty()) {
			action.setEnabled(false);
		} else {
			action.setEnabled(true);
		}

	}

	protected boolean shouldAddToProjectList(IProject project) {
		return project != null && project.exists() && project.isAccessible();
	}

	protected List<IProject> getSelectedProjects() {
		return selectedProjects;
	}

	protected Shell getShell() {
		Shell shell = null;
		if (part != null) {
			shell = part.getSite().getShell();
		}

		if (shell == null) {
			shell = Display.getDefault().getActiveShell();
		}

		return shell;
	}

}
