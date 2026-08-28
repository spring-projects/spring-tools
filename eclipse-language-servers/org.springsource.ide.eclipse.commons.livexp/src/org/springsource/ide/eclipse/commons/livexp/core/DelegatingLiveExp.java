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
package org.springsource.ide.eclipse.commons.livexp.core;

/**
 * A {@link LiveExpression} which is defined as mirroring the
 * contents of a delegate.
 * <p>
 * This delegate is initially null and can be set repeatedly.
 * <p>
 * When delegate is null the valud of the {@link DelegatingLiveExp} will also be null.
 * When delegate points to non-null {@link LiveExpression} then its value will be
 * equal to the value of the delegate.
 *
 * @author Kris De Volder
 */
public class DelegatingLiveExp<T> extends LiveExpression<T> {

	private LiveVariable<LiveExpression<T>> _delegate = new LiveVariable<>();
	private ValueListener<T> delegateListener = (exp, value) -> refresh();

	public DelegatingLiveExp() {
		onDispose(d -> _delegate.dispose());
	}

	@Override
	protected T compute() {
		LiveExpression<T> delegate = _delegate.getValue();
		if (delegate == null) {
			return null;
		} else {
			return delegate.getValue();
		}
	}

	public synchronized void setDelegate(LiveExpression<T> newDelegate) {
		LiveExpression<T> oldDelegate = _delegate.getValue();
		_delegate.setValue(newDelegate);
		if (oldDelegate == newDelegate) {
			return;
		} else {
			if (oldDelegate != null) {
				oldDelegate.removeListener(delegateListener);
			}
			if (newDelegate == null) {
				//trigger a refresh because the delegate changed and the newDelegate won't trigger one
				refresh();
			} else {
				newDelegate.addListener(delegateListener);
			}
		}
	}
	
	public LiveExpression<LiveExpression<T>> getDelegate() {
		return _delegate;
	}

}
