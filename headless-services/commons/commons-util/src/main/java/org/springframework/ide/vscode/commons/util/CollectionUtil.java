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

import java.util.Collection;

/**
 * @author Kris De Volder
 */
public class CollectionUtil {

	/**
	 * Get some element of the collection (will be the first one
	 * found by its iterator, or null if the collection is empty).
	 * <p>
	 * Note that unless the collection is ordered, or has at most
	 * one element, then it may be unpredictable which element
	 * you will get.
	 */
	public static <E> E getAny(Collection<E> elements) {
		for (E e : elements) {
			return e;
		}
		return null;
	}

	public static <E> boolean hasElements(Collection<E> c) {
		return c != null && !c.isEmpty();
	}

}
