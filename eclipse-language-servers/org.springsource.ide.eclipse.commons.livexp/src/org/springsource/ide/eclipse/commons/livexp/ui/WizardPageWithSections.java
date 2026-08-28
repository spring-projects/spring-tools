/*******************************************************************************
 * Copyright (c) 2012, 2023 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui;

import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.wizard.IWizardContainer2;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.forms.widgets.SharedScrolledComposite;
import org.eclipse.ui.progress.UIJob;
import org.springsource.ide.eclipse.commons.livexp.core.CompositeValidator;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;

/**
 * @author Kris De Volder
 */
public abstract class WizardPageWithSections extends WizardPage implements IPageWithSections, Reflowable, ValueListener<ValidationResult> {

	/**
	 * A delay used for posting status messages to the dialog area after a status update happens.
	 * This is to get rid of spurious message that only appear for a fraction of a second as
	 * internal some auto updating states in models are inconsistent. E.g. in new boot project wizard
	 * when project name is entered it is temporarily inconsistent with default project location until
	 * that project location itself is update in response to the change event from the project name.
	 * If the project location validator runs before the location update, a spurious validation error
	 * temporarily results.
	 *
	 * Note: this is a hacky solution. It would be better if the LiveExp framework solved this by
	 * tracking and scheduling refreshes based on the depedency graph. Thus it might guarantee
	 * that the validator never sees the inconsistent state because it is refreshed last.
	 */
	private static final long MESSAGE_DELAY = 250;

	protected WizardPageWithSections(String pageName, String title, ImageDescriptor titleImage) {
		super(pageName, title, titleImage);
	}

	private List<WizardPageSection> sections = null;
	protected CompositeValidator validator;
	private UIJob updateJob;
	private SharedScrolledComposite scroller;
	private Composite page;
	private UIJob reflowJob;

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		GridDataFactory.fillDefaults().grab(true,true).applyTo(parent);

		scroller = new SharedScrolledComposite(parent, SWT.V_SCROLL | SWT.H_SCROLL) {};
		
//		scroller.setWidthHint(500); // Avoid excessively wide dialogs
//		Display display = Display.getCurrent();
//		Color blue = display.getSystemColor(SWT.COLOR_BLUE);
//		scroller.setBackground(blue);
		
		scroller.setBackground(parent.getBackground());
		scroller.setExpandHorizontal(true);
		scroller.setExpandVertical(true);
		page = new Composite(scroller, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginHeight = 12;
        layout.marginWidth = 12;
        page.setLayout(layout);
        validator = new CompositeValidator();
        for (PageSection section : getSections()) {
			section.createContents(page);
			validator.addChild(section.getValidator());
		}
        validator.addListener(this);
        Dialog.applyDialogFont(page);
        page.pack(true);
//        scroller.setMinSize(page.getSize());
//        scroller.setSize(page.getSize());
		scroller.setContent(page);
        setControl(scroller);
    	page.addPaintListener(new PaintListener() {
            //This is a bit yuck. But don't know a better way to 'reflow' the page the first time
            // it gets shown. And if we don't do that, then there are often layout issues.
            //See for example: https://www.pivotaltracker.com/story/show/130209913
			@Override
			public void paintControl(PaintEvent e) {
				page.removePaintListener(this);
				reflow();
			}
		});
        if (getContainer().getCurrentPage() != null) { // Otherwise an NPE will ensue when updating buttons. Buttons depend on current page so that is logical.
	        getContainer().updateButtons();
	        getContainer().updateMessage();
        }
	}

	@Override
	public boolean reflow() {
		if (reflowJob == null) {
			reflowJob = new UIJob(Display.getDefault(), "Reflow Wizard Contents") {
				@Override
				public IStatus runInUIThread(IProgressMonitor monitor) {
					if (scroller != null && !scroller.isDisposed()) {
						scroller.layout(true, true);
						scroller.reflow(true);
					}
					if (getWizard() != null && getWizard().getContainer() instanceof IWizardContainer2) {
						((IWizardContainer2)getWizard().getContainer()).updateSize();
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
			sections = createSections();
		}
		return sections;
	}

	/**
	 * This method should be implemented to generate the contents of the page.
	 */
	protected abstract List<WizardPageSection> createSections();

	public void gotValue(LiveExpression<ValidationResult> exp, final ValidationResult status) {
//		setPageComplete(status.isOk()); //Don't delay this, never allow clicking finish button if state not consistent.
		scheduleUpdateJob();
	}

	private synchronized void scheduleUpdateJob() {
		Shell shell = getShell();
		if (shell != null) {
			if (this.updateJob == null) {
				this.updateJob = new UIJob("Update Wizard message") {
					@Override
					public IStatus runInUIThread(IProgressMonitor monitor) {
						ValidationResult status = validator.getValue();
						setErrorMessage(null);
						setMessage(null);
						if (status.isOk()) {
						} else if (status.status == IStatus.ERROR) {
							setErrorMessage(status.msg);
						} else if (status.status == IStatus.WARNING) {
							setMessage(status.msg, IMessageProvider.WARNING);
						} else if (status.status == IStatus.INFO) {
							setMessage(status.msg, IMessageProvider.INFORMATION);
						} else {
							setMessage(status.msg, IMessageProvider.NONE);
						}
						setPageComplete(status.isOk());
						return Status.OK_STATUS;
					}
				};
				updateJob.setSystem(true);
			}
			updateJob.schedule(MESSAGE_DELAY);
		}
	}

	public void dispose() {
		for (WizardPageSection s : sections) {
			s.dispose();
		}
		super.dispose();
	}

	@Override
	public IRunnableContext getRunnableContext() {
		return getContainer();
	}
}
