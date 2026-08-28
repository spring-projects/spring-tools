/*******************************************************************************
 * Copyright (c) 2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.commons.util;

import java.util.function.Supplier;

/**
 * Utility class to simplify creation of lazy-initialized fields.
 */
public class Lazy<T> {
	
	private T value;
	
	public synchronized T load(Supplier<T> loader) {
		if (value == null) {
			value = loader.get();
			Assert.isNotNull(value);
		}
		return value;
	}
	
}
