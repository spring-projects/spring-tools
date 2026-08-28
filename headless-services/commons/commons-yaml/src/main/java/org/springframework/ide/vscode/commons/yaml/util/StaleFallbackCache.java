/*******************************************************************************
 * Copyright (c) 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.yaml.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.util.ExceptionUtil;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * A simple cache implementation that provides an option for lookups to fallback
 * to a 'stale' cache entry when computing a current one fails.
 */
public class StaleFallbackCache<K, V>{

	private static class Versioned<T> {
		int version;
		T it;
		public Versioned(int version, T it) {
			super();
			this.version = version;
			this.it = it;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((it == null) ? 0 : it.hashCode());
			result = prime * result + version;
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Versioned other = (Versioned) obj;
			if (it == null) {
				if (other.it != null)
					return false;
			} else if (!it.equals(other.it))
				return false;
			if (version != other.version)
				return false;
			return true;
		}
		@Override
		public String toString() {
			return "Versioned [version=" + version + ", it=" + it + "]";
		}
	}

	Map<K, V> staleEntries = new HashMap<>();
	Cache<K, Versioned<CompletableFuture<V>>> latestEntries = CacheBuilder.newBuilder().build();

	public synchronized V get(K key, int version, boolean allowStaleEntries, Callable<? extends V> valueLoader) throws Exception {
		Versioned<CompletableFuture<V>> latest = latestEntries.get(key, () -> new Versioned<>(version, load(valueLoader)));
		if (latest.version != version) {
			latestEntries.invalidate(key);
			keepStaleBackup(key, latest);
			latest = latestEntries.get(key, () -> new Versioned<>(version, load(valueLoader)));
		}
		if (!allowStaleEntries) {
			return future_get(version, latest);
		} else {
			if (latest.it.isCompletedExceptionally()) {
				V staleValue = staleEntries.get(key);
				if (staleValue != null) {
					return staleValue;
				}
			}
			return future_get(latest.it);
		}
	}

	/**
	 * Called when a stale entry is found in the 'latest' map. This method is
	 * responsible for determining if the entry should be kept as a staleBackup,
	 * and store it.
	 */
	private void keepStaleBackup(K key, Versioned<CompletableFuture<V>> latest) {
		try {
			staleEntries.put(key, latest.it.get());
		} catch (InterruptedException | ExecutionException e) {
			//ignore: This means its a 'bad' entry and so we don't want to keep it
			// as a 'stale backup'.
		}
	}

	private V future_get(int wantedVersion, Versioned<CompletableFuture<V>> versioned) throws Exception {
		Assert.isLegal(wantedVersion == versioned.version);
		return future_get(versioned.it);
	}

	private V future_get(CompletableFuture<V> f) throws Exception {
		try {
			return f.get();
		} catch (InterruptedException e) {
			throw e;
		} catch (ExecutionException e) {
			throw ExceptionUtil.exception(e.getCause());
		}
	}

	private CompletableFuture<V> load(Callable<? extends V> valueLoader) {
		CompletableFuture<V> future = new CompletableFuture<V>();
		try {
			V value = valueLoader.call();
			Assert.isNotNull(value);
			future.complete(value);
		} catch (Throwable e) {
			future.completeExceptionally(e);
		}
		return future;
	}

}
