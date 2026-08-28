/*******************************************************************************
 * Copyright (c) 2013 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.UIValueListener;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;

public class ChooseOneSection<T extends Ilabelable> extends WizardPageSection {

	private static final boolean DEBUG = (""+Platform.getLocation()).contains("kdvolder");

	private String labelText;
	private LiveExpression<T[]> validChoices;
	private LiveVariable<T> chosen;
	private LiveExpression<ValidationResult> validator;

	private boolean vertical = false;
	
	private Integer heightHint = null;
	
	public ChooseOneSection(IPageWithSections owner,
			String labelText,
			T[] validChoices,
			LiveVariable<T> chosen,
			LiveExpression<ValidationResult> validator
	) {
	    this(owner, labelText, LiveExpression.constant(validChoices), chosen, validator);
	}
	
    public ChooseOneSection(IPageWithSections owner, String labelText, LiveExpression<T[]> validChoices,
            LiveVariable<T> chosen, LiveExpression<ValidationResult> validator) {
        super(owner);
        this.labelText = labelText;
        this.validChoices = validChoices;
        this.chosen = chosen;
        this.validator = validator;

    }

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return validator;
	}

	@SuppressWarnings("unchecked")
	private T getSingleSelection(ListViewer lv) {
		if (lv != null) {
			ISelection sel = lv.getSelection();
			if (sel instanceof IStructuredSelection) {
				return (T) ((IStructuredSelection) sel).getFirstElement();
			}
		}
		return null;
	}

	@Override
	public void createContents(Composite page) {

		Composite composite = new Composite(page, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = (labelText == null||vertical) ? 1 : 2;
		layout.marginWidth = 0;
		composite.setLayout(layout);
		GridDataFactory grab = GridDataFactory.fillDefaults().grab(true, true);
		if (heightHint != null) {
		    grab = grab.hint(SWT.DEFAULT, heightHint);
		}
		grab.applyTo(composite);
		composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		if (labelText != null) {
			Label label = new Label(composite, SWT.NONE);
			label.setText(labelText);
			if (!vertical) {
				GridDataFactory.fillDefaults()
				.align(SWT.CENTER, SWT.BEGINNING)
				.hint(UIConstants.fieldLabelWidthHint(label), SWT.DEFAULT)
				.applyTo(label);
			}
		}

		final ListViewer tv = new ListViewer(composite, SWT.SINGLE|SWT.BORDER|SWT.V_SCROLL);
		grab.applyTo(tv.getList());
		tv.setContentProvider(new ContentProvider());
		tv.setLabelProvider(new SimpleLabelProvider());
		tv.setInput(validChoices);
		
        validChoices.onChange(UIValueListener.from((e, v) -> {
            tv.refresh(true);
        }));

		chosen.addListener(new UIValueListener<T>() {
			public void uiGotValue(LiveExpression<T> exp, T value) {
				if (value == null) {
					tv.setSelection(StructuredSelection.EMPTY);
				} else {
					tv.setSelection(new StructuredSelection(value));
				}
			}
		});

		if (DEBUG) {
			chosen.addListener(new ValueListener<T>() {
				public void gotValue(LiveExpression<T> exp, T value) {
					System.out.println("starter: "+value);
				}
			});
		}

		tv.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				chosen.setValue(getSingleSelection(tv));
			}
		});

		if (owner instanceof IPageWithOkButton) {
			tv.addDoubleClickListener(new IDoubleClickListener() {
				public void doubleClick(DoubleClickEvent event) {
					((IPageWithOkButton)owner).clickOk();
				}
			});
		}

	}

	class ContentProvider implements IStructuredContentProvider {
		public void dispose() {
		}
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}
		public Object[] getElements(Object inputElement) {
			T[] value  = validChoices.getValue();
            return value == null ? new Object[0] : value;
		}
	}

	public ChooseOneSection<T> vertical() {
		vertical = true;
		return this;
	}

    public void setHeightHint(int heightHint) {
        this.heightHint = heightHint;
    }
	
}
