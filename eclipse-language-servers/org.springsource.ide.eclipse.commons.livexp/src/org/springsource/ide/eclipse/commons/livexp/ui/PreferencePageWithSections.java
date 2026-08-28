/*******************************************************************************
 * Copyright (c) 2013, 2015 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui;

import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;
import org.springsource.ide.eclipse.commons.livexp.core.CompositeValidator;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;

/**
 * An abstract super class to more easily create preferences pages composed of modular sections.
 * Each section encapsulates its own widgetry and validation logic.
 *
 * @author Kris De Volder
 */
public abstract class PreferencePageWithSections extends PreferencePage implements IWorkbenchPreferencePage, ValueListener<ValidationResult>, IPageWithSections {

	private List<PrefsPageSection> sections = null;

	public PreferencePageWithSections() {
	}

	public void init(IWorkbench workbench) {
	}

	/**
	 * This method should be implemented to generate the contents of the page.
	 */
	protected abstract List<PrefsPageSection> createSections();

	@Override
	protected Control createContents(Composite parent) {
		final Composite page = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginHeight = 1;
        layout.marginWidth = 1;
        page.setLayout(layout);
        CompositeValidator validator = new CompositeValidator();
        for (PrefsPageSection section : getSections()) {
			section.createContents(page);
			validator.addChild(section.getValidator());
		}
        validator.addListener(this);

        //Listener below is workaround for a layout glitch. Seems
        // we occasionally need to trigger an additional 'layout'
        // the first time the page is drawn. Otherwise widgets
        // aren't properly layed out until the page is
        // being resized.
        Listener listener = new Listener() {
			@Override
			public void handleEvent(Event event) {
				page.getShell().layout(true, true);
				page.getShell().redraw();
				page.removeListener(event.type, this);
			}
		};
		page.addListener(SWT.Paint, listener);
        return page;
	}

	@Override
	public boolean performOk() {
		for (PrefsPageSection section : getSections()) {
			boolean ok = section.performOK();
			if (!ok) {
				return false;
			}
		}
		//We reach here only when all sections performOK returned true
		return true;
	}

	private synchronized List<PrefsPageSection> getSections() {
		if (sections == null) {
			sections = createSections();
		}
		return sections;
	}

	@Override
	protected void performDefaults() {
		super.performDefaults();
		for (PrefsPageSection section : getSections()) {
			section.performDefaults();
		}
	}

	public void gotValue(LiveExpression<ValidationResult> exp, ValidationResult status) {
		Display.getDefault().asyncExec(() -> {
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
		});
	}

	public IRunnableContext getRunnableContext() {
		//TODO: can we do something more specific for preferences page?
		return PlatformUI.getWorkbench().getProgressService();
	}
}
