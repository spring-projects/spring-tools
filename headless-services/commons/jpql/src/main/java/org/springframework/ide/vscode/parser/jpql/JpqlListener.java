// Generated from Jpql.g4 by ANTLR 4.13.2
package org.springframework.ide.vscode.parser.jpql;

/**
 * JPQL per https://jakarta.ee/specifications/persistence/3.1/jakarta-persistence-spec-3.1.html#bnf
 *
 * This is JPA BNF for JPQL. There are gaps and inconsistencies in the BNF itself, explained by other fragments of the spec.
 *
 * @see https://github.com/jakartaee/persistence/blob/master/spec/src/main/asciidoc/ch04-query-language.adoc#bnf
 * @author Greg Turnquist
 * @author Christoph Strobl
 * @since 3.1
 */

import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link JpqlParser}.
 */
public interface JpqlListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link JpqlParser#start}.
	 * @param ctx the parse tree
	 */
	void enterStart(JpqlParser.StartContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#start}.
	 * @param ctx the parse tree
	 */
	void exitStart(JpqlParser.StartContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#ql_statement}.
	 * @param ctx the parse tree
	 */
	void enterQl_statement(JpqlParser.Ql_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#ql_statement}.
	 * @param ctx the parse tree
	 */
	void exitQl_statement(JpqlParser.Ql_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#select_statement}.
	 * @param ctx the parse tree
	 */
	void enterSelect_statement(JpqlParser.Select_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#select_statement}.
	 * @param ctx the parse tree
	 */
	void exitSelect_statement(JpqlParser.Select_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#update_statement}.
	 * @param ctx the parse tree
	 */
	void enterUpdate_statement(JpqlParser.Update_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#update_statement}.
	 * @param ctx the parse tree
	 */
	void exitUpdate_statement(JpqlParser.Update_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#delete_statement}.
	 * @param ctx the parse tree
	 */
	void enterDelete_statement(JpqlParser.Delete_statementContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#delete_statement}.
	 * @param ctx the parse tree
	 */
	void exitDelete_statement(JpqlParser.Delete_statementContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#from_clause}.
	 * @param ctx the parse tree
	 */
	void enterFrom_clause(JpqlParser.From_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#from_clause}.
	 * @param ctx the parse tree
	 */
	void exitFrom_clause(JpqlParser.From_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#identificationVariableDeclarationOrCollectionMemberDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterIdentificationVariableDeclarationOrCollectionMemberDeclaration(JpqlParser.IdentificationVariableDeclarationOrCollectionMemberDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#identificationVariableDeclarationOrCollectionMemberDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitIdentificationVariableDeclarationOrCollectionMemberDeclaration(JpqlParser.IdentificationVariableDeclarationOrCollectionMemberDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#identification_variable_declaration}.
	 * @param ctx the parse tree
	 */
	void enterIdentification_variable_declaration(JpqlParser.Identification_variable_declarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#identification_variable_declaration}.
	 * @param ctx the parse tree
	 */
	void exitIdentification_variable_declaration(JpqlParser.Identification_variable_declarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#range_variable_declaration}.
	 * @param ctx the parse tree
	 */
	void enterRange_variable_declaration(JpqlParser.Range_variable_declarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#range_variable_declaration}.
	 * @param ctx the parse tree
	 */
	void exitRange_variable_declaration(JpqlParser.Range_variable_declarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#join}.
	 * @param ctx the parse tree
	 */
	void enterJoin(JpqlParser.JoinContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#join}.
	 * @param ctx the parse tree
	 */
	void exitJoin(JpqlParser.JoinContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#fetch_join}.
	 * @param ctx the parse tree
	 */
	void enterFetch_join(JpqlParser.Fetch_joinContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#fetch_join}.
	 * @param ctx the parse tree
	 */
	void exitFetch_join(JpqlParser.Fetch_joinContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#join_spec}.
	 * @param ctx the parse tree
	 */
	void enterJoin_spec(JpqlParser.Join_specContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#join_spec}.
	 * @param ctx the parse tree
	 */
	void exitJoin_spec(JpqlParser.Join_specContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#join_condition}.
	 * @param ctx the parse tree
	 */
	void enterJoin_condition(JpqlParser.Join_conditionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#join_condition}.
	 * @param ctx the parse tree
	 */
	void exitJoin_condition(JpqlParser.Join_conditionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#join_association_path_expression}.
	 * @param ctx the parse tree
	 */
	void enterJoin_association_path_expression(JpqlParser.Join_association_path_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#join_association_path_expression}.
	 * @param ctx the parse tree
	 */
	void exitJoin_association_path_expression(JpqlParser.Join_association_path_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#join_collection_valued_path_expression}.
	 * @param ctx the parse tree
	 */
	void enterJoin_collection_valued_path_expression(JpqlParser.Join_collection_valued_path_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#join_collection_valued_path_expression}.
	 * @param ctx the parse tree
	 */
	void exitJoin_collection_valued_path_expression(JpqlParser.Join_collection_valued_path_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#join_single_valued_path_expression}.
	 * @param ctx the parse tree
	 */
	void enterJoin_single_valued_path_expression(JpqlParser.Join_single_valued_path_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#join_single_valued_path_expression}.
	 * @param ctx the parse tree
	 */
	void exitJoin_single_valued_path_expression(JpqlParser.Join_single_valued_path_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#collection_member_declaration}.
	 * @param ctx the parse tree
	 */
	void enterCollection_member_declaration(JpqlParser.Collection_member_declarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#collection_member_declaration}.
	 * @param ctx the parse tree
	 */
	void exitCollection_member_declaration(JpqlParser.Collection_member_declarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#qualified_identification_variable}.
	 * @param ctx the parse tree
	 */
	void enterQualified_identification_variable(JpqlParser.Qualified_identification_variableContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#qualified_identification_variable}.
	 * @param ctx the parse tree
	 */
	void exitQualified_identification_variable(JpqlParser.Qualified_identification_variableContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#map_field_identification_variable}.
	 * @param ctx the parse tree
	 */
	void enterMap_field_identification_variable(JpqlParser.Map_field_identification_variableContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#map_field_identification_variable}.
	 * @param ctx the parse tree
	 */
	void exitMap_field_identification_variable(JpqlParser.Map_field_identification_variableContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#single_valued_path_expression}.
	 * @param ctx the parse tree
	 */
	void enterSingle_valued_path_expression(JpqlParser.Single_valued_path_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#single_valued_path_expression}.
	 * @param ctx the parse tree
	 */
	void exitSingle_valued_path_expression(JpqlParser.Single_valued_path_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#general_identification_variable}.
	 * @param ctx the parse tree
	 */
	void enterGeneral_identification_variable(JpqlParser.General_identification_variableContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#general_identification_variable}.
	 * @param ctx the parse tree
	 */
	void exitGeneral_identification_variable(JpqlParser.General_identification_variableContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#general_subpath}.
	 * @param ctx the parse tree
	 */
	void enterGeneral_subpath(JpqlParser.General_subpathContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#general_subpath}.
	 * @param ctx the parse tree
	 */
	void exitGeneral_subpath(JpqlParser.General_subpathContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#simple_subpath}.
	 * @param ctx the parse tree
	 */
	void enterSimple_subpath(JpqlParser.Simple_subpathContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#simple_subpath}.
	 * @param ctx the parse tree
	 */
	void exitSimple_subpath(JpqlParser.Simple_subpathContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#treated_subpath}.
	 * @param ctx the parse tree
	 */
	void enterTreated_subpath(JpqlParser.Treated_subpathContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#treated_subpath}.
	 * @param ctx the parse tree
	 */
	void exitTreated_subpath(JpqlParser.Treated_subpathContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#state_field_path_expression}.
	 * @param ctx the parse tree
	 */
	void enterState_field_path_expression(JpqlParser.State_field_path_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#state_field_path_expression}.
	 * @param ctx the parse tree
	 */
	void exitState_field_path_expression(JpqlParser.State_field_path_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#state_valued_path_expression}.
	 * @param ctx the parse tree
	 */
	void enterState_valued_path_expression(JpqlParser.State_valued_path_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#state_valued_path_expression}.
	 * @param ctx the parse tree
	 */
	void exitState_valued_path_expression(JpqlParser.State_valued_path_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#single_valued_object_path_expression}.
	 * @param ctx the parse tree
	 */
	void enterSingle_valued_object_path_expression(JpqlParser.Single_valued_object_path_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#single_valued_object_path_expression}.
	 * @param ctx the parse tree
	 */
	void exitSingle_valued_object_path_expression(JpqlParser.Single_valued_object_path_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#collection_valued_path_expression}.
	 * @param ctx the parse tree
	 */
	void enterCollection_valued_path_expression(JpqlParser.Collection_valued_path_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#collection_valued_path_expression}.
	 * @param ctx the parse tree
	 */
	void exitCollection_valued_path_expression(JpqlParser.Collection_valued_path_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#update_clause}.
	 * @param ctx the parse tree
	 */
	void enterUpdate_clause(JpqlParser.Update_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#update_clause}.
	 * @param ctx the parse tree
	 */
	void exitUpdate_clause(JpqlParser.Update_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#update_item}.
	 * @param ctx the parse tree
	 */
	void enterUpdate_item(JpqlParser.Update_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#update_item}.
	 * @param ctx the parse tree
	 */
	void exitUpdate_item(JpqlParser.Update_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#new_value}.
	 * @param ctx the parse tree
	 */
	void enterNew_value(JpqlParser.New_valueContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#new_value}.
	 * @param ctx the parse tree
	 */
	void exitNew_value(JpqlParser.New_valueContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#delete_clause}.
	 * @param ctx the parse tree
	 */
	void enterDelete_clause(JpqlParser.Delete_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#delete_clause}.
	 * @param ctx the parse tree
	 */
	void exitDelete_clause(JpqlParser.Delete_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#select_clause}.
	 * @param ctx the parse tree
	 */
	void enterSelect_clause(JpqlParser.Select_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#select_clause}.
	 * @param ctx the parse tree
	 */
	void exitSelect_clause(JpqlParser.Select_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#select_item}.
	 * @param ctx the parse tree
	 */
	void enterSelect_item(JpqlParser.Select_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#select_item}.
	 * @param ctx the parse tree
	 */
	void exitSelect_item(JpqlParser.Select_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#select_expression}.
	 * @param ctx the parse tree
	 */
	void enterSelect_expression(JpqlParser.Select_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#select_expression}.
	 * @param ctx the parse tree
	 */
	void exitSelect_expression(JpqlParser.Select_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#constructor_expression}.
	 * @param ctx the parse tree
	 */
	void enterConstructor_expression(JpqlParser.Constructor_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#constructor_expression}.
	 * @param ctx the parse tree
	 */
	void exitConstructor_expression(JpqlParser.Constructor_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#constructor_item}.
	 * @param ctx the parse tree
	 */
	void enterConstructor_item(JpqlParser.Constructor_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#constructor_item}.
	 * @param ctx the parse tree
	 */
	void exitConstructor_item(JpqlParser.Constructor_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#aggregate_expression}.
	 * @param ctx the parse tree
	 */
	void enterAggregate_expression(JpqlParser.Aggregate_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#aggregate_expression}.
	 * @param ctx the parse tree
	 */
	void exitAggregate_expression(JpqlParser.Aggregate_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#where_clause}.
	 * @param ctx the parse tree
	 */
	void enterWhere_clause(JpqlParser.Where_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#where_clause}.
	 * @param ctx the parse tree
	 */
	void exitWhere_clause(JpqlParser.Where_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#groupby_clause}.
	 * @param ctx the parse tree
	 */
	void enterGroupby_clause(JpqlParser.Groupby_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#groupby_clause}.
	 * @param ctx the parse tree
	 */
	void exitGroupby_clause(JpqlParser.Groupby_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#groupby_item}.
	 * @param ctx the parse tree
	 */
	void enterGroupby_item(JpqlParser.Groupby_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#groupby_item}.
	 * @param ctx the parse tree
	 */
	void exitGroupby_item(JpqlParser.Groupby_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#having_clause}.
	 * @param ctx the parse tree
	 */
	void enterHaving_clause(JpqlParser.Having_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#having_clause}.
	 * @param ctx the parse tree
	 */
	void exitHaving_clause(JpqlParser.Having_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#orderby_clause}.
	 * @param ctx the parse tree
	 */
	void enterOrderby_clause(JpqlParser.Orderby_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#orderby_clause}.
	 * @param ctx the parse tree
	 */
	void exitOrderby_clause(JpqlParser.Orderby_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#orderby_item}.
	 * @param ctx the parse tree
	 */
	void enterOrderby_item(JpqlParser.Orderby_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#orderby_item}.
	 * @param ctx the parse tree
	 */
	void exitOrderby_item(JpqlParser.Orderby_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#subquery}.
	 * @param ctx the parse tree
	 */
	void enterSubquery(JpqlParser.SubqueryContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#subquery}.
	 * @param ctx the parse tree
	 */
	void exitSubquery(JpqlParser.SubqueryContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#subquery_from_clause}.
	 * @param ctx the parse tree
	 */
	void enterSubquery_from_clause(JpqlParser.Subquery_from_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#subquery_from_clause}.
	 * @param ctx the parse tree
	 */
	void exitSubquery_from_clause(JpqlParser.Subquery_from_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#subselect_identification_variable_declaration}.
	 * @param ctx the parse tree
	 */
	void enterSubselect_identification_variable_declaration(JpqlParser.Subselect_identification_variable_declarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#subselect_identification_variable_declaration}.
	 * @param ctx the parse tree
	 */
	void exitSubselect_identification_variable_declaration(JpqlParser.Subselect_identification_variable_declarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#derived_path_expression}.
	 * @param ctx the parse tree
	 */
	void enterDerived_path_expression(JpqlParser.Derived_path_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#derived_path_expression}.
	 * @param ctx the parse tree
	 */
	void exitDerived_path_expression(JpqlParser.Derived_path_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#general_derived_path}.
	 * @param ctx the parse tree
	 */
	void enterGeneral_derived_path(JpqlParser.General_derived_pathContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#general_derived_path}.
	 * @param ctx the parse tree
	 */
	void exitGeneral_derived_path(JpqlParser.General_derived_pathContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#simple_derived_path}.
	 * @param ctx the parse tree
	 */
	void enterSimple_derived_path(JpqlParser.Simple_derived_pathContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#simple_derived_path}.
	 * @param ctx the parse tree
	 */
	void exitSimple_derived_path(JpqlParser.Simple_derived_pathContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#treated_derived_path}.
	 * @param ctx the parse tree
	 */
	void enterTreated_derived_path(JpqlParser.Treated_derived_pathContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#treated_derived_path}.
	 * @param ctx the parse tree
	 */
	void exitTreated_derived_path(JpqlParser.Treated_derived_pathContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#derived_collection_member_declaration}.
	 * @param ctx the parse tree
	 */
	void enterDerived_collection_member_declaration(JpqlParser.Derived_collection_member_declarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#derived_collection_member_declaration}.
	 * @param ctx the parse tree
	 */
	void exitDerived_collection_member_declaration(JpqlParser.Derived_collection_member_declarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#simple_select_clause}.
	 * @param ctx the parse tree
	 */
	void enterSimple_select_clause(JpqlParser.Simple_select_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#simple_select_clause}.
	 * @param ctx the parse tree
	 */
	void exitSimple_select_clause(JpqlParser.Simple_select_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#simple_select_expression}.
	 * @param ctx the parse tree
	 */
	void enterSimple_select_expression(JpqlParser.Simple_select_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#simple_select_expression}.
	 * @param ctx the parse tree
	 */
	void exitSimple_select_expression(JpqlParser.Simple_select_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#scalar_expression}.
	 * @param ctx the parse tree
	 */
	void enterScalar_expression(JpqlParser.Scalar_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#scalar_expression}.
	 * @param ctx the parse tree
	 */
	void exitScalar_expression(JpqlParser.Scalar_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#conditional_expression}.
	 * @param ctx the parse tree
	 */
	void enterConditional_expression(JpqlParser.Conditional_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#conditional_expression}.
	 * @param ctx the parse tree
	 */
	void exitConditional_expression(JpqlParser.Conditional_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#conditional_term}.
	 * @param ctx the parse tree
	 */
	void enterConditional_term(JpqlParser.Conditional_termContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#conditional_term}.
	 * @param ctx the parse tree
	 */
	void exitConditional_term(JpqlParser.Conditional_termContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#conditional_factor}.
	 * @param ctx the parse tree
	 */
	void enterConditional_factor(JpqlParser.Conditional_factorContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#conditional_factor}.
	 * @param ctx the parse tree
	 */
	void exitConditional_factor(JpqlParser.Conditional_factorContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#conditional_primary}.
	 * @param ctx the parse tree
	 */
	void enterConditional_primary(JpqlParser.Conditional_primaryContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#conditional_primary}.
	 * @param ctx the parse tree
	 */
	void exitConditional_primary(JpqlParser.Conditional_primaryContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#simple_cond_expression}.
	 * @param ctx the parse tree
	 */
	void enterSimple_cond_expression(JpqlParser.Simple_cond_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#simple_cond_expression}.
	 * @param ctx the parse tree
	 */
	void exitSimple_cond_expression(JpqlParser.Simple_cond_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#between_expression}.
	 * @param ctx the parse tree
	 */
	void enterBetween_expression(JpqlParser.Between_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#between_expression}.
	 * @param ctx the parse tree
	 */
	void exitBetween_expression(JpqlParser.Between_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#in_expression}.
	 * @param ctx the parse tree
	 */
	void enterIn_expression(JpqlParser.In_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#in_expression}.
	 * @param ctx the parse tree
	 */
	void exitIn_expression(JpqlParser.In_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#in_item}.
	 * @param ctx the parse tree
	 */
	void enterIn_item(JpqlParser.In_itemContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#in_item}.
	 * @param ctx the parse tree
	 */
	void exitIn_item(JpqlParser.In_itemContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#like_expression}.
	 * @param ctx the parse tree
	 */
	void enterLike_expression(JpqlParser.Like_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#like_expression}.
	 * @param ctx the parse tree
	 */
	void exitLike_expression(JpqlParser.Like_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#null_comparison_expression}.
	 * @param ctx the parse tree
	 */
	void enterNull_comparison_expression(JpqlParser.Null_comparison_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#null_comparison_expression}.
	 * @param ctx the parse tree
	 */
	void exitNull_comparison_expression(JpqlParser.Null_comparison_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#empty_collection_comparison_expression}.
	 * @param ctx the parse tree
	 */
	void enterEmpty_collection_comparison_expression(JpqlParser.Empty_collection_comparison_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#empty_collection_comparison_expression}.
	 * @param ctx the parse tree
	 */
	void exitEmpty_collection_comparison_expression(JpqlParser.Empty_collection_comparison_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#collection_member_expression}.
	 * @param ctx the parse tree
	 */
	void enterCollection_member_expression(JpqlParser.Collection_member_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#collection_member_expression}.
	 * @param ctx the parse tree
	 */
	void exitCollection_member_expression(JpqlParser.Collection_member_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#entity_or_value_expression}.
	 * @param ctx the parse tree
	 */
	void enterEntity_or_value_expression(JpqlParser.Entity_or_value_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#entity_or_value_expression}.
	 * @param ctx the parse tree
	 */
	void exitEntity_or_value_expression(JpqlParser.Entity_or_value_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#simple_entity_or_value_expression}.
	 * @param ctx the parse tree
	 */
	void enterSimple_entity_or_value_expression(JpqlParser.Simple_entity_or_value_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#simple_entity_or_value_expression}.
	 * @param ctx the parse tree
	 */
	void exitSimple_entity_or_value_expression(JpqlParser.Simple_entity_or_value_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#exists_expression}.
	 * @param ctx the parse tree
	 */
	void enterExists_expression(JpqlParser.Exists_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#exists_expression}.
	 * @param ctx the parse tree
	 */
	void exitExists_expression(JpqlParser.Exists_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#all_or_any_expression}.
	 * @param ctx the parse tree
	 */
	void enterAll_or_any_expression(JpqlParser.All_or_any_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#all_or_any_expression}.
	 * @param ctx the parse tree
	 */
	void exitAll_or_any_expression(JpqlParser.All_or_any_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#comparison_expression}.
	 * @param ctx the parse tree
	 */
	void enterComparison_expression(JpqlParser.Comparison_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#comparison_expression}.
	 * @param ctx the parse tree
	 */
	void exitComparison_expression(JpqlParser.Comparison_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#comparison_operator}.
	 * @param ctx the parse tree
	 */
	void enterComparison_operator(JpqlParser.Comparison_operatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#comparison_operator}.
	 * @param ctx the parse tree
	 */
	void exitComparison_operator(JpqlParser.Comparison_operatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#arithmetic_expression}.
	 * @param ctx the parse tree
	 */
	void enterArithmetic_expression(JpqlParser.Arithmetic_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#arithmetic_expression}.
	 * @param ctx the parse tree
	 */
	void exitArithmetic_expression(JpqlParser.Arithmetic_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#arithmetic_term}.
	 * @param ctx the parse tree
	 */
	void enterArithmetic_term(JpqlParser.Arithmetic_termContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#arithmetic_term}.
	 * @param ctx the parse tree
	 */
	void exitArithmetic_term(JpqlParser.Arithmetic_termContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#arithmetic_factor}.
	 * @param ctx the parse tree
	 */
	void enterArithmetic_factor(JpqlParser.Arithmetic_factorContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#arithmetic_factor}.
	 * @param ctx the parse tree
	 */
	void exitArithmetic_factor(JpqlParser.Arithmetic_factorContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#arithmetic_primary}.
	 * @param ctx the parse tree
	 */
	void enterArithmetic_primary(JpqlParser.Arithmetic_primaryContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#arithmetic_primary}.
	 * @param ctx the parse tree
	 */
	void exitArithmetic_primary(JpqlParser.Arithmetic_primaryContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#string_expression}.
	 * @param ctx the parse tree
	 */
	void enterString_expression(JpqlParser.String_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#string_expression}.
	 * @param ctx the parse tree
	 */
	void exitString_expression(JpqlParser.String_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#datetime_expression}.
	 * @param ctx the parse tree
	 */
	void enterDatetime_expression(JpqlParser.Datetime_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#datetime_expression}.
	 * @param ctx the parse tree
	 */
	void exitDatetime_expression(JpqlParser.Datetime_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#boolean_expression}.
	 * @param ctx the parse tree
	 */
	void enterBoolean_expression(JpqlParser.Boolean_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#boolean_expression}.
	 * @param ctx the parse tree
	 */
	void exitBoolean_expression(JpqlParser.Boolean_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#enum_expression}.
	 * @param ctx the parse tree
	 */
	void enterEnum_expression(JpqlParser.Enum_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#enum_expression}.
	 * @param ctx the parse tree
	 */
	void exitEnum_expression(JpqlParser.Enum_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#entity_expression}.
	 * @param ctx the parse tree
	 */
	void enterEntity_expression(JpqlParser.Entity_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#entity_expression}.
	 * @param ctx the parse tree
	 */
	void exitEntity_expression(JpqlParser.Entity_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#simple_entity_expression}.
	 * @param ctx the parse tree
	 */
	void enterSimple_entity_expression(JpqlParser.Simple_entity_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#simple_entity_expression}.
	 * @param ctx the parse tree
	 */
	void exitSimple_entity_expression(JpqlParser.Simple_entity_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#entity_type_expression}.
	 * @param ctx the parse tree
	 */
	void enterEntity_type_expression(JpqlParser.Entity_type_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#entity_type_expression}.
	 * @param ctx the parse tree
	 */
	void exitEntity_type_expression(JpqlParser.Entity_type_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#type_discriminator}.
	 * @param ctx the parse tree
	 */
	void enterType_discriminator(JpqlParser.Type_discriminatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#type_discriminator}.
	 * @param ctx the parse tree
	 */
	void exitType_discriminator(JpqlParser.Type_discriminatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#functions_returning_numerics}.
	 * @param ctx the parse tree
	 */
	void enterFunctions_returning_numerics(JpqlParser.Functions_returning_numericsContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#functions_returning_numerics}.
	 * @param ctx the parse tree
	 */
	void exitFunctions_returning_numerics(JpqlParser.Functions_returning_numericsContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#functions_returning_datetime}.
	 * @param ctx the parse tree
	 */
	void enterFunctions_returning_datetime(JpqlParser.Functions_returning_datetimeContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#functions_returning_datetime}.
	 * @param ctx the parse tree
	 */
	void exitFunctions_returning_datetime(JpqlParser.Functions_returning_datetimeContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#functions_returning_strings}.
	 * @param ctx the parse tree
	 */
	void enterFunctions_returning_strings(JpqlParser.Functions_returning_stringsContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#functions_returning_strings}.
	 * @param ctx the parse tree
	 */
	void exitFunctions_returning_strings(JpqlParser.Functions_returning_stringsContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#trim_specification}.
	 * @param ctx the parse tree
	 */
	void enterTrim_specification(JpqlParser.Trim_specificationContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#trim_specification}.
	 * @param ctx the parse tree
	 */
	void exitTrim_specification(JpqlParser.Trim_specificationContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#function_invocation}.
	 * @param ctx the parse tree
	 */
	void enterFunction_invocation(JpqlParser.Function_invocationContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#function_invocation}.
	 * @param ctx the parse tree
	 */
	void exitFunction_invocation(JpqlParser.Function_invocationContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#extract_datetime_field}.
	 * @param ctx the parse tree
	 */
	void enterExtract_datetime_field(JpqlParser.Extract_datetime_fieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#extract_datetime_field}.
	 * @param ctx the parse tree
	 */
	void exitExtract_datetime_field(JpqlParser.Extract_datetime_fieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#datetime_field}.
	 * @param ctx the parse tree
	 */
	void enterDatetime_field(JpqlParser.Datetime_fieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#datetime_field}.
	 * @param ctx the parse tree
	 */
	void exitDatetime_field(JpqlParser.Datetime_fieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#extract_datetime_part}.
	 * @param ctx the parse tree
	 */
	void enterExtract_datetime_part(JpqlParser.Extract_datetime_partContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#extract_datetime_part}.
	 * @param ctx the parse tree
	 */
	void exitExtract_datetime_part(JpqlParser.Extract_datetime_partContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#datetime_part}.
	 * @param ctx the parse tree
	 */
	void enterDatetime_part(JpqlParser.Datetime_partContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#datetime_part}.
	 * @param ctx the parse tree
	 */
	void exitDatetime_part(JpqlParser.Datetime_partContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#function_arg}.
	 * @param ctx the parse tree
	 */
	void enterFunction_arg(JpqlParser.Function_argContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#function_arg}.
	 * @param ctx the parse tree
	 */
	void exitFunction_arg(JpqlParser.Function_argContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#case_expression}.
	 * @param ctx the parse tree
	 */
	void enterCase_expression(JpqlParser.Case_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#case_expression}.
	 * @param ctx the parse tree
	 */
	void exitCase_expression(JpqlParser.Case_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#general_case_expression}.
	 * @param ctx the parse tree
	 */
	void enterGeneral_case_expression(JpqlParser.General_case_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#general_case_expression}.
	 * @param ctx the parse tree
	 */
	void exitGeneral_case_expression(JpqlParser.General_case_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#when_clause}.
	 * @param ctx the parse tree
	 */
	void enterWhen_clause(JpqlParser.When_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#when_clause}.
	 * @param ctx the parse tree
	 */
	void exitWhen_clause(JpqlParser.When_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#simple_case_expression}.
	 * @param ctx the parse tree
	 */
	void enterSimple_case_expression(JpqlParser.Simple_case_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#simple_case_expression}.
	 * @param ctx the parse tree
	 */
	void exitSimple_case_expression(JpqlParser.Simple_case_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#case_operand}.
	 * @param ctx the parse tree
	 */
	void enterCase_operand(JpqlParser.Case_operandContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#case_operand}.
	 * @param ctx the parse tree
	 */
	void exitCase_operand(JpqlParser.Case_operandContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#simple_when_clause}.
	 * @param ctx the parse tree
	 */
	void enterSimple_when_clause(JpqlParser.Simple_when_clauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#simple_when_clause}.
	 * @param ctx the parse tree
	 */
	void exitSimple_when_clause(JpqlParser.Simple_when_clauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#coalesce_expression}.
	 * @param ctx the parse tree
	 */
	void enterCoalesce_expression(JpqlParser.Coalesce_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#coalesce_expression}.
	 * @param ctx the parse tree
	 */
	void exitCoalesce_expression(JpqlParser.Coalesce_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#nullif_expression}.
	 * @param ctx the parse tree
	 */
	void enterNullif_expression(JpqlParser.Nullif_expressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#nullif_expression}.
	 * @param ctx the parse tree
	 */
	void exitNullif_expression(JpqlParser.Nullif_expressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#trim_character}.
	 * @param ctx the parse tree
	 */
	void enterTrim_character(JpqlParser.Trim_characterContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#trim_character}.
	 * @param ctx the parse tree
	 */
	void exitTrim_character(JpqlParser.Trim_characterContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#identification_variable}.
	 * @param ctx the parse tree
	 */
	void enterIdentification_variable(JpqlParser.Identification_variableContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#identification_variable}.
	 * @param ctx the parse tree
	 */
	void exitIdentification_variable(JpqlParser.Identification_variableContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#constructor_name}.
	 * @param ctx the parse tree
	 */
	void enterConstructor_name(JpqlParser.Constructor_nameContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#constructor_name}.
	 * @param ctx the parse tree
	 */
	void exitConstructor_name(JpqlParser.Constructor_nameContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#literal}.
	 * @param ctx the parse tree
	 */
	void enterLiteral(JpqlParser.LiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#literal}.
	 * @param ctx the parse tree
	 */
	void exitLiteral(JpqlParser.LiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#input_parameter}.
	 * @param ctx the parse tree
	 */
	void enterInput_parameter(JpqlParser.Input_parameterContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#input_parameter}.
	 * @param ctx the parse tree
	 */
	void exitInput_parameter(JpqlParser.Input_parameterContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#pattern_value}.
	 * @param ctx the parse tree
	 */
	void enterPattern_value(JpqlParser.Pattern_valueContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#pattern_value}.
	 * @param ctx the parse tree
	 */
	void exitPattern_value(JpqlParser.Pattern_valueContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#date_time_timestamp_literal}.
	 * @param ctx the parse tree
	 */
	void enterDate_time_timestamp_literal(JpqlParser.Date_time_timestamp_literalContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#date_time_timestamp_literal}.
	 * @param ctx the parse tree
	 */
	void exitDate_time_timestamp_literal(JpqlParser.Date_time_timestamp_literalContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#entity_type_literal}.
	 * @param ctx the parse tree
	 */
	void enterEntity_type_literal(JpqlParser.Entity_type_literalContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#entity_type_literal}.
	 * @param ctx the parse tree
	 */
	void exitEntity_type_literal(JpqlParser.Entity_type_literalContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#escape_character}.
	 * @param ctx the parse tree
	 */
	void enterEscape_character(JpqlParser.Escape_characterContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#escape_character}.
	 * @param ctx the parse tree
	 */
	void exitEscape_character(JpqlParser.Escape_characterContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#numeric_literal}.
	 * @param ctx the parse tree
	 */
	void enterNumeric_literal(JpqlParser.Numeric_literalContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#numeric_literal}.
	 * @param ctx the parse tree
	 */
	void exitNumeric_literal(JpqlParser.Numeric_literalContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#boolean_literal}.
	 * @param ctx the parse tree
	 */
	void enterBoolean_literal(JpqlParser.Boolean_literalContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#boolean_literal}.
	 * @param ctx the parse tree
	 */
	void exitBoolean_literal(JpqlParser.Boolean_literalContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#enum_literal}.
	 * @param ctx the parse tree
	 */
	void enterEnum_literal(JpqlParser.Enum_literalContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#enum_literal}.
	 * @param ctx the parse tree
	 */
	void exitEnum_literal(JpqlParser.Enum_literalContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#string_literal}.
	 * @param ctx the parse tree
	 */
	void enterString_literal(JpqlParser.String_literalContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#string_literal}.
	 * @param ctx the parse tree
	 */
	void exitString_literal(JpqlParser.String_literalContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#single_valued_embeddable_object_field}.
	 * @param ctx the parse tree
	 */
	void enterSingle_valued_embeddable_object_field(JpqlParser.Single_valued_embeddable_object_fieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#single_valued_embeddable_object_field}.
	 * @param ctx the parse tree
	 */
	void exitSingle_valued_embeddable_object_field(JpqlParser.Single_valued_embeddable_object_fieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#subtype}.
	 * @param ctx the parse tree
	 */
	void enterSubtype(JpqlParser.SubtypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#subtype}.
	 * @param ctx the parse tree
	 */
	void exitSubtype(JpqlParser.SubtypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#collection_valued_field}.
	 * @param ctx the parse tree
	 */
	void enterCollection_valued_field(JpqlParser.Collection_valued_fieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#collection_valued_field}.
	 * @param ctx the parse tree
	 */
	void exitCollection_valued_field(JpqlParser.Collection_valued_fieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#single_valued_object_field}.
	 * @param ctx the parse tree
	 */
	void enterSingle_valued_object_field(JpqlParser.Single_valued_object_fieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#single_valued_object_field}.
	 * @param ctx the parse tree
	 */
	void exitSingle_valued_object_field(JpqlParser.Single_valued_object_fieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#state_field}.
	 * @param ctx the parse tree
	 */
	void enterState_field(JpqlParser.State_fieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#state_field}.
	 * @param ctx the parse tree
	 */
	void exitState_field(JpqlParser.State_fieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#collection_value_field}.
	 * @param ctx the parse tree
	 */
	void enterCollection_value_field(JpqlParser.Collection_value_fieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#collection_value_field}.
	 * @param ctx the parse tree
	 */
	void exitCollection_value_field(JpqlParser.Collection_value_fieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#entity_name}.
	 * @param ctx the parse tree
	 */
	void enterEntity_name(JpqlParser.Entity_nameContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#entity_name}.
	 * @param ctx the parse tree
	 */
	void exitEntity_name(JpqlParser.Entity_nameContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#result_variable}.
	 * @param ctx the parse tree
	 */
	void enterResult_variable(JpqlParser.Result_variableContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#result_variable}.
	 * @param ctx the parse tree
	 */
	void exitResult_variable(JpqlParser.Result_variableContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#superquery_identification_variable}.
	 * @param ctx the parse tree
	 */
	void enterSuperquery_identification_variable(JpqlParser.Superquery_identification_variableContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#superquery_identification_variable}.
	 * @param ctx the parse tree
	 */
	void exitSuperquery_identification_variable(JpqlParser.Superquery_identification_variableContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#collection_valued_input_parameter}.
	 * @param ctx the parse tree
	 */
	void enterCollection_valued_input_parameter(JpqlParser.Collection_valued_input_parameterContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#collection_valued_input_parameter}.
	 * @param ctx the parse tree
	 */
	void exitCollection_valued_input_parameter(JpqlParser.Collection_valued_input_parameterContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#single_valued_input_parameter}.
	 * @param ctx the parse tree
	 */
	void enterSingle_valued_input_parameter(JpqlParser.Single_valued_input_parameterContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#single_valued_input_parameter}.
	 * @param ctx the parse tree
	 */
	void exitSingle_valued_input_parameter(JpqlParser.Single_valued_input_parameterContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#function_name}.
	 * @param ctx the parse tree
	 */
	void enterFunction_name(JpqlParser.Function_nameContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#function_name}.
	 * @param ctx the parse tree
	 */
	void exitFunction_name(JpqlParser.Function_nameContext ctx);
	/**
	 * Enter a parse tree produced by {@link JpqlParser#character_valued_input_parameter}.
	 * @param ctx the parse tree
	 */
	void enterCharacter_valued_input_parameter(JpqlParser.Character_valued_input_parameterContext ctx);
	/**
	 * Exit a parse tree produced by {@link JpqlParser#character_valued_input_parameter}.
	 * @param ctx the parse tree
	 */
	void exitCharacter_valued_input_parameter(JpqlParser.Character_valued_input_parameterContext ctx);
}