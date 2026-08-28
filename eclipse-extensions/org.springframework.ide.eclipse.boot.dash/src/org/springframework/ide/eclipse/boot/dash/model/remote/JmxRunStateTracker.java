/*******************************************************************************
 * Copyright (c) 2020 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model.remote;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import javax.management.remote.JMXConnector;

import org.springframework.ide.eclipse.boot.dash.api.App;
import org.springframework.ide.eclipse.boot.dash.api.JmxConnectable;
import org.springframework.ide.eclipse.boot.dash.model.RunState;
import org.springframework.ide.eclipse.boot.launch.util.JMXClient;
import org.springframework.ide.eclipse.boot.launch.util.SpringApplicationLifeCycleClientManager;
import org.springframework.ide.eclipse.boot.launch.util.SpringApplicationLifecycleClient;
import org.springsource.ide.eclipse.commons.livexp.core.AbstractDisposable;
import org.springsource.ide.eclipse.commons.livexp.core.AsyncLiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.util.Log;

public class JmxRunStateTracker extends AbstractDisposable {

	private final GenericRemoteAppElement bde;
	private final LiveExpression<RunState> _baseRunState;
	private final Callable<JMXConnector> connectionProvider;
	private SpringApplicationLifeCycleClientManager clientMgr;

	private static final long APP_STARTUP_TIMEOUT = 60_000;
	private static final long POLLING_INTERVAL = 500;
	private static final boolean DEBUG = false;

	long creationTime = System.currentTimeMillis();


	private void debug(String string) {
		if (DEBUG) {
			System.out.println(this+" : "+string);
		}
	}

	public final LiveExpression<RunState> augmentedRunState = new AsyncLiveExpression<RunState>(RunState.INACTIVE) {


		{
			setRefreshDelay(POLLING_INTERVAL);
		}

		@Override
		protected RunState compute() {
			debug("Computing augmented runstate...");
			RunState baseRunState = _baseRunState.getValue();
			debug("baseRunState = "+baseRunState);
			if (baseRunState.isActive()) {
				Exception error = null;
				try {
					SpringApplicationLifecycleClient client = clientMgr.getLifeCycleClient();
					if (client == null || client.isReady()) {
						debug("jmxClient.isReady() => true");
						return baseRunState;
					}
					//client.isReady() => false
				} catch (Exception e) {
					// failed to connect
					error = e;
					clientMgr.disposeClient();
				}
				// failed to connect or client.isReady -> false
				try {
					refreshMaybe(error);
					return RunState.STARTING;
				} catch (Exception e1) {
					Throwable cause = ExceptionUtil.getDeepestCause(e1);
					if (cause instanceof IOException || cause instanceof TimeoutException) {
						//expected when container goes away, connections dropping / breaking etc.
					} else {
						Log.log(e1);
					}
					return RunState.UNKNOWN;
				}
			} else {
				return baseRunState;
			}
		}


		private void refreshMaybe(Exception error) throws Exception {
			if (!isDisposed()) {
				long age = System.currentTimeMillis()-creationTime;
				debug("age = "+ age);
				if (age < APP_STARTUP_TIMEOUT) {
					refresh();
				} else {
					if (error != null) {
						throw error;
					}
					else {
						throw new TimeoutException();
					}
				}
			}
		}
	};

	public JmxRunStateTracker(GenericRemoteAppElement bde, LiveExpression<RunState> baseRunState, LiveExpression<App> app) {
		this.bde = bde;
		this._baseRunState = baseRunState;
		this.connectionProvider = () -> {
			App data = app.getValue();
			if (data instanceof JmxConnectable) {
				String url = ((JmxConnectable) data).getJmxUrl();
				debug("jmxUrl = "+url);
				if (url != null) {
					return JMXClient.createJmxConnectorFromUrl(url);
				}
			}
			return null;
		};
		this.clientMgr = new SpringApplicationLifeCycleClientManager(connectionProvider);
		_baseRunState.onChange(this, (_e, _v) -> {
			creationTime = System.currentTimeMillis();
			augmentedRunState.refresh();
		});
		augmentedRunState.dependsOn(app);
		onDispose(d -> clientMgr.disposeClient());
	}

	@Override
	public String toString() {
		return "JmxRunStateTracker("+bde+")";
	}
}
