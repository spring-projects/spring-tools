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
package org.springframework.ide.vscode.commons.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.Callable;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;

/**
 * A partial collection instance represents collection of
 * elements which may not be entirely known.
 * <p>
 * For unknown collection, an optional explanation, in the
 * form of a caught exception may be stored as well.
 */
public class PartialCollection<T> {
	
	private static final PartialCollection<?> UNKNOWN = new PartialCollection<>(ImmutableSet.of(), false);
	private static final PartialCollection<?> EMPTY = new PartialCollection<>(ImmutableSet.of(), true);

	final private ImmutableCollection<T> knownElements;
	final private boolean isComplete;
	final private Throwable explanation;

	private PartialCollection(ImmutableCollection<T> knownElements, boolean isComplete, Throwable error) {
		this.knownElements = knownElements;
		this.isComplete = isComplete;
		this.explanation = error;
	}

	private PartialCollection(ImmutableCollection<T> knownElements, boolean isComplete) {
		this.knownElements = knownElements;
		this.isComplete = isComplete;
		this.explanation = null;
	}

	private PartialCollection(ImmutableCollection<T> knownElements, Throwable error) {
		this.knownElements = knownElements;
		this.isComplete = error == null;
		this.explanation = error;
	}

	/**
	 * Create a {@link PartialCollection} by executing some computation that returs a collectioon. 
	 * If the computation throws the resulting collection will be completely unknown, otherwise
	 * it will be completely known.
	 */
	public static <T> PartialCollection<T> compute(Callable<Collection<T>> computer) {
		try {
			Collection<T> allValues = computer.call();
			if (allValues == null) {
				return PartialCollection.unknown();
			}
			return new PartialCollection<>(ImmutableSet.copyOf(allValues), true);
		} catch (Exception e) {
			return new PartialCollection<>(ImmutableSet.of(), e);
		}
	}
	
	/**
	 * Create a {@link PartialCollection} by executing some computation that returs a collectioon. 
	 * If the computation throws the resulting collection will be completely unknown, otherwise
	 * it will be completely known.
	 */
	public static <T> PartialCollection<T> fromCallable(Callable<PartialCollection<T>> computer) {
		try {
			return computer.call();
		} catch (Exception e) {
			return new PartialCollection<>(ImmutableSet.of(), e);
		}
	}


	/**
	 * @return All the known elements of this partial collection.
	 */
	public Collection<T> getElements() {
		return knownElements;
	}

	public boolean isComplete() {
		return isComplete;
	}

	/**
	 * Returns the totally unknown collection. I.e. a unknown collection with no known elements 
	 */
	@SuppressWarnings("unchecked")
	public static <T> PartialCollection<T> unknown() {
		return (PartialCollection<T>) UNKNOWN;
	}

	/**
	 * Like map on streams, but silently drops any null elements returned by the mapper.
	 */
	public <R> PartialCollection<R> map(Function<? super T, ? extends R> mapper) {
		ImmutableSet<R> mappedElements = getElements().stream().map((x) -> mapper.apply(x)).filter(x -> x != null).collect(CollectorUtil.toImmutableSet());
		return new PartialCollection<R>(mappedElements, isComplete, explanation);
	}

	/**
	 * Returns a empty collection (i.e. the collection is know to be empty).
	 */
	@SuppressWarnings("unchecked")
	public static <T> PartialCollection<T> empty() {
		return (PartialCollection<T>) EMPTY;
	}
	
	/**
	 * Make a copy of this collection that has the same known elements but also has unknown elements.
	 */
	public PartialCollection<T> addUncertainty() {
		if (!this.isComplete()) {
			return this; //No need to make a copy. Current collection is already only partially known.
		}
		return new PartialCollection<>(knownElements, false);
	}

	/**
	 * A completely unknown collection with a given exception explaining the reason.
	 */
	public static <T> PartialCollection<T> unknown(Exception e) {
		Assert.isLegal(e != null);
		return new PartialCollection<>(ImmutableSet.of(), e);
	}

	public PartialCollection<T> addAll(Collection<T> moreElements) {
		ImmutableSet.Builder<T> elements = ImmutableSet.builder();
		elements.addAll(getElements());
		elements.addAll(moreElements);
		return new PartialCollection<>(elements.build(), isComplete, explanation);
	}

	public Throwable getExplanation() {
		return explanation;
	}

	public PartialCollection<T> add(@SuppressWarnings("unchecked") T... values) {
		return addAll(Arrays.asList(values));
	}

	public PartialCollection<T> addAll(PartialCollection<T> values) {
		PartialCollection<T> merged = this.addAll(values.getElements());
		if (!values.isComplete()) {
			merged = merged.addUncertainty();
			if (merged.getExplanation() == null && values.getExplanation() != null) {
				merged = merged.withExplanation(values.getExplanation());
			}
		}
		return merged;
	}

	private PartialCollection<T> withExplanation(Throwable explanation) {
		return new PartialCollection<>(knownElements, isComplete, explanation);
	}
	
	@Override
	public String toString() {
		StringBuilder s = new StringBuilder("PartialCollection(");
		boolean first = true;
		for (T e : knownElements) {
			if (!first) {
				s.append(", ");
			}
			s.append(e.toString());
			first = false;
		}
		if (!isComplete) {
			if (!first) {
				s.append(", ");
			}
			s.append("...");
		}
		s.append(")");
		return s.toString();
	}
}
