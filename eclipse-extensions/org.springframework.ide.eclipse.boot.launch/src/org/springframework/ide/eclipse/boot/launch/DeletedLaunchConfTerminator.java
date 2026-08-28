/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.launch;

import java.util.function.Predicate;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationListener;
import org.eclipse.debug.core.ILaunchManager;
import org.springframework.ide.eclipse.boot.util.Log;

/**
 * Listens for deleted launch configurations and checks for 'orphaned' launches.
 * Any orphaned launches which meet the 'isInteresting' test are terminated.
 *
 * @author Kris De Volder
 */
public class DeletedLaunchConfTerminator implements ILaunchConfigurationListener {

	private ILaunchManager lm;
	private final Predicate<ILaunch> isInteresting;

	public DeletedLaunchConfTerminator(ILaunchManager lm, Predicate<ILaunch> isInteresting) {
		this.isInteresting = isInteresting;
		this.lm = lm;
		this.lm.addLaunchConfigurationListener(this);
	}

	@Override
	public void launchConfigurationAdded(ILaunchConfiguration configuration) {
		//don't care
	}

	@Override
	public void launchConfigurationChanged(ILaunchConfiguration configuration) {
		//don't care
	}

	@Override
	public void launchConfigurationRemoved(ILaunchConfiguration deletedConf) {
		//Careful we are somewhat limited on what we can do with this config since it has been deleted.
		System.out.println("Deleted conf: "+deletedConf);
		for (ILaunch l : lm.getLaunches()) {
			if (l.canTerminate()
					&& !l.isTerminated()
					&& isInteresting.test(l)
			) {
				ILaunchConfiguration conf = l.getLaunchConfiguration();
				//Careful conf could be null (because it was deleted), or not.
				//It depends on when we get here (race condition of some kind).
				//I've seen it happen either way! So we have to handle both cases!
				if (conf == null || deletedConf.equals(conf)) {
					try {
						l.terminate();
					} catch (Exception e) {
						Log.log(e);
					}
				}
			}
		};
	}

}
