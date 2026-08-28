/*******************************************************************************
 * Copyright (c) 2016-2017 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/

package org.springframework.ide.vscode.boot.metadata.types;

import java.util.ArrayList;
import java.util.StringTokenizer;

import org.springframework.ide.vscode.commons.util.StringUtil;

/**
 * Converts types in notation used by spring properties metadata into a 'Structured' form
 *
 * @author Kris De Volder
 */
public class TypeParser {

	private static final String DELIM = "<>,";

	/**
	 * Wrapper around StringTokenizer that manages a single lookahead token.
	 * So it can implement 'peekToken()' method.
	 */
	private static class Tokener {
		private String lookahead;
		private StringTokenizer tokens;
		public Tokener(String input) {
			this.tokens = new StringTokenizer(input, DELIM, true);
		}

		/**
		 * Fetch next token. Returns null if there are no more tokens.
		 */
		public String nextToken() {
			if (lookahead != null) {
				try {
					return lookahead;
				} finally {
					lookahead = null;
				}
			} else if (tokens.hasMoreTokens()) {
				return tokens.nextToken();
			}
			return null;
		}
		/**
		 * Fetch the next token without consuming it.
		 * Returns null if there are no more tokens.
		 */
		public String peekToken() {
			if (lookahead != null) {
				return lookahead;
			} else if (tokens.hasMoreTokens()) {
				lookahead = tokens.nextToken();
				return lookahead;
			}
			return null;
		}
	}

	private Tokener input;

	private TypeParser(String input) {
		this.input = new Tokener(input);
	}

	public static Type parse(String str) {
		if (StringUtil.hasText(str)) {
			return new TypeParser(str).parseType();
		}
		return null;
	}

	private Type parseType() {
		String ident = input.nextToken();
		String token = input.peekToken();
		if ("<".equals(token)) {
			ArrayList<Type> params = parseParams();
			return new Type(ident, params.toArray(new Type[params.size()]));
		} else {
			return new Type(ident, null);
		}
	}

	private ArrayList<Type> parseParams() {
		skip("<");
		try {
			return parseParamList(new ArrayList<Type>());
		} finally {
			skip(">");
		}
	}

	private ArrayList<Type> parseParamList(ArrayList<Type> params) {
		//parse params separate by ",'
		String tok = input.peekToken();
		if (isIdent(tok)) {
			params.add(parseType());
			if (skip(",")) {
				return parseParamList(params);
			}
		}
		return params;
	}

	/**
	 * Skip an expected token, or do nothing if the next token is
	 * something unexpected.
	 * @return whether token was skipped.
	 */
	private boolean skip(String expected) {
		String t = input.peekToken();
		if (expected.equals(t)) {
			input.nextToken();
			return true;
		}
		return false;
	}

	public boolean isIdent(String token) {
		return token != null && !isSeparator(token);
	}

	private boolean isSeparator(String token) {
		if (token != null && token.length() == 1) {
			int len = DELIM.length();
			char c = token.charAt(0);
			for (int i = 0; i < len; i++) {
				if (DELIM.charAt(i) == c) {
					return true;
				}
			}
		}
		return false;
	}

}
