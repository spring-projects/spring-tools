/*******************************************************************************
 * Copyright (c) 2000, 2018, 2019, 2022 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sascha Radike - bug 56642
 *     Martin Oberhuber (Wind River) - [327446] Avoid unnecessary wait-for-build dialog.
 *     Mohamed Hussein - bug 381175
 *     Pivotal Inc - allow synchronizing with backgroundLaunch
 *     VMware - Prompt to save editors
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch.util;

import java.text.MessageFormat;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.IJobChangeListener;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.IStatusHandler;
import org.eclipse.debug.internal.ui.DebugUIMessages;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants;
import org.eclipse.debug.internal.ui.DebugUIPlugin.PendingLaunch;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.ILaunchGroup;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.progress.IProgressConstants2;
import org.eclipse.ui.progress.IProgressService;

/**
 * Bits and pieces copied from {@link DebugUITools} and {@link DebugUIPlugin} with some
 * minor modifications.
 */
@SuppressWarnings("restriction")
public class BootDebugUITools {

	/**
	 * Copied from DebugUIPlugin and modified to add a CompletableFuture return value,
	 * to allow for proper synchronization with the background launch operation.
	 *
	 * Saves and builds the workspace according to current preference settings and
	 * launches the given launch configuration in the specified mode in a background
	 * Job with progress reported via the Job. Exceptions are reported in the Progress
	 * view.
	 *
	 * @param configuration the configuration to launch
	 * @param mode launch mode
	 * @param done
	 * @return CompletableFuture that is completed when the background job finishes.
	 * @since 3.0
	 */
	public static void launchInBackground(final ILaunchConfiguration configuration, final String mode, CompletableFuture<Void> done) {
		final IJobManager jobManager = Job.getJobManager();
		IPreferenceStore store = DebugUIPlugin.getDefault().getPreferenceStore();
		boolean wait = (jobManager.find(ResourcesPlugin.FAMILY_AUTO_BUILD).length > 0 && ResourcesPlugin.getWorkspace().isAutoBuilding())
				|| (jobManager.find(ResourcesPlugin.FAMILY_MANUAL_BUILD).length > 0);
		String waitPref = store.getString(IInternalDebugUIConstants.PREF_WAIT_FOR_BUILD);
		if (wait) { // if there are build jobs running, do we wait or not??
			if (waitPref.equals(MessageDialogWithToggle.PROMPT)) {
				MessageDialogWithToggle dialog = MessageDialogWithToggle.openYesNoCancelQuestion(DebugUIPlugin.getShell(), DebugUIMessages.DebugUIPlugin_23, DebugUIMessages.DebugUIPlugin_24, null, false, store, IInternalDebugUIConstants.PREF_WAIT_FOR_BUILD); //
				switch (dialog.getReturnCode()) {
					case IDialogConstants.CANCEL_ID:
						done.complete(null);
						return;
					case IDialogConstants.YES_ID:
						wait = true;
						break;
					case IDialogConstants.NO_ID:
						wait = false;
						break;
					default:
						break;
				}
			}
			else {
				wait = waitPref.equals(MessageDialogWithToggle.ALWAYS);
			}
		}
		final boolean waitInJob = wait;
		Job job = new Job(MessageFormat.format(DebugUIMessages.DebugUIPlugin_25, new Object[] {configuration.getName()})) {
			@Override
			public IStatus run(final IProgressMonitor monitor) {
				/* Setup progress monitor
				 * - Waiting for jobs to finish (2)
				 * - Build & launch (98) */
				monitor.beginTask(DebugUIMessages.DebugUITools_3, 100);
				try {
					if (waitInJob) {
						StringBuffer buffer = new StringBuffer(configuration.getName());
						buffer.append(DebugUIMessages.DebugUIPlugin_0);
						ILaunchConfigurationWorkingCopy workingCopy = configuration.copy(buffer.toString());
						workingCopy.setAttribute(DebugUIPlugin.ATTR_LAUNCHING_CONFIG_HANDLE, configuration.getMemento());
						final ILaunch pendingLaunch = new PendingLaunch(workingCopy, mode, this);
						DebugPlugin.getDefault().getLaunchManager().addLaunch(pendingLaunch);
                        IJobChangeListener listener= new IJobChangeListener() {
                            @Override
							public void sleeping(IJobChangeEvent event) {}
                            @Override
							public void scheduled(IJobChangeEvent event) {}
                            @Override
							public void running(IJobChangeEvent event) {}
                            @Override
							public void awake(IJobChangeEvent event) {}
                            @Override
							public void aboutToRun(IJobChangeEvent event) {}
                            @Override
							public void done(IJobChangeEvent event) {
                                DebugPlugin dp = DebugPlugin.getDefault();
                                if (dp != null) {
                                	dp.getLaunchManager().removeLaunch(pendingLaunch);
                                }
                                removeJobChangeListener(this);
                            }
                        };
                        addJobChangeListener(listener);
						try {
							jobManager.join(ResourcesPlugin.FAMILY_MANUAL_BUILD, new SubProgressMonitor(monitor, 1));
							jobManager.join(ResourcesPlugin.FAMILY_AUTO_BUILD, new SubProgressMonitor(monitor, 1));
						}
						catch (InterruptedException e) {/*just continue.*/}
                        DebugPlugin.getDefault().getLaunchManager().removeLaunch(pendingLaunch);
					}
					else {
						monitor.worked(2); /* don't wait for jobs to finish */
					}
					if (!monitor.isCanceled()) {
						DebugUIPlugin.buildAndLaunch(configuration, mode, new SubProgressMonitor(monitor, 98));
					}
				} catch (CoreException e) {
					final IStatus status = e.getStatus();
					IStatusHandler handler = DebugPlugin.getDefault().getStatusHandler(status);
					if (handler == null) {
						return status;
					}
					final ILaunchGroup group = DebugUITools.getLaunchGroup(configuration, mode);
					if (group == null) {
						return status;
					}
					Runnable r = () -> DebugUITools.openLaunchConfigurationDialogOnGroup(DebugUIPlugin.getShell(), new StructuredSelection(configuration), group.getIdentifier(), status);
					DebugUIPlugin.getStandardDisplay().asyncExec(r);
				}
				finally	{
					monitor.done();
					done.complete(null);
				}
				return Status.OK_STATUS;
			}
		};

		IWorkbench workbench = DebugUIPlugin.getDefault().getWorkbench();
		IProgressService progressService = workbench.getProgressService();

		job.setPriority(Job.INTERACTIVE);
		job.setProperty(IProgressConstants2.SHOW_IN_TASKBAR_ICON_PROPERTY, Boolean.TRUE);
		job.setName(MessageFormat.format(DebugUIMessages.DebugUIPlugin_25, new Object[] {configuration.getName()}));

		if (wait) {
			progressService.showInDialog(workbench.getActiveWorkbenchWindow().getShell(), job);
		}
		job.schedule();
	}

}
