/*******************************************************************************
 * Copyright (c) 2019, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views;

import java.util.Collection;
import java.util.Iterator;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.remote.GenericRemoteAppElement;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;

import com.google.common.collect.ImmutableSet;

public class LinkWithConsoleAction extends AbstractBootDashElementsAction {


	private ValueListener<ImmutableSet<BootDashElement>> listener;

	public LinkWithConsoleAction(Params params) {
		super(params);
		this.setText("Link with Console");
		this.setToolTipText("Link with Console");
		this.setImageDescriptor(BootDashActivator.getImageDescriptor("icons/link_to_editor.png"));

		selection.getElements().addListener(listener = new ValueListener<ImmutableSet<BootDashElement>>() {
			public void gotValue(LiveExpression<ImmutableSet<BootDashElement>> exp, ImmutableSet<BootDashElement> selecteds) {
				linkToConsole();
			}
		});
	}

	@Override
	public void run() {
		linkToConsole();
	}

	@Override
	public void dispose() {
		if (listener != null && selection !=  null) {
			selection.getElements().removeListener(listener);
		}
		super.dispose();
	}

	protected void linkToConsole() {
		if (LinkWithConsoleAction.this.isChecked()) {
			showSelected(getSelectedElements());
		}
	}

	protected void showSelected(Collection<BootDashElement> selected) {

		Job job = new Job("Opening console") {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				doShowConsoles(selected);
				return Status.OK_STATUS;
			}

		};
		job.schedule();
	}

	protected void doShowConsoles(Collection<BootDashElement> selectedElements) {

		if (selectedElements != null) {

			Iterator<BootDashElement> it = selectedElements.iterator();

			// Show first element only for now
			if (it.hasNext()) {
				BootDashElement element = selectedElements.iterator().next();
				BootDashModel model = element.getBootDashModel();

				try {
					BootDashModelConsoleManager consoleManager = model.getElementConsoleManager();

					// The difference between this and open console is that we do NOT want to show
					// a pop-up dialogue when the error is related to the console not existing. We don't
					// consider this an error condition if the console is not available

					if (consoleManager != null && consoleManager.safeGetOrCreateConsole(element) != null) {
						consoleManager.showConsole(element);
					}

				} catch (Exception e) {
					ui().errorPopup("Open Console Failure", e.getMessage());
				}
			}
		}
	}

	@Override
	public void updateVisibility() {
		this.setVisible(supportsConsole());
	}

	@Override
	public void updateEnablement() {
		this.setEnabled(supportsConsole());
	}

	protected boolean supportsConsole() {
    	BootDashElement element = getSingleSelectedElement();
		boolean supports = false;
		if (element != null) {
			if (element instanceof GenericRemoteAppElement) {
				supports = ((GenericRemoteAppElement) element).canWriteToConsole();
			} else {
				// Backward compatibility with CF. Console action is enabled when a single CF element
				// is selected
				supports = true;
			}
		}
		return supports;
	}

}
