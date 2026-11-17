/*******************************************************************************
 * Copyright (c) 2015, 2026 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.util;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.springframework.ide.eclipse.boot.launch.util.BootLaunchUtils;

/**
 * @author Kris De Volder
 */
public class LaunchConfRunStateTracker extends RunStateTracker<ILaunchConfiguration> {

	@Override
	protected ILaunchConfiguration getOwner(ILaunch l) {
		ILaunchConfiguration conf = l.getLaunchConfiguration();
		if (conf instanceof ILaunchConfigurationWorkingCopy && BootLaunchUtils.isBootLaunch(l)) {
			//Because ngrok expose cheats and launches a working copy...
			return ((ILaunchConfigurationWorkingCopy)conf).getOriginal();
		}
		return conf;
	}

}
