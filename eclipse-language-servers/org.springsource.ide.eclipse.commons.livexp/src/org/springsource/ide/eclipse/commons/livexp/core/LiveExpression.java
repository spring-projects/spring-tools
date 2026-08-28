/*******************************************************************************
 * Copyright (c) 2012 Pivotal Software, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Pivotal Software, Inc. - initial API and implementation
 *******************************************************************************/
package org.springsource.ide.eclipse.commons.livexp.core;

import java.time.Duration;

import org.eclipse.core.runtime.ListenerList;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;
import org.springsource.ide.eclipse.commons.livexp.util.OldValueDisposer;

import com.google.common.base.Function;

/**
 * A 'live' expression is something that conceptually one would like to think of as an expression
 * that returns a value. However, this expression provides a listener-style interface so that
 * interested parties can subscribe to be notified when the value of the expression changes.
 */
public abstract class LiveExpression<V> implements Disposable, OnDispose {

	public static final LiveExpression<Boolean> TRUE = constant(true);
	public static final LiveExpression<Boolean> FALSE = constant(false);

	private ListenerList fListeners = new ListenerList();
	private ListenerList fDisposeHandlers = new ListenerList();

	/**
	 * An optional 'owner' for this expression. Useful when expressions
	 * are part of a model, in which case listeners may want to discover
	 * the owner of a LiveExpression to do something to/with the owner
	 * (e.g. redraw it) when something inside it changes.
	 */
	protected Object owner = null;

	/**
	 * The last computed value of the expression.
	 */
	protected V value;

	/**
	 * Optional liveExp created on demand as requested. Keeps track of refreshes. Can
	 * be used as a mechanism for firing listener after every refresh (even if the value of
	 * the liveexp has not changed.
	 */
	private LiveVariable<Integer> refreshCount;

	public LiveExpression(V initialValue, Object owner) {
		this.value = initialValue;
		this.owner = owner;
	}

	public LiveExpression(V initialValue) {
		this(initialValue, null);
	}

	public LiveExpression() {
		this(null);
	}

	/**
	 * Clients may call this method to request a recomputation of the expression's value from its inputs.
	 */
	public void refresh() {
		//V oldValue = value;
		boolean changed = false;
		synchronized (this) {
			V newValue = compute();
			if (!equals(newValue, value)) {
				value = newValue;
				changed = true;
			}
		}
		if (changed) {
			changed();
		}
		synchronized (this) {
			LiveVariable<Integer> rc = this.refreshCount;
			if (rc != null) {
				rc.setValue(rc.getValue()+1);
			}
		}
	}
	
	public synchronized LiveExpression<Integer> refreshCount() {
		if (refreshCount == null) {
			refreshCount = new LiveVariable<>(0);
		}
		return refreshCount;
	}

	/**
	 * Implementation of value equals that works if either one of the values is null.
	 */
	private static <V> boolean equals(V a, V b) {
		if (a == null||b == null) {
			return a == b;
		} else {
			return a.equals(b);
		}
	}

	/**
	 * Declare that this liveExpression depends on some other live expression. This ensures
	 * that this expression will be refreshed if the value of the other expression changes.
	 */
	public <O> LiveExpression<V> dependsOn(final LiveExpression<O> other) {
		final ValueListener<O> listener = new ValueListener<O>() {
			public void gotValue(LiveExpression<O> exp, O value) {
				refresh();
			}
		};
		other.addListener(listener);
		onDispose(new DisposeListener() {
			public void disposed(Disposable disposed) {
				other.removeListener(listener);
			}
		});
		return this;
	};


	protected abstract V compute();

	protected void changed() {
		if (fListeners != null) {
			Object[] listeners = fListeners.getListeners();
			for (Object _l : listeners) {
				@SuppressWarnings("unchecked")
				ValueListener<V> l = (ValueListener<V>) _l;
				l.gotValue(this, value);
			}
		}
	}

	/**
	 * Retrieves the current (cached) value of the expression.
	 */
	public V getValue() {
		return value;
	}

	/**
	 * Registers a ValueListener which is automatically removed when
	 * its 'owner' is disposed.
	 */
	public void onChange(OnDispose owner, ValueListener<V> l) {
		addListener(l);
		owner.onDispose((o) -> removeListener(l));
	}
	
	/**
	 * Register a ValueListener which can be removed by invoking the
	 * returned {@link Disposable}.
	 */
	public Disposable onChange(ValueListener<V> l) {
		addListener(l);
		return () -> removeListener(l);
	}
	
	public void addListener(ValueListener<V> l) {
		ListenerList fListeners = this.fListeners;
		if (fListeners != null) {
			fListeners.add(l);
			l.gotValue(this, value);
		}
	}

	public void removeListener(ValueListener<V> l) {
		if (fListeners != null) {
			fListeners.remove(l);
		}
	}

	public static <V> LiveExpression<V> constant(final V value) {
		//TODO: Constant expression can be implemented more efficiently they do not need really any of the
		// super class infrastructure since the value of a constant can never change.
		return new LiveExpression<V>(value) {

			@Override
			protected V compute() {
				return value;
			}

			@Override
			public void addListener(ValueListener<V> l) {
				l.gotValue(this, value);
				//Beyond the initial notification ... we ignore listeners... we will never notify again since
				//constants can't change
			}
			@Override
			public void removeListener(ValueListener<V> l) {
				//Ignore all listeners we will never notify anyone since
				//constants can't change
			}

			/* (non-Javadoc)
			 * @see org.springsource.ide.eclipse.gradle.core.util.expression.LiveExpression#refresh()
			 */
			@Override
			public void refresh() {
				//Ignore all refreshes... no need to refresh anything since
				//constants can't change
			}
		};
	}

	/**
	 * Filter liveexp value to elimante values that are not instances of a given class.
	 * <p>
	 * When target expression has a value is not an instance of the class then the
	 * resulting expression's value is 'null' otherwise its value is the same as the
	 * target expression.
	 */
	public <T> LiveExpression<T> filter(final Class<T> klass) {
		final LiveExpression<V> target = this;
		return new LiveExpression<T>() {
			{
				dependsOn(target);
			}
			@SuppressWarnings("unchecked")
			@Override
			protected T compute() {
				V input = target.getValue();
				if (klass.isInstance(input)) {
					return (T) input;
				} else {
					return null;
				}
			}
		};
	}
	
	@SuppressWarnings("unchecked")
	public <T> LiveExpression<T> unsafeCast(Class<T> klass) {
		Object self = this;
		return (LiveExpression<T>) self;
	}
	
	/**
	 * Create a LiveExpression that copies the value this liveexp with 
	 * a synchronization delay. 
	 * <p>
	 * This is useful to avoid 'bursty' changes to cause flickering in the
	 * ui by only propagating the changes 
	 * @param delay
	 * @return
	 */
	public <R> LiveExpression<V> delay(Duration delay) {
		final LiveExpression<V> target = this;
		AsyncLiveExpression<V> result = new AsyncLiveExpression<V>(null) {
			{
				setRefreshDelay(delay.toMillis());
				dependsOn(target);
			}
			@Override
			protected V compute() {
				return target.getValue();
			}
		};
		return result;
	}

	public <R> LiveExpression<R> apply(final Function<V,R> fun) {
		final LiveExpression<V> target = this;
		LiveExpression<R> result = new LiveExpression<R>() {
			{
				dependsOn(target);
			}
			@Override
			protected R compute() {
				return fun.apply(target.getValue());
			}
		};
		return result;
	}
	
	/**
	 * Applies a given 'factory' function to this live expression. And produces
	 * a LiveExpression who's value is the result of calling the function on 
	 * this liveexp's value.
	 * <p>
	 * The resulting liveExp's lifecycle is automatically linked with this liveExp.
	 * So, when this liveExp is disposed then the resulting liveExp is also disposed.
	 * <p>
	 * Additionally, the object's produced by the factory are also considered to be
	 * 'owned' by this liveExp. So the resulting objects are also disposed as needed.
	 */
	public <R> LiveExpression<R> applyFactory(final Function<V,R> factory) {
		@SuppressWarnings("resource")
		LiveVariable<R> var = new OldValueDisposer<R>(this).getVar();
		this.onChange((_e, _v) -> {
			V input = _e.getValue();
			var.setValue(input == null ? null : factory.apply(_e.getValue()));
		});
		return var;
	}
	
	/**
	 * Chain a function that returns another livexp with this livexp. 
	 * <p>
	 * The returned livexp tracks the value of the livexp returned by the function
	 * and is updated whenever either the value of this liveExp changes, or
	 * the value of the returned liveExp changes.
	 * <p>
	 * IMPORTANT: LiveExp(s) returned by the function are not automatically disposed.
	 * (But any listeners attached to it by the returned LiveExp are removed automatically).
	 */
	public <R> DelegatingLiveExp<R> then(Function<V, LiveExpression<R>> fun) {
		LiveExpression<LiveExpression<R>> resultExpExp = this.apply(fun);
		DelegatingLiveExp<R> result = new DelegatingLiveExp<>();
		result.onDispose(d -> resultExpExp.dispose());
		resultExpExp.onChange(result, (e, v) -> 
			result.setDelegate(resultExpExp.getValue())
		);
		return result;
	}

	public Object getOwner() {
		return owner;
	}

	@SuppressWarnings("unchecked")
	public <T> T getOwner(Class<T> cls) {
		if (owner != null && cls.isAssignableFrom(owner.getClass())) {
			return (T) owner;
		}
		return null;
	}

	@Override
	public void dispose() {
		Object[] disposeHandlers = null;
		synchronized (this) {
			if (fDisposeHandlers != null) {
				disposeHandlers = fDisposeHandlers.getListeners();
				fDisposeHandlers = null;
				fListeners = null;
			}
		}
		if (disposeHandlers != null) {
			for (Object _handler : disposeHandlers) {
				DisposeListener handler = (DisposeListener) _handler;
				handler.disposed(this);
			}
		}
	}

	@Override
	public void onDispose(DisposeListener listener) {
		boolean alreadyDisposed = false;
		synchronized (this) {
			if (this.fDisposeHandlers == null) {
				alreadyDisposed = true;
			} else {
				this.fDisposeHandlers.add(listener);
			}
		}
		if (alreadyDisposed) {
			listener.disposed(this);
		}
	}

	public void setOwner(Object owner) {
		this.owner = owner;
	}

}
