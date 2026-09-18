/*******************************************************************************
 * Copyright (c) 2017, 2026 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
 package org.springframework.ide.vscode.commons.languageserver.util;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches the parent process PID and invokes exit if it is no longer available.
 * This implementation waits for periods of inactivity to start querying the PIDs.

 * Copied from JDT LS:
 * https://github.com/eclipse/eclipse.jdt.ls/blob/64b15c5a9e5b11f62ceb5163ceb6930d5dea7129/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/ParentProcessWatcher.java
 * 
 * Adapted to latest changes of the implementation in JDT LS.
 */
public final class ParentProcessWatcher implements Runnable, Function<MessageConsumer, MessageConsumer> {
	
	private static Logger logger = LoggerFactory.getLogger(ParentProcessWatcher.class);

	private static final long INACTIVITY_DELAY = 30_000;
	private static final int POLL_DELAY_SECS = 10;

	private volatile long lastActivityTime;
	private final SimpleLanguageServer server;
	private ScheduledFuture<?> task;
	private ScheduledExecutorService service;
	
	public ParentProcessWatcher(SimpleLanguageServer server ) {
		this.server = server;
		service = Executors.newScheduledThreadPool(1);
		task =  service.scheduleWithFixedDelay(this, POLL_DELAY_SECS, POLL_DELAY_SECS, TimeUnit.SECONDS);
	}

	public void run() {
		if (!parentProcessStillRunning()) {
			logger.info("Parent process stopped running, forcing server exit");
			task.cancel(true);
			server.exit();
		}
	}

	/**
	 * Checks whether the parent process is still running.
	 * If not, then we assume it has crashed, and we have to terminate the Java Language Server.
	 *
	 * @return true if the parent process is still running
	 */
	private boolean parentProcessStillRunning() {
		// Wait until parent process id is available
		final Integer pid = server.getParentProcessId();
		if (pid == null || lastActivityTime > (System.currentTimeMillis() - INACTIVITY_DELAY)) {
			return true;
		}
		
		Optional<ProcessHandle> processHandle = ProcessHandle.of(pid);
		return processHandle.isPresent() && processHandle.get().isAlive();
	}

	@Override
	public MessageConsumer apply(final MessageConsumer consumer) {
		//inject our own consumer to refresh the timestamp
		return message -> {
			lastActivityTime = System.currentTimeMillis();
			try {
				consumer.consume(message);
			} catch (UnsupportedOperationException e) {
				//log a warning and ignore. We are getting some messages from vsCode the server doesn't know about
				logger.warn("Unsupported message was ignored!", e);
			}
		};
	}
	
}
