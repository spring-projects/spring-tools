/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.livexp;

import java.time.Duration;
import java.util.function.Supplier;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;

/**
 * LiveExpression that continually refreshes itself at regular intervals with a background job.
 * <p>
 * The expression, when created, starts out in a 'sleeping' state. It will not start refreshing/computing
 * its value until methods like 'refreshOnce', 'refreshFor' or 'refreshForever' are called.
 *
 * @author Kris De Volder
 */
public abstract class PollingLiveExp<T> extends LiveExpression<T> {

	private static final Supplier<Boolean> STOP_REFRESHING = () -> false;

	private Job refreshJob = createRefreshJob();

	/**
	 * Time in ms to 'sleep' between refreshes.
	 */
	private long sleepBetweenRefreshes = 500;

	private Supplier<Boolean> continueRefreshing = STOP_REFRESHING;

	public PollingLiveExp(T initialValue) {
		super(initialValue);
	}

	/**
	 * Override the default 'sleepBetweenRefreshes' value.
	 */
	public PollingLiveExp<T> sleepBetweenRefreshes(Duration duration) {
		this.sleepBetweenRefreshes = duration.toMillis();
		return this;
	}

	private Job createRefreshJob() {
		Job job = new Job("Refresh PollingLiveExp") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				refresh();
				if (continueRefreshing.get()) {
					this.schedule(sleepBetweenRefreshes);
				}
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		return job;
	}

	@Override
	public void refresh() {
		if (continueRefreshing.get()) {
			super.refresh();
		}
	}

	@Override
	public void dispose() {
		refreshJob = null;
		continueRefreshing = STOP_REFRESHING;
		super.dispose();
	}

	/**
	 * Start refreshing now, and continue until given duration expires.
	 */
	public void refreshFor(Duration duration) {
		Job job = refreshJob;
		if (job != null) {
			long stopRefrestingAfter = System.currentTimeMillis() + duration.toMillis();
			this.continueRefreshing = () -> System.currentTimeMillis() <= stopRefrestingAfter;
			job.schedule();
		}
	}

	/**
	 * Start refreshing now, and continue forever (or until this expression is disposed).
	 */
	public PollingLiveExp<T> refreshForever() {
		Job job = refreshJob;
		if (job != null) {
			continueRefreshing = () -> true;
			job.schedule();
		}
		return this;
	}

	public PollingLiveExp<T> refreshOnce() {
		Job job = refreshJob;
		if (job != null) {
			continueRefreshing = new Supplier<Boolean>() {

				boolean firstTime = true;

				@Override
				public Boolean get() {
					try {
						return firstTime;
					} finally {
						firstTime = false;
					}
				}
			};
			job.schedule();
		}
		return this;
	}

	/**
	 * Lambda-friendly way of creating a PollingLiveExp instance.
	 */
	public static <T> PollingLiveExp<T> create(T initialValue, Supplier<T> computer) {
		return new PollingLiveExp<T>(initialValue) {
			@Override
			protected T compute() {
				return computer.get();
			}
		};
	}

}
