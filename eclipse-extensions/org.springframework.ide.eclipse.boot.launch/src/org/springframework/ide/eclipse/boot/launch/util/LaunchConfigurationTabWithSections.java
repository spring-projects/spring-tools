/*******************************************************************************
 * Copyright (c) 2015 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.util;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.internal.ui.SWTFactory;
import org.eclipse.debug.ui.AbstractLaunchConfigurationTab;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.springsource.ide.eclipse.commons.livexp.core.CompositeValidator;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.OrExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;

@SuppressWarnings("restriction")
public abstract class LaunchConfigurationTabWithSections extends AbstractLaunchConfigurationTab
implements IPageWithSections, Disposable {

	private List<IPageSection> sections = null;
	private boolean disposed = false;

	protected abstract List<IPageSection> createSections();

	/**
	 * This method is final. To add content to a {@link LaunchConfigurationTabWithSections}
	 * override 'createSections' instead.
	 */
	@Override
	public final void createControl(Composite parent) {
		Composite page = SWTFactory.createComposite(parent, parent.getFont(), 1, 1, GridData.FILL_BOTH);
		//((GridLayout)page.getLayout()).verticalSpacing = 0;

		CompositeValidator validator = new CompositeValidator();
		OrExpression dirtyState = new OrExpression();
		for (IPageSection s : getSections()) {
			s.createContents(page);
			validator.addChild(s.getValidator());
			if (s instanceof ILaunchConfigurationTabSection) {
				dirtyState.addChild(((ILaunchConfigurationTabSection) s).getDirtyState());
			}
		}
		setControl(page);

		validator.addListener(new ValueListener<ValidationResult>() {
			public void gotValue(LiveExpression<ValidationResult> exp, ValidationResult value) {
				if (value.isOk()) {
					setErrorMessage(null);
					setWarningMessage(null);
					scheduleUpdateJob();
				} else if (value.status == IStatus.WARNING) {
					setErrorMessage(null);
					setWarningMessage(value.msg);
					scheduleUpdateJob();
				} else {
					setWarningMessage(null);
					setErrorMessage(value.msg);
					scheduleUpdateJob();
				}
			}
		});
		dirtyState.addListener(new ValueListener<Boolean>() {
			public void gotValue(LiveExpression<Boolean> exp, Boolean dirty) {
				if (dirty != null && dirty != isDirty()) {
					setDirty(dirty);
					if (dirty) {
						//no sense in refreshing UI when state actually became 'clean'.
						scheduleUpdateJob();
					}
				}
			}
		});

	}

	@Override
	protected void scheduleUpdateJob() {
		//ignore 'resfresh' request when there's no UI.
		Control control = getControl();
		if (control != null && !control.isDisposed()) {
			super.scheduleUpdateJob();
		}
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy configuration) {
		for (IPageSection s : getSections()) {
			if (s instanceof ILaunchConfigurationTabSection) {
				((ILaunchConfigurationTabSection)s).setDefaults(configuration);
			}
		}
	}

	private Iterable<IPageSection> getSections() {
		if (!disposed) {
			if (sections == null) {
				sections = createSections();
			}
			return sections;
		}
		return Collections.emptyList();
	}

	@Override
	public void initializeFrom(ILaunchConfiguration configuration) {
		for (IPageSection s : getSections()) {
			if (s instanceof ILaunchConfigurationTabSection) {
				((ILaunchConfigurationTabSection)s).initializeFrom(configuration);
			}
		}
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		for (IPageSection s : getSections()) {
			if (s instanceof ILaunchConfigurationTabSection) {
				((ILaunchConfigurationTabSection)s).performApply(configuration);
			}
		}
		setDirty(false);
	}

	@Override
	public Shell getShell() {
		return super.getShell();
	}

	@Override
	public void dispose() {
		if (!disposed) {
			if (sections != null) {
				for (IPageSection s : sections) {
					if (s instanceof Disposable) {
						((Disposable) s).dispose();
					}
				}
				sections = null;
			}
			disposed = true;
		}
		super.dispose();
	}

	public IRunnableContext getRunnableContext() {
		return super.getLaunchConfigurationDialog();
	};

}
