/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.languageserver.java;

import java.util.function.Consumer;

import org.springframework.ide.vscode.commons.java.IJavaProject;

/**
 * Projects Observer. Able to add/remove project listeners which are notified on project changes
 *
 * @author Alex Boyko
 *
 */
public interface ProjectObserver {

	interface Listener {
		void created(IJavaProject project);
		void changed(IJavaProject project, boolean clean);
		void deleted(IJavaProject project);
		default void supported() { }
	}
	default boolean isSupported() { return true; };
	void addListener(Listener listener);
	void removeListener(Listener listener);

	ProjectObserver NULL = new ProjectObserver() {
		@Override
		public void addListener(Listener listener) {
		}

		@Override
		public void removeListener(Listener listener) {
		}

		@Override
		public boolean isSupported() {
			return false;
		}
	};

	/**
	 * Convenience method to create a listener that does the same thing regardless of
	 * whether a project was created / changed / deleted.
	 */
	static Listener onAny(Consumer<IJavaProject> doit) {
		return new Listener() {
			@Override
			public void created(IJavaProject project) {
				doit.accept(project);
			}


			@Override
			public void changed(IJavaProject project, boolean clean) {
				doit.accept(project);
			}

			@Override
			public void deleted(IJavaProject project) {
				doit.accept(project);
			}

		};
	}

}
