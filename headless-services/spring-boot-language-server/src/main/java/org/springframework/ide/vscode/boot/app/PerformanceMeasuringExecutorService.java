/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PerformanceMeasuringExecutorService implements ExecutorService {
	
	private static final Logger log = LoggerFactory.getLogger(PerformanceMeasuringExecutorService.class);
	
	private ExecutorService delegate;
	long milliseconds = 0;

	public PerformanceMeasuringExecutorService(ExecutorService delegate) {
		this.delegate = delegate;
	}

	@Override
	public void execute(Runnable command) {
		this.delegate.execute(new Runnable() {
			@Override
			public void run() {
				long start = System.currentTimeMillis();
				try {
					command.run();
				}
				finally {
					long end = System.currentTimeMillis();
					milliseconds += (end - start);
					log.info("milliseconds spend in indexer (total): " + milliseconds);
				}
			}
		});
	}

	@Override
	public void shutdown() {
		delegate.shutdown();
	}

	@Override
	public List<Runnable> shutdownNow() {
		return delegate.shutdownNow();
	}

	@Override
	public boolean isShutdown() {
		return delegate.isShutdown();
	}

	@Override
	public boolean isTerminated() {
		return delegate.isTerminated();
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
		return delegate.awaitTermination(timeout, unit);
	}

	@Override
	public <T> Future<T> submit(Callable<T> task) {
		return delegate.submit(new Callable<T>() {
			@Override
			public T call() throws Exception {
				long start = System.currentTimeMillis();
				try {
					return task.call();
				}
				finally {
					long end = System.currentTimeMillis();
					milliseconds += (end - start);
					log.info("milliseconds spend in indexer (total): " + milliseconds);
				}
			}
		});
	}

	@Override
	public <T> Future<T> submit(Runnable task, T result) {
		return delegate.submit(new Runnable() {
			@Override
			public void run() {
				long start = System.currentTimeMillis();
				try {
					task.run();
				}
				finally {
					long end = System.currentTimeMillis();
					milliseconds += (end - start);
					log.info("milliseconds spend in indexer (total): " + milliseconds);
				}
			}
		}, result);
	}

	@Override
	public Future<?> submit(Runnable task) {
		return delegate.submit(new Runnable() {
			@Override
			public void run() {
				long start = System.currentTimeMillis();
				try {
					task.run();
				}
				finally {
					long end = System.currentTimeMillis();
					milliseconds += (end - start);
					log.info("milliseconds spend in indexer (total): " + milliseconds);
				}
			}
		});
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
		log.warn("INVOKE ALL");
		return delegate.invokeAll(tasks);
	}

	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException {
		log.warn("INVOKE ALL");
		return delegate.invokeAll(tasks, timeout, unit);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		log.warn("INVOKE ANY");
		return delegate.invokeAny(tasks);
	}

	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		log.warn("INVOKE ANY");
		return delegate.invokeAny(tasks, timeout, unit);
	}

}
