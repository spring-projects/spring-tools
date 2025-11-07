/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.tooling.ls.eclipse.commons;

import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

import org.eclipse.core.runtime.ListenerList;

public final class SpringIndexState {

	private ListenerList<Consumer<Set<String>>> listeners = new ListenerList<>();

	public void addStateChangedListener(Consumer<Set<String>> l) {
		listeners.add(l);
	}

	public void removeStateChangedListener(Consumer<Set<String>> l) {
		listeners.remove(l);
	}

	/*
	 * Ideally, this does not need to be synchronized, but synchronization is necessary if more than one server may notify about index changes to avoid potential errors.
	 */
	synchronized void indexed(Set<String> projectNames) {
		listeners.forEach(l -> l.accept(Collections.unmodifiableSet(projectNames)));
	}
}
