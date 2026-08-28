/*******************************************************************************
 * Copyright (c) 2017, 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.validation;

import java.util.ArrayList;
import java.util.List;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class BootValidationActivator implements BundleActivator {
	
	private static BundleContext context;

	static BundleContext getContext() {
		return context;
	}

	private static List<Runnable> onStop;

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext bundleContext) throws Exception {
		BootValidationActivator.context = bundleContext;
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		BootValidationActivator.context = null;
		for (Runnable runnable : getStopHandlers()) {
			runnable.run();
		}
	}

	private static synchronized Runnable[] getStopHandlers() {
		if (onStop == null) {
			return new Runnable[0];
		}
		try {
			return onStop.toArray(new Runnable[onStop.size()]);
		} finally {
			onStop = null;
		}
	}

	public static final String PLUGIN_ID = "org.springframework.ide.eclipse.boot.validation";
//	public static final String NATURE_ID = PLUGIN_ID+".springbootnature";
	public static final String BUILDER_ID = PLUGIN_ID+".springbootbuilder";

	public static synchronized void onStop(Runnable runnable) {
		if (onStop == null) {
			onStop = new ArrayList<>();
		}
		onStop.add(runnable);
	}

}
