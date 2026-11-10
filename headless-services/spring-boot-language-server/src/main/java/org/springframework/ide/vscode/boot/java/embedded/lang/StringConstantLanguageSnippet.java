/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.embedded.lang;

import java.util.Collections;
import java.util.List;

import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.Region;

public class StringConstantLanguageSnippet implements EmbeddedLanguageSnippet {
	
	final private int offset;
	final private int length;
	final private String value;
	
	public StringConstantLanguageSnippet(int offset, int length, String value) {
		this.offset = offset;
		this.length = length;
		this.value = value;
	}

	@Override
	public List<IRegion> toJavaRanges(IRegion range) {
		return Collections.emptyList();
	}

	@Override
	public IRegion toSingleJavaRange(IRegion range) {
		// the constant's text is not in this file, so point at the expression referencing it
		return new Region(offset, length);
	}

	@Override
	public int toJavaOffset(int offset) {
		return this.offset;
	}

	@Override
	public String getText() {
		return value;
	}

}
