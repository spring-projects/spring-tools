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
package org.springframework.ide.vscode.parser.postgresql;

import java.util.Set;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Formats a PostgreSQL query string into a nicely indented multi-line format.
 * <p>
 * Uses the ANTLR-generated {@link PostgreSqlParser} to parse the query, then walks
 * the parse tree to emit formatted output with newlines at clause boundaries.
 * <p>
 * Formatting rules:
 * <ul>
 *   <li>Major clauses (SELECT, FROM, WHERE, GROUP BY, HAVING, ORDER BY, LIMIT, OFFSET, etc.) start on a new line</li>
 *   <li>JOIN clauses start on a new line, indented one level</li>
 *   <li>AND/OR conditions in WHERE/HAVING start on a new line, indented under the clause</li>
 *   <li>Subqueries increase indentation</li>
 * </ul>
 */
public class PostgreSqlQueryFormatter {

	private static final String DEFAULT_INDENT = "  ";

	/**
	 * Set of token types that should NOT have a space before them.
	 */
	private static final Set<Integer> NO_SPACE_BEFORE = Set.of(
			PostgreSqlLexer.DOT,
			PostgreSqlLexer.CLOSE_PAREN,
			PostgreSqlLexer.COMMA,
			PostgreSqlLexer.SEMI,
			PostgreSqlLexer.CLOSE_BRACKET
	);

	/**
	 * Set of token types that should NOT have a space after them.
	 */
	private static final Set<Integer> NO_SPACE_AFTER = Set.of(
			PostgreSqlLexer.DOT,
			PostgreSqlLexer.OPEN_PAREN,
			PostgreSqlLexer.COLON,
			PostgreSqlLexer.OPEN_BRACKET,
			PostgreSqlLexer.QUESTION
	);

	/**
	 * Set of token types for function-like keywords where '(' should follow
	 * immediately without a space, e.g. COUNT(...), MAX(...), VALUES(...).
	 */
	private static final Set<Integer> FUNCTION_LIKE_TOKENS = Set.of(
			PostgreSqlLexer.Identifier,
			PostgreSqlLexer.PLSQLIDENTIFIER,
			PostgreSqlLexer.VALUES,
			PostgreSqlLexer.COALESCE,
			PostgreSqlLexer.GREATEST,
			PostgreSqlLexer.LEAST,
			PostgreSqlLexer.NULLIF,
			PostgreSqlLexer.EXTRACT,
			PostgreSqlLexer.OVERLAY,
			PostgreSqlLexer.POSITION,
			PostgreSqlLexer.SUBSTRING,
			PostgreSqlLexer.TRIM,
			PostgreSqlLexer.TREAT,
			PostgreSqlLexer.XMLCONCAT,
			PostgreSqlLexer.XMLELEMENT,
			PostgreSqlLexer.XMLEXISTS,
			PostgreSqlLexer.XMLFOREST,
			PostgreSqlLexer.XMLPARSE,
			PostgreSqlLexer.XMLPI,
			PostgreSqlLexer.XMLROOT,
			PostgreSqlLexer.XMLSERIALIZE,
			PostgreSqlLexer.EXISTS,
			PostgreSqlLexer.CAST,
			PostgreSqlLexer.ROW,
			PostgreSqlLexer.ARRAY
			// Built-in function keywords (ABS, COUNT, etc.) in a separate range
			// are handled by the range check in isFunctionLikeToken()
	);

	/**
	 * Set of parser rule indices for major clauses that should start on a new line
	 * at the base indentation level.
	 */
	private static final Set<Integer> MAJOR_CLAUSE_RULES = Set.of(
			PostgreSqlParser.RULE_from_clause,
			PostgreSqlParser.RULE_where_clause,
			PostgreSqlParser.RULE_where_or_current_clause,
			PostgreSqlParser.RULE_group_clause,
			PostgreSqlParser.RULE_having_clause,
			PostgreSqlParser.RULE_window_clause,
			PostgreSqlParser.RULE_sort_clause,
			PostgreSqlParser.RULE_opt_sort_clause,
			PostgreSqlParser.RULE_select_limit,
			PostgreSqlParser.RULE_limit_clause,
			PostgreSqlParser.RULE_offset_clause,
			PostgreSqlParser.RULE_returning_clause,
			PostgreSqlParser.RULE_for_locking_clause
	);

	/**
	 * Set of token types that indicate the start of a JOIN clause as a direct
	 * child of table_ref. These are tokens that can appear at the start of a
	 * join sequence without being wrapped in a join_type rule.
	 * (e.g., "CROSS JOIN", "NATURAL JOIN", bare "JOIN")
	 */
	private static final Set<Integer> JOIN_DIRECT_TOKENS = Set.of(
			PostgreSqlLexer.JOIN,
			PostgreSqlLexer.CROSS,
			PostgreSqlLexer.NATURAL
	);

	private final String indent;

	public PostgreSqlQueryFormatter() {
		this(DEFAULT_INDENT);
	}

	public PostgreSqlQueryFormatter(String indent) {
		this.indent = indent;
	}

	/**
	 * Formats the given PostgreSQL query string into multi-line format.
	 * If the query cannot be parsed, the original string is returned unchanged.
	 * 
	 * @param sql the PostgreSQL query string to format
	 * @return the formatted query string
	 */
	public String format(String sql) {
		if (sql == null || sql.isBlank()) {
			return sql;
		}

		try {
			PostgreSqlLexer lexer = new PostgreSqlLexer(CharStreams.fromString(sql));
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			PostgreSqlParser parser = new PostgreSqlParser(tokens);

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

			PostgreSqlParser.RootContext tree = parser.root();

			if (hasErrors[0]) {
				return sql;
			}

			StringBuilder sb = new StringBuilder();
			FormatState state = new FormatState();
			walkTree(tree, sb, state, 0);
			return sb.toString().trim();
		} catch (Exception e) {
			return sql;
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
		if (node instanceof TerminalNode terminal) {
			int tokenType = terminal.getSymbol().getType();

			// Skip EOF
			if (tokenType == PostgreSqlLexer.EOF) {
				return;
			}

			String text = terminal.getText();

			// Handle AND/OR in expression context - put on new line
			if (isConditionalOperator(terminal)) {
				sb.append('\n');
				appendIndent(sb, baseIndent + 1);
				sb.append(text);
				state.needsSpace = true;
				state.atLineStart = false;
				state.lastTokenType = tokenType;
				return;
			}

			// Handle JOIN keywords directly inside table_ref - put on new line with indent
			if (isJoinDirectTokenInTableRef(terminal)) {
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
					&& !(tokenType == PostgreSqlLexer.OPEN_PAREN && isFunctionLikeToken(state.lastTokenType));

			if (spaceNeeded) {
				sb.append(' ');
			}

			sb.append(text);
			state.needsSpace = true;
			state.atLineStart = false;
			state.lastTokenType = tokenType;
			return;
		}

		if (node instanceof ParserRuleContext ctx) {
			int ruleIndex = ctx.getRuleIndex();

			// Skip empty rules (PostgreSQL grammar uses empty alternatives)
			if (ctx.getChildCount() == 0) {
				return;
			}

			// Major clauses: new line at base indent
			if (MAJOR_CLAUSE_RULES.contains(ruleIndex) && isNotFirstClause(ctx)) {
				sb.append('\n');
				appendIndent(sb, baseIndent);
				state.needsSpace = false;
				state.atLineStart = true;
			}

			// join_type rule inside table_ref: newline + indent before it
			if (ruleIndex == PostgreSqlParser.RULE_join_type && isInsideTableRef(ctx)) {
				sb.append('\n');
				appendIndent(sb, baseIndent + 1);
				state.needsSpace = false;
				state.atLineStart = true;
			}

			// Subqueries (select_with_parens wraps subqueries in parens)
			int childIndent = baseIndent;
			if (ruleIndex == PostgreSqlParser.RULE_select_no_parens
					&& isInsideParens(ctx)) {
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
	 * Checks whether a terminal node is a comma inside the target_list rule
	 * (i.e., separating SELECT list items).
	 */
	private boolean isSelectListComma(TerminalNode terminal) {
		if (terminal.getSymbol().getType() != PostgreSqlLexer.COMMA) {
			return false;
		}
		ParseTree parent = terminal.getParent();
		return parent instanceof ParserRuleContext parentCtx
				&& parentCtx.getRuleIndex() == PostgreSqlParser.RULE_target_list;
	}

	/**
	 * Checks whether a terminal node is an AND or OR inside the expression rules
	 * a_expr_and or a_expr_or (i.e., a WHERE/HAVING condition combinator,
	 * not AND in BETWEEN).
	 */
	private boolean isConditionalOperator(TerminalNode terminal) {
		int tokenType = terminal.getSymbol().getType();
		if (tokenType != PostgreSqlLexer.AND && tokenType != PostgreSqlLexer.OR) {
			return false;
		}

		ParseTree parent = terminal.getParent();
		if (parent instanceof ParserRuleContext parentCtx) {
			int parentRule = parentCtx.getRuleIndex();
			return parentRule == PostgreSqlParser.RULE_a_expr_and
					|| parentRule == PostgreSqlParser.RULE_a_expr_or;
		}
		return false;
	}

	/**
	 * Checks whether a terminal is a JOIN/CROSS/NATURAL token that is a direct child
	 * of the table_ref rule. For "LEFT JOIN", "RIGHT JOIN" etc., the LEFT/RIGHT
	 * token is inside a join_type rule (handled separately), and the subsequent
	 * JOIN token should NOT trigger a new line.
	 */
	private boolean isJoinDirectTokenInTableRef(TerminalNode terminal) {
		int tokenType = terminal.getSymbol().getType();
		if (!JOIN_DIRECT_TOKENS.contains(tokenType)) {
			return false;
		}

		ParseTree parent = terminal.getParent();
		if (parent instanceof ParserRuleContext parentCtx
				&& parentCtx.getRuleIndex() == PostgreSqlParser.RULE_table_ref) {
			// For bare "JOIN" that follows a join_type or CROSS/NATURAL, skip
			int idx = indexInParent(terminal);
			if (idx > 0 && tokenType == PostgreSqlLexer.JOIN) {
				ParseTree prevSibling = parentCtx.getChild(idx - 1);
				// JOIN following join_type (LEFT/RIGHT/FULL/INNER) — skip
				if (prevSibling instanceof ParserRuleContext prevCtx
						&& prevCtx.getRuleIndex() == PostgreSqlParser.RULE_join_type) {
					return false;
				}
				// JOIN following CROSS or NATURAL — skip
				if (prevSibling instanceof TerminalNode prevTerm) {
					int prevType = prevTerm.getSymbol().getType();
					if (prevType == PostgreSqlLexer.CROSS || prevType == PostgreSqlLexer.NATURAL) {
						return false;
					}
				}
			}
			return true;
		}
		return false;
	}

	/**
	 * Checks whether a rule context is a direct child of the table_ref rule.
	 */
	private boolean isInsideTableRef(ParserRuleContext ctx) {
		ParserRuleContext parent = ctx.getParent();
		return parent != null
				&& parent.getRuleIndex() == PostgreSqlParser.RULE_table_ref;
	}

	/**
	 * Checks whether a select_no_parens is nested inside a select_with_parens
	 * (i.e., it is a subquery).
	 */
	private boolean isInsideParens(ParserRuleContext ctx) {
		ParserRuleContext parent = ctx.getParent();
		return parent != null
				&& parent.getRuleIndex() == PostgreSqlParser.RULE_select_with_parens;
	}

	/**
	 * Determines if this clause is not the very first thing in the statement
	 * (i.e., we should prepend a newline before it).
	 */
	private boolean isNotFirstClause(ParserRuleContext ctx) {
		ParserRuleContext parent = ctx.getParent();
		if (parent == null) {
			return false;
		}
		for (int i = 0; i < parent.getChildCount(); i++) {
			ParseTree child = parent.getChild(i);
			if (child == ctx) {
				return i > 0;
			}
		}
		return true;
	}

	/**
	 * Returns the index of the given node among its parent's children.
	 */
	private int indexInParent(ParseTree node) {
		ParseTree parent = node.getParent();
		if (parent == null) {
			return -1;
		}
		for (int i = 0; i < parent.getChildCount(); i++) {
			if (parent.getChild(i) == node) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Checks whether the given token type is a function-like token that should
	 * have no space before an opening parenthesis.
	 */
	private boolean isFunctionLikeToken(int tokenType) {
		if (FUNCTION_LIKE_TOKENS.contains(tokenType)) {
			return true;
		}
		// Built-in function keywords like ABS, COUNT, etc.
		return tokenType >= PostgreSqlLexer.ABS && tokenType <= PostgreSqlLexer.TO_NUMBER;
	}

	private void appendIndent(StringBuilder sb, int level) {
		for (int i = 0; i < level; i++) {
			sb.append(indent);
		}
	}

}
