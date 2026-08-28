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

import org.springsource.ide.eclipse.commons.livexp.core.AsyncLiveExpression.AsyncMode;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * @author Kris De Volder
 */
public class LiveSets {

	@SuppressWarnings("rawtypes")
	public static final ObservableSet EMPTY_SET = ObservableSet.constant(ImmutableSet.of());

	@SuppressWarnings("unchecked")
	public static <T> ObservableSet<T> emptySet(Class<T> t) {
		return EMPTY_SET;
	}

	@SuppressWarnings("unchecked")
	public static <R, A extends R, B extends R> ObservableSet<R> union(ObservableSet<A> e1, ObservableSet<B> e2) {
		if (e1 == EMPTY_SET) {
			return (ObservableSet<R>) e2;
		} else if (e2 == EMPTY_SET) {
			return (ObservableSet<R>) e1;
		} else {
			return new LiveUnion<>(e1, e2);
		}
	}

	private static class LiveUnion<T, A extends T, B extends T> extends ObservableSet<T> {

		private ObservableSet<A> e1;
		private ObservableSet<B> e2;

		public LiveUnion(ObservableSet<A> e1, ObservableSet<B> e2) {
			this.e1 = e1;
			this.e2 = e2;
			this.dependsOn(e1);
			this.dependsOn(e2);
		}

		@Override
		protected ImmutableSet<T> compute() {
			return ImmutableSet.copyOf(Sets.union(e1.getValue(), e2.getValue()));
		}
	}

	@SuppressWarnings("unchecked")
	public static <R, A extends R, B extends R> ObservableSet<R> intersection(ObservableSet<A> a, ObservableSet<B> b) {
		if (a == EMPTY_SET || b == EMPTY_SET) {
			return EMPTY_SET;
		} else {
			return new LiveIntersection<>(a, b);
		}
	}

	private static final class LiveIntersection<T, A extends T, B extends T>  extends ObservableSet<T> {

		private ObservableSet<A> a;
		private ObservableSet<B> b;

		public LiveIntersection(ObservableSet<A> a, ObservableSet<B> b) {
			this.a = a;
			this.b = b;
			this.dependsOn(a);
			this.dependsOn(b);
		}
		@Override
		protected ImmutableSet<T> compute() {
			return ImmutableSet.copyOf(Sets.intersection(a.getValue(), b.getValue()));
		}
	}

	public static <S,T> ObservableSet<T> filter(final ObservableSet<S> source, final Class<T> retainType) {
		ObservableSet<T> filtered = new ObservableSet<T>() {
			@SuppressWarnings("unchecked")
			@Override
			protected ImmutableSet<T> compute() {
				return (ImmutableSet<T>) ImmutableSet.copyOf(
					Sets.filter(source.getValue(), new Predicate<S>() {
						@Override
						public boolean apply(S input) {
							return retainType.isAssignableFrom(input.getClass());
						}
					})
				);
			}
		};
		filtered.dependsOn(source);
		return filtered;
	}

	public static <T> ObservableSet<T> singletonOrEmpty(final LiveExpression<T> exp) {
		return new ObservableSet<T>() {
			{
				dependsOn(exp);
			}
			protected ImmutableSet<T> compute() {
				T val = exp.getValue();
				if (val == null) {
					return ImmutableSet.of();
				} else {
					return ImmutableSet.of(val);
				}
			}
		};
	}

	@SuppressWarnings("unchecked")
	public static <A,R> ObservableSet<R> map(ObservableSet<A> input, AsyncMode asyncRefresh, AsyncMode asyncEvents, Function<A, R> function) {
		if (input == EMPTY_SET) {
			return EMPTY_SET;
		}
		return new MapSet<>(input, asyncRefresh, asyncEvents, function);
	}

	/**
	 * Creates a {@link ObservableSet} by applying a mapping function to another ObservableSet.
	 * <p>
	 * The resulting set is synchronously updated when the input set changes.
	 */
	public static <A,R> ObservableSet<R> mapSync(ObservableSet<A> input, Function<A, R> function) {
		return map(input, AsyncMode.SYNC, AsyncMode.SYNC, function);
	}
}
