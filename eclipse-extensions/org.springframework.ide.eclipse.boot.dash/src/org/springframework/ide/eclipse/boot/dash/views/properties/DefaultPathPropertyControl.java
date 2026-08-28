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
package org.springframework.ide.eclipse.boot.dash.views.properties;

import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposalListener2;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.views.properties.tabbed.TabbedPropertySheetPage;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.views.sections.RequestMappingContentProposalProvider;
import org.springframework.ide.eclipse.boot.dash.views.sections.UIUtils;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;

/**
 * Default Path control for the properties section
 *
 * @author Alex Boyko
 *
 */
public class DefaultPathPropertyControl extends AbstractBdePropertyControl {

	private Text defaultPath;
	private LiveVariable<BootDashElement> liveVar = new LiveVariable<BootDashElement>();
	private ContentProposalAdapter ca;

	/**
	 * Tracks whether editor value is dirty. Necessary to avoid from accidentally
	 * synchronizing the editor text with the model (when there are change event),
	 * and then loosing text we are currently typing.
	 */
	private boolean isDirty = false;

	final private KeyListener KEY_LISTENER = new KeyAdapter() {
		@Override
		public void keyPressed(KeyEvent e) {
			if (e.doit) {
				if (e.character == '\u001b') { // Escape character
					defaultPath.setText(getElementText());
					e.widget.getDisplay().getActiveShell().forceFocus();
					isDirty = false;
				} else if (e.character == '\r') { // Return key
					e.widget.getDisplay().getActiveShell().forceFocus();
				} else {
					isDirty = true;
				}
			}
		}
	};

	@Override
	public void createControl(final Composite composite, TabbedPropertySheetPage page) {
		super.createControl(composite, page);
		page.getWidgetFactory().createLabel(composite, "Path:").setLayoutData(GridDataFactory.fillDefaults().create()); //$NON-NLS-1$
		defaultPath = page.getWidgetFactory().createText(composite, ""); //$NON-NLS-1$
		defaultPath.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
		defaultPath.addFocusListener(new FocusAdapter() {
			@Override
			public void focusLost(FocusEvent e) {
				save();
			}
		});

		defaultPath.addKeyListener(KEY_LISTENER);

		ca = new ContentProposalAdapter(
				defaultPath, new TextContentAdapter(),
				new RequestMappingContentProposalProvider(liveVar),
				UIUtils.CTRL_SPACE, UIUtils.PATH_CA_AUTO_ACTIVATION_CHARS);

		ca.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);

		ca.addContentProposalListener(new IContentProposalListener2() {
			@Override
			public void proposalPopupOpened(ContentProposalAdapter adapter) {
				defaultPath.removeKeyListener(KEY_LISTENER);
			}
			@Override
			public void proposalPopupClosed(ContentProposalAdapter adapter) {
				defaultPath.addKeyListener(KEY_LISTENER);
			}
		});

	}

	@Override
	public void refreshControl() {
		if (!isDirty && defaultPath != null && !defaultPath.isDisposed()) {
			defaultPath.setText(getElementText());
		}
	}

	private void save() {
		BootDashElement bde = getBootDashElement();
		if (bde != null && !defaultPath.getText().equals(getElementText())) {
			bde.setDefaultRequestMappingPath(defaultPath.getText());
		}
		isDirty = false;
	}

	protected String getElementText() {
		BootDashElement e = getBootDashElement();
		if (e != null) {
			String str = e.getDefaultRequestMappingPath();
			if (str != null) {
				return str;
			}
		}
		return "";
	}

	@Override
	public void setInput(BootDashElement bde) {
		super.setInput(bde);
		liveVar.setValue(bde);
	}

}
