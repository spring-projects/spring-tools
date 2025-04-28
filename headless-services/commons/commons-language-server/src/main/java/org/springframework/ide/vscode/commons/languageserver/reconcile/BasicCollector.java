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
package org.springframework.ide.vscode.commons.languageserver.reconcile;

import java.util.Collection;

public class BasicCollector<T> implements ICollector<T> {

	private final Collection<T> collection;

	public BasicCollector(Collection<T> collection) {
		this.collection = collection;
	}

	@Override
	public void beginCollecting() {
	}

	@Override
	public void endCollecting() {
	}

	@Override
	public void accept(T t) {
		collection.add(t);
	}

}
