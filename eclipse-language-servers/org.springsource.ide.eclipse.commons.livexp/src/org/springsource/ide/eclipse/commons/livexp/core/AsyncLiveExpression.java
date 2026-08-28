/*******************************************************************************
 * Copyright (c) 2015 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.core;

import static org.springsource.ide.eclipse.commons.livexp.core.AsyncLiveExpression.AsyncMode.ASYNC;

import java.util.function.Consumer;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

/**
 * Like a LiveExpression but has an option to ensures that its refresh
 * method is always called in a background job.
 * <p>
 * With a regular LiveExp the refresh will be called on the same thread
 * as the change event it reacts to. So in general you have little control
 * over what thread that might be.
 * <p>
 * Similarly, listeners will be called on the same thread as well.
 * <p>
 * An {@link AsyncLiveExpression} provides the option to make either refresh or change notification
 * asynchronous; or both.
 * <p>
 * Making refresh asynchronuus is useful when the 'compute' method called during refresh
 * does something that you might not want to just execute, for example, on the UI thread and
 * you want to ensures it is done in a background job.
 * <p>
 * It is also useful in that when refreshes might be 'lengthy' operations, bursty events
 * triggering refreshes will only causes limited refreshes as only a single Job is
 * being scheduled and rescheduled.
 * <p>
 * Asynchronous change notification is useful when you want to make sure listeners respond to
 * changes in a separate thread. For example, because, you make changes to an underlying model
 * inside a synchronized block and want to avoid implicitly passing ownership of the lock to
 * the listeners.
 * <p>
 * Though it is possible to select both asyncRefresh and asyncEvents mode at the same time.
 * It is probably not ever necessary or logical to that since when refresh is async it
 * means that change notification alreayd gets triggered in an asynchronous manner (i.e. in
 * the same job that performs the refresh.
 * <p>
 * Selecting bot asynch modes at the same time thus essentially just adds unnessary overhead
 * by creating an addional job to perform change notification.
 *
 * @author Kris De Volder
 */
public abstract class AsyncLiveExpression<T> extends LiveExpression<T> {

	public enum AsyncMode {
		SYNC, ASYNC
	}

	private Job refreshJob;
	private Job eventsJob;

	private long refreshDelay = 0;

	public AsyncLiveExpression(T initialValue) {
		this(initialValue, AsyncMode.ASYNC, AsyncMode.SYNC);
	}

	public AsyncLiveExpression(T initialValue, AsyncMode refreshMode, AsyncMode eventsMode) {
		this(initialValue,
				refreshMode == ASYNC ? "AsyncLiveExpression refresh" : null,
				eventsMode == ASYNC ? "AsyncLiveExpression notify" : null
		);
	}

	public AsyncLiveExpression(T initialValue, String refreshJobName) {
		this(initialValue, refreshJobName, null);
	}


	/**
	 * Create AsyncLiveExpression. If Job name is passed in then
	 * this expression will refresh itself asynchronously.
	 * <p>
	 * If the jobName is null then it behaves like a plain (i.e.
	 * synchronous) LiveExpression.
	 */
	public AsyncLiveExpression(T initialValue, String refreshJobName, String eventsJobName) {
		this(initialValue, refreshJobName, eventsJobName, null);
	}
	
	public AsyncLiveExpression(T initialValue, String refreshJobName, String eventsJobName, Consumer<Job> refreshJobCustomizer) {
		super(initialValue);
		if (refreshJobName != null) {
			refreshJob = new Job(refreshJobName) {
				protected IStatus run(IProgressMonitor monitor) {
					syncRefresh();
					return Status.OK_STATUS;
				}
			};
			if (refreshJobCustomizer != null) {
				refreshJobCustomizer.accept(refreshJob);
			}
		}
		if (eventsJobName != null) {
			eventsJob = new Job(eventsJobName) {
				protected IStatus run(IProgressMonitor monitor) {
					syncChanged();
					return Status.OK_STATUS;
				};
			};
		}
	}

	/**
	 * This method is final, if you are overriding this, then you probably should
	 * be overriding 'syncRefresh' instead. Otherwise you probably are breaking
	 * async refresh support.
	 */
	@Override
	public final void refresh() {
		if (refreshJob != null) {
			refreshJob.schedule(refreshDelay);
		} else {
			syncRefresh();
		}
	}

	protected void syncRefresh() {
		super.refresh();
	}

	/**
	 * This method is final, if you are overriding this, then you probably should
	 * be overriding 'syncRefresh' instead. Otherwise you probably are breaking
	 * async event notification support.
	 */
	@Override
	protected final void changed() {
		if (eventsJob != null) {
			eventsJob.schedule();
		} else {
			syncChanged();
		}
	}

	protected void syncChanged() {
		super.changed();
	}

	public long getRefreshDelay() {
		return refreshDelay;
	}

	public void setRefreshDelay(long refreshDelay) {
		this.refreshDelay = refreshDelay;
	}

}
