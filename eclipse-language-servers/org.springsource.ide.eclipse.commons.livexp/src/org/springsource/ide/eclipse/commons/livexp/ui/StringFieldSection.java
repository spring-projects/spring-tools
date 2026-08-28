/*******************************************************************************
 * Copyright (c) 2013, 2023 VMware Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Pivotal VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.springsource.ide.eclipse.commons.livexp.core.FieldModel;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.UIValueListener;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;

public class StringFieldSection extends WizardPageSection {

	/// options
	private String labelText; //what text to use for the label of the field
	private String tooltip = null; // what text to use for tooltip

	/// model elements
	private final LiveVariable<String> variable;
	private final LiveExpression<ValidationResult> validator;
	private LiveExpression<Boolean> enabler = LiveExpression.constant(true);

	/// UI elements
	private Text text;
	private boolean password = false;
	
	private boolean vertical = false;
	
	private boolean labelUnder = false;

	//////////////////////////////

	public StringFieldSection(IPageWithSections owner,
			String labelText,
			LiveVariable<String> variable,
			LiveExpression<ValidationResult> validator) {
		super(owner);
		this.labelText = labelText;
		this.variable = variable;
		this.validator = validator;
	}
	
	/**
	 * Should be called before createContents(...) is called, i.e. before SWT controls are created.
	 */
	public  StringFieldSection vertical(boolean vertical) {
		this.vertical = vertical;
		return this;
	}
	
	/**
	 * Should be called before createContents(...) is called, i.e. before SWT controls are created.
	 */
	public  StringFieldSection labelUnder(boolean under) {
		this.labelUnder = under;
		this.vertical = under || this.vertical;
		return this;
	}
	
	public StringFieldSection(IPageWithSections owner, FieldModel<String> f) {
		this(owner, f.getLabel(), f.getVariable(), f.getValidator());
	}

	public StringFieldSection(IPageWithSections owner,
			String label,
			LiveVariable<String> variable
	) {
		this(owner, label, variable, Validator.OK);
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return validator;
	}

	public StringFieldSection setPassword(boolean enablePasswordBehavior) {
		this.password = enablePasswordBehavior;
		return this;
	}
	
	private Label createLabel(Composite projectGroup) {
        Label label = new Label(projectGroup, SWT.NONE);
        label.setText(labelText);
        GridDataFactory.fillDefaults()
        	.hint(vertical ? SWT.DEFAULT : UIConstants.fieldLabelWidthHint(label), SWT.DEFAULT)
        	.align(SWT.BEGINNING, SWT.CENTER)
        	.applyTo(label);
        if (tooltip != null) {
        	label.setToolTipText(tooltip);
        	text.setToolTipText(tooltip);
        }
        return label;
	}
	
	@Override
	public void createContents(Composite page) {
        // project specification group
        Composite projectGroup = new Composite(page, SWT.NONE);
        
        int columns = vertical ? 1 : 2;
        GridLayout layout = GridLayoutFactory.fillDefaults().numColumns(columns).margins(0,2).create();
        projectGroup.setLayout(layout);
        projectGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        if (!labelUnder) {
        	createLabel(projectGroup);
        }

        text = new Text(projectGroup, SWT.BORDER | (password ? SWT.PASSWORD : 0));
        GridData data = new GridData(GridData.FILL_HORIZONTAL);
        data.widthHint = UIConstants.FIELD_TEXT_AREA_WIDTH;
        text.setLayoutData(data);
        
        if (labelUnder) {
        	createLabel(projectGroup);
        }
        
        text.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				variable.setValue(text.getText());
			}
		});
        variable.addListener(UIValueListener.from((exp, value) -> {
			if (text != null && !text.isDisposed()) {
				if (value == null) {
					value = "";
				}
				String oldText = text.getText();
				if (!oldText.equals(value)) {
					text.setText(value);
				}
			}
		}));
        enabler.addListener(UIValueListener.from((exp, enable) -> {
        	text.setEnabled(enable);
        }));
	}

	public StringFieldSection tooltip(String string) {
		this.tooltip = string;
		return this;
	}

	public StringFieldSection setEnabler(LiveExpression<Boolean> enable) {
		this.enabler = enable;
		return this;
	}

	@Override
	public void setFocus() {
		if (text != null && !text.isDisposed()) {
			text.setFocus();
		}
	}

}
