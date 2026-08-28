/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model;

import java.util.Map;

import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationListener;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springframework.ide.eclipse.boot.launch.BootLaunchConfigurationDelegate;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;

import com.google.common.collect.MapMaker;

/**
 * @author Kris De Volder
 */
public class LaunchConfDashElementFactory implements Disposable {

	private static final boolean DEBUG = false; // (""+Platform.getLocation()).contains("kdvolder");
	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	private LocalBootDashModel model;

	private Map<ILaunchConfiguration, LaunchConfDashElement> cache;

	private ILaunchConfigurationListener listener;

	private ILaunchManager launchManager;

	public LaunchConfDashElementFactory(LocalBootDashModel bootDashModel, ILaunchManager lm) {
		this.cache = new MapMaker()
				.concurrencyLevel(1) //single thread only so don't waste space for 'connurrencyLevel' support
				.makeMap();
		this.model = bootDashModel;
		this.launchManager = lm;
		lm.addLaunchConfigurationListener(listener = new ILaunchConfigurationListener() {

			@Override
			public void launchConfigurationRemoved(ILaunchConfiguration configuration) {
				deleted(configuration);
			}

			@Override
			public void launchConfigurationChanged(ILaunchConfiguration configuration) {
			}

			@Override
			public void launchConfigurationAdded(ILaunchConfiguration configuration) {
			}
		});
	}

	private synchronized void deleted(ILaunchConfiguration configuration) {
		if (this.cache != null) {
			LaunchConfDashElement element = this.cache.remove(configuration);
			if (element != null) {
				debug("deleted from factory: "+element);
				element.dispose();
			}
		}
	}

	public synchronized LaunchConfDashElement createOrGet(ILaunchConfiguration c) {
		try {
			if (cache != null && c != null) {
				ILaunchConfigurationType type = c.getType();
				if (type != null && BootLaunchConfigurationDelegate.TYPE_ID.equals(type.getIdentifier())) {
					LaunchConfDashElement el = cache.get(c);
					if (el == null) {
						cache.put(c, el = new LaunchConfDashElement(model, c));
						debug("created: "+el);
					}
					return el;
				}
			}
		} catch (Exception e) {
			BootDashActivator.log(e);
		}
		return null;
	}

	@Override
	public void dispose() {
		if (listener != null) {
			launchManager.removeLaunchConfigurationListener(listener);
			listener = null;
			launchManager = null;
		}
		cache = null;
	}

}
