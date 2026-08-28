/*******************************************************************************
 * Copyright (c) 2015, 2017 GoPivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     GoPivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.util;

import org.eclipse.debug.core.ILaunch;
import org.springframework.ide.eclipse.boot.launch.util.SpringApplicationLifeCycleClientManager;
import org.springframework.ide.eclipse.boot.launch.util.SpringApplicationLifecycleClient;

/**
 * An instance of this class starts checking a spring application's lifecyle using
 * a JMX bean protocol. Checks are performed repeatedly with a short delay between
 * polls. This continues until either the  SpringApplicationReadyStateMonitor is disposed,
 * or the application enters the 'ready' state.
 * <p>
 * When the application reaches ready state then its 'ready' LiveExp will change value from
 * false to true. Clients who wish to respond to this 'event' can attach a listener to
 * the livexp.
 *
 * @author Kris De Volder
 */
public class SpringApplicationReadyStateMonitor extends AbstractPollingAppReadyStateMonitor {

	private SpringApplicationLifeCycleClientManager clientManager;

	public SpringApplicationReadyStateMonitor(ILaunch launch) {
		super();
		clientManager = new SpringApplicationLifeCycleClientManager(launch);
	}

	public void dispose() {
		if (clientManager != null) {
			clientManager.disposeClient();
		}
		super.dispose();
	}

	protected boolean checkReady() {
		try {
			SpringApplicationLifecycleClient client = clientManager.getLifeCycleClient();
			if (client != null) {
				return client.isReady();
			}
		} catch (Exception e) {
			//Something went wrong asking client for ready state.
			// most likely process died.
			if (clientManager != null) {
				clientManager.disposeClient();
			}
		}
		return false;
	}

}
