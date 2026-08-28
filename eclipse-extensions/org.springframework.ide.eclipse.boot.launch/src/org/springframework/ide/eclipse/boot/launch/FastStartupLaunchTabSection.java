/*******************************************************************************
 * Copyright (c) 2017 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch;

import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.core.BootPreferences;
import org.springframework.ide.eclipse.boot.launch.util.DelegatingLaunchConfigurationTabSection;
import org.springsource.ide.eclipse.commons.livexp.core.FieldModel;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.SelectionModel;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.WizardPageSection;

/**
 * Fast startup UI section for the Boot launch configuration wizard
 *
 * @author Alex Boyko
 *
 */
public class FastStartupLaunchTabSection extends DelegatingLaunchConfigurationTabSection {

	static class UI extends WizardPageSection {

		private String label;
		private Button button;

		private SelectionModel<Boolean> model;

		public UI(IPageWithSections owner, FieldModel<Boolean> model) {
			this(owner, new SelectionModel<>(model.getVariable(), model.getValidator()), model.getLabel());
		}

		public UI(IPageWithSections owner, SelectionModel<Boolean> model, String label) {
			super(owner);
			this.model = model;
			this.label = label;
		}

		@Override
		public void createContents(Composite page) {
			button = new Button(page, SWT.CHECK);
			button.setText(label);
			button.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					model.selection.setValue(button.getSelection());
					if (button.getSelection()) {
						IPreferenceStore store = BootActivator.getDefault().getPreferenceStore();
						boolean remind = store.getBoolean(BootPreferences.PREF_BOOT_FAST_STARTUP_REMIND_MESSAGE);
						if (remind) {
							MessageDialogWithToggle.openWarning(e.display.getActiveShell(), "Fast Startup Warning",
									"Fast startup performs Java VM arguments tuning to enhance startup at the possible expense of a performance of the application during its runtime. It is recommended to use this setting for debugging purposes only",
									"Do not show this message again in the future", !remind, store,
									BootPreferences.PREF_BOOT_FAST_STARTUP_REMIND_MESSAGE);
						}
					}
				}
			});
			model.selection.addListener(new ValueListener<Boolean>() {
				public void gotValue(LiveExpression<Boolean> exp, Boolean selected) {
					if (selected != null) {
						button.setSelection(selected);
					}
				}
			});
		}

		@Override
		public LiveExpression<ValidationResult> getValidator() {
			return model.validator;
		}
	}

	public FastStartupLaunchTabSection(IPageWithSections owner, LaunchTabSelectionModel<Boolean> model) {
		super(owner, model, new UI(owner, model, "Fast startup"));
	}

}
