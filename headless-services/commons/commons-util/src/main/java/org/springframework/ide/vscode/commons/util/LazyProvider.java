/*******************************************************************************
 * Copyright (c) 2016-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.commons.util;

import java.util.function.Supplier;

/**
 * An abstract class implementing {@link Supplier}. The provided value
 * is computed on demand and cached once computed.
 * <p>
 * Subclass must implement the compute method.
 */
public abstract class LazyProvider<T> implements Supplier<T> {

	private boolean computed = false;
	private T cached = null;

	@Override
	public synchronized final T get() {
		if (!computed) {
			cached = compute();
		}
		return cached;
	}

	protected abstract T compute();

}
