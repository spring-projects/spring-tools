/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.core;

import org.eclipse.core.runtime.ListenerList;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;

public abstract class AbstractDisposable implements Disposable, OnDispose {

	private ListenerList<DisposeListener> disposeListeners = new ListenerList<>();

	public boolean isDisposed() {
		return disposeListeners == null;
	}

	@Override
	public synchronized void onDispose(DisposeListener l) {
		if (disposeListeners != null) {
			this.disposeListeners.add(l);
		} else {
			//already disposed. Call listener right away!
			l.disposed(this);
		}
	}

	@Override
	public void dispose() {
		ListenerList<DisposeListener> listeners;
		synchronized (this) {
			listeners = disposeListeners;
			disposeListeners = null;
		}
		if (listeners != null) {
			for (Object _l : listeners.getListeners()) {
				DisposeListener l = (DisposeListener)_l;
				l.disposed(this);
			}
		}
	}

	/**
	 * Convenience method to declare that a given {@link Disposable} is an 'owned' child of
	 * this element and should also be disposed when this element itself is disposed.
	 */
	public <C extends Disposable> C addDisposableChild(final C child) {
		onDispose(new DisposeListener() {
			public void disposed(Disposable disposed) {
				child.dispose();
			}
		});
		return child;
	}
}
