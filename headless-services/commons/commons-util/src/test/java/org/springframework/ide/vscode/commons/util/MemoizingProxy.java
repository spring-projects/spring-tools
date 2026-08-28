/*******************************************************************************
 * Copyright (c) 2018 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.util;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.IgnoreForBinding;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.matcher.ElementMatcher.Junction;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Utility to instrument a given class, memoizing all it's zero-argument method invocations. 
 * Note that the memoization behaves a little different from guava's Suppliers.memoize in that
 * it caches exception results as well as regularly returned values.
 */
public class MemoizingProxy {

	public interface Builder<T> {
		T newInstance(Object... args);
		T delegateTo(T delegate);
	}
	
	private static final Junction<MethodDescription> CACHABLE_METHODS = 
			takesArguments(0).and(not(isStatic()).and(isPublic()));

	public interface IFieldProxy {
		Object getValue();
		void setValue(Object value);
	}
	
	private static final String F_CACHE = "__MemoizingProxy__cache";
	private static final String F_DURATION = "__MemoizingProxy__duration";
	private static final String F_DELEGATE = "__MemoizingProxy__delegate";
	
	private static final MethodDelegation.WithCustomProperties METHOD_DELEGATION = MethodDelegation.withDefaultConfiguration()
			.withBinders(FieldProxy.Binder.install(IFieldProxy.class));

	public static class ClassConstructorInterceptor {
		public static void intercept(@FieldProxy(F_CACHE) IFieldProxy fCache, @FieldValue(F_DURATION) long duration) {
			fCache.setValue(CacheBuilder.newBuilder().expireAfterWrite(duration, TimeUnit.MILLISECONDS).build());
		}
	}
	
	public static class SuperMethodInterceptor {
		@RuntimeType
		public static Object intercept(
				@Origin(cache = true) Method method,
				@FieldValue(F_CACHE) Cache<String,Result> cache, 
				@SuperCall Callable<?> zuper, 
				@AllArguments Object[] args
		) throws Exception {
			synchronized (cache) {
				String mname = method.getName();
				Result r = cache.get(mname, () -> new Result(() -> { 
					try {
						return zuper.call();
					} catch (Throwable e) {
						throw ExceptionUtil.exception(e);
					}
				}));
				return r.get();
			}
		}
	}

	public static class DelegateMethodInterceptor {
		
		@RuntimeType
		public static Object intercept(
				@Origin(cache = true) Method method,
				@FieldValue(F_CACHE) Cache<String,Result> cache, 
				@FieldValue(F_DELEGATE) Object delegate, 
				@AllArguments Object[] args
		) throws Exception {
			if (args.length == 0) {
				synchronized (cache) {
					String mname = method.getName();
					Result r = cache.get(mname, () -> new Result(() -> { 
						return callTheDelegate(method, delegate, args);
					}));
					return r.get();
				}
			} else { //has arguments do not cache
				return callTheDelegate(method, delegate, args);
			}
		}

		@IgnoreForBinding
		private static Object callTheDelegate(Method method, Object delegate, Object[] args) throws Exception {
			try {
				return method.invoke(delegate, args);
			} catch (InvocationTargetException e) {
				throw ExceptionUtil.exception(e.getTargetException());
			} catch (Throwable e) {
				throw ExceptionUtil.exception(e);
			}
		}
	}

	static class Result {
		
		Throwable e;
		Object v;
		
		Result(Callable<?> computer) {
			try {
				v = computer.call();
			} catch (Throwable e) {
				this.e = e;
			}
		}

		public Object get() throws Exception {
			if (e != null) {
				throw ExceptionUtil.exception(e);
			}
			return v;
		}
	}

	public static <T> Builder<T> builder(Class<T> klass, Duration duration, Class<?>... argTypes) {
		try {
			if (klass.isInterface()) {
				Assert.isLegal(argTypes.length == 0, "Should not provide constructor argument types for interface type "+klass.getSimpleName());
				DynamicType.Builder<T> builder = new ByteBuddy()
						.subclass(klass, ConstructorStrategy.Default.NO_CONSTRUCTORS)
						.defineField(F_DURATION, long.class, Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC).value(duration.toMillis())
						.defineField(F_CACHE, Cache.class, Opcodes.ACC_PRIVATE)
						.defineField(F_DELEGATE, klass, Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL)
						.method(ElementMatchers.isAbstract()).intercept(MethodDelegation.to(DelegateMethodInterceptor.class))
						.defineConstructor(Visibility.PUBLIC).withParameters(klass).intercept(
								MethodCall.invoke(Object.class.getConstructor())
								.andThen(FieldAccessor.ofField(F_DELEGATE).setsArgumentAt(0))
								.andThen(METHOD_DELEGATION.to(ClassConstructorInterceptor.class))
						);
				
				Constructor<? extends T> constructor = builder.make().load(klass.getClassLoader()).getLoaded().getConstructor(klass);
				return new Builder<T>() {
	
					@Override
					public T newInstance(Object... args) {
						throw new UnsupportedOperationException(
								"Can't use 'newInstance' because '"+klass.getSimpleName()+" is an interface.\n"+
								"Use 'delegateTo' instead."
						);
					}
	
					@Override
					public T delegateTo(T delegate) {
						try {
							return constructor.newInstance(delegate);
						} catch (Exception e) {
							throw ExceptionUtil.unchecked(e);
						}
					}
					
				};
			} else {
				DynamicType.Builder<T> builder = new ByteBuddy()
						.subclass(klass, ConstructorStrategy.Default.NO_CONSTRUCTORS)
						.defineField(F_DURATION, long.class, Opcodes.ACC_FINAL | Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC).value(duration.toMillis())
						.defineField(F_CACHE, Cache.class, Opcodes.ACC_PRIVATE)
						.method(CACHABLE_METHODS).intercept(MethodDelegation.to(SuperMethodInterceptor.class))
						.defineConstructor(Visibility.PUBLIC).withParameters(argTypes).intercept(
								MethodCall.invoke(klass.getDeclaredConstructor(argTypes)).withAllArguments()
								.andThen(METHOD_DELEGATION.to(ClassConstructorInterceptor.class))
						);
				
				Constructor<? extends T> constructor = builder.make().load(klass.getClassLoader()).getLoaded().getConstructor(argTypes);
				return new Builder<T>() {
					@Override
					public T newInstance(Object... args) {
						try {
							return constructor.newInstance(args);
						} catch (Exception e) {
							throw ExceptionUtil.unchecked(e);
						}
					}
		
					@Override
					public T delegateTo(T delegate) {
						throw new UnsupportedOperationException(
								"Can't use 'delegateTo' because '"+klass.getSimpleName()+" is not an interface.\n"+
								"Use 'newInstance' instead."
						);
					}
				};
			}
		} catch (Exception e) {
			throw ExceptionUtil.unchecked(e);
		}
	}
	
//	/**
//	 * Deprecated: use the 'builder' method instead
//	 */
//	@Deprecated
//	public static <T> T create(Class<T> klass, Duration duration, Class<?>[] argTypes, Object... args) {
//		try {
//			return builder(klass, duration, argTypes).newInstance(args);
//		} catch (Exception e) {
//			throw ExceptionUtil.unchecked(e);
//		}
//	}
		
}
