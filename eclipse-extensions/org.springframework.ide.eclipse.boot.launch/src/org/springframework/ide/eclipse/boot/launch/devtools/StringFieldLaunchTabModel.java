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
package org.springframework.ide.eclipse.boot.launch.devtools;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.launch.LaunchTabSelectionModel;
import org.springsource.ide.eclipse.commons.livexp.core.StringFieldModel;

public class StringFieldLaunchTabModel extends LaunchTabSelectionModel<String> {

	public final StringFieldModel field;
	public String attributeId;

	public StringFieldLaunchTabModel(StringFieldModel field, String attributeId) {
		super(field.getVariable(), field.getValidator());
		this.field = field;
		this.attributeId = attributeId;
	}

	@Override
	public void initializeFrom(ILaunchConfiguration conf) {
		selection.setValue(getAttribute(conf));
		getDirtyState().setValue(false);
	}

	@Override
	public void performApply(ILaunchConfigurationWorkingCopy conf) {
		setAttribute(conf, selection.getValue());
		getDirtyState().setValue(false);
	}

	@Override
	public void setDefaults(ILaunchConfigurationWorkingCopy conf) {
		setAttribute(conf, getDefaultValue());
	}

	/////////////////////////////

	protected String getAttribute(ILaunchConfiguration conf) {
		try {
			return conf.getAttribute(attributeId, getDefaultValue());
		} catch (CoreException e) {
			BootActivator.log(e);
		}
		return getDefaultValue();
	}

	protected void setAttribute(ILaunchConfigurationWorkingCopy conf, String value) {
		conf.setAttribute(attributeId, value);
	}

	public String getDefaultValue() {
		return field.getDefaultValue();
	}

}
