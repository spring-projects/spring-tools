/*******************************************************************************
 * Copyright (c) 2016, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.core.async;

import java.time.Duration;
import java.util.Collection;

import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.springsource.ide.eclipse.commons.frameworks.core.internal.cache.Cache;
import org.springsource.ide.eclipse.commons.frameworks.core.internal.cache.LimitedTimeCache;

import reactor.core.publisher.Flux;

/**
 * COPIED FROM: 
 * org.springframework.ide.eclipse.boot.properties.editor.metadata.CachingValueProvider
 * <p>
 * TODO: refactor so that there is only one CachingValueProvider that can be reused
 * <p>
 * A abstract {@link ValueProviderStrategy} that is mean to help speedup successive invocations of
 * content assist with a similar 'query' string.
 * <p>
 * This implementation is meant to be used for providers that use potentially lengthy/expensive searches
 * to determine hints. Since content assist hints are requested by Eclipse CA framework directly on
 * the UI thread, they can not simply perform a lengthy search and block UI thread until it finished.
 * <p>
 * This implementation therefore does the following:
 * <ul>
 *   <li>Limit the duration of time spent on the UI thread.
 *   <li>Cache results of searches for a limited time.
 *   <li>Speedup queries for successive queries by using the already cached result of a similar (prefix) query.
 *   <li>When the time spent on UI thread waiting for a current search exceeds the allowed time limit,
 *       return immediately with whatever results have been found so far.
 * </ul>
 *
 * TODO: rather than an abstract class this should really be 'Wrapper' class that delegates to another
 * {@link ValueProviderStrategy} and adds a cache in front of it.
 *
 * @author Kris De Volder
 */
public abstract class CachingValueProvider<T> {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofMillis(1000);

	/**
	 * Content assist is called inside UI thread and so doing something lenghty things
	 * like a JavaSearch will block the UI thread completely freezing the UI. So, we
	 * only return as many results as can be obtained within this hard TIMEOUT limit.
	 */
	public static Duration TIMEOUT = DEFAULT_TIMEOUT;

	/**
	 * The maximum number of results returned for a single request. Used to limit the
	 * values that are cached per entry.
	 */
	private int MAX_RESULTS = 500;

	private Cache<String, CacheEntry> cache = createCache();

	private class CacheEntry {
		boolean isComplete = false;
		int count = 0;
		Flux<T> values;

		public CacheEntry(String query, Flux<T> producer) {
			values = producer
			.doOnNext((e) -> {
				count++;
//				debug("onNext["+query+":"+count+"]: "+e.getValue().toString());
			})
			.doOnComplete(() -> {
//				debug("onComplete["+query+":"+count+"]");
				isComplete = true;
			})
			.take(MAX_RESULTS)
			.cache(MAX_RESULTS);
			values.subscribe(); // create infinite demand so that we actually force cache entries to be fetched upto the max.
		}

		@Override
		public String toString() {
			return "CacheEntry [isComplete=" + isComplete + ", count=" + count + "]";
		}

	}

	
	public final Flux<T> getValues(IType type, IJavaSearchScope scope) {
//		debug("CA query: "+query);
		String key = key(type);
		CacheEntry cached = cache.get(key);
		if (cached == null) {
			cache.put(key, cached = new CacheEntry(key, getValuesIncremental(type, scope)));
		}
		return cached.values;
	}
	
	public final Collection<T> getValuesNow(IType type, IJavaSearchScope scope) {
		return this.getValues(type, scope)
		.take(CachingValueProvider.TIMEOUT)
		.collectList()
		.block();
	}

	/**
	 * Tries to use an already cached, complete result for a query that is a prefix of the current query to speed things up.
	 * <p>
	 * Falls back on doing a full-blown search if there's no usable 'prefix-query' in the cache.
	 */
	private Flux<T> getValuesIncremental(IType expectedType, IJavaSearchScope scope) {
//		debug("trying to solve "+query+" incrementally");
		CacheEntry cached = cache.get(key(expectedType));
		if (cached != null) {
			if (cached.isComplete) {
//				debug("filtering "+subquery+" -> "+query);
				return cached.values
//						.doOnNext((hint) -> debug("filter["+query+"]: "+hint.getValue()))
//						.filter((hint) -> 0 != FuzzyMatcher.matchScore(query, hint.getValue().toString()))
						;
			} else {
//				debug("subquery "+subquery+" cached but is incomplete");
			}
		}
//		debug("full search for: "+query);
		return getValuesAsycn(expectedType, scope);
	}

	protected abstract Flux<T> getValuesAsycn(IType expectedType, IJavaSearchScope scope);

	private String key(IType expectedType) {
		return expectedType == null ? null : expectedType.getFullyQualifiedName();
	}

	protected <K,V> Cache<K,V> createCache() {
		return new LimitedTimeCache<>(Duration.ofMinutes(1));
	}

	public static void restoreDefaults() {
		TIMEOUT = DEFAULT_TIMEOUT;
	}

}
