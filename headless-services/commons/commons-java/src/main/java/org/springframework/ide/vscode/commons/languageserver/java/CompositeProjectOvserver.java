/*******************************************************************************
 * Copyright (c) 2017, 2021 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.java;

import java.util.List;

/**
 * Project Observer that is able to act as a single project observer for a list of observers
 * 
 * @author Alex Boyko
 *
 */
public class CompositeProjectOvserver implements ProjectObserver, ProjectChangeNotifier {
	
	private List<ProjectObserver> observers;
	
	public CompositeProjectOvserver(List<ProjectObserver> observers) {
		this.observers = observers;
	}
	
	@Override
	public void addListener(Listener listener) {
		observers.forEach(o -> o.addListener(listener));
	}

	@Override
	public void removeListener(Listener listener) {
		observers.forEach(o -> o.removeListener(listener));
	}

	@Override
	public boolean isSupported() {
		return observers.stream().map(o -> o.isSupported()).reduce(false, (a, b) -> a || b);
	}

	@Override
	public void notifyProjectsChanged() {
		for (ProjectObserver o : observers) {
			if (o instanceof ProjectChangeNotifier) {
				((ProjectChangeNotifier) o).notifyProjectsChanged();
			}
		}
	}

}
