// Generated from Hql.g4 by ANTLR 4.13.2
package org.springframework.ide.vscode.parser.hql;

/**
 * HQL per https://docs.jboss.org/hibernate/orm/6.1/userguide/html_single/Hibernate_User_Guide.html#query-language
 *
 * This is a mixture of Hibernate's BNF and missing bits of grammar. There are gaps and inconsistencies in the
 * BNF itself, explained by other fragments of their spec. Additionally, alternate labels are used to provide easier
 * management of complex rules in the generated Visitor. Finally, there are labels applied to rule elements (op=('+'|'-')
 * to simplify the processing.
 *
 * @author Greg Turnquist
 * @since 3.1
 */

import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link HqlParser}.
 */
public interface HqlListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link HqlParser#start}.
	 * @param ctx the parse tree
	 */
	void enterStart(HqlParser.StartContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#start}.
	 * @param ctx the parse tree
	 */
	void exitStart(HqlParser.StartContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#ql_statement}.
	 * @param ctx the parse tree
	 */
	void enterQl_statement(HqlParser.Ql_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#ql_statement}.
	 * @param ctx the parse tree
	 */
	void exitQl_statement(HqlParser.Ql_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#selectStatement}.
	 * @param ctx the parse tree
	 */
	void enterSelectStatement(HqlParser.SelectStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#selectStatement}.
	 * @param ctx the parse tree
	 */
	void exitSelectStatement(HqlParser.SelectStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#queryExpression}.
	 * @param ctx the parse tree
	 */
	void enterQueryExpression(HqlParser.QueryExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#queryExpression}.
	 * @param ctx the parse tree
	 */
	void exitQueryExpression(HqlParser.QueryExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#withClause}.
	 * @param ctx the parse tree
	 */
	void enterWithClause(HqlParser.WithClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#withClause}.
	 * @param ctx the parse tree
	 */
	void exitWithClause(HqlParser.WithClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#cte}.
	 * @param ctx the parse tree
	 */
	void enterCte(HqlParser.CteContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#cte}.
	 * @param ctx the parse tree
	 */
	void exitCte(HqlParser.CteContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#searchClause}.
	 * @param ctx the parse tree
	 */
	void enterSearchClause(HqlParser.SearchClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#searchClause}.
	 * @param ctx the parse tree
	 */
	void exitSearchClause(HqlParser.SearchClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#searchSpecifications}.
	 * @param ctx the parse tree
	 */
	void enterSearchSpecifications(HqlParser.SearchSpecificationsContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#searchSpecifications}.
	 * @param ctx the parse tree
	 */
	void exitSearchSpecifications(HqlParser.SearchSpecificationsContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#searchSpecification}.
	 * @param ctx the parse tree
	 */
	void enterSearchSpecification(HqlParser.SearchSpecificationContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#searchSpecification}.
	 * @param ctx the parse tree
	 */
	void exitSearchSpecification(HqlParser.SearchSpecificationContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#cycleClause}.
	 * @param ctx the parse tree
	 */
	void enterCycleClause(HqlParser.CycleClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#cycleClause}.
	 * @param ctx the parse tree
	 */
	void exitCycleClause(HqlParser.CycleClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#cteAttributes}.
	 * @param ctx the parse tree
	 */
	void enterCteAttributes(HqlParser.CteAttributesContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#cteAttributes}.
	 * @param ctx the parse tree
	 */
	void exitCteAttributes(HqlParser.CteAttributesContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#orderedQuery}.
	 * @param ctx the parse tree
	 */
	void enterOrderedQuery(HqlParser.OrderedQueryContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#orderedQuery}.
	 * @param ctx the parse tree
	 */
	void exitOrderedQuery(HqlParser.OrderedQueryContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SelectQuery}
	 * labeled alternative in {@link HqlParser#query}.
	 * @param ctx the parse tree
	 */
	void enterSelectQuery(HqlParser.SelectQueryContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SelectQuery}
	 * labeled alternative in {@link HqlParser#query}.
	 * @param ctx the parse tree
	 */
	void exitSelectQuery(HqlParser.SelectQueryContext ctx);
	/**
	 * Enter a parse tree produced by the {@code FromQuery}
	 * labeled alternative in {@link HqlParser#query}.
	 * @param ctx the parse tree
	 */
	void enterFromQuery(HqlParser.FromQueryContext ctx);
	/**
	 * Exit a parse tree produced by the {@code FromQuery}
	 * labeled alternative in {@link HqlParser#query}.
	 * @param ctx the parse tree
	 */
	void exitFromQuery(HqlParser.FromQueryContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#queryOrder}.
	 * @param ctx the parse tree
	 */
	void enterQueryOrder(HqlParser.QueryOrderContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#queryOrder}.
	 * @param ctx the parse tree
	 */
	void exitQueryOrder(HqlParser.QueryOrderContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#fromClause}.
	 * @param ctx the parse tree
	 */
	void enterFromClause(HqlParser.FromClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#fromClause}.
	 * @param ctx the parse tree
	 */
	void exitFromClause(HqlParser.FromClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#entityWithJoins}.
	 * @param ctx the parse tree
	 */
	void enterEntityWithJoins(HqlParser.EntityWithJoinsContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#entityWithJoins}.
	 * @param ctx the parse tree
	 */
	void exitEntityWithJoins(HqlParser.EntityWithJoinsContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#joinSpecifier}.
	 * @param ctx the parse tree
	 */
	void enterJoinSpecifier(HqlParser.JoinSpecifierContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#joinSpecifier}.
	 * @param ctx the parse tree
	 */
	void exitJoinSpecifier(HqlParser.JoinSpecifierContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#fromRoot}.
	 * @param ctx the parse tree
	 */
	void enterFromRoot(HqlParser.FromRootContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#fromRoot}.
	 * @param ctx the parse tree
	 */
	void exitFromRoot(HqlParser.FromRootContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#join}.
	 * @param ctx the parse tree
	 */
	void enterJoin(HqlParser.JoinContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#join}.
	 * @param ctx the parse tree
	 */
	void exitJoin(HqlParser.JoinContext ctx);
	/**
	 * Enter a parse tree produced by the {@code JoinPath}
	 * labeled alternative in {@link HqlParser#joinTarget}.
	 * @param ctx the parse tree
	 */
	void enterJoinPath(HqlParser.JoinPathContext ctx);
	/**
	 * Exit a parse tree produced by the {@code JoinPath}
	 * labeled alternative in {@link HqlParser#joinTarget}.
	 * @param ctx the parse tree
	 */
	void exitJoinPath(HqlParser.JoinPathContext ctx);
	/**
	 * Enter a parse tree produced by the {@code JoinSubquery}
	 * labeled alternative in {@link HqlParser#joinTarget}.
	 * @param ctx the parse tree
	 */
	void enterJoinSubquery(HqlParser.JoinSubqueryContext ctx);
	/**
	 * Exit a parse tree produced by the {@code JoinSubquery}
	 * labeled alternative in {@link HqlParser#joinTarget}.
	 * @param ctx the parse tree
	 */
	void exitJoinSubquery(HqlParser.JoinSubqueryContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#updateStatement}.
	 * @param ctx the parse tree
	 */
	void enterUpdateStatement(HqlParser.UpdateStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#updateStatement}.
	 * @param ctx the parse tree
	 */
	void exitUpdateStatement(HqlParser.UpdateStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#targetEntity}.
	 * @param ctx the parse tree
	 */
	void enterTargetEntity(HqlParser.TargetEntityContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#targetEntity}.
	 * @param ctx the parse tree
	 */
	void exitTargetEntity(HqlParser.TargetEntityContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#setClause}.
	 * @param ctx the parse tree
	 */
	void enterSetClause(HqlParser.SetClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#setClause}.
	 * @param ctx the parse tree
	 */
	void exitSetClause(HqlParser.SetClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#assignment}.
	 * @param ctx the parse tree
	 */
	void enterAssignment(HqlParser.AssignmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#assignment}.
	 * @param ctx the parse tree
	 */
	void exitAssignment(HqlParser.AssignmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#deleteStatement}.
	 * @param ctx the parse tree
	 */
	void enterDeleteStatement(HqlParser.DeleteStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#deleteStatement}.
	 * @param ctx the parse tree
	 */
	void exitDeleteStatement(HqlParser.DeleteStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#insertStatement}.
	 * @param ctx the parse tree
	 */
	void enterInsertStatement(HqlParser.InsertStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#insertStatement}.
	 * @param ctx the parse tree
	 */
	void exitInsertStatement(HqlParser.InsertStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#targetFields}.
	 * @param ctx the parse tree
	 */
	void enterTargetFields(HqlParser.TargetFieldsContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#targetFields}.
	 * @param ctx the parse tree
	 */
	void exitTargetFields(HqlParser.TargetFieldsContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#valuesList}.
	 * @param ctx the parse tree
	 */
	void enterValuesList(HqlParser.ValuesListContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#valuesList}.
	 * @param ctx the parse tree
	 */
	void exitValuesList(HqlParser.ValuesListContext ctx);
	/**
	 * Enter a parse tree produced by the {@code values}
	 * labeled alternative in {@link HqlParser#queryqueryjoinTargetjoinTargetexpressionexpressionexpressionexpressionexpressionexpressionexpressionexpressionexpressionexpressionexpressionexpressionexpressionexpressionprimaryExpressionprimaryExpressionprimaryExpressionprimaryExpressionprimaryExpressionfunctionfunctionfunctionfunctionfunctionfunctionfunctionfunctionframeStartframeStartframeStartframeStartframeExclusionframeExclusionframeExclusionframeExclusionframeEndframeEndframeEndframeEndpredicatepredicatepredicatepredicatepredicatepredicatepredicatepredicatepredicatepredicatepredicatepredicate}.
	 * @param ctx the parse tree
	 */
	void enterValues(HqlParser.ValuesContext ctx);
	/**
	 * Exit a parse tree produced by the {@code values}
	 * labeled alternative in {@link HqlParser#queryqueryjoinTargetjoinTargetexpressionexpressionexpressionexpressionexpressionexpressionexpressionexpressionexpressionexpressionexpressionexpressionexpressionexpressionprimaryExpressionprimaryExpressionprimaryExpressionprimaryExpressionprimaryExpressionfunctionfunctionfunctionfunctionfunctionfunctionfunctionfunctionframeStartframeStartframeStartframeStartframeExclusionframeExclusionframeExclusionframeExclusionframeEndframeEndframeEndframeEndpredicatepredicatepredicatepredicatepredicatepredicatepredicatepredicatepredicatepredicatepredicatepredicate}.
	 * @param ctx the parse tree
	 */
	void exitValues(HqlParser.ValuesContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#instantiation}.
	 * @param ctx the parse tree
	 */
	void enterInstantiation(HqlParser.InstantiationContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#instantiation}.
	 * @param ctx the parse tree
	 */
	void exitInstantiation(HqlParser.InstantiationContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#alias}.
	 * @param ctx the parse tree
	 */
	void enterAlias(HqlParser.AliasContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#alias}.
	 * @param ctx the parse tree
	 */
	void exitAlias(HqlParser.AliasContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#groupedItem}.
	 * @param ctx the parse tree
	 */
	void enterGroupedItem(HqlParser.GroupedItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#groupedItem}.
	 * @param ctx the parse tree
	 */
	void exitGroupedItem(HqlParser.GroupedItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#sortedItem}.
	 * @param ctx the parse tree
	 */
	void enterSortedItem(HqlParser.SortedItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#sortedItem}.
	 * @param ctx the parse tree
	 */
	void exitSortedItem(HqlParser.SortedItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#sortExpression}.
	 * @param ctx the parse tree
	 */
	void enterSortExpression(HqlParser.SortExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#sortExpression}.
	 * @param ctx the parse tree
	 */
	void exitSortExpression(HqlParser.SortExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#sortDirection}.
	 * @param ctx the parse tree
	 */
	void enterSortDirection(HqlParser.SortDirectionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#sortDirection}.
	 * @param ctx the parse tree
	 */
	void exitSortDirection(HqlParser.SortDirectionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#nullsPrecedence}.
	 * @param ctx the parse tree
	 */
	void enterNullsPrecedence(HqlParser.NullsPrecedenceContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#nullsPrecedence}.
	 * @param ctx the parse tree
	 */
	void exitNullsPrecedence(HqlParser.NullsPrecedenceContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#limitClause}.
	 * @param ctx the parse tree
	 */
	void enterLimitClause(HqlParser.LimitClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#limitClause}.
	 * @param ctx the parse tree
	 */
	void exitLimitClause(HqlParser.LimitClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#offsetClause}.
	 * @param ctx the parse tree
	 */
	void enterOffsetClause(HqlParser.OffsetClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#offsetClause}.
	 * @param ctx the parse tree
	 */
	void exitOffsetClause(HqlParser.OffsetClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#fetchClause}.
	 * @param ctx the parse tree
	 */
	void enterFetchClause(HqlParser.FetchClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#fetchClause}.
	 * @param ctx the parse tree
	 */
	void exitFetchClause(HqlParser.FetchClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#subquery}.
	 * @param ctx the parse tree
	 */
	void enterSubquery(HqlParser.SubqueryContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#subquery}.
	 * @param ctx the parse tree
	 */
	void exitSubquery(HqlParser.SubqueryContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#selectClause}.
	 * @param ctx the parse tree
	 */
	void enterSelectClause(HqlParser.SelectClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#selectClause}.
	 * @param ctx the parse tree
	 */
	void exitSelectClause(HqlParser.SelectClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#selectionList}.
	 * @param ctx the parse tree
	 */
	void enterSelectionList(HqlParser.SelectionListContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#selectionList}.
	 * @param ctx the parse tree
	 */
	void exitSelectionList(HqlParser.SelectionListContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#selection}.
	 * @param ctx the parse tree
	 */
	void enterSelection(HqlParser.SelectionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#selection}.
	 * @param ctx the parse tree
	 */
	void exitSelection(HqlParser.SelectionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#selectExpression}.
	 * @param ctx the parse tree
	 */
	void enterSelectExpression(HqlParser.SelectExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#selectExpression}.
	 * @param ctx the parse tree
	 */
	void exitSelectExpression(HqlParser.SelectExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#mapEntrySelection}.
	 * @param ctx the parse tree
	 */
	void enterMapEntrySelection(HqlParser.MapEntrySelectionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#mapEntrySelection}.
	 * @param ctx the parse tree
	 */
	void exitMapEntrySelection(HqlParser.MapEntrySelectionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#jpaSelectObjectSyntax}.
	 * @param ctx the parse tree
	 */
	void enterJpaSelectObjectSyntax(HqlParser.JpaSelectObjectSyntaxContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#jpaSelectObjectSyntax}.
	 * @param ctx the parse tree
	 */
	void exitJpaSelectObjectSyntax(HqlParser.JpaSelectObjectSyntaxContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#whereClause}.
	 * @param ctx the parse tree
	 */
	void enterWhereClause(HqlParser.WhereClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#whereClause}.
	 * @param ctx the parse tree
	 */
	void exitWhereClause(HqlParser.WhereClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#joinType}.
	 * @param ctx the parse tree
	 */
	void enterJoinType(HqlParser.JoinTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#joinType}.
	 * @param ctx the parse tree
	 */
	void exitJoinType(HqlParser.JoinTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#crossJoin}.
	 * @param ctx the parse tree
	 */
	void enterCrossJoin(HqlParser.CrossJoinContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#crossJoin}.
	 * @param ctx the parse tree
	 */
	void exitCrossJoin(HqlParser.CrossJoinContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#joinRestriction}.
	 * @param ctx the parse tree
	 */
	void enterJoinRestriction(HqlParser.JoinRestrictionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#joinRestriction}.
	 * @param ctx the parse tree
	 */
	void exitJoinRestriction(HqlParser.JoinRestrictionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#jpaCollectionJoin}.
	 * @param ctx the parse tree
	 */
	void enterJpaCollectionJoin(HqlParser.JpaCollectionJoinContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#jpaCollectionJoin}.
	 * @param ctx the parse tree
	 */
	void exitJpaCollectionJoin(HqlParser.JpaCollectionJoinContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#groupByClause}.
	 * @param ctx the parse tree
	 */
	void enterGroupByClause(HqlParser.GroupByClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#groupByClause}.
	 * @param ctx the parse tree
	 */
	void exitGroupByClause(HqlParser.GroupByClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#orderByClause}.
	 * @param ctx the parse tree
	 */
	void enterOrderByClause(HqlParser.OrderByClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#orderByClause}.
	 * @param ctx the parse tree
	 */
	void exitOrderByClause(HqlParser.OrderByClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#havingClause}.
	 * @param ctx the parse tree
	 */
	void enterHavingClause(HqlParser.HavingClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#havingClause}.
	 * @param ctx the parse tree
	 */
	void exitHavingClause(HqlParser.HavingClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#setOperator}.
	 * @param ctx the parse tree
	 */
	void enterSetOperator(HqlParser.SetOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#setOperator}.
	 * @param ctx the parse tree
	 */
	void exitSetOperator(HqlParser.SetOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#literal}.
	 * @param ctx the parse tree
	 */
	void enterLiteral(HqlParser.LiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#literal}.
	 * @param ctx the parse tree
	 */
	void exitLiteral(HqlParser.LiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#booleanLiteral}.
	 * @param ctx the parse tree
	 */
	void enterBooleanLiteral(HqlParser.BooleanLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#booleanLiteral}.
	 * @param ctx the parse tree
	 */
	void exitBooleanLiteral(HqlParser.BooleanLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#stringLiteral}.
	 * @param ctx the parse tree
	 */
	void enterStringLiteral(HqlParser.StringLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#stringLiteral}.
	 * @param ctx the parse tree
	 */
	void exitStringLiteral(HqlParser.StringLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#numericLiteral}.
	 * @param ctx the parse tree
	 */
	void enterNumericLiteral(HqlParser.NumericLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#numericLiteral}.
	 * @param ctx the parse tree
	 */
	void exitNumericLiteral(HqlParser.NumericLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#dateTimeLiteral}.
	 * @param ctx the parse tree
	 */
	void enterDateTimeLiteral(HqlParser.DateTimeLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#dateTimeLiteral}.
	 * @param ctx the parse tree
	 */
	void exitDateTimeLiteral(HqlParser.DateTimeLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#datetimeField}.
	 * @param ctx the parse tree
	 */
	void enterDatetimeField(HqlParser.DatetimeFieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#datetimeField}.
	 * @param ctx the parse tree
	 */
	void exitDatetimeField(HqlParser.DatetimeFieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#binaryLiteral}.
	 * @param ctx the parse tree
	 */
	void enterBinaryLiteral(HqlParser.BinaryLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#binaryLiteral}.
	 * @param ctx the parse tree
	 */
	void exitBinaryLiteral(HqlParser.BinaryLiteralContext ctx);
	/**
	 * Enter a parse tree produced by the {@code AdditionExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterAdditionExpression(HqlParser.AdditionExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code AdditionExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitAdditionExpression(HqlParser.AdditionExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code FromDurationExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterFromDurationExpression(HqlParser.FromDurationExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code FromDurationExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitFromDurationExpression(HqlParser.FromDurationExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code PlainPrimaryExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterPlainPrimaryExpression(HqlParser.PlainPrimaryExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code PlainPrimaryExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitPlainPrimaryExpression(HqlParser.PlainPrimaryExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code TupleExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterTupleExpression(HqlParser.TupleExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code TupleExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitTupleExpression(HqlParser.TupleExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code GroupedExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterGroupedExpression(HqlParser.GroupedExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code GroupedExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitGroupedExpression(HqlParser.GroupedExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SignedNumericLiteral}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterSignedNumericLiteral(HqlParser.SignedNumericLiteralContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SignedNumericLiteral}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitSignedNumericLiteral(HqlParser.SignedNumericLiteralContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ToDurationExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterToDurationExpression(HqlParser.ToDurationExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ToDurationExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitToDurationExpression(HqlParser.ToDurationExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SubqueryExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterSubqueryExpression(HqlParser.SubqueryExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SubqueryExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitSubqueryExpression(HqlParser.SubqueryExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code DayOfMonthExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterDayOfMonthExpression(HqlParser.DayOfMonthExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code DayOfMonthExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitDayOfMonthExpression(HqlParser.DayOfMonthExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code DayOfWeekExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterDayOfWeekExpression(HqlParser.DayOfWeekExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code DayOfWeekExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitDayOfWeekExpression(HqlParser.DayOfWeekExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code WeekOfYearExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterWeekOfYearExpression(HqlParser.WeekOfYearExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code WeekOfYearExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitWeekOfYearExpression(HqlParser.WeekOfYearExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code HqlConcatenationExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterHqlConcatenationExpression(HqlParser.HqlConcatenationExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code HqlConcatenationExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitHqlConcatenationExpression(HqlParser.HqlConcatenationExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code MultiplicationExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterMultiplicationExpression(HqlParser.MultiplicationExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code MultiplicationExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitMultiplicationExpression(HqlParser.MultiplicationExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code SignedExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterSignedExpression(HqlParser.SignedExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code SignedExpression}
	 * labeled alternative in {@link HqlParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitSignedExpression(HqlParser.SignedExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code CaseExpression}
	 * labeled alternative in {@link HqlParser#primaryExpression}.
	 * @param ctx the parse tree
	 */
	void enterCaseExpression(HqlParser.CaseExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code CaseExpression}
	 * labeled alternative in {@link HqlParser#primaryExpression}.
	 * @param ctx the parse tree
	 */
	void exitCaseExpression(HqlParser.CaseExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code LiteralExpression}
	 * labeled alternative in {@link HqlParser#primaryExpression}.
	 * @param ctx the parse tree
	 */
	void enterLiteralExpression(HqlParser.LiteralExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code LiteralExpression}
	 * labeled alternative in {@link HqlParser#primaryExpression}.
	 * @param ctx the parse tree
	 */
	void exitLiteralExpression(HqlParser.LiteralExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ParameterExpression}
	 * labeled alternative in {@link HqlParser#primaryExpression}.
	 * @param ctx the parse tree
	 */
	void enterParameterExpression(HqlParser.ParameterExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ParameterExpression}
	 * labeled alternative in {@link HqlParser#primaryExpression}.
	 * @param ctx the parse tree
	 */
	void exitParameterExpression(HqlParser.ParameterExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code FunctionExpression}
	 * labeled alternative in {@link HqlParser#primaryExpression}.
	 * @param ctx the parse tree
	 */
	void enterFunctionExpression(HqlParser.FunctionExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code FunctionExpression}
	 * labeled alternative in {@link HqlParser#primaryExpression}.
	 * @param ctx the parse tree
	 */
	void exitFunctionExpression(HqlParser.FunctionExpressionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code GeneralPathExpression}
	 * labeled alternative in {@link HqlParser#primaryExpression}.
	 * @param ctx the parse tree
	 */
	void enterGeneralPathExpression(HqlParser.GeneralPathExpressionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code GeneralPathExpression}
	 * labeled alternative in {@link HqlParser#primaryExpression}.
	 * @param ctx the parse tree
	 */
	void exitGeneralPathExpression(HqlParser.GeneralPathExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#identificationVariable}.
	 * @param ctx the parse tree
	 */
	void enterIdentificationVariable(HqlParser.IdentificationVariableContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#identificationVariable}.
	 * @param ctx the parse tree
	 */
	void exitIdentificationVariable(HqlParser.IdentificationVariableContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#path}.
	 * @param ctx the parse tree
	 */
	void enterPath(HqlParser.PathContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#path}.
	 * @param ctx the parse tree
	 */
	void exitPath(HqlParser.PathContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#generalPathFragment}.
	 * @param ctx the parse tree
	 */
	void enterGeneralPathFragment(HqlParser.GeneralPathFragmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#generalPathFragment}.
	 * @param ctx the parse tree
	 */
	void exitGeneralPathFragment(HqlParser.GeneralPathFragmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#indexedPathAccessFragment}.
	 * @param ctx the parse tree
	 */
	void enterIndexedPathAccessFragment(HqlParser.IndexedPathAccessFragmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#indexedPathAccessFragment}.
	 * @param ctx the parse tree
	 */
	void exitIndexedPathAccessFragment(HqlParser.IndexedPathAccessFragmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#simplePath}.
	 * @param ctx the parse tree
	 */
	void enterSimplePath(HqlParser.SimplePathContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#simplePath}.
	 * @param ctx the parse tree
	 */
	void exitSimplePath(HqlParser.SimplePathContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#simplePathElement}.
	 * @param ctx the parse tree
	 */
	void enterSimplePathElement(HqlParser.SimplePathElementContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#simplePathElement}.
	 * @param ctx the parse tree
	 */
	void exitSimplePathElement(HqlParser.SimplePathElementContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#caseList}.
	 * @param ctx the parse tree
	 */
	void enterCaseList(HqlParser.CaseListContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#caseList}.
	 * @param ctx the parse tree
	 */
	void exitCaseList(HqlParser.CaseListContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#simpleCaseExpression}.
	 * @param ctx the parse tree
	 */
	void enterSimpleCaseExpression(HqlParser.SimpleCaseExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#simpleCaseExpression}.
	 * @param ctx the parse tree
	 */
	void exitSimpleCaseExpression(HqlParser.SimpleCaseExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#searchedCaseExpression}.
	 * @param ctx the parse tree
	 */
	void enterSearchedCaseExpression(HqlParser.SearchedCaseExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#searchedCaseExpression}.
	 * @param ctx the parse tree
	 */
	void exitSearchedCaseExpression(HqlParser.SearchedCaseExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#caseWhenExpressionClause}.
	 * @param ctx the parse tree
	 */
	void enterCaseWhenExpressionClause(HqlParser.CaseWhenExpressionClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#caseWhenExpressionClause}.
	 * @param ctx the parse tree
	 */
	void exitCaseWhenExpressionClause(HqlParser.CaseWhenExpressionClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#caseWhenPredicateClause}.
	 * @param ctx the parse tree
	 */
	void enterCaseWhenPredicateClause(HqlParser.CaseWhenPredicateClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#caseWhenPredicateClause}.
	 * @param ctx the parse tree
	 */
	void exitCaseWhenPredicateClause(HqlParser.CaseWhenPredicateClauseContext ctx);
	/**
	 * Enter a parse tree produced by the {@code GenericFunction}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void enterGenericFunction(HqlParser.GenericFunctionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code GenericFunction}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void exitGenericFunction(HqlParser.GenericFunctionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code FunctionWithSubquery}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void enterFunctionWithSubquery(HqlParser.FunctionWithSubqueryContext ctx);
	/**
	 * Exit a parse tree produced by the {@code FunctionWithSubquery}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void exitFunctionWithSubquery(HqlParser.FunctionWithSubqueryContext ctx);
	/**
	 * Enter a parse tree produced by the {@code CastFunctionInvocation}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void enterCastFunctionInvocation(HqlParser.CastFunctionInvocationContext ctx);
	/**
	 * Exit a parse tree produced by the {@code CastFunctionInvocation}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void exitCastFunctionInvocation(HqlParser.CastFunctionInvocationContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ExtractFunctionInvocation}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void enterExtractFunctionInvocation(HqlParser.ExtractFunctionInvocationContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ExtractFunctionInvocation}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void exitExtractFunctionInvocation(HqlParser.ExtractFunctionInvocationContext ctx);
	/**
	 * Enter a parse tree produced by the {@code TrimFunctionInvocation}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void enterTrimFunctionInvocation(HqlParser.TrimFunctionInvocationContext ctx);
	/**
	 * Exit a parse tree produced by the {@code TrimFunctionInvocation}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void exitTrimFunctionInvocation(HqlParser.TrimFunctionInvocationContext ctx);
	/**
	 * Enter a parse tree produced by the {@code EveryFunctionInvocation}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void enterEveryFunctionInvocation(HqlParser.EveryFunctionInvocationContext ctx);
	/**
	 * Exit a parse tree produced by the {@code EveryFunctionInvocation}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void exitEveryFunctionInvocation(HqlParser.EveryFunctionInvocationContext ctx);
	/**
	 * Enter a parse tree produced by the {@code AnyFunctionInvocation}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void enterAnyFunctionInvocation(HqlParser.AnyFunctionInvocationContext ctx);
	/**
	 * Exit a parse tree produced by the {@code AnyFunctionInvocation}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void exitAnyFunctionInvocation(HqlParser.AnyFunctionInvocationContext ctx);
	/**
	 * Enter a parse tree produced by the {@code TreatedPathInvocation}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void enterTreatedPathInvocation(HqlParser.TreatedPathInvocationContext ctx);
	/**
	 * Exit a parse tree produced by the {@code TreatedPathInvocation}
	 * labeled alternative in {@link HqlParser#function}.
	 * @param ctx the parse tree
	 */
	void exitTreatedPathInvocation(HqlParser.TreatedPathInvocationContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#functionArguments}.
	 * @param ctx the parse tree
	 */
	void enterFunctionArguments(HqlParser.FunctionArgumentsContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#functionArguments}.
	 * @param ctx the parse tree
	 */
	void exitFunctionArguments(HqlParser.FunctionArgumentsContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#filterClause}.
	 * @param ctx the parse tree
	 */
	void enterFilterClause(HqlParser.FilterClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#filterClause}.
	 * @param ctx the parse tree
	 */
	void exitFilterClause(HqlParser.FilterClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#withinGroup}.
	 * @param ctx the parse tree
	 */
	void enterWithinGroup(HqlParser.WithinGroupContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#withinGroup}.
	 * @param ctx the parse tree
	 */
	void exitWithinGroup(HqlParser.WithinGroupContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#overClause}.
	 * @param ctx the parse tree
	 */
	void enterOverClause(HqlParser.OverClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#overClause}.
	 * @param ctx the parse tree
	 */
	void exitOverClause(HqlParser.OverClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#partitionClause}.
	 * @param ctx the parse tree
	 */
	void enterPartitionClause(HqlParser.PartitionClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#partitionClause}.
	 * @param ctx the parse tree
	 */
	void exitPartitionClause(HqlParser.PartitionClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#frameClause}.
	 * @param ctx the parse tree
	 */
	void enterFrameClause(HqlParser.FrameClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#frameClause}.
	 * @param ctx the parse tree
	 */
	void exitFrameClause(HqlParser.FrameClauseContext ctx);
	/**
	 * Enter a parse tree produced by the {@code UnboundedPrecedingFrameStart}
	 * labeled alternative in {@link HqlParser#frameStart}.
	 * @param ctx the parse tree
	 */
	void enterUnboundedPrecedingFrameStart(HqlParser.UnboundedPrecedingFrameStartContext ctx);
	/**
	 * Exit a parse tree produced by the {@code UnboundedPrecedingFrameStart}
	 * labeled alternative in {@link HqlParser#frameStart}.
	 * @param ctx the parse tree
	 */
	void exitUnboundedPrecedingFrameStart(HqlParser.UnboundedPrecedingFrameStartContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ExpressionPrecedingFrameStart}
	 * labeled alternative in {@link HqlParser#frameStart}.
	 * @param ctx the parse tree
	 */
	void enterExpressionPrecedingFrameStart(HqlParser.ExpressionPrecedingFrameStartContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ExpressionPrecedingFrameStart}
	 * labeled alternative in {@link HqlParser#frameStart}.
	 * @param ctx the parse tree
	 */
	void exitExpressionPrecedingFrameStart(HqlParser.ExpressionPrecedingFrameStartContext ctx);
	/**
	 * Enter a parse tree produced by the {@code CurrentRowFrameStart}
	 * labeled alternative in {@link HqlParser#frameStart}.
	 * @param ctx the parse tree
	 */
	void enterCurrentRowFrameStart(HqlParser.CurrentRowFrameStartContext ctx);
	/**
	 * Exit a parse tree produced by the {@code CurrentRowFrameStart}
	 * labeled alternative in {@link HqlParser#frameStart}.
	 * @param ctx the parse tree
	 */
	void exitCurrentRowFrameStart(HqlParser.CurrentRowFrameStartContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ExpressionFollowingFrameStart}
	 * labeled alternative in {@link HqlParser#frameStart}.
	 * @param ctx the parse tree
	 */
	void enterExpressionFollowingFrameStart(HqlParser.ExpressionFollowingFrameStartContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ExpressionFollowingFrameStart}
	 * labeled alternative in {@link HqlParser#frameStart}.
	 * @param ctx the parse tree
	 */
	void exitExpressionFollowingFrameStart(HqlParser.ExpressionFollowingFrameStartContext ctx);
	/**
	 * Enter a parse tree produced by the {@code CurrentRowFrameExclusion}
	 * labeled alternative in {@link HqlParser#frameExclusion}.
	 * @param ctx the parse tree
	 */
	void enterCurrentRowFrameExclusion(HqlParser.CurrentRowFrameExclusionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code CurrentRowFrameExclusion}
	 * labeled alternative in {@link HqlParser#frameExclusion}.
	 * @param ctx the parse tree
	 */
	void exitCurrentRowFrameExclusion(HqlParser.CurrentRowFrameExclusionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code GroupFrameExclusion}
	 * labeled alternative in {@link HqlParser#frameExclusion}.
	 * @param ctx the parse tree
	 */
	void enterGroupFrameExclusion(HqlParser.GroupFrameExclusionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code GroupFrameExclusion}
	 * labeled alternative in {@link HqlParser#frameExclusion}.
	 * @param ctx the parse tree
	 */
	void exitGroupFrameExclusion(HqlParser.GroupFrameExclusionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code TiesFrameExclusion}
	 * labeled alternative in {@link HqlParser#frameExclusion}.
	 * @param ctx the parse tree
	 */
	void enterTiesFrameExclusion(HqlParser.TiesFrameExclusionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code TiesFrameExclusion}
	 * labeled alternative in {@link HqlParser#frameExclusion}.
	 * @param ctx the parse tree
	 */
	void exitTiesFrameExclusion(HqlParser.TiesFrameExclusionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code NoOthersFrameExclusion}
	 * labeled alternative in {@link HqlParser#frameExclusion}.
	 * @param ctx the parse tree
	 */
	void enterNoOthersFrameExclusion(HqlParser.NoOthersFrameExclusionContext ctx);
	/**
	 * Exit a parse tree produced by the {@code NoOthersFrameExclusion}
	 * labeled alternative in {@link HqlParser#frameExclusion}.
	 * @param ctx the parse tree
	 */
	void exitNoOthersFrameExclusion(HqlParser.NoOthersFrameExclusionContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ExpressionPrecedingFrameEnd}
	 * labeled alternative in {@link HqlParser#frameEnd}.
	 * @param ctx the parse tree
	 */
	void enterExpressionPrecedingFrameEnd(HqlParser.ExpressionPrecedingFrameEndContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ExpressionPrecedingFrameEnd}
	 * labeled alternative in {@link HqlParser#frameEnd}.
	 * @param ctx the parse tree
	 */
	void exitExpressionPrecedingFrameEnd(HqlParser.ExpressionPrecedingFrameEndContext ctx);
	/**
	 * Enter a parse tree produced by the {@code CurrentRowFrameEnd}
	 * labeled alternative in {@link HqlParser#frameEnd}.
	 * @param ctx the parse tree
	 */
	void enterCurrentRowFrameEnd(HqlParser.CurrentRowFrameEndContext ctx);
	/**
	 * Exit a parse tree produced by the {@code CurrentRowFrameEnd}
	 * labeled alternative in {@link HqlParser#frameEnd}.
	 * @param ctx the parse tree
	 */
	void exitCurrentRowFrameEnd(HqlParser.CurrentRowFrameEndContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ExpressionFollowingFrameEnd}
	 * labeled alternative in {@link HqlParser#frameEnd}.
	 * @param ctx the parse tree
	 */
	void enterExpressionFollowingFrameEnd(HqlParser.ExpressionFollowingFrameEndContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ExpressionFollowingFrameEnd}
	 * labeled alternative in {@link HqlParser#frameEnd}.
	 * @param ctx the parse tree
	 */
	void exitExpressionFollowingFrameEnd(HqlParser.ExpressionFollowingFrameEndContext ctx);
	/**
	 * Enter a parse tree produced by the {@code UnboundedFollowingFrameEnd}
	 * labeled alternative in {@link HqlParser#frameEnd}.
	 * @param ctx the parse tree
	 */
	void enterUnboundedFollowingFrameEnd(HqlParser.UnboundedFollowingFrameEndContext ctx);
	/**
	 * Exit a parse tree produced by the {@code UnboundedFollowingFrameEnd}
	 * labeled alternative in {@link HqlParser#frameEnd}.
	 * @param ctx the parse tree
	 */
	void exitUnboundedFollowingFrameEnd(HqlParser.UnboundedFollowingFrameEndContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#castFunction}.
	 * @param ctx the parse tree
	 */
	void enterCastFunction(HqlParser.CastFunctionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#castFunction}.
	 * @param ctx the parse tree
	 */
	void exitCastFunction(HqlParser.CastFunctionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#castTarget}.
	 * @param ctx the parse tree
	 */
	void enterCastTarget(HqlParser.CastTargetContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#castTarget}.
	 * @param ctx the parse tree
	 */
	void exitCastTarget(HqlParser.CastTargetContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#castTargetType}.
	 * @param ctx the parse tree
	 */
	void enterCastTargetType(HqlParser.CastTargetTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#castTargetType}.
	 * @param ctx the parse tree
	 */
	void exitCastTargetType(HqlParser.CastTargetTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#extractFunction}.
	 * @param ctx the parse tree
	 */
	void enterExtractFunction(HqlParser.ExtractFunctionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#extractFunction}.
	 * @param ctx the parse tree
	 */
	void exitExtractFunction(HqlParser.ExtractFunctionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#trimFunction}.
	 * @param ctx the parse tree
	 */
	void enterTrimFunction(HqlParser.TrimFunctionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#trimFunction}.
	 * @param ctx the parse tree
	 */
	void exitTrimFunction(HqlParser.TrimFunctionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#dateTimeFunction}.
	 * @param ctx the parse tree
	 */
	void enterDateTimeFunction(HqlParser.DateTimeFunctionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#dateTimeFunction}.
	 * @param ctx the parse tree
	 */
	void exitDateTimeFunction(HqlParser.DateTimeFunctionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#everyFunction}.
	 * @param ctx the parse tree
	 */
	void enterEveryFunction(HqlParser.EveryFunctionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#everyFunction}.
	 * @param ctx the parse tree
	 */
	void exitEveryFunction(HqlParser.EveryFunctionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#anyFunction}.
	 * @param ctx the parse tree
	 */
	void enterAnyFunction(HqlParser.AnyFunctionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#anyFunction}.
	 * @param ctx the parse tree
	 */
	void exitAnyFunction(HqlParser.AnyFunctionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#treatedPath}.
	 * @param ctx the parse tree
	 */
	void enterTreatedPath(HqlParser.TreatedPathContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#treatedPath}.
	 * @param ctx the parse tree
	 */
	void exitTreatedPath(HqlParser.TreatedPathContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#pathContinutation}.
	 * @param ctx the parse tree
	 */
	void enterPathContinutation(HqlParser.PathContinutationContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#pathContinutation}.
	 * @param ctx the parse tree
	 */
	void exitPathContinutation(HqlParser.PathContinutationContext ctx);
	/**
	 * Enter a parse tree produced by the {@code NullExpressionPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void enterNullExpressionPredicate(HqlParser.NullExpressionPredicateContext ctx);
	/**
	 * Exit a parse tree produced by the {@code NullExpressionPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void exitNullExpressionPredicate(HqlParser.NullExpressionPredicateContext ctx);
	/**
	 * Enter a parse tree produced by the {@code BetweenPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void enterBetweenPredicate(HqlParser.BetweenPredicateContext ctx);
	/**
	 * Exit a parse tree produced by the {@code BetweenPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void exitBetweenPredicate(HqlParser.BetweenPredicateContext ctx);
	/**
	 * Enter a parse tree produced by the {@code OrPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void enterOrPredicate(HqlParser.OrPredicateContext ctx);
	/**
	 * Exit a parse tree produced by the {@code OrPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void exitOrPredicate(HqlParser.OrPredicateContext ctx);
	/**
	 * Enter a parse tree produced by the {@code RelationalPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void enterRelationalPredicate(HqlParser.RelationalPredicateContext ctx);
	/**
	 * Exit a parse tree produced by the {@code RelationalPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void exitRelationalPredicate(HqlParser.RelationalPredicateContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ExistsPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void enterExistsPredicate(HqlParser.ExistsPredicateContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ExistsPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void exitExistsPredicate(HqlParser.ExistsPredicateContext ctx);
	/**
	 * Enter a parse tree produced by the {@code CollectionPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void enterCollectionPredicate(HqlParser.CollectionPredicateContext ctx);
	/**
	 * Exit a parse tree produced by the {@code CollectionPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void exitCollectionPredicate(HqlParser.CollectionPredicateContext ctx);
	/**
	 * Enter a parse tree produced by the {@code AndPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void enterAndPredicate(HqlParser.AndPredicateContext ctx);
	/**
	 * Exit a parse tree produced by the {@code AndPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void exitAndPredicate(HqlParser.AndPredicateContext ctx);
	/**
	 * Enter a parse tree produced by the {@code GroupedPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void enterGroupedPredicate(HqlParser.GroupedPredicateContext ctx);
	/**
	 * Exit a parse tree produced by the {@code GroupedPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void exitGroupedPredicate(HqlParser.GroupedPredicateContext ctx);
	/**
	 * Enter a parse tree produced by the {@code LikePredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void enterLikePredicate(HqlParser.LikePredicateContext ctx);
	/**
	 * Exit a parse tree produced by the {@code LikePredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void exitLikePredicate(HqlParser.LikePredicateContext ctx);
	/**
	 * Enter a parse tree produced by the {@code InPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void enterInPredicate(HqlParser.InPredicateContext ctx);
	/**
	 * Exit a parse tree produced by the {@code InPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void exitInPredicate(HqlParser.InPredicateContext ctx);
	/**
	 * Enter a parse tree produced by the {@code NotPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void enterNotPredicate(HqlParser.NotPredicateContext ctx);
	/**
	 * Exit a parse tree produced by the {@code NotPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void exitNotPredicate(HqlParser.NotPredicateContext ctx);
	/**
	 * Enter a parse tree produced by the {@code ExpressionPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void enterExpressionPredicate(HqlParser.ExpressionPredicateContext ctx);
	/**
	 * Exit a parse tree produced by the {@code ExpressionPredicate}
	 * labeled alternative in {@link HqlParser#predicate}.
	 * @param ctx the parse tree
	 */
	void exitExpressionPredicate(HqlParser.ExpressionPredicateContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#expressionOrPredicate}.
	 * @param ctx the parse tree
	 */
	void enterExpressionOrPredicate(HqlParser.ExpressionOrPredicateContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#expressionOrPredicate}.
	 * @param ctx the parse tree
	 */
	void exitExpressionOrPredicate(HqlParser.ExpressionOrPredicateContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#relationalExpression}.
	 * @param ctx the parse tree
	 */
	void enterRelationalExpression(HqlParser.RelationalExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#relationalExpression}.
	 * @param ctx the parse tree
	 */
	void exitRelationalExpression(HqlParser.RelationalExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#betweenExpression}.
	 * @param ctx the parse tree
	 */
	void enterBetweenExpression(HqlParser.BetweenExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#betweenExpression}.
	 * @param ctx the parse tree
	 */
	void exitBetweenExpression(HqlParser.BetweenExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#dealingWithNullExpression}.
	 * @param ctx the parse tree
	 */
	void enterDealingWithNullExpression(HqlParser.DealingWithNullExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#dealingWithNullExpression}.
	 * @param ctx the parse tree
	 */
	void exitDealingWithNullExpression(HqlParser.DealingWithNullExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#stringPatternMatching}.
	 * @param ctx the parse tree
	 */
	void enterStringPatternMatching(HqlParser.StringPatternMatchingContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#stringPatternMatching}.
	 * @param ctx the parse tree
	 */
	void exitStringPatternMatching(HqlParser.StringPatternMatchingContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#inExpression}.
	 * @param ctx the parse tree
	 */
	void enterInExpression(HqlParser.InExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#inExpression}.
	 * @param ctx the parse tree
	 */
	void exitInExpression(HqlParser.InExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#inList}.
	 * @param ctx the parse tree
	 */
	void enterInList(HqlParser.InListContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#inList}.
	 * @param ctx the parse tree
	 */
	void exitInList(HqlParser.InListContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#existsExpression}.
	 * @param ctx the parse tree
	 */
	void enterExistsExpression(HqlParser.ExistsExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#existsExpression}.
	 * @param ctx the parse tree
	 */
	void exitExistsExpression(HqlParser.ExistsExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#collectionExpression}.
	 * @param ctx the parse tree
	 */
	void enterCollectionExpression(HqlParser.CollectionExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#collectionExpression}.
	 * @param ctx the parse tree
	 */
	void exitCollectionExpression(HqlParser.CollectionExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#instantiationTarget}.
	 * @param ctx the parse tree
	 */
	void enterInstantiationTarget(HqlParser.InstantiationTargetContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#instantiationTarget}.
	 * @param ctx the parse tree
	 */
	void exitInstantiationTarget(HqlParser.InstantiationTargetContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#instantiationArguments}.
	 * @param ctx the parse tree
	 */
	void enterInstantiationArguments(HqlParser.InstantiationArgumentsContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#instantiationArguments}.
	 * @param ctx the parse tree
	 */
	void exitInstantiationArguments(HqlParser.InstantiationArgumentsContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#instantiationArgument}.
	 * @param ctx the parse tree
	 */
	void enterInstantiationArgument(HqlParser.InstantiationArgumentContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#instantiationArgument}.
	 * @param ctx the parse tree
	 */
	void exitInstantiationArgument(HqlParser.InstantiationArgumentContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#parameterOrIntegerLiteral}.
	 * @param ctx the parse tree
	 */
	void enterParameterOrIntegerLiteral(HqlParser.ParameterOrIntegerLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#parameterOrIntegerLiteral}.
	 * @param ctx the parse tree
	 */
	void exitParameterOrIntegerLiteral(HqlParser.ParameterOrIntegerLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#parameterOrNumberLiteral}.
	 * @param ctx the parse tree
	 */
	void enterParameterOrNumberLiteral(HqlParser.ParameterOrNumberLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#parameterOrNumberLiteral}.
	 * @param ctx the parse tree
	 */
	void exitParameterOrNumberLiteral(HqlParser.ParameterOrNumberLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#variable}.
	 * @param ctx the parse tree
	 */
	void enterVariable(HqlParser.VariableContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#variable}.
	 * @param ctx the parse tree
	 */
	void exitVariable(HqlParser.VariableContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#parameter}.
	 * @param ctx the parse tree
	 */
	void enterParameter(HqlParser.ParameterContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#parameter}.
	 * @param ctx the parse tree
	 */
	void exitParameter(HqlParser.ParameterContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#entityName}.
	 * @param ctx the parse tree
	 */
	void enterEntityName(HqlParser.EntityNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#entityName}.
	 * @param ctx the parse tree
	 */
	void exitEntityName(HqlParser.EntityNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#identifier}.
	 * @param ctx the parse tree
	 */
	void enterIdentifier(HqlParser.IdentifierContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#identifier}.
	 * @param ctx the parse tree
	 */
	void exitIdentifier(HqlParser.IdentifierContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#character}.
	 * @param ctx the parse tree
	 */
	void enterCharacter(HqlParser.CharacterContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#character}.
	 * @param ctx the parse tree
	 */
	void exitCharacter(HqlParser.CharacterContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#functionName}.
	 * @param ctx the parse tree
	 */
	void enterFunctionName(HqlParser.FunctionNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#functionName}.
	 * @param ctx the parse tree
	 */
	void exitFunctionName(HqlParser.FunctionNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link HqlParser#reservedWord}.
	 * @param ctx the parse tree
	 */
	void enterReservedWord(HqlParser.ReservedWordContext ctx);
	/**
	 * Exit a parse tree produced by {@link HqlParser#reservedWord}.
	 * @param ctx the parse tree
	 */
	void exitReservedWord(HqlParser.ReservedWordContext ctx);
}