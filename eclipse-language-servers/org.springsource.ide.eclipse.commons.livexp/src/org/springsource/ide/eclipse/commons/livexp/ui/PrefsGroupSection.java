/*******************************************************************************
 * Copyright (c) 2013 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.springsource.ide.eclipse.commons.livexp.core.CompositeValidator;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;

/**
 * A composite PrefsPageSection that wraps its subsections inside a Group element with
 * a title.
 * 
 * @author Kris De Volder
 */
public class PrefsGroupSection extends PrefsPageSection {

	/**
	 * Sections inside the group element.
	 */
	private List<PrefsPageSection> sections;
	
	/**
	 * The validator for the Group is a composite of the validators for each section.
	 */
	private CompositeValidator validator;
	
	private String groupTitle;
	
	/**
	 * Setting isVisible to false will make this group disappear.
	 * Setting it to true will make it re-appear.
	 */
	public final LiveVariable<Boolean> isVisible = new LiveVariable<Boolean>(true);

	public PrefsGroupSection(PreferencePageWithSections owner, String title, PrefsPageSection... _sections) {
		super(owner);
		this.groupTitle = title;
		this.sections = new ArrayList<PrefsPageSection>();
		for (PrefsPageSection s : _sections) {
			if (s != null) {
				sections.add(s);
			}
		}
		
		validator = new CompositeValidator();
		for (PrefsPageSection s : sections) {
			validator.addChild(s.getValidator());
		}
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return validator;
	}

	@Override
	public void createContents(Composite page) {
		final Group group = new Group(page, SWT.NONE);
		group.setText(groupTitle);
		group.setLayout(new GridLayout(1, false));
		GridDataFactory.fillDefaults().grab(true, false).applyTo(group);
		for (PrefsPageSection s : sections) {
			s.createContents(group);
		}
		isVisible.addListener(new ValueListener<Boolean>() {
			public void gotValue(LiveExpression<Boolean> exp, Boolean isVisible) {
				group.setVisible(isVisible); 
				GridData layout = (GridData) group.getLayoutData();
				layout.exclude = !isVisible;
				group.setLayoutData(layout);
				owner.getShell().layout(new Control[] {group});
			};
		});
	}
	
	@Override
	public boolean performOK() {
		for (PrefsPageSection s : sections) {
			if (!s.performOK()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public void performDefaults() {
		for (PrefsPageSection s : sections) {
			s.performDefaults();
		}
	}
	
}
