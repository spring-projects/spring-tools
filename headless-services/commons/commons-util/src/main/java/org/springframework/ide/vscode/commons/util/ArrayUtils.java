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
package org.springframework.ide.vscode.commons.util;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * @author Kris De Volder
 */
public class ArrayUtils {

	public static <T> boolean hasElements(T[] arr) {
		return arr != null && arr.length > 0;
	}

	public static <T> T lastElement(T[] arr) {
		if (hasElements(arr)) {
			return arr[arr.length-1];
		}
		return null;
	}


	public static <T> T firstElement(T[] arr) {
		if (hasElements(arr)) {
			return arr[0];
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static <T> T[] remove(T[] array, T element) {
		ArrayList<T> toKeep = new ArrayList<>(Arrays.asList(array));
		toKeep.remove(element);
		T[] newArray =(T[]) Array.newInstance(array.getClass().getComponentType(), toKeep.size());
		return toKeep.toArray(newArray);
	}
}
