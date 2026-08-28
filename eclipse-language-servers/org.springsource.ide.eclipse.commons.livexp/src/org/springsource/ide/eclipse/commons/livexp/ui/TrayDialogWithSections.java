/*******************************************************************************
 * Copyright (c) 2021 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.TrayDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.widgets.SharedScrolledComposite;
import org.eclipse.ui.progress.UIJob;
import org.springsource.ide.eclipse.commons.livexp.Activator;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

public class TrayDialogWithSections extends TrayDialog implements IPageWithSections, Reflowable {

	private OkButtonHandler model;
	private SharedScrolledComposite scroller;
	private List<WizardPageSection> sections = null;

	protected TrayDialogWithSections(Shell shell, OkButtonHandler model) {
		super(shell);
		this.model = model;
	}

	protected Control createDialogArea(Composite parent) {
		scroller = new SharedScrolledComposite(parent, SWT.V_SCROLL | SWT.H_SCROLL) {};
		scroller.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scroller.setExpandHorizontal(true);
		scroller.setExpandVertical(true);
		Composite page = new Composite(scroller, SWT.NONE);
		applyDialogFont(page);

		GridLayout layout = new GridLayout(1, false);
		layout.marginHeight = 12;
		layout.marginWidth = 12;
		page.setLayout(layout);
		for (PageSection section : getSections()) {
			section.createContents(page);
		}

		page.pack(true);
		scroller.setContent(page);

		return parent;
	}

	private UIJob reflowJob;

	@Override
	public boolean reflow() {
		if (reflowJob == null) {
			reflowJob = new UIJob(Display.getDefault(), "Reflow Wizard Contents") {
				@Override
				public IStatus runInUIThread(IProgressMonitor monitor) {
					if (scroller != null && !scroller.isDisposed()) {
						scroller.reflow(true);
					}
					return Status.OK_STATUS;
				}
			};
			reflowJob.setSystem(true);
		}
		reflowJob.schedule();
		return true;
	}

	protected synchronized List<WizardPageSection> getSections() {
		if (sections == null) {
			sections = safeCreateSections();
		}
		return sections;
	}

	private List<WizardPageSection> safeCreateSections() {
		try {
			return createSections();
		} catch (CoreException e) {
			Activator.log(e);
			return Arrays.asList(
					new CommentSection(this, "Dialog couldn't be created because of an unexpected error:+\n"+ExceptionUtil.getMessage(e)+"\n\n"
							+ "Check the error log for details"),
					new ValidatorSection(Validator.alwaysError(ExceptionUtil.getMessage(e)), this)
					);
		}
	}

	/**
	 * This method should be implemented to generate the contents of the page.
	 */
	protected List<WizardPageSection> createSections() throws CoreException {
		//This default implementation is meant to be overridden
		return Arrays.asList(
				new CommentSection(this, "Override DialogWithSections.createSections() to provide real content."),
				new ValidatorSection(Validator.alwaysError("Subclass must implement validation logic"), this)
				);
	}

	public void dispose() {
		for (WizardPageSection s : sections) {
			s.dispose();
		}
	}

	@Override
	protected void cancelPressed() {
		super.cancelPressed();
	}

	@Override
	protected final void okPressed() {
		super.okPressed();
		try {
			model.performOk();
		} catch (Exception e) {
			Activator.log(e);
			MessageDialog.openError(getShell(), "Error", ExceptionUtil.getMessage(e));
		}
	}

	/**
	 * Simulate clicking the ok button. Does nothing if ok button is not found or disabled.
	 */
	public boolean clickOk() {
		Button button = getButton(OK);
		if (button != null && button.isEnabled()) {
			okPressed();
			return true;
		}
		return false;
	}


	@Override
	public IRunnableContext getRunnableContext() {
		return PlatformUI.getWorkbench().getProgressService();
	}
	
}
