/*******************************************************************************
 * Copyright (c) 2017 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.util;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;

/**
 * Polling ready state monitor.
 *
 * An instance of this class starts checking an application's lifecyle
 * repeatedly with a short delay between polls. This continues until either the
 * monitor object is disposed, or the application enters the 'ready' state.
 * <p>
 * When the application reaches ready state then its 'ready' LiveExp will change
 * value from false to true. Clients who wish to respond to this 'event' can
 * attach a listener to the livexp.
 *
 * @author Kris De Volder
 * @author Alex Boyko
 *
 */
public abstract class AbstractPollingAppReadyStateMonitor implements ReadyStateMonitor {

	public static final long POLLING_INTERVAL = 500/*ms*/;

	private Job job;
	private LiveVariable<Boolean> ready = new LiveVariable<>(false);

	final public void startPolling() {
		initPollingJob();
	}

	private void initPollingJob() {
		this.job = new Job("Ready state poller") {
			protected IStatus run(IProgressMonitor monitor) {
				LiveVariable<Boolean> r = ready;
				if (r != null) { //null means disposed. Job may be lagging behind
					r.setValue(checkReady());
					if (!r.getValue()) {
						this.schedule(POLLING_INTERVAL);
					} else {
						// don't reschedule
					}
				}
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		job.schedule();
	}

	final public LiveExpression<Boolean> getReady() {
		return ready;
	}

	public void dispose() {
		if (job != null) {
			job.cancel();
			job = null;
		}
		ready = null;
	}

	/**
	 * Checks whether application is up and running
	 * @return <code>true</code> if application is up and running
	 */
	abstract protected boolean checkReady();
}
