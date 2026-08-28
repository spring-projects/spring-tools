/*******************************************************************************
 * Copyright (c) 2017 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.util;

import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.OnDispose;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;

/**
 * Wrapper around a LiveVariable. Tracks the live variable's value
 * and, whenever the value is changed, calls 'dispose' on the previous value.
 * <p>
 * It also ensures that 'dispose' is called on the final value if the LiveExp
 * itself is disposed.
 * <p>
 * Note: The LiveExp framework's design as grown over time unfortunately doesn't
 * currently provide a strong guarantee that *all* values will be seen by a listener.
 * <p>
 * So, some care should be taken with the wrapped live expression. It should *only* really be
 * applied to a type of LiveExp that calls its value listeners synchronously (i.e.
 * listener is called immediately when the value changed.
 * <p>
 * Even for synchronous listener calls some strange effects may occur if the synchronous
 * value change propagations contain cycles.
 * <p>
 * A proper solution to this problem would likely entail a big change / rethinking of
 * how livexps work and dispatch events.
 * <p>
 * In the mean time, this class can be used safely to track the value of a simple
 * LiveVariable and dispose its old value whenever a new value is assigned.
 * <p>
 * As of STS 4.5.2 the OldValueDisposer because of these limitations, it is now tightly
 * coupled to a LiveVarable instance that it creates itself. This automatically ensures
 * that it is only ever used with a type of LiveExperssion that doesn't cause problems
 * for its implementation.
 *
 * @author Kris De Volder
 */
public class OldValueDisposer<T> implements Disposable {

	private Object lastObservedValue = null;

	private LiveVariable<T> target = new LiveVariable<>();

	/**
	 * When you call this constructor, then you are responsible for
	 * calling the OldValueDisposer.getVar().dispose() or OldValueDisposer.dispose()
	 * method to ensure the last value is disposed.
	 *
	 * This method is Deprecated, use the constructor that takes
	 * a {@link AbstractDisposable} owner as parameter instead.
	 */
	@Deprecated
	public OldValueDisposer() {
		target.addListener((e, v) -> gotValue(v));
		target.onDispose((e) -> gotValue(null));
	}

	public OldValueDisposer(OnDispose owner) {
		this();
		owner.addDisposableChild(target);
	}

	public void setValue(T newValue) {
		this.target.setValue(newValue);
	}

	private synchronized void gotValue(Object v) {
		Object oldValue = lastObservedValue;
		lastObservedValue = v;
		//Take care with spurious change events! Ideally these shouldn't happen, but livexp isn't perfectly avoiding them!
		if (oldValue != v) {
			disposeValue(oldValue);
		}
	}

	private void disposeValue(Object value) {
		if (value instanceof AutoCloseable) {
			try {
				((AutoCloseable) value).close();
			} catch (Exception e) {
				//ignore
			}
		}
	}

	@Override
	public void dispose() {
		target.dispose();
	}

	public LiveVariable<T> getVar() {
		return target;
	}
}
