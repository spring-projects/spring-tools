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
package org.springframework.ide.vscode.commons.util;

/**
 * Interface for notifying about file system changes.
 * 
 * @author Alex Boyko
 */
public interface FileChangeNotifier {

	void notifyFileCreated(String uri);
	
	void notifyFileChanged(String uri);
	
	void notifyFileDeleted(String uri);

	/** Batch variant of {@link #notifyFileCreated(String)}; one call per URI unless overridden. */
	default void notifyFilesCreated(String[] uris) {
		for (String uri : uris) {
			notifyFileCreated(uri);
		}
	}

	/** Batch variant of {@link #notifyFileChanged(String)}; one call per URI unless overridden. */
	default void notifyFilesChanged(String[] uris) {
		for (String uri : uris) {
			notifyFileChanged(uri);
		}
	}

	/** Batch variant of {@link #notifyFileDeleted(String)}; one call per URI unless overridden. */
	default void notifyFilesDeleted(String[] uris) {
		for (String uri : uris) {
			notifyFileDeleted(uri);
		}
	}

}
