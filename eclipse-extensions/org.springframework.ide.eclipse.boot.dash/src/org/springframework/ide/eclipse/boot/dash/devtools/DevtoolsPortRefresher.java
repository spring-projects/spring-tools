/*******************************************************************************
 * Copyright (c) 2013 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.devtools;

import org.eclipse.core.resources.IProject;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.IStreamListener;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.IStreamMonitor;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.core.BootPropertyTester;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootProjectDashElementFactory;
import org.springframework.ide.eclipse.boot.dash.model.BootProjectDashElement;
import org.springframework.ide.eclipse.boot.dash.model.LocalBootDashModel;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.util.ProcessTracker;
import org.springframework.ide.eclipse.boot.util.ProcessTracker.ProcessListener;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;

/**
 * Responsible for 'poking' local boot dash elements when they need to refresh port info
 * after boot devtools does an in-place restart of an app.
 * <p>
 * As devtools autonously refreshes an app without any involvement of Eclipse... the only way we
 * can see that something happened to the process is by watching the process's output.
 * <p>
 * A DevtoolsPortRefresher uses a 'ProcessTracker' to attach an output monitor to any local process
 * that is being started and corresponds to a local boot dash element with devtools enabled.
 *
 * @author Kris De Volder
 */
public class DevtoolsPortRefresher implements Disposable, ProcessListener {

	private ProcessTracker processTracker;
	private BootProjectDashElementFactory elementFactory;

	public DevtoolsPortRefresher(LocalBootDashModel localBootDashModel, BootProjectDashElementFactory elementFactory) {
		processTracker = new ProcessTracker(this);
		this.elementFactory = elementFactory;
	}

	@Override
	public void dispose() {
		processTracker.dispose();
	}

	@Override
	public void debugTargetCreated(ProcessTracker tracker, IDebugTarget target) {
		IProcess process = target.getProcess();
		if (process != null) { // may be null. E.g. for CF debug targets there's no local process attached to debug target.
			processCreated(tracker, process);
		}
	}

	@Override
	public void processCreated(ProcessTracker tracker, IProcess process) {
		final BootProjectDashElement element = getElementFor(process);
		if (element != null) {
			process.getStreamsProxy().getOutputStreamMonitor().addListener(new IStreamListener() {
				public void streamAppended(String text, IStreamMonitor monitor) {
					if (text.contains("started on port")) {
						element.refreshLivePorts();
					}
				}
			});
		}
	}

	/**
	 * Gets the element this process is related to, if the element is 'interesting'.
	 * @return The element or null (if there's no corresponding element or its not 'interesting')
	 */
	private BootProjectDashElement getElementFor(IProcess process) {
		ILaunch launch = process.getLaunch();
		try {
			if (launch != null) {
				ILaunchConfiguration conf = launch.getLaunchConfiguration();
				if (conf != null && conf.getType().getIdentifier().equals(BootLaunchConfigurationDelegate.TYPE_ID)) {
					IProject p = BootLaunchConfigurationDelegate.getProject(conf);
					if (p != null && BootPropertyTester.hasDevtools(p)) {
						BootDashElement e = elementFactory.createOrGet(p);
						if (BootPropertyTester.hasDevtools(p)) {
							if (e instanceof BootProjectDashElement) { // this test should always succeed but check it anyway
								return (BootProjectDashElement) e;
							}
						}
					}
				}
			}
		} catch (Exception e) {
			BootActivator.log(e);
		}
		return null;
	}

	@Override
	public void debugTargetTerminated(ProcessTracker tracker, IDebugTarget target) {
		//don't care
	}

	@Override
	public void processTerminated(ProcessTracker tracker, IProcess process) {
		//don't care
	}



}
