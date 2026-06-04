/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils.test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.ide.vscode.commons.languageserver.java.ProjectChangeNotifier;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;

public class MockProjectObserver implements ProjectObserver, ProjectChangeNotifier {

	List<Listener> listeners = new ArrayList<>();

	@Override
	synchronized public void addListener(Listener l) {
		listeners.add(l);
	}

	@Override
	synchronized public void removeListener(Listener l) {
		listeners.remove(l);
	}

	@Override
	public void notifyProjectsChanged(boolean clean) {
		// Mock implementation
	}

	public synchronized void doWithListeners(Consumer<Listener> action) {
		for (Listener l : listeners) {
			action.accept(l);
		}
	}
}