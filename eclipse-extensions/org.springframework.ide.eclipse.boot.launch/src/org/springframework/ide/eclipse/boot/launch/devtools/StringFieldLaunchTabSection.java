/*******************************************************************************
 * Copyright (c) 2015, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.devtools;

import org.springframework.ide.eclipse.boot.launch.util.DelegatingLaunchConfigurationTabSection;
import org.springframework.ide.eclipse.boot.launch.util.LaunchConfigurationTabWithSections;
import org.springsource.ide.eclipse.commons.livexp.ui.IPageSection;
import org.springsource.ide.eclipse.commons.livexp.ui.StringFieldSection;

public class StringFieldLaunchTabSection {

	public static IPageSection create(LaunchConfigurationTabWithSections owner, StringFieldLaunchTabModel field) {
		return create(owner, field, false);
	}

	public static IPageSection create(LaunchConfigurationTabWithSections owner, StringFieldLaunchTabModel field, boolean password) {
		StringFieldSection ui = new StringFieldSection(owner, field.field);
		ui.setPassword(password);
		return new DelegatingLaunchConfigurationTabSection(owner, field, ui);
	}


}
