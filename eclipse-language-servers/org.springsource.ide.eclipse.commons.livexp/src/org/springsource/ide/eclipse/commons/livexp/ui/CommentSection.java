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
package org.springsource.ide.eclipse.commons.livexp.ui;

import static org.springsource.ide.eclipse.commons.livexp.core.LiveExpression.constant;
import static org.springsource.ide.eclipse.commons.livexp.core.ValidationResult.OK;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;

import org.eclipse.swt.widgets.Label;

public class CommentSection extends PrefsPageSection {

	private String text;
	private Label l;

	public CommentSection(IPageWithSections owner, String text) {
		super(owner);
		this.text = text;
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return constant(OK); //Nothing much to validate this just displays a comment.
	}

	@Override
	public void createContents(Composite page) {
		l = new Label(page, SWT.WRAP);
		l.setText(text);
		GridDataFactory.fillDefaults().grab(true, false).hint(UIConstants.DIALOG_WIDTH_HINT, SWT.DEFAULT).applyTo(l);
	}
	
	@Override
	public void dispose() {
		if (l != null && !l.isDisposed()) {
			l.dispose();
			l = null;
		}
	}
	
	public void setText(String text) {
		if (text != null && !l.isDisposed()) {
			l.setText(text);
		}
	}

	@Override
	public boolean performOK() {
		return true;
	}

	@Override
	public void performDefaults() {
	}

}
