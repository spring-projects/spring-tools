/*******************************************************************************
 * Copyright (c) 2016, 2021 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.ui;

import java.util.concurrent.Callable;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.springsource.ide.eclipse.commons.livexp.Activator;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.ui.util.SwtConnect;

/**
 * A section containing a single clickable button.
 *
 * @author Kris De Volder
 */
public class ButtonSection extends WizardPageSection {

	private String label;
	private String tooltip;
	private Callable<Void> clickHandler;
    private LiveExpression<Boolean> enabler = LiveExpression.constant(true);

	public ButtonSection(IPageWithSections owner, String label, Runnable clickHandler) {
		this(owner, label, () -> {
			clickHandler.run();
			return null;
		});
	}

	public ButtonSection(IPageWithSections owner, String label, Callable<Void> clickHandler) {
		super(owner);
		this.label = label;
		this.clickHandler = clickHandler;
	}

	@Override
	public void createContents(Composite page) {
		Button button = new Button(page, SWT.PUSH);
		button.setText(label);
		if (tooltip != null) {
			button.setToolTipText(tooltip);
		}
		applyLayoutData(button);
		button.addSelectionListener(new SelectionListener() {
			@Override
			public void widgetSelected(SelectionEvent arg0) {
				try {
					clickHandler.call();
				} catch (Exception e) {
					Activator.log(e);
				}
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent arg0) {
				widgetSelected(arg0);
			}
		});
		
		SwtConnect.connectEnablement(button, enabler);
	}
	
	public ButtonSection tooltip(String tooltip) {
		this.tooltip = tooltip;
		return this;
	}

	/**
	 * Default implementation aligns the button to the right side of the screen. Override this
	 * method to change the layout.
	 */
	protected void applyLayoutData(Button button) {
		if (button.getParent().getLayout() instanceof GridLayout) {
			GridDataFactory.fillDefaults().align(SWT.END, SWT.CENTER).applyTo(button);
		}
	}

	public ButtonSection setEnabler(LiveExpression<Boolean> enabler) {
	    this.enabler = enabler;
	    return this;
	}
}
