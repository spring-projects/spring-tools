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
package org.springframework.ide.vscode.parser.jpql;

import java.util.Set;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Formats a JPQL query string into a nicely indented multi-line format.
 * <p>
 * Uses the ANTLR-generated {@link JpqlParser} to parse the query, then walks
 * the parse tree to emit formatted output with newlines at clause boundaries.
 * <p>
 * Formatting rules:
 * <ul>
 *   <li>Major clauses (SELECT, FROM, WHERE, GROUP BY, HAVING, ORDER BY, UPDATE, SET, DELETE) start on a new line</li>
 *   <li>JOIN clauses start on a new line, indented one level</li>
 *   <li>AND/OR conditions in WHERE/HAVING start on a new line, indented under the clause</li>
 *   <li>Subqueries increase indentation</li>
 * </ul>
 */
public class JpqlQueryFormatter {

	private static final String DEFAULT_INDENT = "  ";

	/**
	 * Set of token types that should NOT have a space before them.
	 */
	private static final Set<Integer> NO_SPACE_BEFORE = Set.of(
			JpqlParser.T__3, // '.'
			JpqlParser.T__2, // ')'
			JpqlParser.T__0  // ','
	);

	/**
	 * Set of token types that should NOT have a space after them.
	 * Note: function-like keywords (COUNT, AVG, etc.) are handled separately
	 * in {@link #shouldSuppressSpaceBeforeParen(int)}.
	 */
	private static final Set<Integer> NO_SPACE_AFTER = Set.of(
			JpqlParser.T__3, // '.'
			JpqlParser.T__1, // '('
			JpqlParser.T__13, // ':'
			JpqlParser.T__14  // '?'
	);

	/**
	 * Set of token types for function-like keywords where '(' should
	 * follow immediately without a space, e.g. COUNT(...), AVG(...).
	 */
	private static final Set<Integer> FUNCTION_LIKE_TOKENS = Set.of(
			JpqlParser.COUNT,
			JpqlParser.AVG,
			JpqlParser.MAX,
			JpqlParser.MIN,
			JpqlParser.SUM,
			JpqlParser.LENGTH,
			JpqlParser.LOCATE,
			JpqlParser.ABS,
			JpqlParser.CEILING,
			JpqlParser.EXP,
			JpqlParser.FLOOR,
			JpqlParser.LN,
			JpqlParser.SIGN,
			JpqlParser.SQRT,
			JpqlParser.MOD,
			JpqlParser.POWER,
			JpqlParser.ROUND,
			JpqlParser.SIZE,
			JpqlParser.INDEX,
			JpqlParser.CONCAT,
			JpqlParser.SUBSTRING,
			JpqlParser.TRIM,
			JpqlParser.LOWER,
			JpqlParser.UPPER,
			JpqlParser.COALESCE,
			JpqlParser.NULLIF,
			JpqlParser.FUNCTION,
			JpqlParser.EXTRACT,
			JpqlParser.TYPE,
			JpqlParser.KEY,
			JpqlParser.VALUE,
			JpqlParser.ENTRY,
			JpqlParser.OBJECT,
			JpqlParser.TREAT,
			JpqlParser.IDENTIFICATION_VARIABLE // for constructor names like com.example.DTO(...)
	);

	/**
	 * Set of parser rule indices for major clauses that should start on a new line
	 * at the base indentation level.
	 */
	private static final Set<Integer> MAJOR_CLAUSE_RULES = Set.of(
			JpqlParser.RULE_select_clause,
			JpqlParser.RULE_from_clause,
			JpqlParser.RULE_where_clause,
			JpqlParser.RULE_groupby_clause,
			JpqlParser.RULE_having_clause,
			JpqlParser.RULE_orderby_clause,
			JpqlParser.RULE_update_clause,
			JpqlParser.RULE_delete_clause,
			JpqlParser.RULE_subquery_from_clause
	);

	/**
	 * Set of parser rule indices for clauses that should start on a new line
	 * indented one extra level (e.g., JOINs).
	 */
	private static final Set<Integer> INDENTED_CLAUSE_RULES = Set.of(
			JpqlParser.RULE_join,
			JpqlParser.RULE_fetch_join
	);

	private final String indent;

	public JpqlQueryFormatter() {
		this(DEFAULT_INDENT);
	}

	public JpqlQueryFormatter(String indent) {
		this.indent = indent;
	}

	/**
	 * Formats the given JPQL query string into multi-line format.
	 * If the query cannot be parsed, the original string is returned unchanged.
	 * 
	 * @param jpql the JPQL query string to format
	 * @return the formatted query string
	 */
	public String format(String jpql) {
		if (jpql == null || jpql.isBlank()) {
			return jpql;
		}

		try {
			JpqlLexer lexer = new JpqlLexer(CharStreams.fromString(jpql));
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			JpqlParser parser = new JpqlParser(tokens);

			lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
			parser.removeErrorListener(ConsoleErrorListener.INSTANCE);

			// Track parse errors
			boolean[] hasErrors = { false };
			parser.addErrorListener(new org.antlr.v4.runtime.BaseErrorListener() {
				@Override
				public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer, Object offendingSymbol,
						int line, int charPositionInLine, String msg,
						org.antlr.v4.runtime.RecognitionException e) {
					hasErrors[0] = true;
				}
			});

			JpqlParser.StartContext tree = parser.start();

			if (hasErrors[0]) {
				return jpql;
			}

			StringBuilder sb = new StringBuilder();
			FormatState state = new FormatState();
			walkTree(tree, sb, state, 0);
			return sb.toString().trim();
		} catch (Exception e) {
			return jpql;
		}
	}

	/**
	 * Internal state used during formatting to track spacing decisions.
	 */
	private static class FormatState {
		boolean needsSpace = false;
		boolean atLineStart = true;
		int lastTokenType = -1;
	}

	private void walkTree(ParseTree node, StringBuilder sb, FormatState state, int baseIndent) {
		if (node instanceof TerminalNode) {
			TerminalNode terminal = (TerminalNode) node;
			int tokenType = terminal.getSymbol().getType();

			// Skip EOF
			if (tokenType == JpqlParser.EOF) {
				return;
			}

			String text = terminal.getText();

			// Handle AND/OR in conditional expressions - put on new line
			if (isConditionalOperator(terminal)) {
				sb.append('\n');
				appendIndent(sb, baseIndent + 1);
				sb.append(text);
				state.needsSpace = true;
				state.atLineStart = false;
				state.lastTokenType = tokenType;
				return;
			}

			// Handle commas in SELECT list - newline after comma
			if (isSelectListComma(terminal)) {
				sb.append(text);
				sb.append('\n');
				appendIndent(sb, baseIndent + 1);
				state.needsSpace = false;
				state.atLineStart = true;
				state.lastTokenType = tokenType;
				return;
			}

			// Determine spacing
			boolean spaceNeeded = state.needsSpace
					&& !state.atLineStart
					&& !NO_SPACE_BEFORE.contains(tokenType)
					&& state.lastTokenType != -1
					&& !NO_SPACE_AFTER.contains(state.lastTokenType)
					&& !(tokenType == JpqlParser.T__1 && FUNCTION_LIKE_TOKENS.contains(state.lastTokenType));

			if (spaceNeeded) {
				sb.append(' ');
			}

			sb.append(text);
			state.needsSpace = true;
			state.atLineStart = false;
			state.lastTokenType = tokenType;
			return;
		}

		if (node instanceof ParserRuleContext) {
			ParserRuleContext ctx = (ParserRuleContext) node;
			int ruleIndex = ctx.getRuleIndex();

			// Major clauses: new line at base indent
			if (MAJOR_CLAUSE_RULES.contains(ruleIndex) && isNotFirstClause(ctx)) {
				sb.append('\n');
				appendIndent(sb, baseIndent);
				state.needsSpace = false;
				state.atLineStart = true;
			}

			// Indented clauses (JOINs): new line at base + 1
			if (INDENTED_CLAUSE_RULES.contains(ruleIndex)) {
				sb.append('\n');
				appendIndent(sb, baseIndent + 1);
				state.needsSpace = false;
				state.atLineStart = true;
			}

			// Subqueries: newline after opening paren, increase indent
			int childIndent = baseIndent;
			if (ruleIndex == JpqlParser.RULE_subquery) {
				childIndent = baseIndent + 1;
				sb.append('\n');
				appendIndent(sb, childIndent);
				state.needsSpace = false;
				state.atLineStart = true;
			}

			// Walk children
			for (int i = 0; i < ctx.getChildCount(); i++) {
				walkTree(ctx.getChild(i), sb, state, childIndent);
			}
		}
	}

	/**
	 * Checks whether a terminal node is a comma inside the select_clause rule
	 * (i.e., separating SELECT list items).
	 */
	private boolean isSelectListComma(TerminalNode terminal) {
		if (terminal.getSymbol().getType() != JpqlParser.T__0) { // ','
			return false;
		}
		ParseTree parent = terminal.getParent();
		return parent instanceof ParserRuleContext
				&& ((ParserRuleContext) parent).getRuleIndex() == JpqlParser.RULE_select_clause;
	}

	/**
	 * Checks whether a terminal node is an AND or OR inside a conditional_expression
	 * or conditional_term (i.e., a WHERE/HAVING condition combinator, not AND in BETWEEN).
	 */
	private boolean isConditionalOperator(TerminalNode terminal) {
		int tokenType = terminal.getSymbol().getType();
		if (tokenType != JpqlParser.AND && tokenType != JpqlParser.OR) {
			return false;
		}

		// Check parent rule - AND/OR should only be reformatted when they're
		// direct children of conditional_expression (OR) or conditional_term (AND)
		ParseTree parent = terminal.getParent();
		if (parent instanceof ParserRuleContext) {
			int parentRule = ((ParserRuleContext) parent).getRuleIndex();
			return parentRule == JpqlParser.RULE_conditional_expression
					|| parentRule == JpqlParser.RULE_conditional_term;
		}
		return false;
	}

	/**
	 * Determines if this clause is not the very first thing in the query
	 * (i.e., we should prepend a newline before it).
	 */
	private boolean isNotFirstClause(ParserRuleContext ctx) {
		// If this is the first clause in the statement, no newline needed
		ParserRuleContext parent = ctx.getParent();
		if (parent == null) {
			return false;
		}
		// Check if there are any siblings before this one that produced output
		for (int i = 0; i < parent.getChildCount(); i++) {
			ParseTree child = parent.getChild(i);
			if (child == ctx) {
				return i > 0;
			}
		}
		return true;
	}

	private void appendIndent(StringBuilder sb, int level) {
		for (int i = 0; i < level; i++) {
			sb.append(indent);
		}
	}

}
