/*******************************************************************************
 * Copyright (c) 2013, 2017 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.cli;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMRunner;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.VMRunnerConfiguration;
import org.springframework.ide.eclipse.boot.core.cli.BootInstallManager;
import org.springframework.ide.eclipse.boot.core.cli.install.IBootInstall;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.util.Log;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

/**
 * Launch Configuration delegate able to start/shut down Spring Boot CLI processes
 *
 * @author Kris De Volder
 * @author Alex Boyko
 *
 */
public class BootCliLaunchConfigurationDelegate extends LaunchConfigurationDelegate {

	private static String[] getSpringBootClasspath(ILaunchConfiguration conf, IBootInstall install) throws Exception {
		File[] bootLibJars = install.getBootLibJars();
		List<String> classpath = new ArrayList<>(2 + bootLibJars.length);
		classpath.add(".");
		Path extensions = install.getHome().toPath().resolve("lib/ext/");
		if (Files.exists(extensions)) {
			classpath.add(extensions.toString());
		}
		classpath.addAll(Arrays.stream(bootLibJars).map(jarFile -> jarFile.toString()).collect(Collectors.toList()));
		return classpath.toArray(new String[classpath.size()]);
	}

	/**
	 * Spring Boot CLI main class to launch (Ideally this shouldn't be overriden)
	 * @param install Spring Boot CLI install
	 * @param launch Eclipse's launch
	 * @param conf Launch configuration
	 * @return main type class name
	 * @throws Exception
	 */
	protected String getMainTypeName(IBootInstall install, ILaunch launch, ILaunchConfiguration conf) throws Exception {
		return "org.springframework.boot.loader.JarLauncher";
	}

	/**
	 * Environment variables for Spring Boot CLI process
	 * @param install Spring Boot CLI install
	 * @param launch Eclipse's launch
	 * @param conf Launch configuration
	 * @return environment variables as array of strings, where each string looks like <b>SPRING_HOME=/Users/me/spring-cli</b>
	 * @throws Exception
	 */
	protected String[] getEnv(IBootInstall install, ILaunch launch, ILaunchConfiguration conf) throws Exception {
		Map<String, String> map = DebugPlugin.getDefault().getLaunchManager().getNativeEnvironment();
		IVMInstall vmInstall = verifyVMInstall(conf);
		map.put("JAVA_HOME", vmInstall.getInstallLocation().toString());
		try {
			map.put("SPRING_HOME", install.getHome().toString());
		} catch (Exception e) {
			Log.log(e);
		}
		List<String> env = new ArrayList<>(map.size());
		String var = null;
		for(Iterator<String> iter = map.keySet().iterator(); iter.hasNext();) {
			var = iter.next();
			String value = map.get(var);
			if (value == null) {
				value = ""; //$NON-NLS-1$
			}
			if (var.equalsIgnoreCase("path")) { //$NON-NLS-1$
//				if (value.indexOf(jrestr) == -1) {
//					value = jrestr+';'+value;
//				}
				continue;
			}
			env.add(var+"="+value); //$NON-NLS-1$
		}

		return env.toArray(new String[env.size()]);
	}

	/**
	 * VM arguments for launching the Spring Boot CLI
	 * @param install Spring Boot CLI install
	 * @param launch Eclipse's launch
	 * @param conf Launch configuration
	 * @return array of VM arguments to launch the CLI
	 * @throws Exception
	 */
	protected String[] getVmArgs(IBootInstall install, ILaunch launch, ILaunchConfiguration conf) throws Exception {
		if (BootLaunchConfigurationDelegate.supportsAnsiConsoleOutput()) {
			return new String[] {"-Dspring.output.ansi.enabled=always"};
		} else {
			return new String[0];
		}
	}

	/**
	 * Specific VM attributes for launching Spring Boot CLI
	 * @param install Spring Boot CLI install
	 * @param launch Eclipse's launch
	 * @param conf Launch configuration
	 * @return specific VM attributes map
	 * @throws Exception
	 */
	protected Map<String, Object> getVmSpecificAttributesMap(IBootInstall install, ILaunch launch, ILaunchConfiguration conf) throws Exception {
		return Collections.emptyMap();
	}

	/**
	 * Program arguments for the Spring Boot CLI. This is the typically overriden method to specify the spring cloud command, i.e. <code>["cloud", "--version"]</code>
	 * @param install Spring Boot CLI install
	 * @param launch Eclipse's launch
	 * @param conf Launch configuration
	 * @return Spring Boot CLI command as an array of strings
	 * @throws Exception
	 */
	protected String[] getProgramArgs(IBootInstall install, ILaunch launch, ILaunchConfiguration conf) throws Exception {
		return new String[0];
	}

	/**
	 * Working directory for launching the Spring Boot CLI
	 * @param install Spring Boot CLI install
	 * @param launch Eclipse's launch
	 * @param conf Launch configuration
	 * @return working directory for the launch
	 * @throws Exception
	 */
	protected String getWorkingDirectory(IBootInstall install, ILaunch launch, ILaunchConfiguration conf) throws Exception {
		return install.getHome().toString();
	}

	@Override
	final public void launch(ILaunchConfiguration conf, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {
		//TODO: some common things that Java launch configs do that this one does not (yet) do but probably should
		//  - offer to save unsaved files
		//  - check for errors in project
		//  - source locators (for debugging processes)
		//  - launching in debug mode
		try {
			IBootInstall install = BootInstallManager.getInstance().getDefaultInstall();
			IVMInstall vm = verifyVMInstall(conf);
			IVMRunner runner = vm.getVMRunner(mode);

			String mainTypeName = getMainTypeName(install, launch, conf);
			String[] classpath = getSpringBootClasspath(conf, install);

			VMRunnerConfiguration runConfiguration = new VMRunnerConfiguration(mainTypeName, classpath);

			runConfiguration.setProgramArguments(getProgramArgs(install, launch, conf));
			runConfiguration.setVMArguments(getVmArgs(install, launch, conf));
			runConfiguration.setWorkingDirectory(getWorkingDirectory(install, launch, conf));
			runConfiguration.setEnvironment(getEnv(install, launch, conf));
			runConfiguration.setVMSpecificAttributesMap(getVmSpecificAttributesMap(install, launch, conf));

			runner.run(runConfiguration, launch, monitor);

		} catch (Exception e) {
			throw ExceptionUtil.coreException(e);
		}
	}

	protected IVMInstall verifyVMInstall(ILaunchConfiguration conf) {
		//Extremely simplistic implementation. Just gets the default JVM for this workspace.
		//TODO: project specific JVM selection or maybe the JVM should be associated with
		// spring boot installation.
		return JavaRuntime.getDefaultVMInstall();
	}

}

