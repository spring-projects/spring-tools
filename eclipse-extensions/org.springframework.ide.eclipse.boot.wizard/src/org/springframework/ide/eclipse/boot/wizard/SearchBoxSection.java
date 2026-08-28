/*******************************************************************************
 * Copyright (c) 2015, 2023 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Text;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.UIValueListener;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageSection;

/**
 * A text box with the look and feel of a search box. The contents
 * of the searchbox text is mirrored into a LiveVariable 'model'.
 *
 * @author Kris De Volder
 */
public class SearchBoxSection extends WizardPageSection implements Disposable {

	private Text searchBox;
	private LiveVariable<String> model;
	private ValueListener<String> modelListener;

	public SearchBoxSection(IPageWithSections owner, LiveVariable<String> model) {
		super(owner);
		this.model = model;
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return OK_VALIDATOR;
	}

	@Override
	public void createContents(Composite page) {
		searchBox = new Text(page, SWT.SINGLE | SWT.BORDER | SWT.SEARCH | SWT.ICON_CANCEL);
		searchBox.setMessage(getSearchHint());
		searchBox.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
		searchBox.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				model.setValue(searchBox.getText());
			}
		});
		this.model.addListener(modelListener = new UIValueListener<String>() {
			@Override
			public void uiGotValue(LiveExpression<String> exp, String ignore) {
				String newText = model.getValue();
				if (searchBox.isDisposed()) {
					SearchBoxSection.this.dispose();
				} else {
					String oldText = searchBox.getText();
					if (!oldText.equals(newText)) { //Avoid cursor bug on macs.
						searchBox.setText(newText);
					}
				}
			}
		});
//		IContentProposalProvider proposalProvider = new TagContentProposalProvider(viewModel);
//		ContentProposalAdapter caAdapter = new ContentProposalAdapter(searchBox, new TextContentAdapter(), proposalProvider, UIUtils.CTRL_SPACE, null);
//		caAdapter.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);
	}

	protected String getSearchHint() {
		return "Type a text to search";
	}

	@Override
	public void dispose() {
		if (modelListener != null) {
			this.model.removeListener(modelListener);
			modelListener = null;
		}
		searchBox.dispose();
	}

	protected Control getControl() {
		return searchBox;
	}

	@Override
	public void setFocus() {
		if (searchBox != null && !searchBox.isDisposed()) {
			searchBox.setFocus();
		}
	}

}
