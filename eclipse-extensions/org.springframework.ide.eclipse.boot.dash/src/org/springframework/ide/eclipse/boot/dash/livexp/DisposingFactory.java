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
package org.springframework.ide.eclipse.boot.dash.livexp;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.springsource.ide.eclipse.commons.livexp.core.DisposeListener;
import org.springsource.ide.eclipse.commons.livexp.core.LiveExpression;
import org.springsource.ide.eclipse.commons.livexp.core.ValueListener;
import org.springsource.ide.eclipse.commons.livexp.ui.Disposable;

import org.springsource.ide.eclipse.commons.livexp.core.ObservableSet;

import com.google.common.collect.ImmutableSet;

/**
 * A disposing factory creates objects of some type V based on some
 * parameter of type K. It guarantees that the same element is returned
 * when the same key is passed in as argument, provided that the
 * key is one of the currently 'validKeys'.
 * <p>
 * The factory is also responsible for monitoring the set of 'validKeys' and calling
 * the dispose method on the values associated with keys that are no longer
 * valid.
 *
 * @author Kris De Volder
 */
public abstract class DisposingFactory<K,V extends Disposable> implements Disposable {

	private ObservableSet<K> validKeys;
	private Map<K,V> cachedInstances = new HashMap<>();
	private ValueListener<ImmutableSet<K>> validKeyListener = null;

	public DisposingFactory(ObservableSet<K> validKeys) {
		this.validKeys = validKeys;
		validKeys.onDispose(new DisposeListener() {
			public void disposed(Disposable disposed) {
				DisposingFactory.this.dispose();
			}
		});
	}

	protected abstract V create(K key);

	public synchronized V createOrGet(K key) {
		ImmutableSet<K> valid = getValidKeys();
		if (valid.contains(key)) {
			enableValidKeyTracking();
			V instance = cachedInstances.get(key);
			if (instance == null) {
				instance=create(key);
				if (instance != null) {
					cachedInstances.put(key, instance);
				}
			}
			return instance;
		}
		return null;
	}

	private void enableValidKeyTracking() {
		if (validKeyListener == null) {
			validKeys.addListener(validKeyListener = new ValueListener<ImmutableSet<K>>() {
				public void gotValue(LiveExpression<ImmutableSet<K>> exp, ImmutableSet<K> value) {
					retainOnlyValidKeys();
				}
			});
		}

	}

	private ImmutableSet<K> getValidKeys() {
		if (validKeys != null) {
			return validKeys.getValues();
		}
		return ImmutableSet.of();
	}

	@Override
	public synchronized void dispose() {
		if (validKeys != null) {
			if (validKeyListener != null) {
				validKeys.removeListener(validKeyListener);
			}
			validKeys = null;
			retainOnlyValidKeys();
			cachedInstances = null;
		}
	}

	private synchronized void retainOnlyValidKeys() {
		if (cachedInstances != null) {
			ImmutableSet<K> valid = getValidKeys();
			Iterator<Entry<K, V>> iter = cachedInstances.entrySet().iterator();
			while (iter.hasNext()) {
				Entry<K, V> e = iter.next();
				K k = e.getKey();
				if (valid.contains(k)) {
					//keep
				} else {
					e.getValue().dispose();
					iter.remove();
				}
			}
		}
	}

}
