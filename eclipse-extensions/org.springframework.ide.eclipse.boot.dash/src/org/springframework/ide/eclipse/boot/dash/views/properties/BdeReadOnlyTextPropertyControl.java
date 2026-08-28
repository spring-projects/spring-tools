/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views.properties;

import java.util.function.Function;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;

/**
 * Property page control that displays the value of a read-only textual property of a {@link BootDashElement}
 *
 * @author Alex Boyko
 * @author Kris De Volder
 */
public class BdeReadOnlyTextPropertyControl<T extends BootDashElement> extends AbstractBdePropertyControl {

	final private String label;
	final private Function<T, String> getter;
	final private Class<T> type;

	public BdeReadOnlyTextPropertyControl(Class<T> type, String label, Function<T, String> getter) {
		this.label = label;
		this.type = type;
		this.getter = getter;
	}

	private Label textWidget;

	@Override
	public void createControl(Composite composite, TabbedPropertySheetPage page) {
		super.createControl(composite, page);

		page.getWidgetFactory().createLabel(composite, label).setLayoutData(GridDataFactory.fillDefaults().create()); //$NON-NLS-1$
		textWidget = page.getWidgetFactory().createLabel(composite, ""/*, SWT.BORDER*/); //$NON-NLS-1$
		textWidget.setLayoutData(GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.FILL).create());
	}

	@Override
	public void refreshControl() {
		BootDashElement element = getBootDashElement();
		if (textWidget != null && !textWidget.isDisposed()) {
			textWidget.setText(getPropertyValue(element));
		}
	}

	protected String getPropertyValue(BootDashElement service){
		BootDashElement element = getBootDashElement();
		if (element != null && type.isAssignableFrom(element.getClass())) {
			@SuppressWarnings("unchecked")
			String val = getter.apply((T)element);
			if (val != null) {
				return val;
			}
		}
		return "";
	}
}
