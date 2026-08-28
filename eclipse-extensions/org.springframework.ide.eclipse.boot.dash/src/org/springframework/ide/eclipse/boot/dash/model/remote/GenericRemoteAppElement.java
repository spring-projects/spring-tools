/*******************************************************************************
 * Copyright (c) 2020, 2024 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model.remote;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleListener;
import org.eclipse.ui.console.IConsoleManager;
import org.springframework.ide.eclipse.beans.ui.live.model.LiveBeansModel;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.api.ActualInstanceCount;
import org.springframework.ide.eclipse.boot.dash.api.App;
import org.springframework.ide.eclipse.boot.dash.api.AppConsole;
import org.springframework.ide.eclipse.boot.dash.api.AppContext;
import org.springframework.ide.eclipse.boot.dash.api.ClasspathBearing;
import org.springframework.ide.eclipse.boot.dash.api.DebuggableApp;
import org.springframework.ide.eclipse.boot.dash.api.Deletable;
import org.springframework.ide.eclipse.boot.dash.api.DesiredInstanceCount;
import org.springframework.ide.eclipse.boot.dash.api.DevtoolsConnectable;
import org.springframework.ide.eclipse.boot.dash.api.JmxConnectable;
import org.springframework.ide.eclipse.boot.dash.api.LogConnection;
import org.springframework.ide.eclipse.boot.dash.api.LogSource;
import org.springframework.ide.eclipse.boot.dash.api.PortConnectable;
import org.springframework.ide.eclipse.boot.dash.api.ProjectRelatable;
import org.springframework.ide.eclipse.boot.dash.api.RunStateIconProvider;
import org.springframework.ide.eclipse.boot.dash.api.RunStateProvider;
import org.springframework.ide.eclipse.boot.dash.api.Styleable;
import org.springframework.ide.eclipse.boot.dash.api.SystemPropertySupport;
import org.springframework.ide.eclipse.boot.dash.console.CloudAppLogManager;
import org.springframework.ide.eclipse.boot.dash.devtools.DevtoolsUtil;
import org.springframework.ide.eclipse.boot.dash.di.SimpleDIContext;
import org.springframework.ide.eclipse.boot.dash.liveprocess.LiveDataCapableElement;
import org.springframework.ide.eclipse.boot.dash.liveprocess.LiveDataConnectionManagementActions.ExecuteCommandAction;
import org.springframework.ide.eclipse.boot.dash.livexp.DisposingFactory;
import org.springframework.ide.eclipse.boot.dash.model.BootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.ClasspathPropertyTester;
import org.springframework.ide.eclipse.boot.dash.model.BootDashModel.ElementStateListener;
import org.springframework.ide.eclipse.boot.dash.model.Failable;
import org.springframework.ide.eclipse.boot.dash.model.MissingLiveInfoMessages;
import org.springframework.ide.eclipse.boot.dash.model.RefreshState;
import org.springframework.ide.eclipse.boot.dash.model.RunState;
import org.springframework.ide.eclipse.boot.dash.model.UserInteractions;
import org.springframework.ide.eclipse.boot.dash.model.WrappingBootDashElement;
import org.springframework.ide.eclipse.boot.dash.model.actuator.ActuatorClient;
import org.springframework.ide.eclipse.boot.dash.model.actuator.JMXActuatorClient;
import org.springframework.ide.eclipse.boot.dash.model.actuator.RequestMapping;
import org.springframework.ide.eclipse.boot.dash.model.actuator.env.LiveEnvModel;
import org.springframework.ide.eclipse.boot.launch.BootLsCommandUtils;
import org.springsource.ide.eclipse.commons.core.pstore.IPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStoreApi;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStores;
import org.springsource.ide.eclipse.commons.livexp.core.AsyncLiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.AsyncLiveExpression.AsyncMode;
import org.springsource.ide.eclipse.commons.livexp.core.DisposeListener;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSetVariable;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.ui.Stylers;
import org.springsource.ide.eclipse.commons.livexp.util.Log;
import org.springsource.ide.eclipse.commons.livexp.util.OldValueDisposer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.gson.reflect.TypeToken;

public class GenericRemoteAppElement extends WrappingBootDashElement<String> implements Deletable, AppContext, Styleable, ElementStateListener, JmxConnectable, LiveDataCapableElement {

	private static final boolean DEBUG = false;

	private LiveVariable<App> app = new LiveVariable<>();

	private final Object parent;

	private LiveSetVariable<String> existingChildIds = new LiveSetVariable<>();

	private final RefreshStateTracker refreshTracker = new RefreshStateTracker(this);

	final protected IPropertyStore backingStore;

	final private List<LiveExpression<?>> summaries = new ArrayList<>();

	DisposingFactory<String, GenericRemoteAppElement> childFactory = new DisposingFactory<>(existingChildIds) {
		@Override
		protected GenericRemoteAppElement create(String appId) {
			GenericRemoteAppElement parent = GenericRemoteAppElement.this;
			GenericRemoteAppElement element = new GenericRemoteAppElement(getBootDashModel(), parent, appId, backingStore);
			return element;
		}
	};

	@Override
	public ImageDescriptor getCustomRunStateIcon() {
		App data = app.getValue();
		if (data instanceof RunStateIconProvider) {
			ImageDescriptor icon = ((RunStateIconProvider) data).getRunStateIcon(getRunState());
			if (icon != null) {
				return icon;
			}
		}
		return super.getCustomRunStateIcon();
	}

	@Override
	public <T extends App> T getParent(Class<T> expectedType) {
		if (parent instanceof GenericRemoteAppElement) {
			App parentApp = ((GenericRemoteAppElement) parent).getAppData();
			if (parentApp != null) {
				return expectedType.cast(parentApp);
			}
		}
		return null;
	}

	private ObservableSet<BootDashElement> children = ObservableSet.<BootDashElement>builder().refresh(AsyncMode.ASYNC).compute(() -> {

		App appVal = app.getValue();
		if (appVal instanceof ChildBearing) {
			try {
				List<App> children = ((ChildBearing)appVal).fetchChildren();
				ImmutableSet.Builder<String> existingIds = ImmutableSet.builder();
				for (App app : children) {
					existingIds.add(app.getName());
				}
				existingChildIds.replaceAll(existingIds.build());

				ImmutableSet.Builder<BootDashElement> builder = ImmutableSet.builder();
				for (App child : children) {
					GenericRemoteAppElement childElement = childFactory.createOrGet(child.getName());
					if (childElement != null) {
						child.setContext(childElement);
						childElement.setAppData(child);
						builder.add(childElement);
					} else {
						Log.warn("No boot dash element for child: "+child);
					}
				}
				return builder.build();
//			} catch (TimeoutException e) {
//				//ignore, expected error
			} catch (Exception e) {
				Log.log(e);
			}
		}
		return ImmutableSet.of();
	}).build();
	{
		children.dependsOn(app);
		children.dependsOn(refreshTracker.refreshState);
	}

	@Override
	public void setGoalState(RunState s) {
		App data = getAppData();
		if (data != null) {
			if (s == RunState.PAUSED) {
				RemoteJavaLaunchUtil.disconnectRelatedLaunches(this);
			}
			data.setGoalState(s);
		}
	}

	LiveExpression<RunState> baseRunState = new AsyncLiveExpression<>(RunState.UNKNOWN) {
		{
			dependsOn(app);
			dependsOn(children);
		}

		@Override
		protected RunState compute() {
			App data = app.getValue();
			Assert.isLegal(!(data instanceof RunStateProvider && data instanceof ChildBearing));
			if (data instanceof RunStateProvider) {
				RunState v = ((RunStateProvider) data).fetchRunState();
				return v;
			} else if (data instanceof ChildBearing) {
				RunState v = RunState.INACTIVE;
				for (BootDashElement child : children.getValues()) {
					v = v.merge(child.getRunState());
				}
				return v;
			}
			return RunState.UNKNOWN;
		}
	};

	private JmxRunStateTracker jmxRunStateTracker = new JmxRunStateTracker(this, baseRunState, app);


	private ObservableSet<Integer> livePorts = ObservableSet.<Integer>builder().refresh(AsyncMode.ASYNC).compute(() -> {

		ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();

		if (getRunState() == RunState.RUNNING || getRunState() == RunState.DEBUGGING) {
			App appVal = app.getValue();
			debug("appVal: " + appVal);
			if (appVal instanceof PortConnectable) {

				Set<Integer> appLivePorts = ((PortConnectable) appVal).getPorts();

				debug("from PortConnectable: " +appLivePorts);

				if (appLivePorts != null) {
					builder.addAll(appLivePorts);
				}
			}
			else {
				debug("not PortConnectable");
			}

			ImmutableSet<BootDashElement> children = this.children.getValue();
			if (children != null && !children.isEmpty()) {
				for (BootDashElement child : children) {
					ImmutableSet<Integer> childPorts = child.getLivePorts();
					debug("from child: " + child.getName() + " - " + childPorts);
					if (childPorts != null) {
						builder.addAll(childPorts);
					}
				}
			}
			else {
				debug("No Children");
			}
		}
		return builder.build();
	}).build();

	{
		livePorts.dependsOn(children);
		livePorts.dependsOn(app);
		livePorts.dependsOn(getRunStateExp());
	}

	private LiveExpression<Integer> debugPort = new AsyncLiveExpression<>(0) {

		//it is important that events for this exp are fired asynchronously to avoid starving ui thread
		// causing deadlock. (I.e. the important thing is to have the event listeners not be doing things
		// in the ui thread. It might be okay to do the refresh of the exp itself synchronously.
		{
			dependsOn(app);
		}
		protected Integer compute() {
			App data = app.getValue();
			if (data instanceof DebuggableApp) {
				return ((DebuggableApp) data).getDebugPort();
			}
			return 0;
		};
	};

	private LiveExpression<String> remoteDevtoolsUrl = new AsyncLiveExpression<>(null) {

		//it is important that events for this exp are fired asynchronously to avoid starving ui thread
		// causing deadlock. (I.e. the important thing is to have the event listeners not be doing things
		// in the ui thread. It might be okay to do the refresh of the exp itself synchronously.

		{
			dependsOn(livePorts);
		}

		protected String compute() {
			return DevtoolsUtil.remoteUrl(GenericRemoteAppElement.this);
		}
	};

	private LiveExpression<Integer> actualInstanceCounts = sumarizeFromChildren(new AppDataSummarizer<Integer>() {

		public Integer zero() { return 0;}

		@Override
		public Integer getHere(GenericRemoteAppElement app) {
			if (getRunState().isActive()) {
				App appVal = app.getAppData();
				debug("appVal: " + appVal);
				if (appVal instanceof ActualInstanceCount) {
					return ((ActualInstanceCount) appVal).getActualInstances();
				}
			}
			return zero();
		}

		@Override
		public Integer getSummary(GenericRemoteAppElement element) {
			return element.getActualInstances();
		}

		public Integer merge(Integer d1, Integer d2) {
			return d1 + d2;
		}
	});

	private OldValueDisposer<LogConnection> logConnection = new OldValueDisposer<>(this);

	private <T> LiveExpression<T> sumarizeFromChildren(AppDataSummarizer<T> sumarizer) {
		AsyncLiveExpression<T> sumary = new AsyncLiveExpression<>(sumarizer.zero()) {
			@Override
			protected T compute() {
				T sum = sumarizer.getHere(GenericRemoteAppElement.this);
				ImmutableSet<BootDashElement> children = GenericRemoteAppElement.this.children.getValue();
				if (children != null && !children.isEmpty()) {
					for (BootDashElement _child : children) {
						if (_child instanceof GenericRemoteAppElement) {
							GenericRemoteAppElement child = ((GenericRemoteAppElement) _child);
							sum = sumarizer.merge(sum, sumarizer.getSummary(child));
						}
					}
				}
				return sum;
			}
		};
		sumary.dependsOn(children);
		sumary.dependsOn(app);
		sumary.dependsOn(getRunStateExp());
		addDisposableChild(sumary);
		addElementNotifier(sumary);
		summaries.add(sumary);
		return sumary;
	}

	@Override
	public RefreshState getRefreshState() {
		return refreshTracker.refreshState.getValue();
	}

	private void debug(String message) {
		if (DEBUG) {
			System.out.println(this + ": " + message);
		}
	}

	public GenericRemoteAppElement(GenericRemoteBootDashModel<?,?> model, Object parent, String appId, IPropertyStore parentPropertyStore) {
		super(model, appId);
		backingStore = PropertyStores.createSubStore(getName(), parentPropertyStore);
		children.dependsOn(model.refreshCount());
		addDisposableChild(children);
		addElementNotifier(children);

		baseRunState.dependsOn(model.refreshCount());
		addDisposableChild(this.childFactory);
		this.parent = parent;

		app.dependsOn(model.getRunTarget().getClientExp());
		app.dependsOn(getBootDashModel().refreshCount());
		addDisposableChild(baseRunState);
		addDisposableChild(jmxRunStateTracker);
		addElementNotifier(getRunStateExp());
		addElementNotifier(refreshTracker.refreshState);
		addDisposableChild(livePorts);
		addElementNotifier(livePorts);

		model.addElementStateListener(this);

		debugPort.onChange(this, (e, v) -> {
			RemoteJavaLaunchUtil.synchronizeWith(this);
		});

		remoteDevtoolsUrl.onChange(this, (e, v) -> {
			DevtoolsUtil.launchClientIfNeeded(this);
		});

		refreshTracker.refreshState.addListener((e, v) -> {
			RefreshState s = e.getValue();
			if (s != null && !s.isLoading()) {
				app.refresh();
			}
		});

		onDispose(d -> {
			model.removeElementStateListener(this);
		});

		getRunStateExp().onChange(this, (e, v) -> connectOrDisconnectConsoleIfNeeded());

		IConsoleListener consoleListener = new IConsoleListener() {

			@Override
			public void consolesAdded(IConsole[] consoles) {
				connectOrDisconnectConsoleIfNeeded();
			}

			@Override
			public void consolesRemoved(IConsole[] consoles) {
				connectOrDisconnectConsoleIfNeeded();
			}

		};

		IConsoleManager consoleManager = injections().getBean(CloudAppLogManager.class).getConsoleManager();

		consoleManager.addConsoleListener(consoleListener);

		addDisposableChild(() -> consoleManager.removeConsoleListener(consoleListener));
	}

	private boolean firstConsoleConnection = true;
	private void connectOrDisconnectConsoleIfNeeded() {
		App app = this.app.getValue();
		CloudAppLogManager appLogManager = injections().getBean(CloudAppLogManager.class);
		LogConnection logConnection = this.logConnection.getVar().getValue();
		boolean hasConnection = logConnection != null && !logConnection.isClosed();
		boolean connectConsole = app instanceof LogSource && appLogManager.hasConsole(app);

		if (hasConnection != connectConsole) {
			if (connectConsole) {
				AppConsole console = appLogManager.getConsole(app);
				this.logConnection.setValue(((LogSource)app).connectLog(console, firstConsoleConnection));
				firstConsoleConnection = false;
			} else {
				this.logConnection.setValue(null);
			}
		}

	}

	@Override
	public GenericRemoteBootDashModel<?, ?> getBootDashModel() {
		return (GenericRemoteBootDashModel<?, ?>) super.getBootDashModel();
	}

	@Override
	public String getName() {
		return super.delegate;
	}

	public App getAppData() {
		return this.app.getValue();
	}

	public void setAppData(App appData) {
		this.app.setValue(appData);
	}

	@Override
	public RunState getRunState() {
		return getRunStateExp().getValue();
	}

	public ImmutableSet<String> getActiveProfiles() {
		return ImmutableSet.of();
	}

	private LiveExpression<RunState> getRunStateExp() {
		return jmxRunStateTracker.augmentedRunState;
	}

	@Override
	public RefreshStateTracker getRefreshTracker() {
		return refreshTracker;
	}

	@Override
	public EnumSet<RunState> supportedGoalStates() {
		App app = this.app.getValue();
		return app != null ? app.supportedGoalStates() : EnumSet.noneOf(RunState.class);
	}

	@Override
	public IProject getProject() {
		App data = this.app.getValue();
		if (data instanceof ProjectRelatable) {
			return ((ProjectRelatable) data).getProject();
		}
		return null;
	}

	@Override
	public int getLivePort() {
		ImmutableSet<Integer> ports = getLivePorts();
		if (ports != null && !ports.isEmpty()) {
			return ports.iterator().next();
		}
		return 0;
	}

	@Override
	public ImmutableSet<Integer> getLivePorts() {
		return livePorts.getValue();
	}

	@Override
	public String getLiveHost() {
		return "localhost";
	}

	@Override
	public ILaunchConfiguration getActiveConfig() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void stop() throws Exception {
		App a = this.app.getValue();
		a.setGoalState(RunState.INACTIVE);
	}

	@Override
	public void restart(RunState runingOrDebugging, UserInteractions ui) throws Exception {
		App a = this.app.getValue();
		a.restart(runingOrDebugging);
	}

	@Override
	public void openConfig(UserInteractions ui) {
		// TODO Auto-generated method stub

	}

	@Override
	public int getActualInstances() {
		return actualInstanceCounts.getValue();
	}

	@Override
	public int getDesiredInstances() {
		App data = getAppData();
		if (data instanceof DesiredInstanceCount) {
			return ((DesiredInstanceCount)data).getDesiredInstances();
		}
		return -1;
	}

	@Override
	public Object getParent() {
		return parent;
	}

	@Override
	public PropertyStoreApi getPersistentProperties() {
		return PropertyStores.createApi(backingStore);
	}

	@Override
	public ObservableSet<BootDashElement> getChildren() {
		return children;
	}

	@Override
	public boolean canDelete() {
		App app = this.app.getValue();
		if (app instanceof Deletable) {
			return ((Deletable) app).canDelete();
		}
		return false;
	}

	@Override
	public void delete() throws Exception {
		App app = this.app.getValue();
		if (app instanceof Deletable) {
			((Deletable) app).delete();
		}
	}

	@Override
	public StyledString getStyledName(Stylers stylers) {
		App app = this.app.getValue();
		if (app instanceof Styleable) {
			return ((Styleable) app).getStyledName(stylers);
		}
		return new StyledString(getName());
	}

	@Override
	public void stateChanged(BootDashElement e) {
		this.livePorts.refresh();
		for (LiveExpression<?> sumary : summaries) {
			sumary.refresh();
		}
		//We do the next thing asynchronously because stateChanged events can be fired on the ui
		// thread and the 'synchronizeWith' can a) take a while and b) cause deadlock.
//		refreshTracker.runAsync("Synchronize remote java launch", () -> {
//			System.out.println("refresh java debug launch");
//			RemoteJavaLaunchUtil.synchronizeWith(this);
//		});
//		refreshTracker.runAsync("Synchronize devtools client launch", () -> {
//			DevtoolsUtil.launchClientIfNeeded(this);
//		});
	}

	/**
	 * Summarise all the debug ports either defined by this node or it's children.
	 */
	public ImmutableSet<Integer> getDebugPortSummary() {
		ImmutableSet.Builder<Integer> ports = ImmutableSet.builder();
		collectDebugPorts(ports);
		return ports.build();
	}

	private void collectDebugPorts(Builder<Integer> ports) {
		int port = getDebugPort();
		if (port > 0) {
			ports.add(port);
		}
		for (BootDashElement c : children.getValues()) {
			if (c instanceof GenericRemoteAppElement) {
				((GenericRemoteAppElement)c).collectDebugPorts(ports);
			}
		}
	}

	@Override
	public ImmutableSet<ILaunchConfiguration> getLaunchConfigs() {
		return RemoteJavaLaunchUtil.getLaunchConfigs(this);
	}

	public int getDebugPort() {
		return debugPort.getValue();
	}

	@Override
	public String getJmxUrl() {
		App data = getAppData();
		if (data != null && data instanceof JmxConnectable) {
			return ((JmxConnectable)data).getJmxUrl();
		}
		return null;
	}

	public boolean canWriteToConsole() {
		App data = getAppData();
		return data instanceof LogSource;
	}

	private LiveExpression<Set<String>> actuatorUrls = sumarizeFromChildren(new AppDataSummarizer<Set<String>>() {

		@Override
		public Set<String> getHere(GenericRemoteAppElement app) {
			String url = app.getActuatorUrlHere();
			return url == null ? ImmutableSet.of() : ImmutableSet.of(url);
		}

		@Override
		public Set<String> merge(Set<String> d1, Set<String> d2) {
			ImmutableSet.Builder<String> builder = ImmutableSet.builder();
			if (d1 != null) {
				builder.addAll(d1);
			}
			if (d2 != null) {
				builder.addAll(d2);
			}
			return builder.build();
		}

		@Override
		public Set<String> getSummary(GenericRemoteAppElement element) {
			return element.getActuatorUrls().getValue();
		}

	});
	public LiveExpression<Set<String>> getActuatorUrls() {
		return actuatorUrls;
	}

	String getActuatorUrlHere() {
		RunState rs = getRunState();
		if (rs.isActive()) {
			return getJmxUrl();
		}
		return null;
	}

	private LiveExpression<Failable<ImmutableList<RequestMapping>>> liveRequestMappings;
	private LiveExpression<Failable<LiveEnvModel>> liveEnv;
	private LiveExpression<Failable<LiveBeansModel>> liveBeans;
	private LiveExpression<Failable<ImmutableSet<String>>> liveProfiles;

	@Override
	public Failable<ImmutableList<RequestMapping>> getLiveRequestMappings() {
		synchronized (this) {
			if (liveRequestMappings == null) {
				final LiveExpression<Set<String>> actuatorUrls = getActuatorUrls();
				liveRequestMappings = new AsyncLiveExpression<>(Failable.error(MissingLiveInfoMessages.NOT_YET_COMPUTED), "Fetch request mappings for '"+getStyledName(null).getString()+"'") {
					@Override
					protected Failable<ImmutableList<RequestMapping>> compute() {
						Set<String> targets = actuatorUrls.getValue();
						if (targets != null && !targets.isEmpty()) {
							if (targets.size() == 1) {
								String target = targets.iterator().next();
								ActuatorClient client = JMXActuatorClient.forUrl(getTypeLookup(), () -> target);
								List<RequestMapping> list = client.getRequestMappings();
								if (list != null) {
									return Failable.of(ImmutableList.copyOf(client.getRequestMappings()));
								}
							} else {
								return Failable.error(buffer -> buffer.p("More than one child can provide live data. Please select one."));
							}
						}
						return Failable.error(getBootDashModel().getRunTarget().getType().getMissingLiveInfoMessages().getMissingInfoMessage(getStyledName(null).getString(), "mappings"));
					}

				};
				liveRequestMappings.dependsOn(actuatorUrls);
				addElementState(liveRequestMappings);
				addDisposableChild(liveRequestMappings);
			}
		}
		return liveRequestMappings.getValue();
	}

	@Override
	public Failable<ImmutableSet<String>> getLiveProfiles() {
		synchronized (this) {
			if (liveProfiles == null) {
				final LiveExpression<Set<String>> actuatorUrls = getActuatorUrls();
				liveProfiles = new AsyncLiveExpression<>(Failable.error(MissingLiveInfoMessages.NOT_YET_COMPUTED), "Fetch active profiles for '"+getStyledName(null).getString()+"'") {
					@Override
					protected Failable<ImmutableSet<String>> compute() {
						Set<String> targets = actuatorUrls.getValue();
						if (targets != null && !targets.isEmpty()) {
							if (targets.size() == 1) {
								String target = targets.iterator().next();
								try {
									String[] o = BootLsCommandUtils.executeCommand(TypeToken.get(String[].class), "sts/livedata/get", Map.of(
											"endpoint", "profiles",
											"processKey", target
									)).get().orElse(new String[0]);
									return Failable.of(ImmutableSet.copyOf(o));
								} catch (Exception e) {
									return Failable.error(getBootDashModel().getRunTarget().getType().getMissingLiveInfoMessages().getMissingInfoMessage(getStyledName(null).getString(), "profiles"));
								}
							} else {
								return Failable.error(buffer -> buffer.p("More than one child can provide live data. Please select one."));
							}
						}
						return Failable.error(getBootDashModel().getRunTarget().getType().getMissingLiveInfoMessages().getMissingInfoMessage(getStyledName(null).getString(), "profiles"));
					}

				};
				liveProfiles.dependsOn(actuatorUrls);
				addElementState(liveProfiles);
				addDisposableChild(liveProfiles);
			}
		}
		return liveProfiles.getValue();
	}

	@Override
	public Failable<LiveEnvModel> getLiveEnv() {
		synchronized (this) {
			if (liveEnv == null) {
				final LiveExpression<Set<String>> actuatorUrls = getActuatorUrls();
				liveEnv = new AsyncLiveExpression<>(Failable.error(MissingLiveInfoMessages.NOT_YET_COMPUTED), "Fetch env for '"+getStyledName(null).getString()+"'") {
					@Override
					protected Failable<LiveEnvModel> compute() {
						Set<String> targets = actuatorUrls.getValue();
						if (targets != null && !targets.isEmpty()) {
							if (targets.size() == 1) {
								String target = targets.iterator().next();
								ActuatorClient client = JMXActuatorClient.forUrl(getTypeLookup(), () -> target);
								LiveEnvModel env = client.getEnv();
								if (env != null) {
									return Failable.of(env);
								}
							} else {
								return Failable.error(buffer -> buffer.p("More than one child can provide live data. Please select one."));
							}
						}
						return Failable.error(getBootDashModel().getRunTarget().getType().getMissingLiveInfoMessages().getMissingInfoMessage(getStyledName(null).getString(), "env"));
					}

				};
				liveEnv.dependsOn(actuatorUrls);
				addElementState(liveEnv);
				addDisposableChild(liveEnv);
			}
		}
		return liveEnv.getValue();
	}

	protected final SimpleDIContext injections() {
		return getBootDashModel().getViewModel().getContext().injections;
	}

	@Override
	public Failable<LiveBeansModel> getLiveBeans() {
		synchronized (this) {
			if (liveBeans == null) {
				LiveExpression<Set<String>> actuatorUrls = getActuatorUrls();
				liveBeans = new AsyncLiveExpression<>(Failable.error(MissingLiveInfoMessages.NOT_YET_COMPUTED), "Fetch beans for '"+getStyledName(null).getString()+"'") {
					@Override
					protected Failable<LiveBeansModel> compute() {
						Set<String> targets = actuatorUrls.getValue();
						if (targets != null && !targets.isEmpty()) {
							if (targets.size() == 1) {
								String target = targets.iterator().next();
								ActuatorClient client = JMXActuatorClient.forUrl(getTypeLookup(), () -> target);
								LiveBeansModel beans = client.getBeans();
								if (beans != null) {
									return Failable.of(beans);
								}
							} else {
								return Failable.error(buffer -> buffer.p("More than one child can provide live data. Please select one."));
							}
						}
						return Failable.error(getBootDashModel().getRunTarget().getType().getMissingLiveInfoMessages().getMissingInfoMessage(getStyledName(null).getString(), "beans"));
					}

				};
				liveBeans.dependsOn(actuatorUrls);
				addElementState(liveBeans);
				addDisposableChild(liveBeans);
			}
		}
		return liveBeans.getValue();
	}

	public CompletableFuture<Void> enableDevtools(boolean enable) {
		if (enable) {
			return refreshTracker.runAsync("Enable DevTools Support for application '" + getStyledName(null).getString() + "'", () -> {
				App app = getAppData();
				if (app instanceof SystemPropertySupport) {
					SystemPropertySupport sysprops = (SystemPropertySupport) app;
					IProject project = getProject();
					if (project != null) {
						sysprops.setSystemProperty(DevtoolsUtil.REMOTE_SECRET_PROP, DevtoolsUtil.getSecret(project));
					}
				}
			});
		} else {
			return refreshTracker.runAsync("Disable DevTools Support for application '" + getStyledName(null).getString() + "'", () -> {
				App app = getAppData();
				if (app instanceof SystemPropertySupport) {
					SystemPropertySupport sysprops = (SystemPropertySupport) app;
					sysprops.setSystemProperty(DevtoolsUtil.REMOTE_SECRET_PROP, null);
				}
			});
		}
	}

	private static class LaunchTerminator implements DisposeListener, ValueListener<RunState> {

		private ILaunch launch;
		private GenericRemoteAppElement owner;

		public LaunchTerminator(ILaunch launch, GenericRemoteAppElement owner) {
			this.launch = launch;
			this.owner = owner;
			owner.getRunStateExp().addListener(this);
			owner.onDispose(d -> {
				terminate();
			});

		}

		@Override
		public void gotValue(LiveExpression<RunState> exp, RunState value) {
			if (exp.getValue() == RunState.INACTIVE) {
				terminate();
			}
 		}

		@Override
		public void disposed(Disposable disposed) {
			terminate();
		}

		void terminate() {
			ILaunch launch;
			synchronized (this) {
				launch = this.launch;
				this.launch = null;
			}
			if (launch != null) {
				try {
					launch.terminate();
				} catch (Exception e) {
					Log.log(e);
				}
				owner.getRunStateExp().removeListener(this);
			}
		}
	}

	public void restartRemoteDevtoolsClient() {
		refreshTracker.runAsync("(Re)starting remote devtools client", () -> {
			IProject project = getProject();
			if (project != null) {
				DevtoolsUtil.disconnectDevtoolsClientsFor(this);
				ILaunch launch = DevtoolsUtil.launchDevtools(this, DevtoolsUtil.getSecret(project), ILaunchManager.RUN_MODE, new NullProgressMonitor());
				new LaunchTerminator(launch, this);
			}
		});
	}

	public UserInteractions ui() {
		return getBootDashModel().ui();
	}

	@Override
	public ImageDescriptor getRunStateImageDecoration() {
		if (this.getTarget() != null && this.getRunState() == RunState.RUNNING) {
			if (DevtoolsUtil.isDevClientAttached(this, ILaunchManager.RUN_MODE)) {
				return BootDashActivator.getDefault().getImageRegistry().getDescriptor(BootDashActivator.DT_ICON_ID);
			}
		}
		return null;
	}

	@Override
	public boolean isDevtoolsGreenColor() {
		App data = getAppData();
		if (data instanceof DevtoolsConnectable) {
			return ((DevtoolsConnectable) data).getDevtoolsSecret() != null;
		}
		return false;
	}

	@Override
	public String getConsoleDisplayName() {
		App data = app.getValue();
		return data != null ? data.getConsoleDisplayName() : null;
	}

	@Override
	public boolean matchesLiveProcessCommand(ExecuteCommandAction action) {
		App data = getAppData();
		boolean selfMatch = data.getName() != null&& data.getName().equals(action.getProcessId());
		return selfMatch || childMatch(action);
	}

	@Override
	public boolean hasClasspathProperty(ClasspathPropertyTester tester) {
		App data = getAppData();
		if (data instanceof ClasspathBearing) {
			return ((ClasspathBearing) data).hasClasspathProperty(tester);
		}
		return projectHasClasspathProperty(tester);
	}

	private boolean childMatch(ExecuteCommandAction action) {
		for (BootDashElement child : this.getChildren().getValues()) {
			if (
					child instanceof LiveDataCapableElement &&
					((LiveDataCapableElement) child).matchesLiveProcessCommand(action)
			) {
				return true;
			}
		}
		return false;
	}

}
