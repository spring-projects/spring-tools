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
package org.springframework.ide.vscode.commons.languageserver.util;

public class OS {
	
	public static boolean isWindows() {
		String os = System.getProperty("os.name");
		if (os != null) {
			return os.toLowerCase().indexOf("win") >= 0;
		}
		return false;
	}

}
