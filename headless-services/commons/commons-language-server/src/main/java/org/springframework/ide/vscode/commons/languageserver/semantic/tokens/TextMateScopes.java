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
package org.springframework.ide.vscode.commons.languageserver.semantic.tokens;

import java.util.Map;

/**
 * Maps the standard LSP semantic token types to TextMate scopes, using VS Code's defaults. For clients, such as
 * LSP4E in Eclipse, that look up the color of a token type in a TextMate theme and don't map the types themselves.
 * Types without a mapping are left as is. The caller decides when to use it.
 *
 * @author Alex Boyko
 */
public final class TextMateScopes {

	private static final Map<String, String> SCOPES = Map.ofEntries(
			Map.entry("comment", "comment"),
			Map.entry("string", "string"),
			Map.entry("keyword", "keyword.control"),
			Map.entry("number", "constant.numeric"),
			Map.entry("regexp", "constant.regexp"),
			Map.entry("operator", "keyword.operator"),
			Map.entry("namespace", "entity.name.namespace"),
			Map.entry("type", "entity.name.type"),
			Map.entry("struct", "entity.name.type.struct"),
			Map.entry("class", "entity.name.type.class"),
			Map.entry("interface", "entity.name.type.interface"),
			Map.entry("enum", "entity.name.type.enum"),
			Map.entry("typeParameter", "entity.name.type.parameter"),
			Map.entry("function", "entity.name.function"),
			Map.entry("method", "entity.name.function.member"),
			Map.entry("macro", "entity.name.function.preprocessor"),
			Map.entry("variable", "variable.other.readwrite"),
			Map.entry("parameter", "variable.parameter"),
			Map.entry("property", "variable.other.property"),
			Map.entry("enumMember", "variable.other.enummember"),
			Map.entry("event", "variable.other.event"),
			Map.entry("decorator", "entity.name.decorator")
	);

	private TextMateScopes() {
	}

	/**
	 * @return the TextMate scope for the token type if the type is a standard LSP type, otherwise the type itself
	 */
	public static String scopeFor(String tokenType) {
		return SCOPES.getOrDefault(tokenType, tokenType);
	}

}
