/*******************************************************************************
 * Copyright (c) 2020, 2022 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.docker.runtarget;

import static org.eclipse.ui.plugin.AbstractUIPlugin.imageDescriptorFromPlugin;
import static org.springframework.ide.eclipse.boot.dash.docker.runtarget.DockerRunTargetType.PLUGIN_ID;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.StyledString;
import org.springframework.ide.eclipse.boot.dash.api.ActualInstanceCount;
import org.springframework.ide.eclipse.boot.dash.api.App;
import org.springframework.ide.eclipse.boot.dash.api.AppConsole;
import org.springframework.ide.eclipse.boot.dash.api.AppConsoleProvider;
import org.springframework.ide.eclipse.boot.dash.api.AppContext;
import org.springframework.ide.eclipse.boot.dash.api.DebuggableApp;
import org.springframework.ide.eclipse.boot.dash.api.Deletable;
import org.springframework.ide.eclipse.boot.dash.api.DevtoolsConnectable;
import org.springframework.ide.eclipse.boot.dash.api.JmxConnectable;
import org.springframework.ide.eclipse.boot.dash.api.LogConnection;
import org.springframework.ide.eclipse.boot.dash.api.LogSource;
import org.springframework.ide.eclipse.boot.dash.api.PortConnectable;
import org.springframework.ide.eclipse.boot.dash.api.ProjectRelatable;
import org.springframework.ide.eclipse.boot.dash.api.RunStateIconProvider;
import org.springframework.ide.eclipse.boot.dash.api.RunStateProvider;
import org.springframework.ide.eclipse.boot.dash.api.Styleable;
import org.springframework.ide.eclipse.boot.dash.console.LogType;
import org.springframework.ide.eclipse.boot.dash.devtools.DevtoolsUtil;
import org.springframework.ide.eclipse.boot.dash.docker.jmx.JmxSupport;
import org.springframework.ide.eclipse.boot.dash.docker.util.Ownable;
import org.springframework.ide.eclipse.boot.dash.model.ClasspathPropertyTester;
import org.springframework.ide.eclipse.boot.dash.model.RunState;
import org.springframework.ide.eclipse.boot.dash.model.remote.RefreshStateTracker;
import org.springframework.ide.eclipse.boot.util.RetryUtil;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlPath;
import org.springframework.ide.eclipse.editor.support.yaml.path.YamlTraversal;
import org.springsource.ide.eclipse.commons.core.util.StringUtil;
import org.springsource.ide.eclipse.commons.frameworks.core.util.StringUtils;
import org.springsource.ide.eclipse.commons.livexp.ui.Stylers;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class DockerContainer implements App, RunStateProvider, JmxConnectable, Styleable, PortConnectable, 
	Deletable, ActualInstanceCount, DebuggableApp, ProjectRelatable, DevtoolsConnectable, LogSource, RunStateIconProvider
{

	public static final Duration WAIT_BEFORE_KILLING = Duration.ofSeconds(10);
	private static final boolean DEBUG = false;
	private final Container container;
	private final DockerRunTarget target;
	public final CompletableFuture<RefreshStateTracker> refreshTracker = new CompletableFuture<>();
	
	private static Map<RunState, ImageDescriptor> RUNSTATE_ICONS = null;
	private DockerApp app;
	private AppContext context;

	public DockerContainer(DockerRunTarget target, DockerApp app, Container container) {
		this.target = target;
		this.app = app;
		this.container = container;
	}

	@Override
	public String getName() {
		return container.getId();
	}

	@Override
	public RunState fetchRunState() {
		return getRunState(container);
	}

	public static RunState getRunState(Container container) {
		String state = container.getState();
		if ("running".equals(state)) {
			return (container.getLabels().get(DockerApp.DEBUG_PORT) != null) 
					? RunState.DEBUGGING
					: RunState.RUNNING;
		} else if ("exited".equals(state)) {
			return RunState.INACTIVE;
		} else if ("paused".equals(state)) {
			return RunState.PAUSED;
		} else if ("created".equals(state)) {
			return RunState.STARTING;
		}
		return RunState.UNKNOWN;
	}

	@Override
	public DockerRunTarget getTarget() {
		return this.target;
	}

	@Override
	public String getJmxUrl() {
		try {
			String port = container.getLabels().get(DockerApp.JMX_PORT);
			if (port != null) {
				return new JmxSupport(Integer.valueOf(port)).getJmxUrl();
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}
	
	@Override
	public String toString() {
		return "DockerContainer("+container.getId()+")";
	}


	@Override
	public StyledString getStyledName(Stylers stylers) {
		StyledString styledString = new StyledString();
		if (container.getNames() != null && container.getNames().length > 0) {
			styledString = styledString.append(StringUtil.removePrefix(container.getNames()[0], "/"));
		}
		styledString = styledString.append(" (" +getShortHash()+")", StyledString.QUALIFIER_STYLER);
		return styledString;
	}

	private String getShortHash() {
		String id = container.getId();
		if (id.length() > 12) {
			id = id.substring(0, 12);
		}		
		return id;
	}

	@Override
	public Set<Integer> getPorts() {
		ImmutableSet.Builder<Integer> livePorts = ImmutableSet.builder();
		String portVal = container.getLabels().get(DockerApp.APP_LOCAL_PORT);
		if (portVal != null) {
			livePorts.add(Integer.parseInt(portVal));
		}
		return livePorts.build();
	}
	
	@Override
	public EnumSet<RunState> supportedGoalStates() {
		Set<RunState> supported = new HashSet<>();
		supported.add(RunState.INACTIVE);
		if (container.getLabels().get(DockerApp.DEBUG_PORT) != null) {
			supported.add(RunState.DEBUGGING);
		} else {
			supported.add(RunState.RUNNING);
		}
		supported.add(RunState.PAUSED);
		return EnumSet.copyOf(supported);
	}
	
	@Override
	public void setGoalState(RunState goal) {
		RunState currentState = fetchRunState();
		if (currentState != goal) {

			DockerRunTarget dockerTarget = getTarget();
			DockerClient client = dockerTarget.getClient();
			if (client != null) {
				try {
					RefreshStateTracker rt = this.refreshTracker.get();
					
					if (goal.isActive()) {
						if (currentState == RunState.PAUSED) {
							rt.run("Resuming " + getStyledName(null).getString(), () -> {
								client.unpauseContainerCmd(container.getId()).exec();
								RetryUtil.until(100, 1000, runstate -> runstate.isActive(), this::fetchRunState);
							});
						} else {
							rt.run("Starting " + getStyledName(null).getString(), () -> {
								client.startContainerCmd(container.getId()).exec();
								RetryUtil.until(100, 1000, runstate -> runstate.equals(RunState.RUNNING), this::fetchRunState);
							});
						}
					} else if (goal == RunState.INACTIVE) {
						rt.run("Stopping " + getShortHash(), () -> {
							debug("Stopping  ");
							client.stopContainerCmd(container.getId()).withTimeout((int) WAIT_BEFORE_KILLING.getSeconds()).exec();
							debug("Waiting for stopped state...");
							RetryUtil.until(100, WAIT_BEFORE_KILLING.toMillis(),
									runstate -> runstate.equals(RunState.INACTIVE), this::fetchRunState);
							debug("Stopped  ");
						});
					} else if (goal == RunState.PAUSED) {
						rt.run("Suspending "+getStyledName(null).getString(), () -> {
							client.pauseContainerCmd(getName()).exec();
						});
						RetryUtil.until(100, WAIT_BEFORE_KILLING.toMillis(),
								runstate -> runstate.equals(RunState.PAUSED), this::fetchRunState);
					}
				} catch (Exception e) {
					Log.log(e);
				}
			}
		}
	}
		
	private void debug(String message) {
		if (DEBUG) {
			System.out.println("DockerContainer " + getShortHash() + ": " + message);
		}
	}
	
	/**
	 * For debugging purposes, logs to Eclipse Error log if Debugging is enabled.
	 * @param message to log
	 */
	private static void logOnDebug(String message) {
		if (DEBUG) {
			Log.info("Debugging: " + message);
		}
	}

	@Override
	public void restart(RunState runingOrDebugging) {
		DockerRunTarget dockerTarget = getTarget();
		DockerClient client = dockerTarget.getClient();
		if (client != null) {
			try {
				AppConsole console = target.injections().getBean(AppConsoleProvider.class).getConsole(this);
				console.show();
				
				RefreshStateTracker rt = this.refreshTracker.get();
				rt.run("Starting " + getShortHash(), () -> {
					client.restartContainerCmd(container.getId()).exec();					
					RetryUtil.until(100, 1000, runstate -> runstate.equals(RunState.RUNNING), this::fetchRunState);
				});
			} catch (Exception e) {
				Log.log(e);
			}
		}
		
	}
	
	@Override
	public void setContext(AppContext context) {
		this.refreshTracker.complete(context.getRefreshTracker());
		this.context = context;
	}

	@Override
	public void delete() throws Exception {
		DockerClient client = getTarget().getClient();
		if (client != null) {
			RefreshStateTracker rt = this.refreshTracker.get();
			rt.run("Deleting " + getShortHash(), () -> {
				debug("Deleting");
				client.removeContainerCmd(container.getId()).withForce(true).exec();
				debug("Waiting for Deleting");

				RetryUtil.until(100, WAIT_BEFORE_KILLING.toMillis(),
						exception -> exception instanceof NotFoundException, () -> {
							try {
								client.inspectContainerCmd(container.getId()).exec();
							} catch (Exception e) {
								return e;
							}
							return null;
						});
				debug("Deleted");
			});
		}
	}

	@Override
	public int getActualInstances() {
		return fetchRunState().isActive() ? 1 : 0;
	}

	@Override
	public int getDebugPort() {
		try {
			if (fetchRunState().isActive()) {
				String portStr = container.getLabels().get(DockerApp.DEBUG_PORT);
				if (portStr != null) {
					int port = Integer.valueOf(portStr);
					if (port > 0) {
						return port;
					}
				}
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return -1;
	}

	@Override
	public IProject getProject() {
		try {
			String projectName = container.getLabels().get(DockerApp.APP_NAME);
			if (projectName != null) {
				return ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return null;
	}

	@Override
	public String getDevtoolsSecret() {
		Map<String, String> sysprops = getSystemProps(container);
		return sysprops.getOrDefault(DevtoolsUtil.REMOTE_SECRET_PROP, null);
	}

	private static final YamlTraversal dependencyNamePath = YamlPath.EMPTY
			.thenValAt("bom")
			.thenAnyChild()
			.thenValAt("metadata")
			.thenValAt("dependencies")
			.thenAnyChild()
			.thenValAt("name");
	
	private static final boolean USE_DEDICATED_CLIENT = false;
	

	@Override
	public boolean hasClasspathProperty(ClasspathPropertyTester tester) {
		if (context != null) {
			DockerImage image = context.getParent(DockerImage.class);
			return image.hasClasspathProperty(tester);
		}
		return false;
	}

	// for debugging... keep in comments for now
//	private void findNode(Object json, Predicate<String> test, String path) {
//		if (json instanceof String) {
//			if (test.apply((String) json)) {
//				System.out.println(path+" = "+json);
//			}
//		} else if (json instanceof Map) {
//			Map<String, ?> map = (Map<String, ?>) json; 
//			for (Entry<String, ?> me : map.entrySet()) {
//				findNode(me.getValue(), test, path+"."+escape(me.getKey()));
//			}
//		} else if (json instanceof List) {
//			List<?> list = (List<?>) json;
//			int index = 0;
//			for (Object object : list) {
//				findNode(object, test, path+"["+index+"]");
//				index++;
//			}
//		}
//		
//	}
//
//	private String escape(String path) {
//		if (path.contains(".")) {
//			return "["+path+"]";
//		}
//		return path;
//	}

	public Map<String,String> getSystemProps() {
		return container != null ? getSystemProps(container) : null;
	}

	@SuppressWarnings("unchecked")
	public static Map<String,String> getSystemProps(Container c) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			String sysprops = c.getLabels().get(DockerApp.SYSTEM_PROPS);
			if (StringUtils.hasText(sysprops)) {
				return mapper.readValue(sysprops, Map.class);
			}
		} catch (Exception e) {
			Log.log(e);
		}
		return ImmutableMap.of();
	}

	@Override
	public LogConnection connectLog(AppConsole logConsole, boolean includeHistory) {
		return connectLog(target, container.getId(), logConsole, includeHistory);
	}
	
	/**
	 * For debugging purposes. Keep track of number of active log handlers. So we
	 * can log this info and check whether log handlers are closed properly.
	 */
	private static AtomicInteger activeLogHandlers = new AtomicInteger();
	
	/**
	 * For debugging purposes. Keep track of number of calls to start for which there
	 * isn't yet a corresponding 'close' call.
	 */
	private static AtomicInteger startedLogHandlers = new AtomicInteger();

	
	private static class LogHandler implements ResultCallback<Frame>{

		private AtomicBoolean isClosed = new AtomicBoolean();

		private OutputStream consoleOut;
		private OutputStream consoleErr;
		
		LogConnection connection = new LogConnection() {
			
			@Override
			public void dispose() {
				LogHandler.this.close();
			}
			
			@Override
			public boolean isClosed() {
				return isClosed.get();
			}
		};

		private final CompletableFuture<Closeable> closeable = new CompletableFuture<Closeable>();

		private Ownable<DockerClient> client;

		public LogHandler(Ownable<DockerClient> client, AppConsole console) {
			this.client = client;
			consoleOut = console.getOutputStream(LogType.APP_OUT);
			consoleErr = console.getOutputStream(LogType.APP_OUT);
			logOnDebug("Creating log handler. Now active: "+activeLogHandlers.incrementAndGet());
		}


		@Override
		public void close() {
			if (isClosed.compareAndSet(false, true)) {
				closeable.thenAccept(c -> {
					logOnDebug("Closing ResultCallback. Now active: "+startedLogHandlers.decrementAndGet());
					try {
						c.close();
					} catch (IOException e) {
					}
				});
				try {
					consoleOut.close();
				} catch (IOException e) {
				}
				try {
					consoleErr.close();
				} catch (IOException e) {
				}
				try {
					if (client.isOwned) {
						client.ref.close();
					}
				} catch (IOException e) {
				}
				logOnDebug("Closing log handler. Now active: "+activeLogHandlers.decrementAndGet());
			}
		}

		@Override
		public void onStart(Closeable closeable) {
			logOnDebug("ResultCallback.onStart. Now active: "+startedLogHandlers.incrementAndGet());
			this.closeable.complete(closeable);
		}

		@Override
		public void onNext(Frame logMsg) {
			try {
				StreamType tp = logMsg.getStreamType();
				if (tp == StreamType.STDERR) {
					consoleErr.write(logMsg.getPayload());
				} else if (tp == StreamType.STDOUT) {
					consoleOut.write(logMsg.getPayload());
				} else {
					Log.warn("Unknown docker log frame type dropped: "+tp);
				}
			} catch (Exception e) {
				Log.log(e);
			}
		}

		@Override
		public void onError(Throwable e) {
			try {
				consoleErr.write((ExceptionUtil.getMessage(e)+"\n").getBytes("UTF8"));
			} catch (Exception e1) {
				Log.log(e1);
			}
			Log.log(e);
			this.close();
		}

		@Override
		public void onComplete() {
			this.close();
		}
	}
	
	public static LogConnection connectLog(DockerRunTarget target, String containerId, AppConsole console, boolean includeHistory) {
		//Use a dedicated client for log streaming because java docker client will eventually run out of connections in connection pool
		//otherwise. 
		//See: 
		//  - https://www.pivotaltracker.com/n/projects/1346850
		//  - https://github.com/docker-java/docker-java/issues/1466
		Ownable<DockerClient> client = USE_DEDICATED_CLIENT 
				? Ownable.owned(target.getDedicatedClientInstance()) 
				: Ownable.borrowed(target.getClient());
		if (client != null) {
			LogContainerCmd cmd = client.ref.logContainerCmd(containerId)
					.withStdOut(true).withStdErr(true).withFollowStream(true);
			
			if (!includeHistory) {
				cmd = cmd.withSince((int)Instant.now().getEpochSecond());
			}
			
			LogHandler logHandler = cmd.exec(new LogHandler(client, console));
			return logHandler.connection;
		}
		return null;
	}

	@Override
	public ImageDescriptor getRunStateIcon(RunState runState) {
		try {
			if (RUNSTATE_ICONS == null) {
				RUNSTATE_ICONS = ImmutableMap.of(
						RunState.RUNNING, imageDescriptorFromPlugin(PLUGIN_ID, "/icons/container_started.png"),
						RunState.INACTIVE, imageDescriptorFromPlugin(PLUGIN_ID, "/icons/container_stopped.png"),
						RunState.DEBUGGING, imageDescriptorFromPlugin(PLUGIN_ID, "/icons/container_debugging.png"),
						RunState.PAUSED, imageDescriptorFromPlugin(PLUGIN_ID, "/icons/container_paused.png")
				);
			}
		} catch (Exception e) {
			Log.log(e);
		}
		if (RUNSTATE_ICONS != null) {
			return RUNSTATE_ICONS.get(runState);
		}
		return null;
	}
	
	@Override
	public String getConsoleDisplayName() {
		return app.getName() + " - in container "+getStyledName(null).getString()+" @ "+getTarget().getName();
	}

}
