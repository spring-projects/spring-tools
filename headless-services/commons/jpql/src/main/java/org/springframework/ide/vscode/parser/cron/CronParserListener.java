// Generated from CronParser.g4 by ANTLR 4.13.2
package org.springframework.ide.vscode.parser.cron;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link CronParser}.
 */
public interface CronParserListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link CronParser#cronExpression}.
	 * @param ctx the parse tree
	 */
	void enterCronExpression(CronParser.CronExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#cronExpression}.
	 * @param ctx the parse tree
	 */
	void exitCronExpression(CronParser.CronExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#secondsElement}.
	 * @param ctx the parse tree
	 */
	void enterSecondsElement(CronParser.SecondsElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#secondsElement}.
	 * @param ctx the parse tree
	 */
	void exitSecondsElement(CronParser.SecondsElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#minutesElement}.
	 * @param ctx the parse tree
	 */
	void enterMinutesElement(CronParser.MinutesElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#minutesElement}.
	 * @param ctx the parse tree
	 */
	void exitMinutesElement(CronParser.MinutesElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#hoursElement}.
	 * @param ctx the parse tree
	 */
	void enterHoursElement(CronParser.HoursElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#hoursElement}.
	 * @param ctx the parse tree
	 */
	void exitHoursElement(CronParser.HoursElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#daysElement}.
	 * @param ctx the parse tree
	 */
	void enterDaysElement(CronParser.DaysElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#daysElement}.
	 * @param ctx the parse tree
	 */
	void exitDaysElement(CronParser.DaysElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#monthsElement}.
	 * @param ctx the parse tree
	 */
	void enterMonthsElement(CronParser.MonthsElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#monthsElement}.
	 * @param ctx the parse tree
	 */
	void exitMonthsElement(CronParser.MonthsElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#daysOfWeekElement}.
	 * @param ctx the parse tree
	 */
	void enterDaysOfWeekElement(CronParser.DaysOfWeekElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#daysOfWeekElement}.
	 * @param ctx the parse tree
	 */
	void exitDaysOfWeekElement(CronParser.DaysOfWeekElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#cronElement}.
	 * @param ctx the parse tree
	 */
	void enterCronElement(CronParser.CronElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#cronElement}.
	 * @param ctx the parse tree
	 */
	void exitCronElement(CronParser.CronElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#rangeCronElement}.
	 * @param ctx the parse tree
	 */
	void enterRangeCronElement(CronParser.RangeCronElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#rangeCronElement}.
	 * @param ctx the parse tree
	 */
	void exitRangeCronElement(CronParser.RangeCronElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#terminalCronElement}.
	 * @param ctx the parse tree
	 */
	void enterTerminalCronElement(CronParser.TerminalCronElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#terminalCronElement}.
	 * @param ctx the parse tree
	 */
	void exitTerminalCronElement(CronParser.TerminalCronElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#periodicCronElement}.
	 * @param ctx the parse tree
	 */
	void enterPeriodicCronElement(CronParser.PeriodicCronElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#periodicCronElement}.
	 * @param ctx the parse tree
	 */
	void exitPeriodicCronElement(CronParser.PeriodicCronElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#rangeCronList}.
	 * @param ctx the parse tree
	 */
	void enterRangeCronList(CronParser.RangeCronListContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#rangeCronList}.
	 * @param ctx the parse tree
	 */
	void exitRangeCronList(CronParser.RangeCronListContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#nthDayOfWeekElement}.
	 * @param ctx the parse tree
	 */
	void enterNthDayOfWeekElement(CronParser.NthDayOfWeekElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#nthDayOfWeekElement}.
	 * @param ctx the parse tree
	 */
	void exitNthDayOfWeekElement(CronParser.NthDayOfWeekElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#lastDayOfWeekElement}.
	 * @param ctx the parse tree
	 */
	void enterLastDayOfWeekElement(CronParser.LastDayOfWeekElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#lastDayOfWeekElement}.
	 * @param ctx the parse tree
	 */
	void exitLastDayOfWeekElement(CronParser.LastDayOfWeekElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#nearestWeekDayToDayOfMonthElement}.
	 * @param ctx the parse tree
	 */
	void enterNearestWeekDayToDayOfMonthElement(CronParser.NearestWeekDayToDayOfMonthElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#nearestWeekDayToDayOfMonthElement}.
	 * @param ctx the parse tree
	 */
	void exitNearestWeekDayToDayOfMonthElement(CronParser.NearestWeekDayToDayOfMonthElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#lastDayOfMonthElement}.
	 * @param ctx the parse tree
	 */
	void enterLastDayOfMonthElement(CronParser.LastDayOfMonthElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#lastDayOfMonthElement}.
	 * @param ctx the parse tree
	 */
	void exitLastDayOfMonthElement(CronParser.LastDayOfMonthElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#weekdayLiteral}.
	 * @param ctx the parse tree
	 */
	void enterWeekdayLiteral(CronParser.WeekdayLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#weekdayLiteral}.
	 * @param ctx the parse tree
	 */
	void exitWeekdayLiteral(CronParser.WeekdayLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link CronParser#monthLiteral}.
	 * @param ctx the parse tree
	 */
	void enterMonthLiteral(CronParser.MonthLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link CronParser#monthLiteral}.
	 * @param ctx the parse tree
	 */
	void exitMonthLiteral(CronParser.MonthLiteralContext ctx);
}