/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.springsource.ide.eclipse.commons.livexp.core.FieldModel;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.SelectionModel;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageSection;

/**
 * Page section containing a single checkbox.
 *
 * @author Kris De Volder
 */
public class CheckboxSection extends WizardPageSection {

	private String label;
	private Button button;

	private SelectionModel<Boolean> model;
	private boolean grabVer = false;
	private boolean grabHor = false;

	public CheckboxSection(IPageWithSections owner, FieldModel<Boolean> model) {
		this(owner, new SelectionModel<>(model.getVariable(), model.getValidator()), model.getLabel());
	}

	public CheckboxSection(IPageWithSections owner, LiveVariable<Boolean> model, String label) {
		this(owner, new SelectionModel<>(model), label);
	}

	public CheckboxSection(IPageWithSections owner, SelectionModel<Boolean> model, String label) {
		super(owner);
		this.model = model;
		this.label = label;
	}

	public CheckboxSection grabHor(boolean v) {
		grabHor = v;
		return this;
	}

	public CheckboxSection grabVer(boolean v) {
		grabVer = v;
		return this;
	}

	@Override
	public void createContents(Composite page) {
		button = new Button(page, SWT.CHECK);
		button.setText(label);
		applyLayoutData(button);
		button.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				model.selection.setValue(button.getSelection());
			}
		});
		model.selection.addListener(new ValueListener<Boolean>() {
			public void gotValue(LiveExpression<Boolean> exp, Boolean selected) {
				if (selected != null) {
					button.setSelection(selected);
				}
			}
		});
	}

	protected void applyLayoutData(Button button) {
		GridDataFactory.fillDefaults().grab(grabHor, grabVer).applyTo(button);
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return model.validator;
	}
}