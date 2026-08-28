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

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;

/**
 * Represents a Set constructed by applying a function to each element of another
 * set.
 */
public class MapSet<S, T> extends ObservableSet<T> {

	private ObservableSet<S> input;
	private Function<S, T> function;

	public MapSet(ObservableSet<S> input, AsyncMode asyncRefresh, AsyncMode asyncEvents, Function<S, T> function) {
		super(ImmutableSet.<T>of(), asyncRefresh, asyncEvents);
		this.input = input;
		this.function = function;
		dependsOn(input);
	}

	/**
	 * Deprecated. Use the constructor that explicitly allows choosing async versus sync behavior.
	 */
	@Deprecated
	public MapSet(ObservableSet<S> input, Function<S, T> function) {
		this(input, AsyncMode.SYNC, AsyncMode.SYNC, function);
	}

	@Override
	protected ImmutableSet<T> compute() {
		ImmutableSet.Builder<T> builder = ImmutableSet.builder();
		for (S a : input.getValues()) {
			T v = function.apply(a);
			//Check for null, generally google collections don't allow nulls (which is good)
			// and we can take advantage of returning nulls to combine mapping and filtering with
			// a single function.
			if (v != null) {
				builder.add(v);
			}
		}
		return builder.build();
	}

}
