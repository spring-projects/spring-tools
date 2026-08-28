/*******************************************************************************
 * Copyright (c) 2020 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.di;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.eclipse.core.runtime.Assert;
import org.springframework.ide.eclipse.boot.dash.api.RunTargetType;
import org.springsource.ide.eclipse.commons.livexp.util.ExceptionUtil;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;

/**
 * A very simple DI framework.
 * <p>
 * Can we use something else? Maybe guice, spring, or the DI framework built-into Eclipse 4?
 */
public class SimpleDIContext {

	public static final SimpleDIContext EMPTY = new SimpleDIContext().lockdown();

	private String creationLocation;

	public SimpleDIContext() {
		this.creationLocation = ExceptionUtil.stacktrace();
	}

	private Cache<Class<?>, Object> beanCache = CacheBuilder.newBuilder().build();
	private Cache<Class<?>, List<Object>> beanListCache = CacheBuilder.newBuilder().build();

	public interface BeanFactory<T> {
		T create(SimpleDIContext context) throws Exception;
	}

	public static final class Definition<T> {
		private final Class<T> type;
		private final BeanFactory<T> factory;
		AtomicBoolean requested = new AtomicBoolean(false);
		private CompletableFuture<T> instance = new CompletableFuture<>();
		public Definition(Class<T> type, BeanFactory<T> factory) {
			super();
			this.type = type;
			this.factory = factory;
			this.reload();
		}
		public boolean satisfies(Class<?> requested) {
			return requested.isAssignableFrom(type);
		}
		public synchronized T get(SimpleDIContext context) throws Exception {
			if (requested.compareAndSet(false, true)) {
				try {
					instance.complete(factory.create(context));
				} catch (Throwable e) {
					instance.completeExceptionally(e);
				}
			}
			return instance.get();
		}
		public void reload() {
			instance = new CompletableFuture<>();
			requested.set(false);
		}
		public CompletableFuture<Void> whenCreated(Consumer<T> requestor) {
			return instance.thenAccept(requestor);
		}
		@Override
		public String toString() {
			return "Definition("+type.getName()+")";
		}
	}

	private List<Definition<?>> definitions = new ArrayList<>();
	private AtomicBoolean locked = new AtomicBoolean();

	public <T> SimpleDIContext def(Class<T> type, BeanFactory<T> factory) {
		definitions.add(new Definition<>(type, factory));
		return this;
	}

	public <T> SimpleDIContext defInstance(Class<T> klass, T instance) {
		Assert.isNotNull(instance);
		def(klass, (x) -> instance);
		return this;
	}

	@SuppressWarnings("unchecked")
	public synchronized <T> T getBean(Class<T> type) {
		lockdown();
		try {
			return (T) beanCache.get(type, () -> resolveDefinition(type).get(this));
		} catch (Exception e) {
			throw ExceptionUtil.unchecked(e);
		}

	}

	@SuppressWarnings("unchecked")
	protected <T> Definition<T> resolveDefinition(Class<T> type) {
		for (int i = definitions.size()-1; i >= 0; i--) {
			Definition<?> d = definitions.get(i);
			if (d.satisfies(type)) {
				return (Definition<T>) d;
			}
		}
		throw new IllegalStateException("No definition for bean of type "+type+"\n context created:\n"+creationLocation);
	}


	/**
	 * Prevents additional definitions from being added. The idea is that using an injection
	 * context proceeds in two separate stages. Stage 1 initializes the context with bean definitions.
	 * Stage 2 allows a client to request beans that are then created on demand. Once stage 2 is
	 * started, which happens automatically when the first bean is requested, the context becomes
	 * immmutable and no longer allows adding definitions.
	 */
	SimpleDIContext lockdown() {
		if (locked.compareAndSet(false, true)) {
			definitions = ImmutableList.copyOf(definitions);
		}
		return this;
	}

	public void assertDefinitionFor(Class<?> requested) {
		Assert.isLegal(hasDefinitionFor(requested), "No definition for "+requested);
	}

	public void assertNoDefinitionFor(Class<RunTargetType> requested) {
		Assert.isLegal(!hasDefinitionFor(requested), "Definition already exists for "+requested);
	}


	public boolean hasDefinitionFor(Class<?> requested) {
		for (Definition<?> d : definitions) {
			if (d.satisfies(requested)) {
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	public <T> List<T> getBeans(Class<T> type) {
		lockdown();
		try {
			return (List<T>) beanListCache.get(type, () -> {
				ImmutableList.Builder<Object> builder = ImmutableList.builder();
				resolveDefinitions(type).forEach(d -> {
					try {
						builder.add(d.get(this));
					} catch (Exception e) {
						throw ExceptionUtil.unchecked(e);
					}
				});
				return builder.build();
			});
		} catch (Exception e) {
			throw ExceptionUtil.unchecked(e);
		}
	}

	@SuppressWarnings("unchecked")
	private <T> Stream<Definition<T>> resolveDefinitions(Class<T> type) {
		Object defs = definitions.stream().filter(d -> d.satisfies(type));
		return (Stream<Definition<T>>)defs;
	}

	/**
	 * Clears out all bean caches and forces new beans to be created
	 * when they are requested again,
	 */
	public void reload() {
		beanCache.invalidateAll();
		beanListCache.invalidateAll();
		for (Definition<?> d : definitions) {
			d.reload();
		}
	}

	public <T> Supplier<T> supplier(Class<T> type) {
		return () -> getBean(type);
	}

	public <T> CompletableFuture<Void> whenCreated(Class<T> type, Consumer<T> requestor) {
		return resolveDefinition(type).whenCreated(requestor);
	}

}
