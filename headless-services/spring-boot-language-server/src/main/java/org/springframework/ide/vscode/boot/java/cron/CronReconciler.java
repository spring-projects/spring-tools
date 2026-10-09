/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.cron;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.antlr.v4.runtime.ANTLRErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ConsoleErrorListener;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.atn.ATNConfigSet;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.RuleNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ide.vscode.boot.java.handlers.Reconciler;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblemImpl;
import org.springframework.ide.vscode.commons.util.BadLocationException;
import org.springframework.ide.vscode.commons.util.text.IRegion;
import org.springframework.ide.vscode.commons.util.text.Region;
import org.springframework.ide.vscode.commons.util.text.linetracker.DefaultLineTracker;
import org.springframework.ide.vscode.parser.cron.CronLexer;
import org.springframework.ide.vscode.parser.cron.CronParser;
import org.springframework.ide.vscode.parser.cron.CronParser.DaysElementContext;
import org.springframework.ide.vscode.parser.cron.CronParser.DaysOfWeekElementContext;
import org.springframework.ide.vscode.parser.cron.CronParser.HoursElementContext;
import org.springframework.ide.vscode.parser.cron.CronParser.MinutesElementContext;
import org.springframework.ide.vscode.parser.cron.CronParser.MonthsElementContext;
import org.springframework.ide.vscode.parser.cron.CronParser.SecondsElementContext;
import org.springframework.ide.vscode.parser.cron.CronParserBaseListener;

public class CronReconciler implements Reconciler {
	
	private static final Logger log = LoggerFactory.getLogger(CronReconciler.class);
	
	private Method parseSeconds;
	private Method parseMinutes;
	private Method parseHours;
	private Method parseDaysOfMonth;
	private Method parseMonth;
	private Method parseDaysOfWeek;
	
	public CronReconciler() {
		initReflection();
	}

	@Override
	public void reconcile(String text, Function<IRegion, IRegion> mapping, IProblemCollector problemCollector) {
		CronLexer lexer = new CronLexer(CharStreams.fromString(text));
		CommonTokenStream antlrTokens = new CommonTokenStream(lexer);
		CronParser parser = new CronParser(antlrTokens);
		AtomicReference<DefaultLineTracker> lineTrackerRef = new AtomicReference<>();
		
		lexer.removeErrorListener(ConsoleErrorListener.INSTANCE);
		parser.removeErrorListener(ConsoleErrorListener.INSTANCE);
		
		parser.addParseListener(new CronParserBaseListener() {

			@Override
			public void exitSecondsElement(SecondsElementContext ctx) {
				validate(ctx, parseSeconds);
			}

			@Override
			public void exitMinutesElement(MinutesElementContext ctx) {
				validate(ctx, parseMinutes);
			}

			@Override
			public void exitHoursElement(HoursElementContext ctx) {
				validate(ctx, parseHours);
			}

			@Override
			public void exitDaysElement(DaysElementContext ctx) {
				validate(ctx, parseDaysOfMonth);
			}

			@Override
			public void exitMonthsElement(MonthsElementContext ctx) {
				validate(ctx, parseMonth);
			}

			@Override
			public void exitDaysOfWeekElement(DaysOfWeekElementContext ctx) {
				validate(ctx, parseDaysOfWeek);
			}
			
			private void validate(ParserRuleContext ctx, Method m) {
				if (!hasSyntaxErrors(ctx) && m != null) {
					try {
						m.invoke(null, ctx.getText());
					} catch (IllegalAccessException e) {
						log.error("", e);
					} catch (IllegalArgumentException e) {
						log.error("", e);
					} catch (InvocationTargetException e) {
						Throwable target = e.getTargetException();
						String message = target.getMessage();
						if (message.startsWith("For input string:")) {
							markProblemsForNumberFormatException(ctx, message);
						} else {
							IRegion r = mapping.apply(new Region(ctx.getStart().getStartIndex(), ctx.getText().length()));
							problemCollector.accept(new ReconcileProblemImpl(CronProblemType.CRON_FIELD, "CRON: %s".formatted(message), r.getOffset(), r.getLength()));
						}
					}	
				}
			}
			
			private void markProblemsForNumberFormatException(ParserRuleContext ctx, String message) {
				int start = message.indexOf("\"");
				if (start >= 0) {
					int end = message.lastIndexOf("\"");
					if (start + 1 < end) {
						String text = ctx.getText();
						String problemText = message.substring(start + 1, end);
						int offset = text.indexOf(problemText);
						if (offset >= 0) {
							IRegion r = mapping.apply(new Region(ctx.getStart().getStartIndex() + offset, problemText.length()));
							problemCollector.accept(new ReconcileProblemImpl(CronProblemType.CRON_FIELD, "CRON: Number expected", r.getOffset(), r.getLength()));
							return;
						}
					}
				}
				IRegion r = mapping.apply(new Region(ctx.getStart().getStartIndex(), ctx.getText().length()));
				problemCollector.accept(new ReconcileProblemImpl(CronProblemType.CRON_FIELD, "CRON: %s".formatted(message), r.getOffset(), r.getLength()));
			}

			
		});
		
		ANTLRErrorListener antlrErrorListener = new ANTLRErrorListener() {
			
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine,
					String msg, RecognitionException e) {
				int offset = 0;
				int length = 0;
				if (offendingSymbol instanceof Token) {
					Token token = (Token) offendingSymbol;
					offset = token.getStartIndex();
					if (token.getStartIndex() <= token.getStopIndex()) {
						length = token.getText() == null ? token.getStopIndex() + 1 - token.getStartIndex() : token.getText().length();
					}
				} else {
					DefaultLineTracker lt = lineTrackerRef.get();
					if (lt == null) {
						lt = new DefaultLineTracker();
						lt.set(text);
						lineTrackerRef.set(lt);
					}
					try {
						offset = lt.getLineOffset(line - 1) + charPositionInLine;
					} catch (BadLocationException e1) {
						log.error("", e1);
					}
				}
				IRegion r = mapping.apply(new Region(offset, length));
				problemCollector.accept(new ReconcileProblemImpl(CronProblemType.CRON_SYNTAX, "CRON: " + msg, r.getOffset(), r.getLength()));
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
		};
		
		lexer.addErrorListener(antlrErrorListener);
		parser.addErrorListener(antlrErrorListener);

		parser.cronExpression();
	}
	
	private boolean hasSyntaxErrors(ParserRuleContext ctx) {
		return ctx.accept(new AbstractParseTreeVisitor<Boolean>() {

			@Override
			public Boolean visitErrorNode(ErrorNode node) {
				return true;
			}

			@Override
			protected Boolean defaultResult() {
				return false;
			}

			@Override
			protected boolean shouldVisitNextChild(RuleNode node, Boolean currentResult) {
				return currentResult == null || !currentResult.booleanValue();
			}

		});
		
	}
	
	private void initReflection() {
		try {
			Class<?> clazz = Class.forName("org.springframework.scheduling.support.CronField");
			parseSeconds = clazz.getDeclaredMethod("parseSeconds", String.class);
			parseSeconds.setAccessible(true);
			parseMinutes = clazz.getDeclaredMethod("parseMinutes", String.class);
			parseMinutes.setAccessible(true);
			parseHours = clazz.getDeclaredMethod("parseHours", String.class);
			parseHours.setAccessible(true);
			parseDaysOfMonth = clazz.getDeclaredMethod("parseDaysOfMonth", String.class);
			parseDaysOfMonth.setAccessible(true);
			parseMonth = clazz.getDeclaredMethod("parseMonth", String.class);
			parseMonth.setAccessible(true);
			parseDaysOfWeek = clazz.getDeclaredMethod("parseDaysOfWeek", String.class);
			parseDaysOfWeek.setAccessible(true);
		} catch (Exception e) {
			log.error("", e);
		}
	}
	
}
