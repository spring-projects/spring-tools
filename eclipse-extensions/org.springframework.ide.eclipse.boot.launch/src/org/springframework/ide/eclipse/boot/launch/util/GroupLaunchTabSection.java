/*******************************************************************************
 * Copyright (c) 2012 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.util;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Shell;
import org.springsource.ide.eclipse.commons.livexp.core.CompositeValidator;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.OrExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.ui.GroupSection;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageSection;

/**
 * Like {@link GroupSection} but implements {@link ILaunchConfigurationTabSection}
 *
 * @author Kris De Volder
 */
public class GroupLaunchTabSection extends WizardPageSection implements ILaunchConfigurationTabSection{

	List<IPageSection> sections;

	private CompositeValidator validator;
	private String groupTitle;

	/**
	 * Setting isVisible to false will make this group disappear.
	 * Setting it to true will make it re-appear.
	 */
	public final LiveVariable<Boolean> isVisible = new LiveVariable<Boolean>(true);

	private int columns = 1; //one columnby default

	private LiveExpression<Boolean> dirtyState;

	/**
	 * If title is null then it creates a normal composite without a box around it. Otherwise
	 * it creates a 'group' and uses the title as label for the group.
	 */
	public GroupLaunchTabSection(IPageWithSections owner, String title, WizardPageSection... _sections) {
		super(owner);
		this.groupTitle = title;
		this.sections = new ArrayList<IPageSection>();
		for (IPageSection s : _sections) {
			if (s != null) {
				sections.add(s);
			}
		}

		validator = new CompositeValidator();
		for (IPageSection s : sections) {
			validator.addChild(s.getValidator());
		}
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return validator;
	}

	@Override
	public void createContents(Composite page) {
		final Composite group = createComposite(page);
		for (IPageSection s : sections) {
			s.createContents(group);
		}
		isVisible.addListener(new ValueListener<Boolean>() {
			public void gotValue(LiveExpression<Boolean> exp, Boolean isVisible) {
				group.setVisible(isVisible);
				GridData layout = (GridData) group.getLayoutData();
				layout.exclude = !isVisible;
				group.setLayoutData(layout);
				Shell shell = owner.getShell();
				if (shell != null) {
					shell.layout(new Control[] {group});
				}
			};
		});
	}

	private Composite createComposite(Composite page) {
		if (groupTitle != null) {
			//Create a group with box around it and a title
			final Group group = new Group(page, SWT.NONE);
			group.setText(groupTitle);
			group.setLayout(createLayout());
			GridDataFactory.fillDefaults().grab(true, false).applyTo(group);
			return group;
		} else {
			//Create a normal composite. No box
			final Composite composite = new Composite(page, SWT.NONE);
			composite.setLayout(createLayout());
			GridDataFactory.fillDefaults().grab(true, false).applyTo(composite);
			return composite;
		}
	}

	/**
	 * Factory method that creates the layout for the group. Subclass may override to change how
	 * components in the group are layed out. Default creates a gridlayout with specified number of
	 * columns of equals width
	 */
	protected GridLayout createLayout() {
		GridLayout layout = new GridLayout(this.columns, true);
		if (groupTitle == null) {
			layout.marginWidth = 0;
		}
		return layout;
	}

	@Override
	public void dispose() {
		for (IPageSection s : sections) {
			if (s instanceof Disposable) {
				((Disposable) s).dispose();
			}
		}
		if (dirtyState != null) {
			dirtyState.dispose();
		}
		super.dispose();
	}

	public GroupLaunchTabSection columns(int i) {
		Assert.isLegal(i >= 1);
		this.columns = i;
		return this;
	}

	@Override
	public void initializeFrom(ILaunchConfiguration conf) {
		for (IPageSection s : sections) {
			if (s instanceof ILaunchConfigurationTabSection) {
				((ILaunchConfigurationTabSection) s).initializeFrom(conf);
			}
		}
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy conf) {
		for (IPageSection s : sections) {
			if (s instanceof ILaunchConfigurationTabSection) {
				((ILaunchConfigurationTabSection) s).performApply(conf);
			}
		}
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy conf) {
		for (IPageSection s : sections) {
			if (s instanceof ILaunchConfigurationTabSection) {
				((ILaunchConfigurationTabSection) s).setDefaults(conf);
			}
		}
	}

	@Override
	public LiveExpression<Boolean> getDirtyState() {
		if (dirtyState == null) {
			OrExpression ds = new OrExpression();
			for (IPageSection s : sections) {
				if (s instanceof ILaunchConfigurationTabSection) {
					ds.addChild(((ILaunchConfigurationTabSection) s).getDirtyState());
				}
			}
			dirtyState = ds;
		}
		return dirtyState;
	}

}
