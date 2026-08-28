/*******************************************************************************
 * Copyright (c) 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.jdt.ls.commons.java;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.core.IJavaProject;
import org.springframework.tooling.jdt.ls.commons.Logger;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader.InvalidCacheLoadException;

import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

public abstract class CachingFluxJavaSearch<T> implements FluxSearch<T> {
	
	private int MAX_RESULTS = 500;
	
	final protected Logger logger;
	final protected boolean includeBinaries;
	final protected boolean includeSystemLibs;

	private Cache<Tuple2<String,String>, CacheEntry> cache = createCache();

	private class CacheEntry {
		boolean isComplete = false;
		int count = 0;
		Flux<T> values;

		public CacheEntry(String query, Flux<T> producer) {
			values = producer
			.doOnNext(t -> count++)
			.doOnComplete(() -> isComplete = true)
			.take(MAX_RESULTS)
			.cache(MAX_RESULTS);
			values.subscribe(); // create infinite demand so that we actually force cache entries to be fetched upto the max.
		}

		@Override
		public String toString() {
			return "CacheEntry [isComplete=" + isComplete + ", count=" + count + "]";
		}

	}
	
	public CachingFluxJavaSearch(Logger logger, boolean includeBinaries, boolean includeSystemLibs) {
		this.logger = logger;
		this.includeBinaries = includeBinaries;
		this.includeSystemLibs = includeSystemLibs;
	}
	
	@Override
	public final Flux<T> search(IJavaProject javaProject, String query, String searchType) {
		Tuple3<String, String, String> key = key(javaProject, query, searchType);
		CacheEntry cached = null;
		try {
			cached = cache.get(key, () -> new CacheEntry(query, getValuesIncremental(javaProject, query, searchType)));
		} catch (ExecutionException e) {
			logger.log(e);
		}
		return cached.values;
	}

	/**
	 * Tries to use an already cached, complete result for a query that is a prefix of the current query to speed things up.
	 * <p>
	 * Falls back on doing a full-blown search if there's no usable 'prefix-query' in the cache.
	 */
	private Flux<T> getValuesIncremental(IJavaProject javaProject, String query, String searchType) {
//		debug("trying to solve "+query+" incrementally");
		String subquery = query;
		while (subquery.length() >= 1) {
			subquery = subquery.substring(0, subquery.length()-1);
			CacheEntry cached = null;
			try {
				cached = cache.get(key(javaProject, subquery, searchType), () -> null);
			} catch (ExecutionException | InvalidCacheLoadException e) {
//				Log.log(e);
			}
			if (cached != null) {
//				debug("cached "+subquery+": "+cached);
				if (cached.isComplete) {
					return cached.values
//							.doOnNext((hint) -> debug("filter["+query+"]: "+hint.getValue()))
							.filter((result) -> 0 != FuzzyMatcher.matchScore(query, stringValue(result)));
				} else {
//					debug("subquery "+subquery+" cached but is incomplete");
				}
			}
		}
//		debug("full search for: "+query);
		return getValuesAsync(javaProject, query, searchType);
	}
	
	protected abstract Flux<T> getValuesAsync(IJavaProject javaProject, String query, String searchType);
	
	protected abstract String stringValue(T t);

	private Tuple3<String,String,String> key(IJavaProject javaProject, String query, String searchType) {
		return Tuples.of(javaProject == null ? null : javaProject.getElementName(), query, searchType);
	}

	protected <K,V> Cache<K,V> createCache() {
		return CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.MINUTES).expireAfterAccess(1, TimeUnit.MINUTES).build();
	}
	
}
