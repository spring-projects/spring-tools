/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.common;

import org.springframework.ide.vscode.boot.metadata.types.Type;
import org.springframework.ide.vscode.boot.metadata.types.TypeUtil;
import org.springframework.ide.vscode.commons.languageserver.util.PrefixFinder;
import org.springframework.ide.vscode.commons.util.text.IDocument;

/**
 * Finds the prefix of a property value for completions. This is the last word of the value, i.e. it does not
 * go beyond whitespace and comma, unless the type of the value says otherwise. A CRON expression is made of
 * multiple parts and is completed as a whole, the same way it is done in Java code.
 *
 * @author Alex Boyko
 */
public class ValuePrefixFinder extends PrefixFinder {

	/**
	 * Takes the whole value up to the cursor as the prefix. Only the quote of a quoted YAML value ends the prefix.
	 */
	private static final PrefixFinder WHOLE_VALUE = new PrefixFinder() {
		@Override
		protected boolean isPrefixChar(char c) {
			return c != '"' && c != '\'';
		}
	};

	@Override
	protected boolean isPrefixChar(char c) {
		return CommonLanguageTools.isValuePrefixChar(c);
	}

	/**
	 * @param type type of the value, may be <code>null</code> if not known. In that case the prefix is the last word.
	 */
	public String getPrefix(IDocument doc, int offset, int lowerBound, Type type) {
		if (TypeUtil.isCron(type)) {
			return WHOLE_VALUE.getPrefix(doc, offset, lowerBound);
		}
		return getPrefix(doc, offset, lowerBound);
	}

}
