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
package org.springframework.ide.eclipse.boot.dash.util;

import java.util.Collection;

import com.google.common.collect.ImmutableSet;

public class CollectionUtils {

	public static <T> T getSingle(Collection<T> configs) {
		if (configs.size() == 1) {
			for (T t : configs) {
				return t;
			}
		}
		return null;
	}

	public static <T> T getAny(Collection<T> c) {
		if (c != null && !c.isEmpty()) {
			for (T t : c) {
				return t;
			}
		}
		return null;
	}

	public static <T> T getAnyOr(Collection<T> c, T orElse) {
		T it = getAny(c);
		return it == null ? orElse : it;
	}
}
