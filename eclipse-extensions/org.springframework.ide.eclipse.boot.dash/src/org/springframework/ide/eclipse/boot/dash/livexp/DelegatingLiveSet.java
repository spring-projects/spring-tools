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
package org.springframework.ide.eclipse.boot.dash.livexp;

import java.util.Collections;
import java.util.Set;

import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;

import com.google.common.collect.ImmutableSet;

import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;

/**
 * A {@link ObservableSet} which is defined as mirroring the
 * contents of a delegate.
 * <p>
 * This delegate is initially null and can be set repeatedly.
 * <p>
 * When delegate is null the DelegatingSet will be empty, when set
 * to point to non-null ObservableSet then its contents will be equal to the
 * contents of the delegate.
 *
 * @author Kris De Volder
 */
public class DelegatingLiveSet<T> extends ObservableSet<T> {

	public DelegatingLiveSet() {
	}

	private ObservableSet<T> delegate = null;
	private ValueListener<ImmutableSet<T>> delegateListener = new ValueListener<ImmutableSet<T>>() {
		public void gotValue(LiveExpression<ImmutableSet<T>> exp, ImmutableSet<T> value) {
			refresh();
		}
	};

	@Override
	protected ImmutableSet<T> compute() {
		if (delegate == null) {
			return ImmutableSet.of();
		} else {
			return delegate.getValue();
		}
	}

	public synchronized void setDelegate(ObservableSet<T> newDelegate) {
		LiveExpression<ImmutableSet<T>> oldDelegate = this.delegate;
		this.delegate = newDelegate;
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

}
