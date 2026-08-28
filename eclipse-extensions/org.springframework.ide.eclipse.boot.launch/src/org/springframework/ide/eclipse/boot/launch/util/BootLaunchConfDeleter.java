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
package org.springframework.ide.eclipse.boot.launch.util;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.devtools.BootDevtoolsClientLaunchConfigurationDelegate;
import org.springsource.ide.eclipse.commons.frameworks.core.ExceptionUtil;
import org.springsource.ide.eclipse.commons.frameworks.core.workspace.ProjectDeletionListenerManager;
import org.springsource.ide.eclipse.commons.frameworks.core.workspace.ProjectDeletionListenerManager.ProjectDeletionListener;

import org.springsource.ide.eclipse.commons.frameworks.core.util.JobUtil;

/**
 * When projects are deleted, also deletes corresponding local launch configs.
 *
 * @author Kris De Volder
 */
public class BootLaunchConfDeleter {

	private ILaunchManager launchMan;
	private ProjectDeletionListenerManager listenerManager;

	private static final ISchedulingRule RULE = JobUtil.lightRule("LaunchConfDeleteRule");

	private String[] TYPE_IDS = {
			BootLaunchConfigurationDelegate.TYPE_ID,
			BootDevtoolsClientLaunchConfigurationDelegate.TYPE_ID
	};

	public BootLaunchConfDeleter(IWorkspace workspace, ILaunchManager launchMan) {
		this.launchMan = launchMan;
		this.listenerManager = new ProjectDeletionListenerManager(workspace, new ProjectDeletionListener() {
			@Override
			public void projectWasDeleted(IProject project) {
				handleDelete(project);
			}
		});
	}

	private void handleDelete(IProject project) {
		final List<ILaunchConfiguration> confs = getDeletableLaunchConfigs(project);
		if (confs != null && !confs.isEmpty()) {
			Job deleteJob = new Job("Delete launch configs for "+project.getName()) {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						for (ILaunchConfiguration c : confs) {
							c.delete();
						}
						return Status.OK_STATUS;
					} catch (Exception e) {
						return ExceptionUtil.status(e);
					}
				}
			};
			//what rule should we use here? Something less aggressive??
			deleteJob.setRule(RULE); //In case many projects deleted in a burst... avoid concurrency issues.
			deleteJob.schedule();
		}
	}

	private List<ILaunchConfiguration> getDeletableLaunchConfigs(IProject project) {
		List<ILaunchConfiguration> configs = new ArrayList<>();
		for (String typeId : TYPE_IDS) {
			try {
				ILaunchConfigurationType type = launchMan.getLaunchConfigurationType(typeId);
				for (ILaunchConfiguration conf : launchMan.getLaunchConfigurations(type)) {
					if (
							project.equals(BootLaunchConfigurationDelegate.getProject(conf)) &&
							isDeletable(conf)
					) {
						configs.add(conf);
					}
				}
			} catch (Exception e) {
				BootActivator.log(e);
			}
		}
		return configs;
	}

	private boolean isDeletable(ILaunchConfiguration conf) {
		return conf.exists() && conf.isLocal();
	}

	public void dispose() {
		listenerManager.dispose();
	}
}
