/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Phil Webb - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.restart;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.internal.ui.views.console.ProcessConsole;
import org.eclipse.jface.action.Action;
import org.eclipse.ui.texteditor.IUpdate;
import org.springframework.ide.eclipse.boot.core.BootPropertyTester;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;

@SuppressWarnings("restriction")
public class RestartAction extends Action implements IUpdate {

	private static final String[] CLASSPATH_PREFIX = { "-classpath ", "-cp " };

	private ProcessConsole console;

	public RestartAction(ProcessConsole console) {
		super("Trigger Restart");
		setToolTipText("Trigger devtools-based Restart of Spring Boot Application");
		setImageDescriptor(RestartPluginImages
				.getImageDescriptor(RestartConstants.IMG_RESTART_ICON));
		this.console = console;
		update();
	}

	@Override
	@SuppressWarnings("deprecation")
	public void update() {
		boolean bootProject = false;
		boolean devtools = false;
		boolean running = !this.console.getProcess().isTerminated();

		try {
			IProcess process = this.console.getProcess();
			ILaunchConfiguration launchConfiguration = process.getLaunch().getLaunchConfiguration();
			if (launchConfiguration != null) {
				ILaunchConfigurationType type = launchConfiguration.getType();
				if (type != null) {
					ILaunchConfigurationDelegate delegate = type.getDelegate();

					bootProject = delegate instanceof BootLaunchConfigurationDelegate;

					IProject project = BootLaunchConfigurationDelegate.getProject(launchConfiguration);
					devtools = BootPropertyTester.hasDevtools(project);
				}
			}
		} catch (CoreException e) {
		}
		setEnabled(running & bootProject & devtools);
	}

	@Override
	public void run() {
		if (!this.console.getProcess().isTerminated()) {
			IProcess process = this.console.getProcess();
			String commandLine = process
					.getAttribute("org.eclipse.debug.core.ATTR_CMDLINE");
			String classPath = getClassPath(commandLine);
			File folder = getFolder(classPath);
			if (folder != null) {
				writeTriggerFile(new File(folder, ".reloadtrigger"));
				System.out.println(folder);
			}
		}
	}

	private String getClassPath(String commandLine) {
		for (String prefix : CLASSPATH_PREFIX) {
			int startIndex = commandLine.indexOf(prefix);
			if (startIndex != -1) {
				return commandLine.substring(startIndex + prefix.length()).trim();
			}
		}
		return null;
	}

	private File getFolder(String classPath) {
		if (classPath == null) {
			return null;
		}
		int index = classPath.indexOf(File.pathSeparator);
		String element = (index == -1 ? classPath : classPath.substring(0, index));
		if ("".equals(element)) {
			return null;
		}
		File file = new File(element);
		if (file.isDirectory() && file.exists()) {
			return file;
		}
		return (index == -1 ? null : getFolder(classPath.substring(index + 1)));
	}

	private void writeTriggerFile(File file) {
		try {
			OutputStream outputStream = new FileOutputStream(file);
			try {
				Date date = new Date();
				String content = date.toString() + " " + date.getTime();
				outputStream.write(content.getBytes());
			}
			finally {
				outputStream.close();
			}
		}
		catch (IOException ex) {
			throw new IllegalStateException("Unable to write trigger file", ex);
		}
	}

	public void dispose() {
		this.console = null;
	}

}
