/*******************************************************************************
 * Copyright (c) 2013, 2017 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.cli;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.springframework.ide.eclipse.boot.core.cli.install.IBootInstall;

public class BootGroovyScriptLaunchConfigurationDelegate extends BootCliLaunchConfigurationDelegate {

	public static final String ID = "org.springsource.ide.eclipse.boot.groovy.script.launch";

	/*
	  Example of a commandline invocation of the spring boot runtime. This is what we
	  are to emulate in here:

	/usr/lib/jvm/java-7-oracle/bin/java
		-cp
		.:/home/kdvolder/Applications/spring-0.5.0.M6/bin:/home/kdvolder/Applications/spring-0.5.0.M6/lib/spring-boot-cli-0.5.0.M6.jar
		org.springframework.boot.loader.JarLauncher
		run
		app.groovy

	*/

	private static final String SCRIPT_RSRC = "spring.groovy.script.rsrc";

	public static void setScript(ILaunchConfigurationWorkingCopy wc, IFile rsrc) {
		wc.setAttribute(SCRIPT_RSRC, rsrc.getFullPath().toString());
	}

	private static IFile getScript(ILaunchConfiguration conf) throws CoreException {
		String fullPathStr = conf.getAttribute(SCRIPT_RSRC, (String)null);
		if (fullPathStr != null) {
			IPath fullPath = new Path(fullPathStr);
			Assert.isLegal(fullPath.segmentCount() >= 2);
			String projectName = fullPath.segment(0);
			IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
			return project.getFile(fullPath.removeFirstSegments(1));
		}
		return null;
	}

	private static String getProjectName(ILaunchConfiguration conf) throws CoreException {
		String fullPathStr = conf.getAttribute(SCRIPT_RSRC, (String)null);
		if (fullPathStr != null) {
			IPath fullPath = new Path(fullPathStr);
			return fullPath.segment(0);
		}
		return null;
	}

	private static IProject getProject(ILaunchConfiguration conf) throws CoreException {
		String name = getProjectName(conf);
		if (name != null) {
			return ResourcesPlugin.getWorkspace().getRoot().getProject(name);
		}
		return null;
	}

	@Override
	protected String getWorkingDirectory(IBootInstall install, ILaunch launch, ILaunchConfiguration conf)
			throws Exception {
		IProject project = getProject(conf);
		return project.getLocation().toFile().toString();
	}

	@Override
	protected String[] getProgramArgs(IBootInstall install, ILaunch launch, ILaunchConfiguration conf) throws Exception {
		return new String[] {
			"run",
			getScript(conf).getProjectRelativePath().toString()
		};
	}



}
