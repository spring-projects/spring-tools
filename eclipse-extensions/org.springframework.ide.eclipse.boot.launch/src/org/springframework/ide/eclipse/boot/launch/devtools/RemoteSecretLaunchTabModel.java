/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.devtools;

import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.springsource.ide.eclipse.commons.livexp.core.StringFieldModel;

/**
 * Launch tab model for the DevTools remote secret field. Unlike the generic
 * {@link StringFieldLaunchTabModel}, reads and writes go through
 * {@link BootDevtoolsClientLaunchConfigurationDelegate#getRemoteSecret(ILaunchConfiguration)}
 * and {@link BootDevtoolsClientLaunchConfigurationDelegate#setRemoteSecret(ILaunchConfigurationWorkingCopy, String)},
 * so the secret is kept in secure storage rather than as a plain launch
 * configuration attribute.
 *
 * @author Broadcom, Inc.
 */
public class RemoteSecretLaunchTabModel extends StringFieldLaunchTabModel {

	public RemoteSecretLaunchTabModel(StringFieldModel field) {
		super(field, BootDevtoolsClientLaunchConfigurationDelegate.REMOTE_SECRET);
	}

	@Override
	protected String getAttribute(ILaunchConfiguration conf) {
		String secret = BootDevtoolsClientLaunchConfigurationDelegate.getRemoteSecret(conf);
		return secret == null ? getDefaultValue() : secret;
	}

	@Override
	protected void setAttribute(ILaunchConfigurationWorkingCopy conf, String value) {
		BootDevtoolsClientLaunchConfigurationDelegate.setRemoteSecret(conf, value);
	}

}
