/*******************************************************************************
 * Copyright (c) 2016, 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views.properties;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;

import com.google.common.base.Predicate;

/**
 * URL control for property section composite
 *
 * @author Kris De Volder
 */
public class ReadOnlyStringPropertyControl<T extends BootDashElement> extends AbstractBdePropertyControl {

	private static final String PLACEHOLDER_TEXT = "Getting value...";
	private final String labelString;
	private StyledText value;

	private final Class<T> type;
	private final Function<T, String> getter;
	private final boolean asyncRefresh;
	private Predicate<T> visibleWhen = x -> true;
	private Label label;

	public ReadOnlyStringPropertyControl(Class<T> type, String label, Function<T, String> getter, boolean asyncRefresh) {
		this.type = type;
		this.labelString = label;
		this.getter = getter;
		this.asyncRefresh = asyncRefresh;
	}

	@Override
	public void createControl(Composite composite, TabbedPropertySheetPage page) {
		super.createControl(composite, page);

		label = page.getWidgetFactory().createLabel(composite, labelString);
		label.setLayoutData(GridDataFactory.fillDefaults().create()); //$NON-NLS-1$
		value = new StyledText(composite, SWT.READ_ONLY);
		value.setCaret(null);
		value.setText(PLACEHOLDER_TEXT);
		value.setStyleRange(new StyleRange(0, PLACEHOLDER_TEXT.length(), value.getDisplay().getSystemColor(SWT.COLOR_GRAY), null));
		value.setLayoutData(GridDataFactory.fillDefaults().align(SWT.BEGINNING, SWT.FILL).create());
	}

	@Override
	public void refreshControl() {
		BootDashElement element = getBootDashElement();
		// This must on the UI thread
		final Display display = Display.getCurrent();
		if (asyncRefresh) {
			CompletableFuture.supplyAsync(() -> fetchValue(element)).thenAccept(text -> {
				if (!display.isDisposed()) {
					display.asyncExec(() -> updateUI(text));
				}
			});
		} else {
			updateUI(fetchValue(element));
		}
	}

	private void updateUI(String text) {
		boolean show = shouldShow();
		if (show) {
			if (value != null && !value.isDisposed()) {
				value.setText(text);
				int width = value.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
				GridData data = GridDataFactory.copyData(((GridData) value.getLayoutData()));
				data.widthHint = width;
				value.setLayoutData(data);
				value.getParent().layout();
			}
			show();
		} else {
			hide();
		}
	}

	private boolean shouldShow() {
		BootDashElement element = getBootDashElement();
		if (element != null && type.isAssignableFrom(element.getClass())) {
			@SuppressWarnings("unchecked")
			Boolean val = visibleWhen.apply((T)element);
			if (val != null) {
				return val;
			}
		}
		return false;
	}

	protected String fetchValue(BootDashElement element) {
		if (element != null && type.isAssignableFrom(element.getClass())) {
			@SuppressWarnings("unchecked")
			String val = getter.apply((T)element);
			if (val != null) {
				return val;
			}
		}
		return "";
	}

	public ReadOnlyStringPropertyControl<T> visibleWhen(Predicate<T> visibleWhen) {
		this.visibleWhen = visibleWhen; //TODO: support this
		return this;
	}

	// For future possible dynamic filtering of controls
	private void hide() {
		label.setLayoutData(GridDataFactory.swtDefaults().exclude(true).create()); //$NON-NLS-1$
		label.setVisible(false);
		value.setLayoutData(GridDataFactory.swtDefaults().exclude(true).create());
		value.setVisible(false);
		relayout();
	}

	private void relayout() {
		Composite c = label.getParent();
		while (c != null) {
			c.layout(true, true);
			c = c.getParent();
		}
	}

	private void show() {
		label.setLayoutData(GridDataFactory.fillDefaults().create()); //$NON-NLS-1$
		label.setVisible(true);
		value.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
		value.setVisible(true);
		relayout();
	}

}
