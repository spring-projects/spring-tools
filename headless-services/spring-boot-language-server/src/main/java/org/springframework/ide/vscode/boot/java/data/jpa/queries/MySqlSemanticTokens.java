/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.jpa.queries;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.springframework.ide.vscode.boot.java.embedded.lang.AntlrUtils;
import org.springframework.ide.vscode.boot.java.spel.SpelSemanticTokens;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokenData;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokensDataProvider;
import org.springframework.ide.vscode.parser.mysql.MySqlLexer;
import org.springframework.ide.vscode.parser.mysql.MySqlParser;
import org.springframework.ide.vscode.parser.mysql.MySqlParser.ParameterContext;
import org.springframework.ide.vscode.parser.mysql.MySqlParser.UdfFunctionCallContext;
import org.springframework.ide.vscode.parser.mysql.MySqlParserBaseListener;

public class MySqlSemanticTokens implements SemanticTokensDataProvider {

	private static List<String> TOKEN_TYPES = List.of("keyword", "type", "string", "number", "operator",
			"variable", "comment", "parameter", "method");

	private final Optional<SpelSemanticTokens> optSpelTokens;
	private final Optional<Consumer<RecognitionException>> parseErrorHandler;
	
	public MySqlSemanticTokens(Optional<SpelSemanticTokens> optSpelTokens) {
		this(optSpelTokens, Optional.empty());
	}

	public MySqlSemanticTokens(Optional<SpelSemanticTokens> optSpelTokens, Optional<Consumer<RecognitionException>> parseErrorHandler) {
		this.optSpelTokens = optSpelTokens;
		this.parseErrorHandler = parseErrorHandler;
	}
	
	@Override
	public List<String> getTokenTypes() {
		LinkedHashSet<String> tokenTypes = new LinkedHashSet<>(TOKEN_TYPES);
		tokenTypes.addAll(optSpelTokens.map(s -> s.getTokenTypes()).orElse(Collections.emptyList()));
		return tokenTypes.stream().toList();
	}

	@Override
	public List<String> getTypeModifiers() {
		return optSpelTokens.map(s -> s.getTypeModifiers()).orElse(Collections.emptyList());
	}

	@Override
	public List<SemanticTokenData> computeTokens(String text) {
		MySqlLexer lexer = new MySqlLexer(CharStreams.fromString(text));
		CommonTokenStream antlrTokens = new CommonTokenStream(lexer);
		MySqlParser parser = new MySqlParser(antlrTokens);
		
		Map<Token, String> semantics = new HashMap<>();
		
		List<SemanticTokenData> tokens = new ArrayList<>();
		
		lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
		parser.removeErrorListener(ConsoleErrorListener.INSTANCE);
		
		parser.addParseListener(new MySqlParserBaseListener() {

			private void processTerminalNode(TerminalNode node) {
				int type = node.getSymbol().getType();
				switch (type) {
				case MySqlParser.SPEL:
					tokens.addAll(JpqlSemanticTokens.computeTokensFromSpelNode(node, 0, optSpelTokens));
					break;
				case MySqlParser.ID:
					semantics.put(node.getSymbol(), "variable");
					break;
				case MySqlParser.DOT_ID:
					semantics.put(node.getSymbol(), "property");
					break;
				case MySqlParser.DOT:
					semantics.put(node.getSymbol(), "operator");
					break;
				case MySqlParser.NULL_LITERAL:
					semantics.put(node.getSymbol(), "number");
					break;
				case MySqlParser.FILESIZE_LITERAL:
					semantics.put(node.getSymbol(), "number");
					break;
				case MySqlParser.ZERO_DECIMAL:
				case MySqlParser.ONE_DECIMAL:
				case MySqlParser.TWO_DECIMAL:
					semantics.put(node.getSymbol(), "number");
					break;
				case MySqlParser.START_NATIONAL_STRING_LITERAL:
				case MySqlParser.STRING_LITERAL:
					semantics.put(node.getSymbol(), "string");
					break;
				case MySqlParser.STRING_CHARSET_NAME:
					semantics.put(node.getSymbol(), "type");
					break;
				case MySqlParser.HOST_IP_ADDRESS:
				case MySqlParser.LOCAL_ID:
				case MySqlParser.GLOBAL_ID:
					semantics.put(node.getSymbol(), "string");
					break;
				case MySqlParser.SPEC_MYSQL_COMMENT:
				case MySqlParser.COMMENT_INPUT:
				case MySqlParser.LINE_COMMENT:
					semantics.put(node.getSymbol(), "comment");
					break;
				default:
					if (MySqlParser.VAR_ASSIGN <= type && type <= MySqlParser.QUESTION_SYMB) {
						semantics.put(node.getSymbol(), "operator");
					} else if (MySqlParser.DECIMAL_LITERAL <= type && type <= MySqlParser.BIT_STRING) {
						semantics.put(node.getSymbol(), "number");
					} else if (MySqlParser.ABS <=  type && type <= MySqlParser.X_FUNCTION) {
						// Common function names
						semantics.put(node.getSymbol(), "keyword");
					} else if (MySqlParser.ADD <= type && type <= MySqlParser.ZEROFILL) {
						// Keywords
						// Common Keywords
						semantics.put(node.getSymbol(), "keyword");
					} else if (MySqlParser.TINYINT <= type && type <= MySqlParser.SERIAL) {
						// DATA TYPE Keywords
						semantics.put(node.getSymbol(), "type");
					} else if (MySqlParser.YEAR_MONTH <= type && type <= MySqlParser.DAY_MICROSECOND) {
						// Interval type Keywords
						semantics.put(node.getSymbol(), "type");
					} else if (MySqlParser.JSON_ARRAY <= type && type <= MySqlParser.PATH) {
						// JSON keywords
						semantics.put(node.getSymbol(), "keyword");
					} else if (MySqlParser.JSON_ARRAY <= type && type <= MySqlParser.PATH) {
						// Group function Keywords
						semantics.put(node.getSymbol(), "keyword");
					} else if (MySqlParser.CURRENT_DATE <= type && type <= MySqlParser.UTC_TIMESTAMP) {
						// Common function Keywords
						semantics.put(node.getSymbol(), "keyword");
					} else if (MySqlParser.ACCOUNT <= type && type <= MySqlParser.YES) {
						// Keywords, but can be ID
						// Common Keywords, but can be ID
						semantics.put(node.getSymbol(), "type");
					} else if (MySqlParser.EUR <= type && type <= MySqlParser.INTERNAL) {
						// Date format Keywords
						semantics.put(node.getSymbol(), "type");
					} else if (MySqlParser.QUARTER <= type && type <= MySqlParser.MICROSECOND) {
						// Date format Keywords
						semantics.put(node.getSymbol(), "type");
					} else if (MySqlParser.ADMIN <= type && type <= MySqlParser.XA_RECOVER_ADMIN) {
						// PRIVILEGES
						semantics.put(node.getSymbol(), "type");
					} else if (MySqlParser.ARMSCII8 <= type && type <= MySqlParser.UTF8MB4) {
						// Charsets
						semantics.put(node.getSymbol(), "type");
					} else if (MySqlParser.ARCHIVE <= type && type <= MySqlParser.TOKUDB) {
						// DB Engines
						semantics.put(node.getSymbol(), "type");
					} else if (MySqlParser.REPEATABLE <= type && type <= MySqlParser.SERIALIZABLE) {
						// Transaction Levels
						semantics.put(node.getSymbol(), "type");
					} else if (MySqlParser.GEOMETRYCOLLECTION <= type && type <= MySqlParser.POLYGON) {
						// Spatial data types
						semantics.put(node.getSymbol(), "type");
					}
				}
			}

			@Override
			public void visitTerminal(TerminalNode node) {
				processTerminalNode(node);
			}

			@Override
			public void visitErrorNode(ErrorNode node) {
				processTerminalNode(node);
			}
			
			

			@Override
			public void exitUdfFunctionCall(UdfFunctionCallContext fc) {
				if (fc.fullId() != null) {
					List<Token> ls = AntlrUtils.getAllLeafs(fc.fullId()).toList();
					if (!ls.isEmpty()) {
						semantics.put(ls.get(ls.size() - 1), "method");
					}
				}
			}

			@Override
			public void exitParameter(ParameterContext ctx) {
				if (ctx.dottedId() != null) {
					AntlrUtils.getAllLeafs(ctx.dottedId()).forEach(t -> semantics.put(t, "parameter"));
				}
				if (ctx.uid() != null) {
					AntlrUtils.getAllLeafs(ctx.uid()).forEach(t -> semantics.put(t, "parameter"));
				}
				if (ctx.decimalLiteral() != null) {
					AntlrUtils.getAllLeafs(ctx.decimalLiteral()).forEach(t -> semantics.put(t, "parameter"));
				}
			}
			
		});
		
		parser.addErrorListener(new ANTLRErrorListener() {
			
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
					String msg, RecognitionException e) {
				parseErrorHandler.ifPresent(h -> h.accept(e));
			}
			
			@Override
			public void reportContextSensitivity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, int prediction,
					ATNConfigSet configs) {
			}
			
			@Override
			public void reportAttemptingFullContext(Parser recognizer, DFA dfa, int startIndex, int stopIndex,
					BitSet conflictingAlts, ATNConfigSet configs) {
			}
			
			@Override
			public void reportAmbiguity(Parser recognizer, DFA dfa, int startIndex, int stopIndex, boolean exact,
					BitSet ambigAlts, ATNConfigSet configs) {
			}
		});
		
		
		parser.sqlStatements();
		
		semantics.entrySet().stream()
				.flatMap(e -> {
					int startIndex = e.getKey().getStartIndex();
					if (e.getKey().getType() == MySqlLexer.DOT_ID) {
						return Stream.of(
								// the prefix '.' is an operator
								new SemanticTokenData(startIndex,
										startIndex + 1, "operator",
										new String[0]),
								// the rest whatever it was meant to be initially
								new SemanticTokenData(startIndex + 1,
										startIndex + e.getKey().getText().length(), e.getValue(),
										new String[0])
						);
					} else {
						return Stream.of(new SemanticTokenData(startIndex,
								startIndex + e.getKey().getText().length(), e.getValue(),
								new String[0]));
					}
				})
				.forEach(tokens::add);
		
		Collections.sort(tokens);
		
		return tokens;

	}
	


}
