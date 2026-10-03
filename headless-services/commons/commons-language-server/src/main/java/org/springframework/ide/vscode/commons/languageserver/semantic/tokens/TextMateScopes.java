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
 * Translates the standard LSP semantic token types into TextMate scopes.
 * <p>
 * LSP4E in Eclipse hands the token type from the legend to the TM4E theme as is, i.e. as a TextMate scope. Themes
 * only define TextMate scopes (<code>keyword.operator</code>, <code>constant.numeric</code>...) and so
 * standard types such as <code>operator</code> or <code>number</code> are not colored. Only the few types that
 * happen to be top level TextMate scopes (<code>keyword</code>, <code>string</code>, <code>comment</code>,
 * <code>variable</code>) are. VS Code maps standard types to TextMate scopes itself.
 * <p>
 * This class is only the mapping. It is up to the calling code to decide when to use it, i.e. when the client is Eclipse
 * and the token types are shown by LSP4E and TM4E.
 * <p>
 * The mapping is VS Code's default mapping (the first scope VS Code probes for a type). Types without
 * a mapping, i.e. non standard ones, are left as is.
 *
 * @author Alex Boyko
 */
public class TextMateScopes {

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
