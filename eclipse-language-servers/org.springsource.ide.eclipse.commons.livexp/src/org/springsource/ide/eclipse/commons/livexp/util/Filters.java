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
package org.springsource.ide.eclipse.commons.livexp.util;

import java.util.List;
import java.util.Optional;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;

import com.google.common.collect.ImmutableList;

/**
 * Helper methods for creating common filters.
 *
 * @author Kris De Volder
 */
public class Filters {

	@SuppressWarnings("rawtypes")
	private static final Filter ACCEPT_ALL = new Filter() {
		public boolean accept(Object t) {
			return true;
		}
		public boolean isTrivial() { return true; }
		
	};

	@SuppressWarnings("unchecked")
	public static <T> Filter<T> acceptAll() {
		return ACCEPT_ALL;
	}

	public static <T> Filter<T> compose(final Filter<T> f1, final Filter<T> f2) {
		if (f1 == ACCEPT_ALL) {
			return f2;
		} else if (f2 == ACCEPT_ALL) {
			return f1;
		}
		return new Filter<T>() {
			public boolean accept(T t) {
				return f1.accept(t) && f2.accept(t);
			}

		};
	}

	public static <T> LiveExpression<Filter<T>> compose(final LiveExpression<Filter<T>> f1, final LiveExpression<Filter<T>> f2) {
		final Filter<T> initial = acceptAll();
		return new LiveExpression<Filter<T>>(initial) {
			{
				dependsOn(f1);
				dependsOn(f2);
			}
			protected Filter<T> compute() {
				return compose(f1.getValue(), f2.getValue());
			}
		};
	}

	/**
	 * Creates a filter that wraps another filter provided by a LiveExp.
	 * <p>
	 * Note that the returned filter is not purely functional since there is state 
	 * inherent in that the delegated to can change over time. 
	 * Some care has to be taken using this filter.
	 */
	public static <T> Filter<T> delegatingTo(LiveExpression<Filter<T>> delegate) {
		return new Filter<T>() {

			@Override
			public boolean accept(T t) {
				return ofNullable(delegate.getValue()).accept(t);
			}

			@Override
			public boolean isTrivial() {
				return ofNullable(delegate.getValue()).isTrivial();
			}

			@Override
			public Iterable<IRegion> getHighlights(String text) {
				return ofNullable(delegate.getValue()).getHighlights(text);
			}
		};
	}

	public static <T> Filter<T> ofNullable(Filter<T> maybeFilter) {
		return maybeFilter != null ? maybeFilter : acceptAll();
	}
	
	public static Filter<String> caseInsensitiveSubstring(String _lookfor) {
		if (_lookfor != null) {
			String lookfor = _lookfor.trim().toLowerCase();
			if (!"".equals(lookfor)) {
				return new Filter<String>() {
					@Override
					public boolean accept(String text) {
						return text.toLowerCase().contains(lookfor);
					}
					
					@Override
					public List<IRegion> getHighlights(String text) {
						int start = text.toLowerCase().indexOf(lookfor);
						if (start >= 0) {
							return ImmutableList.of(new Region(start, lookfor.length()));
						}
						return ImmutableList.of();
					}
					
					@Override
					public String toString() {
						return "CaseInsensitiveSubstringFilter("+lookfor+")";
					}
				};
			}
		}
		//The 'lookfor' is empty or null
		return acceptAll();
	}

}
