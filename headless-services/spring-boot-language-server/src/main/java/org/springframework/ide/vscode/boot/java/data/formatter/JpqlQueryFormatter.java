/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 ******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.formatter;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.Token;
import org.springframework.ide.vscode.parser.jpql.JpqlLexer;
import org.springframework.ide.vscode.parser.jpql.JpqlParser;

public class JpqlQueryFormatter implements QueryFormatter {

	@Override
	public String format(String query) {
		try {
			JpqlLexer lexer = new JpqlLexer(CharStreams.fromString(query));
			lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);

			StringBuilder sb = new StringBuilder();

			int lastStopIndex = -1;
			for (Token token = lexer.nextToken(); token.getType() != Token.EOF; token = lexer.nextToken()) {
				boolean newlineAdded = false;
				if (isNewlineKeyword(token)) {
					sb.append("\n        ");
					newlineAdded = true;
				}

				if (!newlineAdded && lastStopIndex != -1 && token.getStartIndex() > lastStopIndex + 1) {
					sb.append(" ");
				} else if (!newlineAdded && sb.length() == 0) {
					sb.append("\n        ");
				}

				sb.append(token.getText());
				lastStopIndex = token.getStopIndex();
			}
			return sb.toString();
		} catch (Exception e) {
			return "\n        " + query;
		}
	}

	private boolean isNewlineKeyword(Token token) {
		int type = token.getType();
		if (type == JpqlParser.SELECT || type == JpqlParser.FROM || type == JpqlParser.WHERE 
				|| type == JpqlParser.ORDER || type == JpqlParser.GROUP || type == JpqlParser.HAVING
				|| type == JpqlParser.UPDATE || type == JpqlParser.DELETE
				|| type == JpqlParser.SET
				|| type == JpqlParser.JOIN || type == JpqlParser.LEFT || type == JpqlParser.INNER) {
			return true;
		}
		String text = token.getText();
		if ("INSERT".equalsIgnoreCase(text) || "VALUES".equalsIgnoreCase(text) || "RIGHT".equalsIgnoreCase(text)) {
			return true;
		}
		return false;
	}

}
