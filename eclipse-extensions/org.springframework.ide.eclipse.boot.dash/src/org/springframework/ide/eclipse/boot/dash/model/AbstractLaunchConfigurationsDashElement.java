/*******************************************************************************
 * Copyright (c) 2015, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model;

import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.swt.widgets.Display;
import org.springframework.ide.eclipse.beans.ui.live.model.LiveBeansModel;
import org.springframework.ide.eclipse.boot.dash.livexp.PollingLiveExp;
import org.springframework.ide.eclipse.boot.dash.model.actuator.ActuatorClient;
import org.springframework.ide.eclipse.boot.dash.model.actuator.JMXActuatorClient;
import org.springframework.ide.eclipse.boot.dash.model.actuator.RequestMapping;
import org.springframework.ide.eclipse.boot.dash.model.actuator.env.LiveEnvModel;
import org.springframework.ide.eclipse.boot.dash.util.CollectionUtils;
import org.springframework.ide.eclipse.boot.dash.util.LaunchConfRunStateTracker;
import org.springframework.ide.eclipse.boot.dash.util.RunStateTracker.RunStateListener;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.BootLsCommandUtils;
import org.springframework.ide.eclipse.boot.launch.cli.CloudCliServiceLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.util.BootDebugUITools;
import org.springframework.ide.eclipse.boot.launch.util.BootLaunchUtils;
import org.springframework.ide.eclipse.boot.launch.util.JMXClient;
import org.springframework.ide.eclipse.boot.launch.util.SpringApplicationLifeCycleClientManager;
import org.springframework.ide.eclipse.boot.launch.util.SpringApplicationLifecycleClient;
import org.springframework.ide.eclipse.boot.util.RetryUtil;
import org.springsource.ide.eclipse.commons.core.pstore.IPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStoreApi;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStores;
import org.springsource.ide.eclipse.commons.core.util.ProcessUtils;
import org.springsource.ide.eclipse.commons.frameworks.core.maintype.MainTypeFinder;
import org.springsource.ide.eclipse.commons.livexp.core.AsyncLiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.DisposeListener;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;
import org.springsource.ide.eclipse.commons.ui.launch.LaunchUtils;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.reflect.TypeToken;

/**
 * Abstracts out the commonalities between {@link BootProjectDashElement} and {@link LaunchConfDashElement}. Each can
 * be viewed as representing a collection of launch configuration.
 * <p>
 * A {@link BootProjectDashElement} element represents all the launch configurations associated with a given project whereas as
 * {@link LaunchConfDashElement} represent a single launch configuration (i.e. a singleton collection).
 *
 * @author Kris De Volder
 */
public abstract class AbstractLaunchConfigurationsDashElement<T> extends WrappingBootDashElement<T> implements Duplicatable<LaunchConfDashElement> {

	private static final boolean DEBUG = false; //DebugUtil.isDevelopment();
	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	public static final EnumSet<RunState> READY_STATES = EnumSet.of(RunState.RUNNING, RunState.DEBUGGING);

	private static final Duration LIVE_DATA_REFRESH_TIMEOUT = Duration.ofMinutes(2);

	private LiveExpression<RunState> runState;
	private LiveExpression<Integer> livePort;
	private LiveExpression<Integer> actuatorPort;
	private LiveExpression<Integer> actualInstances;

	private PropertyStoreApi persistentProperties;

	private PollingLiveExp<Failable<ImmutableList<RequestMapping>>> liveRequestMappings;
	private PollingLiveExp<Failable<LiveBeansModel>> liveBeans;
	private PollingLiveExp<Failable<LiveEnvModel>> liveEnv;
	private PollingLiveExp<Failable<ImmutableSet<String>>> liveProfiles;

	public AbstractLaunchConfigurationsDashElement(LocalBootDashModel bootDashModel, T delegate) {
		super(bootDashModel, delegate);
		this.runState = createRunStateExp();
		this.livePort = createPortExpression(runState);
		this.actuatorPort = createActuatorPortExpression(runState);
		this.actualInstances = createActualInstancesExp();
		addElementNotifier(livePort);
		addElementNotifier(runState);
		addElementNotifier(actualInstances);
	}

	protected abstract IPropertyStore createPropertyStore();

	@Override
	public abstract ImmutableSet<ILaunchConfiguration> getLaunchConfigs();

	@Override
	public abstract IProject getProject();

	@Override
	public abstract String getName();

	@Override
	public RunState getRunState() {
		return runState.getValue();
	}

	public LiveExpression<RunState> getRunStateExp() {
		return runState;
	}

	@Override
	public String toString() {
		return this.getClass().getSimpleName()+"("+getName()+")";
	}

	@Override
	public int getLivePort() {
		return livePort.getValue();
	}

	@Override
	public String getLiveHost() {
		return "localhost";
	}

	@Override
	public ILaunchConfiguration getActiveConfig() {
		ILaunchConfiguration single = CollectionUtils.getSingle(getLaunchConfigs());
		if (single!=null) {
			return single;
		}
		return null;
	}

	@Override
	public void stop() throws Exception {
		stop(true);
	}

	public void stop(boolean sync) throws Exception {
		debug("Stopping: "+this+" "+(sync?"...":""));
		final CompletableFuture<Void> done = sync?new CompletableFuture<>():null;
		try {
			ImmutableSet<ILaunch> launches = getLaunches();
			if (sync) {
				LaunchUtils.whenTerminated(launches, new Runnable() {
					public void run() {
						done.complete(null);
					}
				});
			}
			try {
				BootLaunchUtils.terminate(launches);
			} catch (Exception e) {
				//why does terminating process with Eclipse debug UI fail so #$%# often?
				Log.log(new Error("Termination of "+this+" failed", e));
			}
		} catch (Exception e) {
			Log.log(e);
		}
		if (sync) {
			//Eclipse waits for 5 seconds before timing out. So we use a similar timeout but slightly
			// larger. Windows case termination seem to fail silently sometimes so its up to us
			// to handle here.
			done.get(6, TimeUnit.SECONDS);
			debug("Stopping: "+this+" "+"DONE");
		}
	}

	/**
	 * Get the launches associated with this element.
	 * <p>
	 * Note, we could implement it here by taking the union of all launches for all launch confs,
	 * but subclass can provide more efficient implementation so we make this abstract.
	 */
	public abstract ImmutableSet<ILaunch> getLaunches();

	@Override
	public void restart(RunState runningOrDebugging, UserInteractions ui) throws Exception {
		switch (runningOrDebugging) {
		case RUNNING:
			restart(ILaunchManager.RUN_MODE, ui);
			break;
		case DEBUGGING:
			restart(ILaunchManager.DEBUG_MODE, ui);
			break;
		default:
			throw new IllegalArgumentException("Restart expects RUNNING or DEBUGGING as 'goal' state");
		}
	}

	public void restart(final String runMode, UserInteractions ui) throws Exception {
		stopSync();
		start(runMode, ui);
	}

	public void stopSync() throws Exception {
		try {
			stop(true);
		} catch (TimeoutException e) {
			Log.info("Termination of '"+this.getName()+"' timed-out. Retrying");
			//Try it one more time. On windows this times out occasionally... and then
			// it works the next time.
			stop(true);
		}
	}

	private void start(final String runMode, UserInteractions ui) {
		try {
			ILaunchConfiguration conf = getOrCreateLaunchConfig(ui);
			if (conf!=null) {
				launch(runMode, conf);
			}
		} catch (Exception e) {
			Log.log(e);
		}
	}

	private ILaunchConfiguration getOrCreateLaunchConfig(UserInteractions ui) throws Exception {
		ILaunchConfiguration conf = null;

		ImmutableSet<ILaunchConfiguration> configs = getLaunchConfigs();
		if (configs.isEmpty()) {
			IType mainType = chooseMainType(ui);
			if (mainType!=null) {
				LocalRunTarget target = getTarget();
				IJavaProject jp = getJavaProject();
				conf = target.createLaunchConfig(jp, mainType);
			}
		} else {
			conf = chooseConfig(ui, configs);
		}

		return conf;
	}

	private IType chooseMainType(UserInteractions ui) throws CoreException {
		IType[] mainTypes = guessMainTypes();
		if (mainTypes.length==0) {
			ui.errorPopup("Problem launching", "Couldn't find a main type in '"+getName()+"'");
			return null;
		} else if (mainTypes.length==1){
			return mainTypes[0];
		} else {
			return ui.chooseMainType(mainTypes, "Choose Main Type", "Choose main type for '"+getName()+"'");
		}
	}

	protected IType[] guessMainTypes() throws CoreException {
		return MainTypeFinder.guessMainTypes(getJavaProject(), new NullProgressMonitor());
	}

	protected void launch(final String runMode, final ILaunchConfiguration conf) throws Exception {
		CompletableFuture<Void> done = new CompletableFuture<>();
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				try {
					BootDebugUITools.launchInBackground(conf, runMode, done);
				} catch (Throwable e) {
					done.completeExceptionally(e);
				}
			}
		});
		Display display = Display.getCurrent();
		if (display!=null) {
			//Blocking the ui thread is iffy. It has a tendency to deadlock when
			// work you are waiting for is actually using 'syncExec or asyncExec' somewhere inside.
			//We can avoid this deadlock by calling on display.readAndDispatch to allow other stuff to run in
			//the ui thread while we are waiting.
			while (!done.isDone() ) {
				while (display.readAndDispatch()) {}
				done.get(100, TimeUnit.MILLISECONDS);
			}
		}
		done.get();
	}

	@Override
	public void openConfig(UserInteractions ui) {
		try {
			IProject p = getProject();
			if (p!=null) {
				ILaunchConfiguration conf;
				ImmutableSet<ILaunchConfiguration> configs = getLaunchConfigs();
				if (configs.isEmpty()) {
					conf = createLaunchConfigForEditing();
				} else {
					conf = chooseConfig(ui, configs);
				}
				if (conf!=null) {
					ui.openLaunchConfigurationDialogOnGroup(conf, getLaunchGroup());
				}
			}
		} catch (Exception e) {
			ui.errorPopup("Couldn't open config for "+getName(), ExceptionUtil.getMessage(e));
		}
	}

	@Override
	public boolean canDuplicate() {
		return getLaunchConfigs().size()==1;
	}

	@Override
	public LaunchConfDashElement duplicate(UserInteractions ui) {
		try {
			ILaunchConfiguration conf = CollectionUtils.getSingle(getLaunchConfigs());
			if (conf!=null) {
				ILaunchConfiguration newConf = BootLaunchConfigurationDelegate.duplicate(conf);
				return getBootDashModel().getLaunchConfElementFactory().createOrGet(newConf);
			}
		} catch (Exception e) {
			Log.log(e);
			ui.errorPopup("Couldn't duplicate config", ExceptionUtil.getMessage(e));
		}
		return null;
	}

	@Override
	public int getDesiredInstances() {
		//special case for no launch configs (a single launch conf is created on demand,
		//so we should treat it as if it already has one).
		return Math.max(1, getLaunchConfigs().size());
	}

	@Override
	public int getActualInstances() {
		return actualInstances.getValue();
	}

	@Override
	public PropertyStoreApi getPersistentProperties() {
		if (persistentProperties==null) {
			IPropertyStore backingStore = createPropertyStore();
			this.persistentProperties = PropertyStores.createApi(backingStore);
		}
		return persistentProperties;
	}

	private LaunchConfRunStateTracker runStateTracker() {
		return getBootDashModel().getLaunchConfRunStateTracker();
	}

	protected void refreshRunState() {
		runState.refresh();
	}

	///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public ILaunchConfiguration createLaunchConfigForEditing() throws Exception {
		IJavaProject jp = getJavaProject();
		LocalRunTarget target = getTarget();
		IType[] mainTypes = guessMainTypes();
		return target.createLaunchConfig(jp, mainTypes.length==1?mainTypes[0]:null);
	}

	protected ILaunchConfiguration chooseConfig(UserInteractions ui, Collection<ILaunchConfiguration> configs) {
		//TODO: this should probably be removed. Actions etc. should either apply to all the elements at once,
		// or be disabled if that seems ill-conceived. In such a ui there should be no need to popup a dialog
		// to choose a configuration.
		ILaunchConfiguration conf = chooseConfigurationDialog(configs,
				"Choose Launch Configuration",
				"Several launch configurations are associated with '"+getName()+"' "+
				"Choose one.", ui);
		return conf;
	}

	private ILaunchConfiguration chooseConfigurationDialog(Collection<ILaunchConfiguration> configs, String dialogTitle, String message, UserInteractions ui) {
		if (configs.size()==1) {
			return CollectionUtils.getSingle(configs);
		} else if (configs.size()>0) {
			ILaunchConfiguration chosen = ui.chooseConfigurationDialog(dialogTitle, message, configs);
			return chosen;
		}
		return null;
	}

	private String getLaunchGroup() {
		switch (getRunState()) {
		case RUNNING:
			return IDebugUIConstants.ID_RUN_LAUNCH_GROUP;
		case DEBUGGING:
			return IDebugUIConstants.ID_DEBUG_LAUNCH_GROUP;
		default:
			return IDebugUIConstants.ID_DEBUG_LAUNCH_GROUP;
		}
	}

	public int getActuatorPort() {
		return actuatorPort.getValue();
	}

	protected LiveExpression<RunState> createRunStateExp() {
		final LaunchConfRunStateTracker tracker = runStateTracker();
		final LiveExpression<RunState> exp = new LiveExpression<>() {
			protected RunState compute() {
				AbstractLaunchConfigurationsDashElement<T> it = AbstractLaunchConfigurationsDashElement.this;
				debug("Computing runstate for "+it);
				LaunchConfRunStateTracker tracker = runStateTracker();
				RunState state = RunState.INACTIVE;
				for (ILaunchConfiguration conf : getLaunchConfigs()) {
					RunState confState = tracker.getState(conf);
					debug("state for conf "+conf+" = "+confState);
					state = state.merge(confState);
				}
				debug("runstate for "+it+" => "+state);
				return state;
			}
			@Override
			public String toString() {
				return "LiveExp(runState)";
			}
			@Override
			public void dispose() {
				super.dispose();
			}
		};
		final RunStateListener<ILaunchConfiguration> runStateListener = new RunStateListener<>() {
			@Override
			public void stateChanged(ILaunchConfiguration changedConf) {
				if (getLaunchConfigs().contains(changedConf)) {
					exp.refresh();
				}
			}
		};
		tracker.addListener(runStateListener);
		exp.onDispose(new DisposeListener() {
			public void disposed(Disposable disposed) {
				tracker.removeListener(runStateListener);
			}
		});
		addDisposableChild(exp);
		exp.refresh();
		return exp;
	}

	protected LiveExpression<Integer> createActualInstancesExp() {
		final LaunchConfRunStateTracker tracker = runStateTracker();
		final LiveExpression<Integer> exp = new LiveExpression<>(0) {
			@Override
			public String toString() {
				return "LiveExp(actualInstances)";
			}
			protected Integer compute() {
				int activeCount = 0;
				for (ILaunchConfiguration c : getLaunchConfigs()) {
					if (READY_STATES.contains(tracker.getState(c))) {
						activeCount++;
					}
				}
				return activeCount;
			}
		};
		final RunStateListener<ILaunchConfiguration> runStateListener = new RunStateListener<>() {
			@Override
			public void stateChanged(ILaunchConfiguration changedConf) {
				if (getLaunchConfigs().contains(changedConf)) {
					exp.refresh();
				}
			}
		};
		tracker.addListener(runStateListener);
		exp.onDispose(new DisposeListener() {
			public void disposed(Disposable disposed) {
				tracker.removeListener(runStateListener);
			}
		});
		addDisposableChild(exp);
		exp.refresh();
		return exp;
	}

	protected LiveExpression<Integer> createPortExpression(final LiveExpression<RunState> runState) {
		return createLivePortExp(runState, "local.server.port");
	}

	protected LiveExpression<Integer> createActuatorPortExpression(final LiveExpression<RunState> runState) {
		return createLivePortExp(runState, "local.management.port");
	}

	private LiveExpression<Integer> createLivePortExp(final LiveExpression<RunState> runState, final String propName) {
		AsyncLiveExpression<Integer> exp = new AsyncLiveExpression<>(-1, "Refreshing port info ("+propName+") for "+getName()) {
			{
				//Doesn't really depend on runState, but should be recomputed when runState changes.
				dependsOn(runState);
			}
			@Override
			protected Integer compute() {
				return getLivePort(propName);
			}
			@Override
			public String toString() {
				return "LivePortExp("+propName+")";
			}
		};
		addDisposableChild(exp);
		return exp;
	}

	protected ActuatorClient getActuatorClient() {
		return JMXActuatorClient.forUrl(getTypeLookup(), this::getJmxUrl);
	}

	/**
	 * JMX URL for this element's running process: a fixed jmxurl if a JMX port was pinned,
	 * otherwise attaches to the process id on-demand to resolve its local management agent's
	 * URL (see ProcessUtils.getLocalManagementAgentUrl).
	 */
	private String getJmxUrl() {
		for (ILaunchConfiguration c : getLaunchConfigs()) {
			for (ILaunch l : LaunchUtils.getLaunches(c)) {
				if (!l.isTerminated()) {
					int port = BootLaunchConfigurationDelegate.getJMXPortAsInt(l);
					if (port>0) {
						return JMXClient.createLocalJmxUrl(port);
					}
					String pid = l.getAttribute(BootLaunchConfigurationDelegate.PROCESS_ID);
					if (pid!=null) {
						return ProcessUtils.getLocalManagementAgentUrl(pid);
					}
				}
			}
		}
		return null;
	}

	@Override
	public Failable<ImmutableList<RequestMapping>> getLiveRequestMappings() {
		synchronized (this) {
			if (liveRequestMappings==null) {
				ActuatorClient client = getActuatorClient();
				liveRequestMappings = PollingLiveExp.create(Failable.error(MissingLiveInfoMessages.NOT_YET_COMPUTED), () -> {
					List<RequestMapping> requestMappings = client.getRequestMappings();
					return requestMappings == null ?
							Failable.error(getBootDashModel().getRunTarget().getType().getMissingLiveInfoMessages().getMissingInfoMessage(getName(), "mappings")) :
							Failable.of(ImmutableList.copyOf(requestMappings));
				});
				addElementState(liveRequestMappings);
				addDisposableChild(liveRequestMappings);
				runState.addListener((e, runstate) -> {
					if (READY_STATES.contains(runstate)) {
						liveRequestMappings.refreshFor(LIVE_DATA_REFRESH_TIMEOUT);
					} else {
						liveRequestMappings.refreshOnce();
					}
				});
			}
			return liveRequestMappings.getValue();
		}
	}

	public Failable<LiveBeansModel> getLiveBeans() {
		synchronized (this) {
			if (liveBeans == null) {
				ActuatorClient client = getActuatorClient();
				liveBeans = PollingLiveExp.create(Failable.error(MissingLiveInfoMessages.NOT_YET_COMPUTED), () -> {
					LiveBeansModel beans = client.getBeans();
					return beans == null ? Failable.error(getBootDashModel().getRunTarget().getType().getMissingLiveInfoMessages().getMissingInfoMessage(getName(), "beans")) :
						Failable.of(beans);
				});
				addElementState(liveBeans);
				addDisposableChild(liveBeans);
				runState.addListener((e, runstate) -> {
					if (READY_STATES.contains(runstate)) {
						// After the app is running refresh for 2 minutes every 5 sec
						liveBeans.sleepBetweenRefreshes(Duration.ofSeconds(5));
						liveBeans.refreshFor(LIVE_DATA_REFRESH_TIMEOUT);
					} else {
						liveBeans.refreshOnce();
					}
				});
			}
			return liveBeans.getValue();
		}
	}

	public Failable<LiveEnvModel> getLiveEnv() {
		synchronized (this) {
			if (liveEnv == null) {
				ActuatorClient client = getActuatorClient();
				liveEnv = PollingLiveExp.create(Failable.error(MissingLiveInfoMessages.NOT_YET_COMPUTED), () -> {
					LiveEnvModel env = client.getEnv();
					return env == null ? Failable.error(getBootDashModel().getRunTarget().getType().getMissingLiveInfoMessages().getMissingInfoMessage(getName(), "env")) :
						Failable.of(env);
				});
				addElementState(liveEnv);
				addDisposableChild(liveEnv);
				runState.addListener((e, runstate) -> {
					if (READY_STATES.contains(runstate)) {
						// After the app is running refresh for 2 minutes every 5 sec
						liveEnv.sleepBetweenRefreshes(Duration.ofSeconds(5));
						liveEnv.refreshFor(LIVE_DATA_REFRESH_TIMEOUT);
					} else {
						liveEnv.refreshOnce();
					}
				});
			}
			return liveEnv.getValue();
		}
	}

	@Override
	public Failable<ImmutableSet<String>> getLiveProfiles() {
		synchronized (this) {
			if (liveProfiles == null) {
				liveProfiles = PollingLiveExp.create(Failable.<ImmutableSet<String>>error(MissingLiveInfoMessages.NOT_YET_COMPUTED), () -> {
					try {
						String processKey = getProcessKey();
						if (processKey == null) {
							return Failable.error(getBootDashModel().getRunTarget().getType().getMissingLiveInfoMessages().getMissingInfoMessage(getName(), "profiles"));
						}
						String[] o = BootLsCommandUtils.executeCommand(TypeToken.get(String[].class), "sts/livedata/get", Map.of(
								"endpoint", "profiles",
								"processKey", processKey
						)).get().orElse(new String[0]);
						liveProfiles.refreshOnce();
						return Failable.of(ImmutableSet.copyOf(o));
					} catch (Exception e) {
						return Failable.error(getBootDashModel().getRunTarget().getType().getMissingLiveInfoMessages().getMissingInfoMessage(getName(), "profiles"));
					}
				});
				addElementState(liveProfiles);
				addDisposableChild(liveProfiles);
				runState.addListener((e, runstate) -> {
					if (READY_STATES.contains(runstate)) {
						// After the app is running refresh for 5 sec every 1 sec until active profiles are fetched
						liveProfiles.sleepBetweenRefreshes(Duration.ofSeconds(1));
						liveProfiles.refreshFor(Duration.ofSeconds(5));
					} else {
						liveProfiles.refreshOnce();
					}
				});
			}
			return liveProfiles.getValue();
		}
	}

	/**
	 * Key identifying this element's running process to the language server's live data
	 * connections: a jmxurl if a fixed JMX port was pinned, otherwise the process id (the
	 * language server attaches to it on-demand; see PROCESS_ID / SpringApplicationLifeCycleClientManager).
	 */
	private String getProcessKey() {
		for (ILaunchConfiguration c : getLaunchConfigs()) {
			for (ILaunch l : LaunchUtils.getLaunches(c)) {
				if (!l.isTerminated()) {
					int port = BootLaunchConfigurationDelegate.getJMXPortAsInt(l);
					if (port>0) {
						return JMXClient.createLocalJmxUrl(port);
					}
					String pid = l.getAttribute(BootLaunchConfigurationDelegate.PROCESS_ID);
					if (pid!=null) {
						return pid;
					}
				}
			}
		}
		return null;
	}

	private int getLivePort(String propName) {
		debug("["+this.getName()+"] getLivePort("+propName+")");
		ILaunchConfiguration conf = getActiveConfig();
		debug("["+this.getName()+"] getLivePort("+propName+") conf = "+conf);
		if (conf!=null && READY_STATES.contains(getRunState())) {
			debug("["+this.getName()+"] getLivePort("+propName+") runstate ok");
			if (BootLaunchConfigurationDelegate.canUseLifeCycle(conf) || CloudCliServiceLaunchConfigurationDelegate.canUseLifeCycle(conf)) {
				debug("["+this.getName()+"] getLivePort("+propName+") canUseLifeCycle ok");
				//TODO: what if there are several launches? Right now we ignore all but the first
				// non-terminated launch.
				for (ILaunch l : BootLaunchUtils.getLaunches(conf)) {
					if (!l.isTerminated()) {
						debug("["+this.getName()+"] getLivePort("+propName+") found a launch");
						// Uses a pinned JMX port directly if one was set, otherwise attaches to
						// the launch's process id on-demand (see SpringApplicationLifeCycleClientManager).
						SpringApplicationLifeCycleClientManager cm = null;
						try {
							cm = new SpringApplicationLifeCycleClientManager(l);
							SpringApplicationLifecycleClient c = cm.getLifeCycleClient();
							debug("["+this.getName()+"] getLivePort("+propName+") lifeCycleClient = "+c);
							if (c!=null) {
								//Just because lifecycle bean is ready does not mean that the port property has already been set.
								//To avoid race condition we should wait here until the port is set (some apps aren't web apps and
								//may never get a port set, so we shouldn't wait indefinitely!)
								return RetryUtil.retry(100, 1000, () -> {
									debug("["+this.getName()+"] getLivePort("+propName+") trying to get...");
									int port = c.getProperty(propName, -1);
									debug("["+this.getName()+"] getLivePort("+propName+") port = "+ port);
									if (port<=0) {
										throw new IllegalStateException("port not (yet) set");
									}
									return port;
								});
							}
						} catch (Exception e) {
							debug(ExceptionUtil.getMessage(e));
							//most likely this just means the app isn't running so ignore
						} finally {
							if (cm!=null) {
								cm.disposeClient();
							}
						}
					}
				}
			}
		}
		debug("["+this.getName()+"] getLivePort("+propName+") => -1");
		return -1;
	}

	public void refreshLivePorts() {
		refresh(livePort, actuatorPort);
	}

	private void refresh(LiveExpression<?>... exps) {
		for (LiveExpression<?> e : exps) {
			if (e!=null) {
				e.refresh();
			}
		}
	}

	@Override
	public LocalBootDashModel getBootDashModel() {
		return (LocalBootDashModel) super.getBootDashModel();
	}

	@Override
	public EnumSet<RunState> supportedGoalStates() {
		return RunTargets.LOCAL_RUN_GOAL_STATES;
	}

	@Override
	public LocalRunTarget getTarget() {
		return (LocalRunTarget) super.getTarget();
	}

	@Override
	public ImmutableSet<String> getActiveProfiles() {
		ILaunchConfiguration launchConfig = getActiveConfig();
		if (launchConfig != null) {
			String profile = BootLaunchConfigurationDelegate.getProfile(launchConfig);
			if (profile != null) {
				return ImmutableSet.of(profile);
			}
		}
		return ImmutableSet.of();
	}

}
