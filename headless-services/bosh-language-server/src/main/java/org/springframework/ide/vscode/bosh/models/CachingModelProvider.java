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
package org.springframework.ide.vscode.bosh.models;

import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.commons.yaml.schema.DynamicSchemaContext;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * Wraps around a {@link DynamicModelProvider} to add a cache.
 */
public class CachingModelProvider<T> implements DynamicModelProvider<T> {

	/**
	 * Special key to use when the actual key is null (because guava cache doesn't
	 * like null keys).
	 */
	private static final Object NULL_KEY = new Object();

	private long timeout = 30;
	private TimeUnit timeoutUnit = TimeUnit.SECONDS;
	private Cache<Object, CompletableFuture<T>> cache = createCache();

	private final DynamicModelProvider<T> delegate;
	private Class<T> modelInterface;

	public CachingModelProvider(DynamicModelProvider<T> delegate, Class<T> modelInterface) {
		Assert.isNotNull(delegate);
		this.delegate = delegate;
		this.modelInterface = modelInterface;
	}

	/**
	 * Function used to determine the caching key, given the current {@link DynamicSchemaContext}.
	 * <p>
	 * The default keyGetter ignores the context and just returns the same object all the time. This
	 * results in a cache that only keeps a single value (since there's only a single key ever used
	 * to store / find cache entries.
	 */
	private Function<DynamicSchemaContext, Object> keyGetter = (dc) -> "WHATEVER";

	protected Cache<Object, CompletableFuture<T>> createCache() {
		return CacheBuilder.newBuilder()
				.expireAfterWrite(timeout, timeoutUnit)
				.build();
	}

	public CachingModelProvider<T> setTimeout(long timeout, TimeUnit unit) {
		this.timeout = timeout;
		this.timeoutUnit = unit;
		return this;
	}

	@Override
	public T getModel(DynamicSchemaContext dc) throws Exception {
		Object key = keyGetter.apply(dc);
		if (key == null) {
			//guava cache doesn't like null key
			key = NULL_KEY;
		}
		CompletableFuture<T> cached;
		synchronized (this) {
			cached = cache.get(key, () -> {
				try {
					return CompletableFuture.completedFuture(wrapWithCachingProxy(delegate.getModel(dc)));
				} catch (Throwable e) {
					return failed(e);
				}
			});
		}
		return cached.get();
	}

	private static <T> CompletableFuture<T> failed(Throwable e) {
		CompletableFuture<T> failed = new CompletableFuture<>();
		failed.completeExceptionally(e);
		return failed;
	}

	@SuppressWarnings("unchecked")
	private T wrapWithCachingProxy(T model) {
		if (model == null) {
			//Special case for 'no model' we'll create a model that always returns null
			return (T) Proxy.newProxyInstance(modelInterface.getClassLoader(), new Class[] {modelInterface}, (o, m, a) -> {
				return null;
			});
		}
		Cache<String, CompletableFuture<Object>> attributesCache = CacheBuilder.newBuilder().build();
		return (T) Proxy.newProxyInstance(modelInterface.getClassLoader(), new Class[] {modelInterface}, (o, m, a) -> {
			//We only support caching results for methods that have no arguments (for now, its all we need).
			if (m.getParameterTypes().length == 0) {
				return attributesCache.get(m.getName(), () -> {
					try {
						return CompletableFuture.completedFuture((T)m.invoke(model, a));
					} catch (Throwable e) {
						return failed(e);
					}
				}).get();
			}
			return m.invoke(model, a);
		});
	}

}
