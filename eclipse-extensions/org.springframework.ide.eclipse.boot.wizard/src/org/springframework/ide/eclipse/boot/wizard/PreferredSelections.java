/*******************************************************************************
 * Copyright (c) 2012, 2016 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.wizard;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.preference.IPreferenceStore;
import org.springsource.ide.eclipse.commons.livexp.core.FieldModel;

/**
 * Component that keeps track of some of the 'New Spring Starter Project' wizard
 * selections upon finishing the wizard,
 * so that we may pre-select those options again, the next time the wizard
 * is opened.
 *
 * @author Kris De Volder
 */
public class PreferredSelections {

	private String PREFIX = PreferredSelections.class.getName()+".";

	private IPreferenceStore store;

	public PreferredSelections(IPreferenceStore store) {
		Assert.isNotNull(store);
		this.store = store;
	}

	protected String key(String id) {
		String key = PREFIX+id;
		return key;
	}

	public void save(NewSpringBootWizardModel wizard) {
		for (FieldModel<String> input : wizard.stringInputs) {
			if (isInteresting(input)) {
				put(input.getName(), input.getValue());
			}
		}

		for (RadioGroup input : wizard.getRadioGroups().getGroups()) {
			if (isInteresting(input)) {
				put(input.getName(), input.getValue().getValue());
			}
		}
	}

	protected boolean isInteresting(RadioGroup input) {
		return true;
	}

	protected boolean isInteresting(FieldModel<String> input) {
		return true;
	}


	public void restore(NewSpringBootWizardModel wizard) {
		for (FieldModel<String> input : wizard.stringInputs) {
			if (isInteresting(input)) {
				String v = get(input.getName(), input.getValue());
				input.setValue(v);
			}
		}
		for (RadioGroup input : wizard.getRadioGroups().getGroups()) {
			if (isInteresting(input)) {
				String choiceId = get(input.getName(), null);
				RadioInfo info = input.getRadio(choiceId);
				if (info != null) {
					input.getVariable().setValue(info);
				}
			}
		}
	}

	private void put(String id, String value) {
		String key = key(id);
		store.setValue(key, value);
	}

	private String get(String name, String dflt) {
		String key = key(name);
		String v = store.getString(key);
		if (StringUtils.isNotBlank(v)) {
			return v;
		}
		return dflt;
	}

}
