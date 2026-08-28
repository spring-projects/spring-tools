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

import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class Streams {

	/**
	 * Returns the element in the stream, if the stream has exactly one element.
	 * Otherwise returns null
	 */
	public static <T> T getSingle(Stream<T> stream) {
		ArrayList<T> els = stream
				.limit(2) //Don't need more than 2 to know there is more than 1
				.collect(Collectors.toCollection(() -> new ArrayList<>(2)));
		return els.size() == 1 ? els.get(0) : null;
	}

	/**
	 * Like java.util.Stream.of but returns Stream.empty if the element is null
	 */
	public static <T> Stream<T> fromNullable(T e) {
		return e == null ? Stream.empty() : Stream.of(e);
	}

	public static <T> Stream<T> fromIterable(Iterable<T> target) {
		return StreamSupport.stream(target.spliterator(), false);
	}

}
