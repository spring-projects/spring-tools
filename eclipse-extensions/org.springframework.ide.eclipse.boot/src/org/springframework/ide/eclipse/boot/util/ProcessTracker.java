/*******************************************************************************
 * Copyright (c) 2015 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.util;

import static org.eclipse.debug.core.DebugEvent.CREATE;
import static org.eclipse.debug.core.DebugEvent.TERMINATE;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;

/**
 * @author Kris De Volder
 */
public class ProcessTracker implements Disposable {

	public interface ProcessListener {
		void debugTargetCreated(ProcessTracker tracker, IDebugTarget target);
		void debugTargetTerminated(ProcessTracker tracker, IDebugTarget target);
		void processTerminated(ProcessTracker tracker, IProcess process);
		void processCreated(ProcessTracker tracker, IProcess process);
	}

	private IDebugEventSetListener debugListener;
	private ProcessListener listener;

	public ProcessTracker(ProcessListener listener) {
		this.listener = listener;
		//Pick up any processes already running
		DebugPlugin.getDefault().addDebugEventListener(debugListener = new IDebugEventSetListener() {
			@Override
			public void handleDebugEvents(DebugEvent[] events) {
				if (events != null) {
					for (DebugEvent debugEvent : events) {
						handleDebugEvent(debugEvent);
					}
				}
			};
		});

		//What if processes got started before we attached the listener?
		ILaunch[] launches = DebugPlugin.getDefault().getLaunchManager().getLaunches();
		if (launches != null) {
			for (ILaunch launch : launches) {
				for (IProcess process : launch.getProcesses()) {
					processCreated(process);
				}
			}
		}
	}

	protected final void handleDebugEvent(DebugEvent debugEvent) {
		int kind = debugEvent.getKind();
		Object source = debugEvent.getSource();
		switch (kind) {
		case CREATE:
			if (source instanceof IProcess) {
				processCreated((IProcess)source);
			} else if (source instanceof IDebugTarget) {
				debugTargetCreated((IDebugTarget)source);
			}
			break;
		case TERMINATE:
			if (source instanceof IProcess) {
				processTerminated((IProcess)source);
			} else if (source instanceof IDebugTarget) {
				debugTargetTerminated((IDebugTarget)source);
			}
			break;
		default:
			break;
		}
	}

	private void debugTargetCreated(IDebugTarget source) {
		ProcessListener listener = this.listener;
		if (listener != null) {
			listener.debugTargetCreated(this, source);
		}
	}

	private void debugTargetTerminated(IDebugTarget source) {
		ProcessListener listener = this.listener;
		if (listener != null) {
			listener.debugTargetTerminated(this, source);
		}
	}

	private void processCreated(IProcess process) {
		ProcessListener listener = this.listener;
		if (listener != null) {
			listener.processCreated(this, process);
		}
	}

	private void processTerminated(IProcess process) {
		ProcessListener listener = this.listener;
		if (listener != null) {
			listener.processTerminated(this, process);
		}
	}

	/**
	 * Call to free up or deregister stuff if we don't need it any more. (e.g disconnect debug event listeners)
	 */
	@Override
	public void dispose() {
		if (debugListener != null) {
			DebugPlugin.getDefault().removeDebugEventListener(debugListener);
			debugListener = null;
			if (listener instanceof Disposable) {
				((Disposable) listener).dispose();
			}
			listener = null;
		}
	}
}
