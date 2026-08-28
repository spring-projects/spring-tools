/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.jdt.debug.ui.launchConfigurations.JavaDependenciesTab;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.springframework.ide.eclipse.boot.core.SpringBootCore;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

public class BootDependenciesTab extends JavaDependenciesTab {

	@Override
	public void initializeFrom(ILaunchConfiguration conf) {
		//See bug: https://github.com/spring-projects/spring-ide/issues/222
		// If user did not create boot launch config via boot dash and tries to immediately edit classpath
		// an incorrect default classpath will be computed because of the missing classpath provider.
		// So make sure the classpath provider is present.
		IProject project = BootLaunchConfigurationDelegate.getProject(conf);
		try {
			if (project != null && project.hasNature(SpringBootCore.M2E_NATURE) && !conf.hasAttribute(IJavaLaunchConfigurationConstants.ATTR_CLASSPATH_PROVIDER)) {
				ILaunchConfigurationWorkingCopy wc = conf.getWorkingCopy();
				BootLaunchConfigurationDelegate.enableMavenClasspathProviders(wc);
				conf = wc;
			}
		} catch (CoreException e) {
			Log.log(e);
		}
		super.initializeFrom(conf);
	}

}
