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

}
