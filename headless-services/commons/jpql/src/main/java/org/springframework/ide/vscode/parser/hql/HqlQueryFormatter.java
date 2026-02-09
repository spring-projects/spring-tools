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
package org.springframework.ide.vscode.parser.hql;

import java.util.Set;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Formats an HQL query string into a nicely indented multi-line format.
 * <p>
 * Uses the ANTLR-generated {@link HqlParser} to parse the query, then walks
 * the parse tree to emit formatted output with newlines at clause boundaries.
 * <p>
 * Formatting rules:
 * <ul>
 *   <li>Major clauses (SELECT, FROM, WHERE, GROUP BY, HAVING, ORDER BY, SET, LIMIT, OFFSET, FETCH) start on a new line</li>
 *   <li>JOIN and CROSS JOIN clauses start on a new line, indented one level</li>
 *   <li>AND/OR conditions in WHERE/HAVING predicates start on a new line, indented under the clause</li>
 *   <li>Subqueries increase indentation</li>
 * </ul>
 */
public class HqlQueryFormatter {

	private static final String DEFAULT_INDENT = "  ";

	/**
	 * Set of token types that should NOT have a space before them.
	 */
	private static final Set<Integer> NO_SPACE_BEFORE = Set.of(
			HqlParser.T__13, // '.'
			HqlParser.T__2,  // ')'
			HqlParser.T__0,  // ','
			HqlParser.T__12  // ']'
	);

	/**
	 * Set of token types that should NOT have a space after them.
	 */
	private static final Set<Integer> NO_SPACE_AFTER = Set.of(
			HqlParser.T__13, // '.'
			HqlParser.T__1,  // '('
			HqlParser.T__21, // ':'
			HqlParser.T__22, // '?'
			HqlParser.T__11  // '['
	);

	/**
	 * Set of token types for function-like keywords where '(' should
	 * follow immediately without a space, e.g. COUNT(...), AVG(...).
	 * <p>
	 * In HQL, many functions (abs, concat, lower, upper, coalesce, etc.) are
	 * parsed generically via the {@code functionName} rule and appear as
	 * {@code IDENTIFICATION_VARIABLE} tokens, so that token type is included here.
	 */
	private static final Set<Integer> FUNCTION_LIKE_TOKENS = Set.of(
			HqlParser.COUNT,
			HqlParser.AVG,
			HqlParser.MAX,
			HqlParser.MIN,
			HqlParser.SUM,
			HqlParser.CEILING,
			HqlParser.EXP,
			HqlParser.FLOOR,
			HqlParser.LN,
			HqlParser.POWER,
			HqlParser.SIZE,
			HqlParser.INDEX,
			HqlParser.SUBSTRING,
			HqlParser.TRIM,
			HqlParser.FUNCTION,
			HqlParser.EXTRACT,
			HqlParser.TYPE,
			HqlParser.KEY,
			HqlParser.VALUE,
			HqlParser.ENTRY,
			HqlParser.OBJECT,
			HqlParser.TREAT,
			HqlParser.CAST,
			HqlParser.OVERLAY,
			HqlParser.POSITION,
			HqlParser.TRUNC,
			HqlParser.TRUNCATE,
			HqlParser.LISTAGG,
			HqlParser.ELEMENT,
			HqlParser.ELEMENTS,
			HqlParser.INDICES,
			HqlParser.MAXELEMENT,
			HqlParser.MAXINDEX,
			HqlParser.MINELEMENT,
			HqlParser.MININDEX,
			HqlParser.EXISTS,
			HqlParser.EVERY,
			HqlParser.ANY,
			HqlParser.FORMAT,
			HqlParser.IDENTIFICATION_VARIABLE // for function names and constructor names like com.example.DTO(...)
	);

	/**
	 * Set of parser rule indices for major clauses that should start on a new line
	 * at the base indentation level.
	 */
	private static final Set<Integer> MAJOR_CLAUSE_RULES = Set.of(
			HqlParser.RULE_selectClause,
			HqlParser.RULE_fromClause,
			HqlParser.RULE_whereClause,
			HqlParser.RULE_groupByClause,
			HqlParser.RULE_havingClause,
			HqlParser.RULE_queryOrder,
			HqlParser.RULE_orderByClause,
			HqlParser.RULE_setClause,
			HqlParser.RULE_limitClause,
			HqlParser.RULE_offsetClause,
			HqlParser.RULE_fetchClause
	);

	/**
	 * Set of parser rule indices for clauses that should start on a new line
	 * indented one extra level (e.g., JOINs, CROSS JOINs).
	 */
	private static final Set<Integer> INDENTED_CLAUSE_RULES = Set.of(
			HqlParser.RULE_join,
			HqlParser.RULE_crossJoin,
			HqlParser.RULE_jpaCollectionJoin
	);

	private final String indent;

	public HqlQueryFormatter() {
		this(DEFAULT_INDENT);
	}

	public HqlQueryFormatter(String indent) {
		this.indent = indent;
	}

	/**
	 * Formats the given HQL query string into multi-line format.
	 * If the query cannot be parsed, the original string is returned unchanged.
	 *
	 * @param hql the HQL query string to format
	 * @return the formatted query string
	 */
	public String format(String hql) {
		if (hql == null || hql.isBlank()) {
			return hql;
		}

		try {
			HqlLexer lexer = new HqlLexer(CharStreams.fromString(hql));
			CommonTokenStream tokens = new CommonTokenStream(lexer);
			HqlParser parser = new HqlParser(tokens);

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

			HqlParser.StartContext tree = parser.start();

			if (hasErrors[0]) {
				return hql;
			}

			StringBuilder sb = new StringBuilder();
			FormatState state = new FormatState();
			walkTree(tree, sb, state, 0);
			return sb.toString().trim();
		} catch (Exception e) {
			return hql;
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
			if (tokenType == HqlParser.EOF) {
				return;
			}

			String text = terminal.getText();

			// Handle AND/OR in predicate expressions - put on new line
			if (isConditionalOperator(terminal)) {
				sb.append('\n');
				appendIndent(sb, baseIndent + 1);
				sb.append(text);
				state.needsSpace = true;
				state.atLineStart = false;
				state.lastTokenType = tokenType;
				return;
			}

			// Determine spacing
			boolean spaceNeeded = state.needsSpace
					&& !state.atLineStart
					&& !NO_SPACE_BEFORE.contains(tokenType)
					&& state.lastTokenType != -1
					&& !NO_SPACE_AFTER.contains(state.lastTokenType)
					&& !(tokenType == HqlParser.T__1 && FUNCTION_LIKE_TOKENS.contains(state.lastTokenType));

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

			// Major clauses: new line at base indent
			if (MAJOR_CLAUSE_RULES.contains(ruleIndex) && isNotFirstClause(ctx)) {
				sb.append('\n');
				appendIndent(sb, baseIndent);
				state.needsSpace = false;
				state.atLineStart = true;
			}

			// Indented clauses (JOINs, CROSS JOINs): new line at base + 1
			if (INDENTED_CLAUSE_RULES.contains(ruleIndex)) {
				sb.append('\n');
				appendIndent(sb, baseIndent + 1);
				state.needsSpace = false;
				state.atLineStart = true;
			}

			// Subqueries: newline after opening paren, increase indent
			int childIndent = baseIndent;
			if (ruleIndex == HqlParser.RULE_subquery) {
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
	 * Checks whether a terminal node is an AND or OR inside the predicate rule
	 * used as a logical combinator (not AND in BETWEEN...AND...).
	 * <p>
	 * In HQL, {@code predicate AND predicate} and {@code predicate OR predicate}
	 * are alternatives of the predicate rule itself (AndPredicate/OrPredicate).
	 * AND inside BETWEEN is a child of betweenExpression, so checking the parent
	 * rule index correctly distinguishes them.
	 */
	private boolean isConditionalOperator(TerminalNode terminal) {
		int tokenType = terminal.getSymbol().getType();
		if (tokenType != HqlParser.AND && tokenType != HqlParser.OR) {
			return false;
		}

		ParseTree parent = terminal.getParent();
		if (parent instanceof ParserRuleContext parentCtx) {
			return parentCtx.getRuleIndex() == HqlParser.RULE_predicate;
		}
		return false;
	}

	/**
	 * Determines if this clause is not the very first thing in the query
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

	private void appendIndent(StringBuilder sb, int level) {
		for (int i = 0; i < level; i++) {
			sb.append(indent);
		}
	}

}
