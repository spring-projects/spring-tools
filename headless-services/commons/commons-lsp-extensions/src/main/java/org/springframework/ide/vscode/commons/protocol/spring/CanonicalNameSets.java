/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.protocol.spring;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.stream.Collectors;

/**
 * Shares the sets of type names index elements hold - annotation types, supertypes - across all
 * elements: most of them carry one of only a few combinations, like {@code @Service} with its
 * meta-annotations, and would otherwise each hold a copy of their own, down to the names themselves.
 *
 * <p>The shared sets are immutable and compact ({@link Set#copyOf}), and held weakly, so that a
 * set no element refers to anymore can be collected.
 *
 * @author Martin Lippert
 */
public final class CanonicalNameSets {

	// the value refers to the key weakly as well, so that it doesn't keep its own entry alive
	private static final Map<Set<String>, WeakReference<Set<String>>> SETS = new WeakHashMap<>();

	private CanonicalNameSets() {
	}

	/**
	 * The shared, immutable set equal to the given one - null for null.
	 */
	public static Set<String> of(Set<String> names) {
		if (names == null) {
			return null;
		}
		if (names.isEmpty()) {
			return Set.of();
		}

		synchronized (SETS) {
			// looked up as it is: only a set not seen yet is copied - most are repeats
			Set<String> known = known(names);
			if (known == null) {
				known = names.stream().map(String::intern).collect(Collectors.toUnmodifiableSet());
				SETS.put(known, new WeakReference<>(known));
			}
			return known;
		}
	}

	private static Set<String> known(Set<String> names) {
		WeakReference<Set<String>> known = SETS.get(names);
		return known == null ? null : known.get();
	}

}
