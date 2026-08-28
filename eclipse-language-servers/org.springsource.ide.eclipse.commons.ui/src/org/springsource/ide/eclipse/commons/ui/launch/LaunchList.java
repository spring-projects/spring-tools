/*******************************************************************************
 * Copyright (c) 2013 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.ui.launch;

import static org.eclipse.debug.core.DebugEvent.CREATE;
import static org.eclipse.debug.core.DebugEvent.TERMINATE;

import java.util.Collection;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.IProcess;

/**
 * @author Kris De Volder
 */
public abstract class LaunchList {

	public static class Item {
		public final ILaunchConfiguration conf;
		public final String mode;
		public Item(ILaunchConfiguration conf, String mode) {
			this.conf = conf;
			this.mode = mode;
		}

		@Override
		public String toString() {
			return "LaunchItem("+conf.getName()+", "+mode+")";
		}

		public String getName() {
			return conf.getName();
		}
	}

	public interface Listener {
		void changed();
	}

	private IDebugEventSetListener debugListener;
	private final ListenerList listeners = new ListenerList();

	protected LaunchList() {
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
		switch (kind) {
		case CREATE:
			if (debugEvent.getSource() instanceof IProcess) {
				//We can only track one process. Ignore additional events.
				processCreated((IProcess)debugEvent.getSource());
			}
			break;
		case TERMINATE:
			if (debugEvent.getSource() instanceof IProcess) {
				processTerminated((IProcess)debugEvent.getSource());
			}
			break;
		default:
			break;
		}
	}

	protected abstract void processTerminated(IProcess process);
	protected abstract void processCreated(IProcess process);

	public final LaunchList addListener(Listener listener) {
		listeners.add(listener);
		return this;
	}

	public final void removeListener(Listener listener) {
		listeners.remove(listener);
	}

	protected void fireChangeEvent() {
		for (Object l : listeners.getListeners()) {
			((Listener)l).changed();
		}
	}

	/**
	 * Call to free up or deregister stuff if we don't need it any more. (e.g disconnect debug event listeners)
	 */
	public void dispose() {
		DebugPlugin.getDefault().removeDebugEventListener(debugListener);
	}

	public abstract Item getLast();

	public abstract Collection<Item> getLaunches();

}
