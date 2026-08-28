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

import java.util.ArrayList;

import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventFilter;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jdt.debug.core.IJavaExceptionBreakpoint;
import org.eclipse.jdt.debug.core.IJavaThread;
import org.springframework.ide.eclipse.boot.core.BootActivator;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;

/**
 * An instance of this type causes a given type of exception to be 'ignored' by the debug UI.
 *
 * @author Kris De Volder
 */
public class IgnoreExceptionOfType implements IDebugEventFilter, Disposable {

	//This class works as follows:
	// - an 'EventFilter is attached to the debug model.
	// - if an event is detected that corresponds to suspending on an exception breakpoint of the 'ignored' type.
	//     => a) the event is'filtered' (so that it won't cause a popup to swithc to debugging perspective
	//        b) the suspended thread is automatically resumed (so that it looks to the user as if the breakpoint wasn't hit)

	// Why such a strange way of doing things?

	// Because these methods do not work:
	//
	//  a) try to use a BreakPointListener instead. It didn't seem possible to suppress a breakpoint from being installed
	//     or the breakpoint from causing a suspension. (There's a voting mechanism, but its not possible to override a
	//     vote to install / suspend cast by another existing listener)

	//  b) use a DebugEventListener (instead of a filter). This does work, but it has the annoying effect that,
	//     because breakpoint is actually triggered and then auto-resumed, the debug UI still pops up a dialog
	//     to switch into the debug perspective. The filter allows us to 'hide' that event. (note that just filtering
	//     the event isn't enough as this only stops the debug ui from reacting to the breakpoint being hit, but
	//     the thread still ends up suspended nevertheless)

//	private static final boolean DEBUG = true;
//
//	private static void debug(String string) {
//		if (DEBUG) {
//			System.out.println(string);
//		}
//	}

	private ILaunch launch;
	private String exceptionToIgnore;

	/**
	 * Create an instance and register it as a listened of the debug model.
	 */
	public IgnoreExceptionOfType(ILaunch launch, String exceptionToIgnore) {
		this.launch = launch;
		this.exceptionToIgnore = exceptionToIgnore;
		DebugPlugin.getDefault().addDebugEventFilter(this);
	}

	@Override
	public void dispose() {
		DebugPlugin.getDefault().removeDebugEventFilter(this);
	}

	@Override
	public DebugEvent[] filterDebugEvents(DebugEvent[] events) {
		ArrayList<DebugEvent> filtered = new ArrayList<>(events.length);
		for (DebugEvent e : events) {
			if (select(e)) {
				filtered.add(e);
			}
		}
		if (!filtered.isEmpty()) {
			return filtered.toArray(new DebugEvent[filtered.size()]);
		}
		return null;
	}

	private boolean select(DebugEvent e) {
		if (e.getKind() == DebugEvent.SUSPEND && e.getDetail() == DebugEvent.BREAKPOINT) {
			IJavaThread source = (IJavaThread) e.getSource();
			if (launch == null || launch.equals(source.getLaunch())) {
				IBreakpoint[] bps = source.getBreakpoints();
				if (bps != null) {
					for (IBreakpoint bp : bps) {
						if (bp instanceof IJavaExceptionBreakpoint) {
							IJavaExceptionBreakpoint ebp = (IJavaExceptionBreakpoint) bp;
							if (exceptionToIgnore.equals(ebp.getExceptionTypeName())) {
								resume(source);
								return false;
							}
						}
					}
				}
			}
		}
		return true;
	}

	private void resume(IJavaThread source) {
		try {
			source.resume();
		} catch (DebugException e) {
			BootActivator.log(e);
		}
	}

}