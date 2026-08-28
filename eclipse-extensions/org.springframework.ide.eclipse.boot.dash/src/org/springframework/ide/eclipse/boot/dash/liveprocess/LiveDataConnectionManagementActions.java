/*******************************************************************************
 * Copyright (c) 2019, 2024 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.liveprocess;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.resource.ImageDescriptor;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel.ElementStateListener;
import org.springframework.ide.eclipse.boot.dash.model.RunState;
import org.springframework.ide.eclipse.boot.dash.views.AbstractBootDashElementsAction;
import org.springframework.ide.eclipse.boot.dash.views.AbstractBootDashElementsAction.Params;
import org.springframework.ide.eclipse.boot.dash.views.sections.DynamicSubMenuSupplier;
import org.springsource.ide.eclipse.commons.livexp.core.AbstractDisposable;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class LiveDataConnectionManagementActions extends AbstractDisposable implements DynamicSubMenuSupplier {

	private static final IAction DUMMY_ACTION = new Action("No matching processes") {
		{
			setEnabled(false);
		}
	};
	private final Params params;
	private LiveProcessCommandsExecutor liveProcessCmds;
	private final LiveExpression<Boolean> isEnabled;

	@Override
	public String getLabel() {
		return "Live Data Connections...";
	}

	@Override
	public ImageDescriptor getImageDescriptor() {
		return BootDashActivator.getImageDescriptor("icons/light-bulb.png");
	}

	@Override
	public ImageDescriptor getDisabledImageDescriptor() {
		return BootDashActivator.getImageDescriptor("icons/light-bulb-disabled.png");
	}

	@Override
	public boolean isVisible() {
		if (liveProcessCmds != null) {
			Set<BootDashElement> selection = params.getSelection().getValue();
			return selection.isEmpty() || selection.stream().anyMatch(x -> x instanceof LiveDataCapableElement);
		}
		return false;
	}

	public class ExecuteCommandAction extends AbstractBootDashElementsAction {
		private String projectName;
		private String label;
		private CommandInfo commandInfo;

		public ExecuteCommandAction(CommandInfo commandInfo) {
			super(params);
			this.commandInfo = commandInfo;
			this.projectName = (String) commandInfo.info.get("projectName");
			label = computeLabel(commandInfo);
			this.setText(label);
		}

		private String computeLabel(CommandInfo commandInfo) {
			switch (commandInfo.command) {
			case "sts/livedata/connect":
				return "Show Live Data from: %s".formatted(commandInfo.info.get("label"));
			case "sts/livedata/refresh":
				return "Refresh Live Data from: %s".formatted(commandInfo.info.get("label"));
			case "sts/livedata/disconnect":
				return "Hide Live Data from: %s".formatted(commandInfo.info.get("label"));
			default:
			}
			return (String) commandInfo.info.get("label");
		}

		public String getProjectName() {
			return projectName;
		}

		@Override
		public String toString() {
			return "ExecuteCommandAction [projectName=" + projectName +", label="+label+"]";
		}

		@Override
		public void updateVisibility() {
			this.setVisible(true);
		}

		@Override
		public void updateEnablement() {
			this.setEnabled(true);
		}

		@Override
		public void run() {
			try {
				liveProcessCmds.executeCommand(commandInfo).block(Duration.ofSeconds(2));
			} catch (Exception e) {
				Log.log(e);
			}
		}

		public String getProcessId() {
			return (String) commandInfo.info.get("processId");
		}
	}

	public LiveDataConnectionManagementActions(Params params) {
		this.params = params;
		this.liveProcessCmds = params.getLiveProcessCmds();
		ObservableSet<BootDashElement> selection = params.getSelection().getElements();
		this.isEnabled = addDisposableChild(new LiveExpression<>(false) {

			ElementStateListener elementStateListener = (BootDashElement e) -> {
				refresh();
			};

			{
				dependsOn(selection);
				params.getModel().addElementStateListener(elementStateListener);
			}

			@Override
			protected Boolean compute() {
				ImmutableSet<BootDashElement> els = selection.getValues();
				if (els.isEmpty()) {
					return true;
				} else {
					for (BootDashElement bde : els) {
						if (bde instanceof LiveDataCapableElement) {
							RunState s = bde.getRunState();
							if (s == RunState.RUNNING || s == RunState.DEBUGGING) {
								return true;
							}
						}
					}
					return false;
				}
			}
		});
	}

	@Override
	public List<IAction> getActions() {
		Set<BootDashElement> bdes = params.getSelection().getValue();
		Predicate<ExecuteCommandAction> filter;
		if (bdes.isEmpty()) {
			filter = x -> true;
		} else {
			filter = action -> {
				for (BootDashElement bde : bdes) {
					if (bde instanceof LiveDataCapableElement) {
						if (((LiveDataCapableElement)bde).matchesLiveProcessCommand(action)) {
							return true;
						}
					}
				}
				return false;
			};
		}
		try {
			return liveProcessCmds.listCommands().map(cmdInfo -> new ExecuteCommandAction(cmdInfo))
			.filter(filter)
			.cast(IAction.class)
			.collect(Collectors.toList())
			.map(actions -> {
				if (actions.isEmpty()) {
					return ImmutableList.of(DUMMY_ACTION);
				}
				return actions;
			})
			.block();
		} catch (Exception e) {
			Log.log(e);
		}
		return ImmutableList.of(DUMMY_ACTION);
	}

	@Override
	public LiveExpression<Boolean> isEnabled() {
		return isEnabled;
	}
}
