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

import static org.springframework.ide.eclipse.boot.dash.model.RunState.INACTIVE;
import static org.springframework.ide.eclipse.boot.dash.model.RunState.RUNNING;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.dash.views.sections.BootDashColumn;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.cli.CloudCliServiceLaunchConfigurationDelegate;
import org.springframework.ide.eclipse.boot.launch.util.BootLaunchUtils;
import org.springframework.ide.eclipse.boot.util.RetryUtil;
import org.springsource.ide.eclipse.commons.core.pstore.IPropertyStore;
import org.springsource.ide.eclipse.commons.core.pstore.PropertyStores;
import org.springsource.ide.eclipse.commons.core.util.ProcessUtils;
import org.springsource.ide.eclipse.commons.livexp.core.AsyncLiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;

/**
 * Spring Cloud CLI local service boot dash element implementation
 *
 * @author Alex Boyko
 *
 */
public class LocalCloudServiceDashElement extends AbstractLaunchConfigurationsDashElement<String> {

	private static final EnumSet<RunState> LOCAL_CLOUD_SERVICE_RUN_GOAL_STATES = EnumSet.of(INACTIVE, RUNNING);

	private static final BootDashColumn[] COLUMNS = {BootDashColumn.NAME, BootDashColumn.LIVE_PORT, BootDashColumn.RUN_STATE_ICN, BootDashColumn.TAGS};

	private static final LoadingCache<String, ILaunchConfigurationWorkingCopy> LAUNCH_CONFIG_CACHE = CacheBuilder.newBuilder().build(new CacheLoader<String, ILaunchConfigurationWorkingCopy>() {
		@Override
		public ILaunchConfigurationWorkingCopy load(String key) throws Exception {
			return CloudCliServiceLaunchConfigurationDelegate.createLaunchConfig(key);
		}
	});

	public LocalCloudServiceDashElement(LocalBootDashModel bootDashModel, String id) {
		super(bootDashModel, id);
	}

	@Override
	public IProject getProject() {
		return null;
	}

	public ImmutableSet<ILaunch> getLaunches() {
		List<ILaunch> launches = new ArrayList<>();
		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		ILaunchConfigurationType type = launchManager.getLaunchConfigurationType(CloudCliServiceLaunchConfigurationDelegate.TYPE_ID);
		for (ILaunch launch : launchManager.getLaunches()) {
			ILaunchConfiguration configuration = launch.getLaunchConfiguration();
			try {
				if (configuration != null && configuration.getType() == type && delegate.equals(configuration.getAttribute(CloudCliServiceLaunchConfigurationDelegate.ATTR_CLOUD_SERVICE_ID, (String) null))) {
					launches.add(launch);
				}
			} catch (CoreException e) {
				Log.log(e);
			}
		}
		return ImmutableSet.copyOf(launches);
	}

	@Override
	public void openConfig(UserInteractions ui) {
	}

	@Override
	public int getActualInstances() {
		return 0;
	}

	@Override
	public int getDesiredInstances() {
		return 0;
	}

	@Override
	public Object getParent() {
		return getBootDashModel();
	}

	@Override
	public String getName() {
		return delegate;
	}

	@Override
	public BootDashColumn[] getColumns() {
		return COLUMNS;
	}

	@Override
	public LocalBootDashModel getBootDashModel() {
		return (LocalBootDashModel) super.getBootDashModel();
	}

	public String getId() {
		return delegate;
	}

	@Override
	protected IPropertyStore createPropertyStore() {
		return PropertyStores.createSubStore("S-"+delegate, getBootDashModel().getModelStore());
	}

	@Override
	public ImmutableSet<ILaunchConfiguration> getLaunchConfigs() {
		try {
			return ImmutableSet.of(LAUNCH_CONFIG_CACHE.get(delegate));
		} catch (ExecutionException e) {
			Log.log(e);
			return ImmutableSet.of();
		}
	}

	@Override
	public boolean canDuplicate() {
		return false;
	}

	@Override
	public EnumSet<RunState> supportedGoalStates() {
		return LOCAL_CLOUD_SERVICE_RUN_GOAL_STATES;
	}

	protected LiveExpression<Integer> createPortExpression(final LiveExpression<RunState> runState) {
		if (CloudCliServiceLaunchConfigurationDelegate.isSingleProcessServiceConfig(getActiveConfig())) {
			AsyncLiveExpression<Integer> exp = new AsyncLiveExpression<>(-1, "Refreshing port info for "+getName()) {
				{
					//Doesn't really depend on runState, but should be recomputed when runState changes.
					dependsOn(runState);
				}
				@Override
				protected Integer compute() {
					return computeLivePort();
				}
				@Override
				public String toString() {
					return "LivePortExp for " + getName();
				}
			};
			addDisposableChild(exp);
			return exp;
		}
		return super.createPortExpression(runState);
	}

	private int computeLivePort() {
		ILaunchConfiguration conf = getActiveConfig();
		if (conf != null && READY_STATES.contains(getRunState())) {
			for (ILaunch l : BootLaunchUtils.getLaunches(conf)) {
				if (!l.isTerminated()) {
					String pid = l.getAttribute(BootLaunchConfigurationDelegate.PROCESS_ID);
					if (pid != null && !pid.isEmpty()) {
						JMXConnector jmxConnector = ProcessUtils.createJMXConnector(pid);
						try {
							MBeanServerConnection connection = jmxConnector.getMBeanServerConnection();
							// Just because lifecycle bean is ready does not mean that the port property has
							// already been set.
							// To avoid race condition we should wait here until the port is set (some apps
							// aren't web apps and
							// may never get a port set, so we shouldn't wait indefinitely!)
							return RetryUtil.retry(100, 1000, () -> {
								String port = getPortViaTomcatBean(connection);
								if (port == null) {
									throw new IllegalStateException("port not (yet) set");
								}
								return Integer.valueOf(port);
							});
						} catch (Exception e) {
							// most likely this just means the app isn't running so ignore
						} finally {
							if (jmxConnector != null) {
								try {
									jmxConnector.close();
								} catch (IOException e) {
									Log.log(e);
								}
							}
						}
					}

				}
			}
		}
		return -1;
	}

	private String getPortViaTomcatBean(MBeanServerConnection connection) throws Exception {
		try {
			Set<ObjectName> queryNames = connection.queryNames(null, null);

			for (ObjectName objectName : queryNames) {
				if (objectName.toString().startsWith("Tomcat") && objectName.toString().contains("type=Connector")) {
					// Cloud CLI service 2.x Tomcat bean port value
					Object result = connection.getAttribute(objectName, "port");
					if (result == null) {
						// Older Tomcat bean format for Cloud CLI 1.x
						result = connection.getAttribute(objectName, "localPort");
					}
					if (result != null) {
						return result.toString();
					}
				}
			}
		}
		catch (InstanceNotFoundException e) {
		}

		return null;
	}

	@Override
	public ImageDescriptor getCustomRunStateIcon() {
		switch (getRunState()) {
		case RUNNING:
			return BootDashActivator.getDefault().getImageRegistry().getDescriptor(BootDashActivator.SERVICE_ICON);
		case STARTING:
			return null; // fall back on default implementation
		default:
			return BootDashActivator.getDefault().getImageRegistry().getDescriptor(BootDashActivator.SERVICE_INACTIVE_ICON);
		}
	}
}
