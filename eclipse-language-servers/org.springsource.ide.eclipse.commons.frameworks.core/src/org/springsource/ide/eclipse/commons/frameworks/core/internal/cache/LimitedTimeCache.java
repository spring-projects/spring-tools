/*******************************************************************************
 * Copyright (c) 2016, 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.frameworks.core.internal.cache;

import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

/**
 * A cache that automatically removes entries when they reach a certain age.
 *
 * @author Kris De Volder
 */
public class LimitedTimeCache<K,V> implements Cache<K, V> {

	private static final boolean DEBUG = false; //(""+Platform.getLocation()).contains("kdvolder");

	private static void debug(String string) {
		if (DEBUG) {
			System.out.println(string);
		}
	}

	public final long MAX_AGE;
	public final long AGE_MARGIN;

	private Map<K, Entry> cache = new HashMap<>();

	/**
	 * Job that removes expired entries from the cache.
	 */
	private Job clearExpiredJob = new Job("Clean expired cache entries") {

		{
			setSystem(true);
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			long oldest = clearExpired();
			//Reschedule job to again run when the next entry expires.
			if (oldest >= 0) {
				clearExpiredJob.schedule(MAX_AGE - oldest + AGE_MARGIN);
			}
			return Status.OK_STATUS;
		}
	};

	private class Entry {
		long lastUsed;
		V value;
		Entry(V v) {
			this.value = v;
			this.lastUsed = System.currentTimeMillis();
		}
		long age() {
			return System.currentTimeMillis() - lastUsed;
		}
	}

	public LimitedTimeCache(Duration MAX_AGE_DURATION) {
		MAX_AGE = MAX_AGE_DURATION.toMillis();
		AGE_MARGIN = Math.max(1000, MAX_AGE / 20);
	}

	@Override
	public synchronized V get(K key) {
		Entry e = cache.get(key);
		if (e != null) {
			e.lastUsed = System.currentTimeMillis();
			return e.value;
		}
		return null;
	}

	@Override
	public synchronized void put(K key, V value) {
		boolean wasEmpty = cache.isEmpty();
		if (value == null) {
			cache.remove(key);
		} else {
			cache.put(key, new Entry(value));
		}
		//if the cache was not empty then it is already guaranteed a 'cleaningJob' will run to
		// clean the existing oldest entry.
		if (wasEmpty && !cache.isEmpty()) {
			//The entry we just added is the only one in the cache.
			//Therefore, that entry must be the oldest and its age is 0.
			//So we know when the job should run next:
			clearExpiredJob.schedule(MAX_AGE+AGE_MARGIN);
		}
	}

	@Override
	public synchronized void clear() {
		cache.clear();
	}

	/**
	 * Iterates the cache removing all expired entries.
	 * @return The age of the oldest non-expired entry in the cache or -1 if the cache is empty.
	 */
	private synchronized long clearExpired() {
		debug(">>> clearExpired MAX_AGE = "+MAX_AGE);
		Iterator<java.util.Map.Entry<K, LimitedTimeCache<K, V>.Entry>> iter = cache.entrySet().iterator();
		long oldest = -1;
		while (iter.hasNext()) {
			java.util.Map.Entry<K, LimitedTimeCache<K, V>.Entry> me = iter.next();
			Entry e = me.getValue();
			long age = e.age();
			if (age >= MAX_AGE) {
				debug("Expired: "+me.getKey() +" age: "+e.age());
				iter.remove();
			} else {
				oldest = Math.max(oldest, age);
			}
		}
		debug("<<< clearExpired ["+cache.size()+"]");
		return oldest;
	}

	/**
	 * Applies a {@link LimitedTimeCache} cache around a given function.
	 *
	 * @param duration time after which entries in the cache should expire.
	 * @param func Function to wrap the cache around
	 * @return Equivalent function but with caching.
	 */
	public static <K,V> Function<K,V> applyOn(Duration duration, Function<K,V> func) {
		LimitedTimeCache<K, V> cache = new LimitedTimeCache<>(duration);
		return (k) -> {
			V v = cache.get(k);
			if (v == null) {
				cache.put(k, v=func.apply(k));
			}
			return v;
		};
	}

}
