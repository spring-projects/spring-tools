/*******************************************************************************
 * Copyright (c) 2015, 2023 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.views;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IAction;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.api.RunTargetType;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.deployment.DeployToRemoteTargetAction;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.liveprocess.LiveDataConnectionManagementActions;
import org.springframework.ide.eclipse.boot.dash.liveprocess.LiveProcessCommandsExecutor;
import org.springframework.ide.eclipse.boot.dash.livexp.DisposingFactory;
import org.springframework.ide.eclipse.boot.dash.livexp.MultiSelection;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel;
import org.springframework.ide.eclipse.boot.dash.model.BootDashViewModel;
import org.springframework.ide.eclipse.boot.dash.model.RunState;
import org.springframework.ide.eclipse.boot.dash.model.RunTarget;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.dash.model.remote.GenericRemoteAppElement;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.LocalRunTargetType;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RemoteRunTarget;
import org.springframework.ide.eclipse.boot.dash.model.runtargettypes.RemoteRunTargetType;
import org.springframework.ide.eclipse.boot.dash.prefs.BootDashPrefsPage;
import org.springframework.ide.eclipse.boot.dash.views.AbstractBootDashAction.Location;
import org.springframework.ide.eclipse.boot.dash.views.AbstractBootDashElementsAction.Params;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;

public class BootDashActions {

	private final static String PROPERTIES_VIEW_ID = "org.eclipse.ui.views.PropertySheet"; //$NON-NLS-1$

	///// context info //////////////
	private BootDashViewModel model;
	MultiSelection<BootDashElement> elementsSelection;
	private LiveExpression<BootDashModel> sectionSelection;
	private LiveProcessCommandsExecutor liveProcessCmds;

	///// actions ///////////////////
	private RunStateAction[] runStateActions;
	private AbstractBootDashElementsAction openConsoleAction;
	private LinkWithConsoleAction linkWithConsoleAction;
	private OpenLaunchConfigAction openConfigAction;
	private OpenInBrowserAction openBrowserAction;
	private OpenInPackageExplorer openInPackageExplorerAction;
	private AddRunTargetAction[] addTargetActions;
	private RefreshRunTargetAction refreshAction;
	private RemoveRunTargetAction removeTargetAction;
	private ShowViewAction showPropertiesViewAction;

	private OpenPreferencesAction openBootDashPreferencesAction;

	private DuplicateConfigAction duplicateConfigAction;

	private DeleteElementsAction<RemoteRunTargetType> deleteAppsAction;
	private DeleteElementsAction<LocalRunTargetType> deleteConfigsAction;

	private OpenToggleFiltersDialogAction toggleFiltersDialogAction;
	private CustmomizeTargetLabelAction customizeTargetLabelAction;

	private DisposingFactory<RunTarget, AbstractBootDashAction> debugOnTargetActions;
	private DisposingFactory<RunTarget, AbstractBootDashAction> runOnTargetActions;

	private Map<String, IAction> defIdToActions = new HashMap<>();

	private LiveDataConnectionManagementActions liveDataConnectionManagement;
	private final SimpleDIContext context;

	private List<AbstractBootDashAction> injectedActions;

	private ToggleBootDashModelConnection connectAction;

	private EnableRemoteDevtoolsAction enableRemoteDevtoolsAction;

	private RestartDevtoolsClientAction restartDevtoolsClientAction;


	public interface Factory {
		Collection<AbstractBootDashAction> create(BootDashActions actions, BootDashViewModel model, MultiSelection<BootDashElement> selection, LiveExpression<BootDashModel> section, SimpleDIContext context, LiveProcessCommandsExecutor liveProcessCmds);
	}

	public BootDashActions(BootDashViewModel model, MultiSelection<BootDashElement> selection, SimpleDIContext context,  LiveProcessCommandsExecutor liveProcessCmds) {
		this(
				model,
				selection,
				null,
				context,
				liveProcessCmds
		);
	}

	public BootDashActions(BootDashViewModel model, MultiSelection<BootDashElement> selection, LiveExpression<BootDashModel> section, SimpleDIContext context, LiveProcessCommandsExecutor liveProcessCmds) {
		this.liveProcessCmds = liveProcessCmds;
		Assert.isNotNull(context);
		context.assertDefinitionFor(UserInteractions.class);
		this.model = model;
		this.elementsSelection = selection;
		this.sectionSelection = section;
		this.context = context;

		makeActions();
	}

	private Params defaultActionParams() {
		return new Params(this)
				.setModel(model)
				.setSelection(elementsSelection)
				.setContext(context)
				.setLiveProcessCmds(liveProcessCmds);
	}

	protected void makeActions() {
		RunStateAction restartAction = new RestartAction(defaultActionParams().setDefinitionId("org.springframework.ide.eclipse.boot.dash.boot.dash.RestartAction"), RunState.RUNNING);

		RunStateAction rebugAction = new RedebugAction(defaultActionParams().setDefinitionId("org.springframework.ide.eclipse.boot.dash.boot.dash.RedebugAction"), RunState.DEBUGGING);

		RunStateAction stopAction = new RunStateAction(defaultActionParams().setDefinitionId("org.springframework.ide.eclipse.boot.dash.boot.dash.StopAction"), RunState.INACTIVE) {
			@Override
			protected boolean currentStateAcceptable(RunState s) {
				// Enable stop button so CF apps can be stopped when "STARTING"
				return s != RunState.INACTIVE;
			}

			@Override
			protected Job createJob() {
				final Collection<BootDashElement> selecteds = elementsSelection.getValue();
				if (!selecteds.isEmpty()) {
					return new Job("Stopping " + selecteds.size() + " Boot Dash Elements") {
						protected IStatus run(IProgressMonitor monitor) {
							monitor.beginTask("Stopping " + selecteds.size() + " Elements", selecteds.size());
							try {

								List<CompletableFuture<Void>> futures = new ArrayList<>(selecteds.size());
								for (BootDashElement el : selecteds) {
									futures.add(CompletableFuture.runAsync(() -> {
										try {
											el.stop();
											monitor.worked(1);
										} catch (Exception e) {
											monitor.worked(1);
											throw new CompletionException(e);
										}
									}));
								}

								try {
									CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).get(60, TimeUnit.SECONDS);
								} catch (InterruptedException e) {
									BootActivator.createErrorStatus(e);
								} catch (ExecutionException e) {
									BootActivator.createErrorStatus(e);
								} catch (TimeoutException e) {
									BootActivator.createErrorStatus(e);
								}


								return Status.OK_STATUS;
							} finally {
								monitor.done();
							}
						}
					};
				}
				return null;
			}
		};
		stopAction.setText("Stop");
		stopAction.setToolTipText("Stop the process(es) associated with the selected elements");
		stopAction.setImageDescriptor(BootDashActivator.getImageDescriptor("icons/stop.png"));
		stopAction.setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/stop_disabled.png"));

		RunStateAction pauseAction = new RunStateAction(defaultActionParams(), RunState.PAUSED) {
			@Override
			protected boolean isVisibleForElement(BootDashElement e) {
				return RunState.PAUSED != e.getRunState() && super.isVisibleForElement(e);
			}

			@Override
			protected boolean currentStateAcceptable(RunState s) {
				return s.isActive();
			}

			@Override
			public boolean showInToolbar() {
				return false;
			}
		};
		pauseAction.setText("Pause");
		pauseAction.setToolTipText("Suspend the process(es) associated with the selected elements.");
		pauseAction.setImageDescriptor(BootDashActivator.getImageDescriptor("icons/suspend.gif"));
		pauseAction.setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/suspend_disabled.gif"));

		RunStateAction resumeRunAction = new RunStateAction(defaultActionParams(), RunState.RUNNING) {
			@Override
			protected boolean currentStateAcceptable(RunState s) {
				return s == RunState.PAUSED;
			}

			@Override
			protected boolean isVisibleForElement(BootDashElement e) {
				// Only show Resume action for elements that can be paused explicitly
				return e.supportedGoalStates().contains(RunState.PAUSED) && super.isVisibleForElement(e);
			}

			@Override
			public boolean showInToolbar() {
				return false;
			}
		};
		resumeRunAction.setText("Resume (Running)");
		resumeRunAction.setToolTipText("Resume previously suspended process(es) associated with the selected elements.");
		resumeRunAction.setImageDescriptor(BootDashActivator.getImageDescriptor("icons/resume.gif"));
		resumeRunAction.setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/resumed.gif"));


		RunStateAction resumeDebugAction = new RunStateAction(defaultActionParams(), RunState.DEBUGGING) {
			@Override
			protected boolean currentStateAcceptable(RunState s) {
				return s == RunState.PAUSED;
			}

			@Override
			protected boolean isVisibleForElement(BootDashElement e) {
				// Only show Resume action for elements that can be paused explicitly
				return e.supportedGoalStates().contains(RunState.PAUSED) && super.isVisibleForElement(e);
			}

			@Override
			public boolean showInToolbar() {
				return false;
			}
		};
		resumeDebugAction.setText("Resume (Debugging)");
		resumeDebugAction.setToolTipText("Resume previously suspended process(es) associated with the selected elements.");
		resumeDebugAction.setImageDescriptor(BootDashActivator.getImageDescriptor("icons/resume.gif"));
		resumeDebugAction.setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/resumed.gif"));

		runStateActions = new RunStateAction[] { restartAction, rebugAction, stopAction, pauseAction, resumeRunAction, resumeDebugAction };

		openConfigAction = new OpenLaunchConfigAction(defaultActionParams().setDefinitionId("org.springframework.ide.eclipse.boot.dash.boot.dash.OpenLaunchConfigAction"));
		openConsoleAction = new OpenConsoleAction(defaultActionParams());
		linkWithConsoleAction = new LinkWithConsoleAction(defaultActionParams().setStyle(IAction.AS_CHECK_BOX));
		openBrowserAction = new OpenInBrowserAction(defaultActionParams());
		openInPackageExplorerAction = new OpenInPackageExplorer(defaultActionParams());
		addTargetActions = createAddTargetActions();

		deleteAppsAction = new DeleteElementsAction<>(this, RemoteRunTargetType.class, elementsSelection, context);
		deleteAppsAction.setText("Delete");
		deleteAppsAction.setToolTipText("Permantently removes selected artifact(s) from Remote Target");
		deleteConfigsAction = new DeleteElementsAction<>(this, LocalRunTargetType.class, elementsSelection, context);
		deleteConfigsAction.setText("Delete Config");
		deleteConfigsAction.setToolTipText("Permantently deletes Launch Configgurations from the workspace");

		if (sectionSelection != null) {
			connectAction = new ToggleBootDashModelConnection(sectionSelection, context);
			refreshAction = new RefreshRunTargetAction(sectionSelection, context);
			removeTargetAction = new RemoveRunTargetAction(sectionSelection, model, context);
			customizeTargetLabelAction = new CustmomizeTargetLabelAction(sectionSelection, context);
		}

		showPropertiesViewAction = new ShowViewAction(PROPERTIES_VIEW_ID);

		toggleFiltersDialogAction = new OpenToggleFiltersDialogAction(model.getToggleFilters(), elementsSelection, context);

		duplicateConfigAction = new DuplicateConfigAction(defaultActionParams());

		debugOnTargetActions = createDeployOnTargetActions(RunState.DEBUGGING);
		runOnTargetActions = createDeployOnTargetActions(RunState.RUNNING);

		openBootDashPreferencesAction = new OpenPreferencesAction(context, BootDashPrefsPage.class.getName(),
				"Boot Dash UI Preferences...",
				"Open Preferences for Boot Dash"
		);
		liveDataConnectionManagement = new LiveDataConnectionManagementActions(defaultActionParams());

		enableRemoteDevtoolsAction = new EnableRemoteDevtoolsAction(defaultActionParams());
		restartDevtoolsClientAction = new RestartDevtoolsClientAction(defaultActionParams());

		ImmutableList.Builder<AbstractBootDashAction> injectedActions = ImmutableList.builder();
		for (Factory f : context.getBeans(Factory.class)) {
			injectedActions.addAll(f.create(this, model, elementsSelection, sectionSelection, context, liveProcessCmds));
		};
		this.injectedActions = injectedActions.build();
	}

	private AddRunTargetAction[] createAddTargetActions() {
		Set<RunTargetType> targetTypes = model.getRunTargetTypes();
		ArrayList<AddRunTargetAction> actions = new ArrayList<>();
		for (RunTargetType tt : targetTypes) {
			if (tt.canInstantiate()) {
				actions.add(new AddRunTargetAction(tt, model.getRunTargets(), context));
			}
		}
		return actions.toArray(new AddRunTargetAction[actions.size()]);
	}

	private static final class RestartAction extends RunOrDebugStateAction {
		private RestartAction(Params params, RunState goalState) {
			super(params, goalState);
			setText("(Re)start");
			setToolTipText("Start or restart the process associated with the selected elements");
			setImageDescriptor(BootDashActivator.getImageDescriptor("icons/restart.png"));
			setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/restart_disabled.png"));
		}
	}

	private static final class RedebugAction extends RunOrDebugStateAction {
		private RedebugAction(Params params, RunState goalState) {
			super(params, goalState);
			setText("(Re)debug");
			setToolTipText("Start or restart the process associated with the selected elements in debug mode");
			setImageDescriptor(BootDashActivator.getImageDescriptor("icons/rebug.png"));
			setDisabledImageDescriptor(BootDashActivator.getImageDescriptor("icons/rebug_disabled.png"));
		}
	}

	public static class RunOrDebugStateAction extends RunStateAction {

		public RunOrDebugStateAction(Params params, RunState goalState) {
			super(params, goalState);
			Assert.isLegal(goalState == RunState.RUNNING || goalState == RunState.DEBUGGING);
		}

		@Override
		protected Job createJob() {
			final Collection<BootDashElement> selecteds = getTargetElements();
			if (!selecteds.isEmpty()) {
				return new Job("Restarting " + selecteds.size() + " Dash Elements") {
					@Override
					public IStatus run(IProgressMonitor monitor) {
						monitor.beginTask("Restart Boot Dash Elements", selecteds.size());
						try {

							List<CompletableFuture<Void>> futures = new ArrayList<>(selecteds.size());
							for (BootDashElement el : selecteds) {
								futures.add(CompletableFuture.runAsync(() -> {
									monitor.subTask("Restarting: " + el.getName());
									try {
										el.restart(goalState, ui());
										monitor.worked(1);
									} catch (Exception e) {
										monitor.worked(1);
										throw new CompletionException(e);
									}
								}));
							}

							try {
								CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).get(60, TimeUnit.SECONDS);
							} catch (InterruptedException e) {
								return BootActivator.createErrorStatus(e);
							} catch (ExecutionException e) {
								return BootActivator.createErrorStatus(e);
							} catch (TimeoutException e) {
								return BootActivator.createErrorStatus(e);
							}

							return Status.OK_STATUS;
						} finally {
							monitor.done();
						}
					}

				};
			}
			return null;
		}

		/**
		 * Automatically retarget this action to apply to all the children of an element
		 * (if it has children). This way the action behaves logically if both a parent and some children
		 * are selected (i.e. we don't want to execute the action twice on the explicitly selected children!)
		 */
		public Collection<BootDashElement> getTargetElements() {
			Builder<BootDashElement> builder = ImmutableSet.builder();
			addTargetsFor(builder, getSelectedElements());
			return builder.build();
		}

		private void addTargetsFor(Builder<BootDashElement> builder, Collection<BootDashElement> selecteds) {
			for (BootDashElement s : selecteds) {
				addTargetsFor(builder, s);
			}
		}

		private void addTargetsFor(Builder<BootDashElement> builder, BootDashElement s) {
			if (s instanceof GenericRemoteAppElement) { //TODO: yuck!!! smelly.
														// should refactor so that the special logci below which is
														// applicale only to local elelements is somehow moved into that instead.
				builder.add(s);
			} else {
				ImmutableSet<BootDashElement> children = s.getChildren().getValues();
				if (children.isEmpty()) {
					//No children, add s itself
					builder.add(s);
				} else {
					addTargetsFor(builder, children);
				}
			}
		}
	}

	public RunStateAction[] getRunStateActions() {
		return runStateActions;
	}

	public AbstractBootDashElementsAction getOpenBrowserAction() {
		return openBrowserAction;
	}

	public AbstractBootDashElementsAction getOpenConsoleAction() {
		return openConsoleAction;
	}

	public AbstractBootDashElementsAction getLinkWithConsoleAction() {
		return linkWithConsoleAction;
	}

	public AbstractBootDashElementsAction getOpenInPackageExplorerAction() {
		return openInPackageExplorerAction;
	}

	public OpenLaunchConfigAction getOpenConfigAction() {
		return openConfigAction;
	}

	public AddRunTargetAction[] getAddRunTargetActions() {
		return addTargetActions;
	}

	public ToggleBootDashModelConnection getConnectAction() {
		return connectAction;
	}

	public IAction getRemoveRunTargetAction() {
		return removeTargetAction;
	}

	/**
	 * @return May be null as it may not be supported on all models.
	 */
	public IAction getRefreshRunTargetAction() {
		return refreshAction;
	}

	public DeleteElementsAction<?> getDeleteAppsAction() {
		return deleteAppsAction;
	}

	public IAction getDeleteConfigsAction() {
		return deleteConfigsAction;
	}

	/**
	 * @return show properties view action instance
	 */
	public IAction getShowPropertiesViewAction() {
		return showPropertiesViewAction;
	}

	public EnableRemoteDevtoolsAction getEnableDevtoolsAction() {
		return enableRemoteDevtoolsAction;
	}

	public RestartDevtoolsClientAction getRestartDevtoolsClientAction() {
		return restartDevtoolsClientAction;
	}

	public void dispose() {
		if (runStateActions != null) {
			for (RunStateAction a : runStateActions) {
				a.dispose();
			}
			runStateActions = null;
		}
		if (openConsoleAction != null) {
			openConsoleAction.dispose();
		}
		if (linkWithConsoleAction != null) {
			linkWithConsoleAction.dispose();
		}
		if (openConfigAction != null) {
			openConfigAction.dispose();
		}
		if (openBrowserAction != null) {
			openBrowserAction.dispose();
		}
		if (addTargetActions != null) {
			for (AddRunTargetAction a : addTargetActions) {
				a.dispose();
			}
			addTargetActions = null;
		}
		if (toggleFiltersDialogAction != null) {
			toggleFiltersDialogAction.dispose();
			toggleFiltersDialogAction = null;
		}

		if (duplicateConfigAction != null) {
			duplicateConfigAction.dispose();
			duplicateConfigAction = null;
		}
		debugOnTargetActions.dispose();
		runOnTargetActions.dispose();
		liveDataConnectionManagement.dispose();
	}

	public IAction getToggleFiltersDialogAction() {
		return toggleFiltersDialogAction;
	}

	public DuplicateConfigAction getDuplicateConfigAction() {
		return duplicateConfigAction;
	}

	public CustmomizeTargetLabelAction getCustomizeTargetLabelAction() {
		return customizeTargetLabelAction;
	}

	public ImmutableList<IAction> getDebugOnTargetActions() {
		return getDeployAndStartOnTargetActions(debugOnTargetActions);
	}
	public ImmutableList<IAction> getRunOnTargetActions() {
		return getDeployAndStartOnTargetActions(runOnTargetActions);
	}

	public IAction getOpenBootDashPreferencesAction() {
		return this.openBootDashPreferencesAction;
	}

	private ImmutableList<IAction> getDeployAndStartOnTargetActions(
			DisposingFactory<RunTarget, AbstractBootDashAction> actionFactory) {
		ArrayList<RunTarget> targets = new ArrayList<>(model.getRunTargets().getValues());
		Collections.sort(targets, model.getTargetComparator());

		ImmutableList.Builder<IAction> builder = ImmutableList.builder();
		for (RunTarget target : targets) {
			if (target.getType() instanceof RemoteRunTargetType) {
				AbstractBootDashAction a = actionFactory.createOrGet(target);
				if (a != null) {
					builder.add(actionFactory.createOrGet(target));
				}
			}
		}
		return builder.build();
	}

	private DisposingFactory<RunTarget, AbstractBootDashAction> createDeployOnTargetActions(final RunState runningOrDebugging) {
		ObservableSet<RunTarget> runtargets = model.getRunTargets();
		return new DisposingFactory<>(runtargets) {
			@SuppressWarnings({ "rawtypes", "unchecked" })
			@Override
			protected AbstractBootDashAction create(RunTarget target) {
				if (target instanceof RemoteRunTarget) {
					return new DeployToRemoteTargetAction(defaultActionParams(), (RemoteRunTarget)target, runningOrDebugging);
				}
				return null;
			}
		};
	}

	void bindAction(AbstractBootDashElementsAction action) {
		Assert.isTrue(!defIdToActions .containsKey(action.getActionDefinitionId()), "Duplicate action definition id " + action.getActionDefinitionId());
		defIdToActions.put(action.getActionDefinitionId(), action);
	}

	public IAction getAction(String id) {
		return defIdToActions.get(id);
	}

	public LiveDataConnectionManagementActions getLiveDataConnectionManagement() {
		return liveDataConnectionManagement;
	}

	UserInteractions ui() {
		return context.getBean(UserInteractions.class);
	}

	public List<IAction> getInjectedActions(Location menu) {
		return injectedActions.stream().filter(a -> a.showIn().contains(menu)).collect(Collectors.toList());
	}

	public Collection<AbstractBootDashAction> getAllInjectedActions() {
		return injectedActions;
	}
}
