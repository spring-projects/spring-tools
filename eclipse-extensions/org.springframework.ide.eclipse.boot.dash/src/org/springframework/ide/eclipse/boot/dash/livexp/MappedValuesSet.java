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

import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;

/**
 * An observable set that contains the results of applying a mapping function onto
 * the values of an ObservableSet of LiveExps.
 */
public abstract class MappedValuesSet<T, R> extends ObservableSet<R> {

	private final ObservableSet<LiveExpression<T>> target;
	private ImmutableSet<LiveExpression<T>> listenersAttached = ImmutableSet.of();

	private ValueListener<T> valueListener = new ValueListener<T>() {
		@Override
		public void gotValue(LiveExpression<T> exp, T value) {
			MappedValuesSet.this.refresh();
		}
	};

	private ValueListener<ImmutableSet<LiveExpression<T>>> setListener = new  ValueListener<ImmutableSet<LiveExpression<T>>>() {
		public void gotValue(LiveExpression<ImmutableSet<LiveExpression<T>>> exp, ImmutableSet<LiveExpression<T>> value) {
			refreshValueListeners();
		}
	};

	public MappedValuesSet(ObservableSet<LiveExpression<T>> target) {
		this.target = target;
		this.target.addListener(setListener);
	}

	/**
	 * Override to define the function to apply to each value before adding to the result set.
	 */
	protected abstract R applyFun(T arg);

	/**
	 * Called when the target set changes. This should add and remove valuelistener to
	 * the LiveExps in the target to ensure we listen each expression.
	 */
	private synchronized void refreshValueListeners() {
		ImmutableSet<LiveExpression<T>> current = target.getValues();
		SetView<LiveExpression<T>> removed = Sets.difference(listenersAttached, current);
		SetView<LiveExpression<T>> added = Sets.difference(current, listenersAttached);
		for (LiveExpression<T> exp : removed) {
			exp.removeListener(valueListener);
		}
		for (LiveExpression<T> exp : added) {
			exp.addListener(valueListener);
		}
		listenersAttached = current;
	}

	@Override
	protected ImmutableSet<R> compute() {
		ImmutableSet.Builder<R> builder = immutableSetBuilder();
		for (LiveExpression<T> exp : target.getValues()) {
			R val = applyFun(exp.getValue());
			if (val != null) {
				builder.add(val);
			}
		}
		return builder.build();
	}

	/**
	 * By overriding this method, subclass can determine the kind of ImmutableSet that will
	 * be constructed to hold the mapped values. For example, a subclass may want to use ImmutableSortedSet instead
	 * of a plain ImmutableSet which iterates elements in unpredictable order.
	 */
	protected ImmutableSet.Builder<R> immutableSetBuilder() {
		//TODO: pull up to superclass and use consistently anywhere a ObservableSet needs to
		// construct a immutable set?
		return ImmutableSet.builder();
	}

	@Override
	public void dispose() {
		synchronized (this) {
			if (listenersAttached != null) {
				for (LiveExpression<T> exp : listenersAttached) {
					exp.removeListener(valueListener);
				}
				listenersAttached = null;
			}
			if (setListener != null) {
				target.removeListener(setListener);
				setListener = null;
			}
		}
		super.dispose();
	}

}
