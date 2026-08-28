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
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.Hyperlink;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springsource.ide.eclipse.commons.ui.UiUtil;

/**
 * URL control for property section composite
 *
 * @author Alex Boyko
 *
 */
public class UrlPropertyControl<T extends BootDashElement> extends AbstractBdePropertyControl {

	private final String label;
	private final Class<T> type;
	private final Function<T, String> getter;

	private Hyperlink url;

	public UrlPropertyControl(Class<T> type, String label, Function<T, String> getter) {
		this.type = type;
		this.label = label;
		this.getter = getter;
	}

	@Override
	public void createControl(Composite composite, TabbedPropertySheetPage page) {
		super.createControl(composite, page);

		page.getWidgetFactory().createLabel(composite, label).setLayoutData(GridDataFactory.fillDefaults().create()); //$NON-NLS-1$
		url = page.getWidgetFactory().createHyperlink(composite, getUrl(getBootDashElement()), SWT.NO_FOCUS);
		url.setLayoutData(GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.FILL).create());
		url.setEnabled(false);
		url.addHyperlinkListener(new HyperlinkAdapter() {
			@Override
			public void linkActivated(HyperlinkEvent e) {
				if (!url.getText().isEmpty()) {
					UiUtil.openUrl(url.getText());
				}
			}
		});
	}

	@Override
	public void refreshControl() {
		BootDashElement element = getBootDashElement();
		if (url != null && !url.isDisposed()) {
			String text = getUrl(element);
			url.setText(text);
			url.setEnabled(!text.isEmpty());
			int width = url.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
			GridData data = GridDataFactory.copyData(((GridData)url.getLayoutData()));
			data.widthHint = width;
			url.setLayoutData(data);
			url.getParent().layout();
		}
	}

	protected String getUrl(BootDashElement element) {
		if (element != null && type.isAssignableFrom(element.getClass())) {
			@SuppressWarnings("unchecked")
			String url = getter.apply((T)element);
			if (url != null) {
				return url;
			}
		}
		return "";
	}

}
