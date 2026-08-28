/*******************************************************************************
 * Copyright (c) 2013 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard.guides;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.springframework.ide.eclipse.boot.wizard.importing.ImportStrategies;
import org.springframework.ide.eclipse.boot.wizard.importing.ImportStrategy;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.SelectionModel;
import org.springsource.ide.eclipse.commons.livexp.core.UIValueListener;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;
import org.springsource.ide.eclipse.commons.livexp.ui.GroupSection;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageWithSections;

/**
 * Group of radio buttons that allows selection a ImportStrategy
 */
public class ImportStrategiesRadiosSection extends GroupSection {

	private static class Choice extends WizardPageSection {

		private final ImportStrategy strategy;
		private final LiveVariable<ImportStrategy> selection;

		public Choice(IPageWithSections owner, ImportStrategy buildType, LiveVariable<ImportStrategy> selection) {
			super(owner);
			this.strategy = buildType;
			this.selection = selection;
		}

		@Override
		public LiveExpression<ValidationResult> getValidator() {
			//This validator really doesn't matter because for a radio button group it makes
			// more sense to create a validator for the group rather than to compose it from
			// each component in the group.
			return Validator.constant(ValidationResult.OK);
		}

		@Override
		public void createContents(Composite page) {
			final Button button = new Button(page, SWT.RADIO);
			button.setText(strategy.displayName());
			GridDataFactory.fillDefaults().grab(true, false).applyTo(button);
			selection.addListener(new UIValueListener<ImportStrategy>() {
				@Override
				protected void uiGotValue(LiveExpression<ImportStrategy> exp, ImportStrategy value) {
					button.setSelection(value == strategy);
				}
			});
			button.addSelectionListener(new SelectionListener() {

				//@Override
				public void widgetSelected(SelectionEvent e) {
					if (button.getSelection()) {
						selection.setValue(strategy);
					}
				}

				//@Override
				public void widgetDefaultSelected(SelectionEvent e) {
					if (button.getSelection()) {
						selection.setValue(strategy);
					}
				}
			});
		}

	}

	private final SelectionModel<ImportStrategy> selection;

	@Override
	protected GridLayout createLayout() {
		return new GridLayout(3, true);
	}

	public ImportStrategiesRadiosSection(WizardPageWithSections owner, SelectionModel<ImportStrategy> selection) {
		super(owner, "Build Type", createSections(owner, selection));
		this.selection = selection;
	}

	private static WizardPageSection[] createSections(WizardPageWithSections owner, SelectionModel<ImportStrategy> selection) {
		List<WizardPageSection> section = new ArrayList<>();
		for (ImportStrategy strat : ImportStrategies.all()) {
			section.add(new Choice(owner, strat, selection.selection));
		}
		return section.toArray(new WizardPageSection[section.size()]);
	}

	@Override
	public LiveExpression<ValidationResult> getValidator() {
		return selection.validator;
	}

}
