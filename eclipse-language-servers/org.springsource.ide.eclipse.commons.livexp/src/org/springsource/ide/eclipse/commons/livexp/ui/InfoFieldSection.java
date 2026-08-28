/*******************************************************************************
 * Copyright (c) 2013 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.UIValueListener;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;

/**
 * Section containing a short info field. This is similar to a comment section except that
 * it provides a label and a text to display. This is so it can be inserted into
 * a page/dialog with a number of other fields and align nicely rather than look
 * out of place.
 * 
 * @author Kris De Volder
 */
public class InfoFieldSection extends WizardPageSection {

	private final String labelText;
	private final LiveExpression<String> infoTextExp;

	public InfoFieldSection(IPageWithSections owner, String label, LiveExpression<String> info) {
		super(owner);
		this.labelText = label;
		this.infoTextExp = info;
	}

	public InfoFieldSection(IPageWithSections owner, String label, String info) {
		this(owner, label, LiveExpression.constant(info));
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return Validator.OK;
	}

	@Override
	public void createContents(Composite page) {
		Composite composite =  new Composite(page, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.numColumns = 2;
        
        layout.marginWidth = 0;
        composite.setLayout(layout);
        composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        
        Label label = new Label(composite, SWT.NONE);
        label.setText(labelText);
        GridDataFactory.fillDefaults()
        	.hint(UIConstants.fieldLabelWidthHint(label), SWT.DEFAULT)
        	.align(SWT.BEGINNING, SWT.CENTER)
        	.applyTo(label);

        Label info = new Label(composite, SWT.NONE);
        infoTextExp.addListener(UIValueListener.from((e,v) -> {
            info.setText(getInfoText());
            composite.layout(new Control[] {info});
        }));
        GridDataFactory.fillDefaults()
        	.grab(true, false)
        	.align(SWT.BEGINNING, SWT.BEGINNING)
        	.applyTo(info);
	}

	private String getInfoText() {
		String v = infoTextExp.getValue();
		return v == null ? "" : v;
	}

}
