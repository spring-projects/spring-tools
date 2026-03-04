/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.commons;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.Platform;
import org.eclipse.lsp4e.server.ProcessStreamConnectionProvider;

/**
 * A 'last resort cleanup' utility for LSP server processes. This class, when instantiated
 * registers a JVM shutdown hook. When the hook is executed it kills of all tracked processes
 * with the most aggressive process termination method available through the JRE.
 * <p>
 * This is meant only as a kind of 'fail-safe' as it is expected that lps4e does proper
 * house-keeping and cleans up these processes automatically and cleanly. So... unless
 * something goes wrong inside lsp4e then the `destroyProcesses` method really should
 * just be iterating an empty collection of processes. I.e. the typical debug output
 * on shutdown should look something like this:
 *
 * <pre>
 *  LanguageServerProcessReaper: Destroying errant processes...
 *  LanguageServerProcessReaper: Number of alive processes = 0
 *  LanguageServerProcessReaper: Destroying errant processes... DONE
 * </pre>
 *
 * @author Kris De Volder
 */
public class LanguageServerProcessReaper extends Thread {

	private static final boolean DEBUG = (""+Platform.getLocation()).contains("kdvolder");

	private static void debug(String string) {
		if (DEBUG) {
			System.err.println("LanguageServerProcessReaper: "+string);
		}
	}

	public static Process getProcess(ProcessStreamConnectionProvider connectionProvider) {
		try {
			//The super class is doesn't provide a way to get at the process without using reflection...
			// This method can be removed if / when the super-class provides a getProcess method we can call.
			Field processField = ProcessStreamConnectionProvider.class.getDeclaredField("process");
			processField.setAccessible(true);
			Process process = (Process) processField.get(connectionProvider);
			return process;
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			return null;
		}
	}

	Map<String, Process> processes = new HashMap<>();
	{
		Runtime.getRuntime().addShutdownHook(this);
	}

	public synchronized void addProcess(String id, Process process) {
		debug("added process: "+process);
		if (process!=null) {
			Process existingProcess = processes.get(id);
			//workaround for this bug: https://bugs.eclipse.org/bugs/show_bug.cgi?id=549904
			if (existingProcess!=null) {
				if (existingProcess.isAlive()) {
					debug("Destroying leaked process: "+process);
					existingProcess.destroyForcibly();
				}
			}
			processes.put(id, process);
		}
		garbageCollect();
	}

	/**
	 * If processes are managed reasonably by lsp4e then we don't expect the list of
	 * actually 'alive' processes to keep on growing. This checks for and removes
	 * processes that are already dead.
	 */
	protected void garbageCollect() {
		Process process;
		Iterator<Process> iter = processes.values().iterator();
		while (iter.hasNext()) {
			process = iter.next();
			if (!process.isAlive()) {
				iter.remove();
			}
		}
		debug("Number of alive processes = "+processes.size());
	}

	public synchronized void removeProcess(Process process) {
		debug("removeProcess "+process);
		garbageCollect();
	}

	@Override
	public void run() {
		destroyProcesses();
	}

	private static final int DESTROY_TIMEOUT_SECONDS = 5;

	private synchronized void destroyProcesses() {
		debug("Destroying errant processes... ");
		garbageCollect();
		for (Process process : processes.values()) {
			try {
				if (process.isAlive()) {
					boolean exited = false;
					if (process.supportsNormalTermination()) {
						debug("Waiting for process to exit on its own "+process);
						exited = process.waitFor(DESTROY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
						if (exited) {
							debug("Process exited gracefully "+process);
							continue;
						}
						debug("Process did not exit, sending destroy "+process);
						process.destroy();
					}
					exited = process.waitFor(DESTROY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
					if (exited) {
						debug("Process exited after destroy "+process);
						continue;
					}
					debug("Force-killing process "+process);
					process.destroyForcibly();
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				process.destroyForcibly();
			} catch (Throwable e) {
				process.destroyForcibly();
			}
		}
		debug("Destroying errant processes... DONE");
	}

}
