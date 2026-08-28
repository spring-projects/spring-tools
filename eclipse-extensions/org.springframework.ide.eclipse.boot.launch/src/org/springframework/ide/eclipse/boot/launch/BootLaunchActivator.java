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
package org.springframework.ide.eclipse.boot.launch;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.prefs.BackingStoreException;
import org.springframework.ide.eclipse.boot.launch.util.BootLaunchConfDeleter;

/**
 * @author Kris De Volder
 */
public class BootLaunchActivator extends AbstractUIPlugin {

	private static BootLaunchActivator instance;

	private BootLaunchConfDeleter workspaceListener;

	public BootLaunchActivator() {
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		workspaceListener = new BootLaunchConfDeleter(ResourcesPlugin.getWorkspace(), launchManager);
		instance = this;

		IPreferenceStore myStore = instance.getPreferenceStore();
		if (!myStore.getBoolean("cglib.breakpoint.warning.disabled")) {
			setPreference("org.eclipse.jdt.debug.ui", "org.eclipse.jdt.debug.ui.prompt_unable_to_install_breakpoint", false);
			myStore.setValue("cglib.breakpoint.warning.disabled", true);
		}

		TestJarSupport.start();
	}

	private void setPreference(String plugin, String key, boolean value) {
		try {
			IEclipsePreferences store = DefaultScope.INSTANCE.getNode(plugin);
			store.putBoolean(key, value);
			store.flush();
		} catch (BackingStoreException e) {
			//ignore
		}
		try {
			IEclipsePreferences store = InstanceScope.INSTANCE.getNode(plugin);
			store.putBoolean(key, value);
			store.flush();
		} catch (BackingStoreException e) {
			//ignore
		}
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		instance = null;
		if (workspaceListener != null) {
			workspaceListener.dispose();
		}
		TestJarSupport.stop();
		super.stop(context);
	}

	public static BootLaunchActivator getInstance() {
		return instance;
	}

	private static final String LIVEBEAN_SUPPORT_BUNDLE_ID = "org.springframework.ide.eclipse.boot.launch.livebean";
	public boolean isLiveBeanSupported() {
		for (Bundle bndl : getBundle().getBundleContext().getBundles()) {
			if (bndl.getSymbolicName().equals(LIVEBEAN_SUPPORT_BUNDLE_ID)) {
				return true;
			}
		}
		return false;
	}
}
