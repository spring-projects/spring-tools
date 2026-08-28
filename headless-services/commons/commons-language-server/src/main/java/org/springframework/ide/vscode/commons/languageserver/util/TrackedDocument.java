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
package org.springframework.ide.vscode.commons.languageserver.util;

import java.util.List;

import org.springframework.ide.vscode.commons.languageserver.quickfix.Quickfix;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

import com.google.common.collect.ImmutableList;

public class TrackedDocument {

	private final TextDocument doc;
	private List<Quickfix<?>> quickfixes = ImmutableList.of();
	private int openCount = 0;

	public TrackedDocument(TextDocument doc) {
		this.doc = doc;
	}

	public TextDocument getDocument() {
		return doc;
	}

	public void setQuickfixes(List<Quickfix<?>> quickfixes) {
		this.quickfixes = quickfixes;
	}

	public List<Quickfix<?>> getQuickfixes() {
		return quickfixes;
	}

	public TrackedDocument open() {
		openCount++;
		return this;
	}

	public boolean close() {
		openCount--;
		return openCount <= 0;
	}

	public int getOpenCount() {
		return openCount;
	}

}
