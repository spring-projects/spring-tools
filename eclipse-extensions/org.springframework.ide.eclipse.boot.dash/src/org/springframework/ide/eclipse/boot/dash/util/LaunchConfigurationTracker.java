/*******************************************************************************
 * Copyright (c) 2012 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.util;

import static org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate.isHiddenFromBootDash;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationListener;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.dash.model.BootProjectDashElement;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springsource.ide.eclipse.commons.livexp.core.AsyncLiveExpression.AsyncMode;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSetVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;

/**
 * This class is responsible of maintaining a map of {@link ILaunchConfiguration}
 * that represent the children of {@link BootProjectDashElement}s.
 *
 * @author Kris De Volder
 */
public class LaunchConfigurationTracker implements Disposable {

	private final AtomicBoolean initialized = new AtomicBoolean(false);
	private final ILaunchManager launchManager;
	private final ILaunchConfigurationType launchType;
	private final Map<IProject, LiveSetVariable<ILaunchConfiguration>> configs = new HashMap<>();
	private ILaunchConfigurationListener launchConfListener;
	private Job refreshJob;

	public LaunchConfigurationTracker(String launchTypeId, ILaunchManager launchManager) {
		this.launchManager = launchManager;
		this.launchType = launchManager.getLaunchConfigurationType(launchTypeId);
	}

	public LaunchConfigurationTracker(String typeId) {
		this(typeId, DebugPlugin.getDefault().getLaunchManager());
	}

	private void init() {
		if (initialized.compareAndSet(false, true)) {
			launchManager.addLaunchConfigurationListener(launchConfListener = new ILaunchConfigurationListener() {
				@Override
				public void launchConfigurationRemoved(ILaunchConfiguration configuration) {
					//Careful, do not call 'refreshIfNeeded' here. It is impossible to determine
					// the type of a deleted config (eclipse will throw an exeption if you try)
					//So we have to call refresh directly here.
					refresh();
				}

				@Override
				public void launchConfigurationChanged(ILaunchConfiguration configuration) {
					refreshIfNeeded(configuration);
				}

				@Override
				public void launchConfigurationAdded(ILaunchConfiguration configuration) {
					refreshIfNeeded(configuration);
				}

				private void refreshIfNeeded(ILaunchConfiguration configuration) {
					try {
						if (configuration != null && launchType.equals(configuration.getType())) {
							refresh();
						}
					} catch (CoreException e) {
						BootActivator.log(e);
					}
				}
			});
			refresh();
		}
	}

	private void refresh() {
		refreshJob().schedule();
	}

	private synchronized Job refreshJob() {
		if (refreshJob == null) {
			refreshJob = new Job("Refresh Launch Conf Boot Dash Elements") {
				protected IStatus run(IProgressMonitor arg0) {
					Map<IProject, Set<ILaunchConfiguration>> newSets = new HashMap<>();
					synchronized (LaunchConfigurationTracker.this) {
						for (IProject oldProject : configs.keySet()) {
							//enure there's at least an empty set for any relevant project
							//in the newSets map:
							getSet(newSets, oldProject);
						}
					}
					for (ILaunchConfiguration conf : getRelevantConfs()) {
						IProject project = BootLaunchConfigurationDelegate.getProject(conf);
						if (project != null) {
							add(newSets, project, conf);
						}
					}
					for (Entry<IProject, Set<ILaunchConfiguration>> newEntry : newSets.entrySet()) {
						IProject newProject = newEntry.getKey();
						LiveSetVariable<ILaunchConfiguration> liveset = getVar(newProject);
						liveset.replaceAll(newEntry.getValue());
					}
					return Status.OK_STATUS;
				}
			};
			refreshJob.setSystem(true);
		}
		return refreshJob;
	}

	private void add(Map<IProject, Set<ILaunchConfiguration>> index, IProject project, ILaunchConfiguration conf) {
		getSet(index, project).add(conf);
	}

	private Set<ILaunchConfiguration> getSet(Map<IProject, Set<ILaunchConfiguration>> index,
			IProject project) {
		Set<ILaunchConfiguration> elements = index.get(project);
		if (elements == null) {
			index.put(project, elements = new HashSet<>());
		}
		return elements;
	}

	public ObservableSet<ILaunchConfiguration> getConfigs(IProject project) {
		init();
		return getVar(project);
	}

	private synchronized LiveSetVariable<ILaunchConfiguration> getVar(IProject project) {
		LiveSetVariable<ILaunchConfiguration> existing = configs.get(project);
		if (existing == null) {
			configs.put(project, existing = new LiveSetVariable<>(AsyncMode.SYNC));
		}
		return existing;
	}

	private ImmutableSet<ILaunchConfiguration> getRelevantConfs() {
		try {
			ILaunchConfiguration[] allConfigs = launchManager.getLaunchConfigurations(launchType);
			Builder<ILaunchConfiguration> builder = ImmutableSet.builder();
			for (ILaunchConfiguration c : allConfigs) {
				if (isRelevant(c)) {
					builder.add(c);
				}
			}
			return builder.build();
		} catch (Exception e) {
			BootActivator.log(e);
			return ImmutableSet.of();
		}
	}

	private boolean isRelevant(ILaunchConfiguration c) {
		//Note: no need to check the launch conf type as only configs of the right type are passed in here.
		return !isHiddenFromBootDash(c);
	}

	@Override
	public synchronized void dispose() {
		if (launchConfListener != null) {
			launchManager.removeLaunchConfigurationListener(launchConfListener);
			launchConfListener = null;
		}
	}

}
