/*******************************************************************************
 * Copyright (c) 2017 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard;

import java.util.List;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.springframework.ide.eclipse.boot.wizard.CheckBoxesSection.CheckBoxModel;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.ReflowableSection;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageSection;
import org.springsource.ide.eclipse.commons.livexp.util.Filter;

public class SelectedSection<T> extends ReflowableSection {

	private List<CheckBoxModel<T>> model;
	private Composite composite;
	private WizardPageSection[] subsections;

	private String label;
	private LiveVariable<Boolean> visibleState = new LiveVariable<>(true);


	public SelectedSection(IPageWithSections owner, List<CheckBoxModel<T>> model, String label) {
		super(owner);
		this.label = label;
		this.model = model;
	}

	@Override
	public void createContents(Composite page) {
		composite = createComposite(page);
		GridLayoutFactory.fillDefaults().numColumns(1).margins(0, 0).spacing(0, 0).applyTo(composite);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(composite);
		createSubsections();
		composite.pack();
		visibleState.addListener(new ValueListener<Boolean>() {
			@Override
			public void gotValue(LiveExpression<Boolean> exp, Boolean value) {
				if (value != null && composite != null && !composite.isDisposed()) {
					boolean newState = value;
					composite.setVisible(newState);
					GridData data = (GridData) composite.getLayoutData();
					data.exclude = !newState;
					reflow(owner, composite);
				}
			}
		});
	}
	
	private void createSubsections() {
		subsections = new WizardPageSection[Math.max(1, model.size())];

		for (int i = 0; i < model.size(); i++) {
			subsections[i] = new SelectedButtonSection<T>(owner, model.get(i));
			subsections[i].createContents(composite);
		}
	}

	protected Composite createComposite(Composite page) {
		if (this.label != null) {
			Group comp = new Group(page, SWT.NONE);
			comp.setText(label);
			return comp;
		} else {
			return new Composite(page, SWT.NONE);
		}
	}

	public boolean applyFilter(Filter<T> filter) {
		if (subsections != null) {
			boolean visibilityChanged = false;
			for (WizardPageSection subsection : subsections) {
				if (subsection instanceof SelectedButtonSection) {
					@SuppressWarnings("unchecked")
					SelectedButtonSection<T> cb = (SelectedButtonSection<T>) subsection;
					visibilityChanged |= cb.applyFilter(filter);
				}
			}
			if (visibilityChanged) {
				reflow(owner, composite);
			}
			return visibilityChanged;
		}
		return false;
	}

	public boolean hasVisible() {
		if (subsections != null) {
			for (WizardPageSection s : subsections) {
				if (s instanceof SelectedButtonSection) {
					@SuppressWarnings("unchecked")
					SelectedButtonSection<T> cb = ((SelectedButtonSection<T>) s);
					if (cb.isVisible()) {
						return true;
					}
				}
			}
		}
		return false;
	}
	
	boolean isCreated() {
		return composite != null;
	}

	@Override
	public void dispose() {
		
		if (subsections != null) {
			for (WizardPageSection subsection : subsections) {
				subsection.dispose();
			}
		}
		super.dispose();
	}
	
	public void setModel(List<CheckBoxModel<T>> model) {
		dispose();
		this.model = model;
		createSubsections();
		composite.layout();
	}

}
