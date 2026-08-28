/*******************************************************************************
 * Copyright (c) 2012, 2015 Pivotal Software, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 *  Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 ***********************************************************************************/
package org.springframework.ide.eclipse.boot.launch.console;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.internal.ui.views.console.ProcessConsole;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.SubContributionManager;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsolePageParticipant;
import org.eclipse.ui.part.IPageBookViewPage;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.devtools.BootDevtoolsClientLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.util.BootLaunchUtils;
import org.springframework.ide.eclipse.boot.ui.BootUIImages;
import org.springframework.ide.eclipse.boot.util.ProcessListenerAdapter;
import org.springframework.ide.eclipse.boot.util.ProcessTracker;
import org.springsource.ide.eclipse.commons.frameworks.core.ExceptionUtil;

/**
 * @author Kris De Volder
 */
@SuppressWarnings("restriction")
public class BootConsolePageParticipant implements IConsolePageParticipant {

	private class TerminateProcessAction extends Action {
		@Override
		public void run() {
			try {
				final IProcess process = console.getProcess();
				if (process != null && process.canTerminate()) {
					Job job = new Job("Terminate process") {
						protected IStatus run(IProgressMonitor monitor) {
							try {
								BootLaunchUtils.terminate(process.getLaunch());
								return Status.OK_STATUS;
							} catch (Exception e) {
								return ExceptionUtil.status(e);
							}
						}

					};
					job.schedule();
				}
			} catch (Exception e) {
				BootActivator.log(e);
			}
		}
	}

	private ProcessConsole console;
	private ProcessTracker processTracker;
	private TerminateProcessAction terminateAction;

	public void activated() {
		// ignore
	}

	public void deactivated() {
		// ignore
	}

	public void dispose() {
		if (processTracker != null) {
			processTracker.dispose();
			processTracker = null;
		}
	}

	public Object getAdapter(@SuppressWarnings("rawtypes") Class adapter) {
		return null;
	}

	public void init(IPageBookViewPage page, IConsole console) {
		this.console = (ProcessConsole)console;

		//TODO: This code works assuming that our IConsolePageParticipant is called after the
		//  ProcessConsolePageParticipant (which creates the action we are replacing
		//When testing this that was always the case... but it may not be guaranteed.

		if (isDevtoolsClient(this.console) || isBootApp(this.console)) {
			terminateAction = new TerminateProcessAction();
			try {
				terminateAction.setImageDescriptor(BootUIImages.descriptor("icons/stop.png"));
				terminateAction.setDisabledImageDescriptor(BootUIImages.descriptor("icons/stop_disabled.png"));
			} catch (Exception e) {
				BootActivator.log(e);
			}
			IToolBarManager toolbar = page.getSite().getActionBars().getToolBarManager();
			IContributionItem replace = findReplacementItem(toolbar);
			if (replace != null) {
		 		toolbar.appendToGroup(IConsoleConstants.LAUNCH_GROUP, terminateAction);
		 		toolbar.remove(replace);
			}
			boolean enabled = getConsoleProcess().canTerminate();
			terminateAction.setEnabled(enabled);
			if (enabled) {
				this.processTracker = new ProcessTracker(new ProcessListenerAdapter() {
					@Override
					public void processTerminated(ProcessTracker tracker, IProcess terminated) {
						if (getConsoleProcess().equals(terminated)) {
							terminateAction.setEnabled(false);
							//after process is terminated... it can't come back to life so... can stop listening now.
							tracker.dispose();
						}
					}
				});
			}
		}
	}

	private boolean isDevtoolsClient(ProcessConsole console) {
		return isLaunchType(console, BootDevtoolsClientLaunchConfigurationDelegate.TYPE_ID);
	}

	private IProcess getConsoleProcess() {
		if (console != null) {
			return console.getProcess();
		}
		return null;
	}

	private boolean isBootApp(ProcessConsole console) {
		return isLaunchType(console, BootLaunchConfigurationDelegate.TYPE_ID);
	}

	private IContributionItem findReplacementItem(IToolBarManager toolbar) {
		SubContributionManager contributions = (SubContributionManager) toolbar;
		for (IContributionItem item : contributions.getItems()) {
			if (item instanceof ActionContributionItem) {
				ActionContributionItem actionItem = (ActionContributionItem) item;
				IAction replaceAction = actionItem.getAction();
				if (replaceAction.getClass().getName().equals("org.eclipse.debug.internal.ui.views.console.ConsoleTerminateAction")) {
					return item;
				}
			}
		}
		return null;
	}

	private static boolean isLaunchType(ProcessConsole console, String typeId) {
		return isLaunchType(console.getProcess(), typeId);
	}

	private static boolean isLaunchType(IProcess process, String launchTypeId) {
		try {
			if (process != null) {
				ILaunch launch = process.getLaunch();
				if (launch != null) {
					ILaunchConfiguration conf = launch.getLaunchConfiguration();
					if (conf != null) {
						return launchTypeId.equals(conf.getType().getIdentifier());
					}
				}
			}
		} catch (Exception e) {
			BootActivator.log(e);
		}
		return false;
	}


}

