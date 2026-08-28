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
package org.springframework.ide.eclipse.boot.dash.test;

import static org.junit.Assert.fail;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * JUnit 4 style test 'rule' that does some cleanups for test code
 * that creates launch configurations and launches processes with them.
 *
 * @author Kris De Volder
 */
public class LaunchCleanups implements TestRule {

	@Override
	public Statement apply(final Statement base, final Description description) {
		return new Statement() {
			public void evaluate() throws Throwable {
				try {
					base.evaluate();
				} finally {
					cleanups();
				}
			}

		};
	}

	private void cleanups() throws Exception {
		ILaunchManager launchManager = DebugPlugin.getDefault().getLaunchManager();
		ILaunch[] launches = launchManager.getLaunches();
		for (ILaunch l : launches) {
			if (!l.isTerminated()) {
				fail("Leaky test code leaves launch running? "+nicerToString(l));
			}
			launchManager.removeLaunch(l);
		}

		for (ILaunchConfiguration conf : launchManager.getLaunchConfigurations()) {
			conf.delete();
		}
	}

	private String nicerToString(ILaunch l) {
		ILaunchConfiguration c = l.getLaunchConfiguration();
		if (c != null) {
			return c.getName();
		}
		return l.toString();
	}

}
