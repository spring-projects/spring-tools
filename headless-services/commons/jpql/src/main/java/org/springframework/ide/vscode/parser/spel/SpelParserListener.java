// Generated from SpelParser.g4 by ANTLR 4.13.2
package org.springframework.ide.vscode.parser.spel;

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

import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link SpelParser}.
 */
public interface SpelParserListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link SpelParser#script}.
	 * @param ctx the parse tree
	 */
	void enterScript(SpelParser.ScriptContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#script}.
	 * @param ctx the parse tree
	 */
	void exitScript(SpelParser.ScriptContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#spelExpr}.
	 * @param ctx the parse tree
	 */
	void enterSpelExpr(SpelParser.SpelExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#spelExpr}.
	 * @param ctx the parse tree
	 */
	void exitSpelExpr(SpelParser.SpelExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#node}.
	 * @param ctx the parse tree
	 */
	void enterNode(SpelParser.NodeContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#node}.
	 * @param ctx the parse tree
	 */
	void exitNode(SpelParser.NodeContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#nonDottedNode}.
	 * @param ctx the parse tree
	 */
	void enterNonDottedNode(SpelParser.NonDottedNodeContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#nonDottedNode}.
	 * @param ctx the parse tree
	 */
	void exitNonDottedNode(SpelParser.NonDottedNodeContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#dottedNode}.
	 * @param ctx the parse tree
	 */
	void enterDottedNode(SpelParser.DottedNodeContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#dottedNode}.
	 * @param ctx the parse tree
	 */
	void exitDottedNode(SpelParser.DottedNodeContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#functionOrVar}.
	 * @param ctx the parse tree
	 */
	void enterFunctionOrVar(SpelParser.FunctionOrVarContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#functionOrVar}.
	 * @param ctx the parse tree
	 */
	void exitFunctionOrVar(SpelParser.FunctionOrVarContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#methodArgs}.
	 * @param ctx the parse tree
	 */
	void enterMethodArgs(SpelParser.MethodArgsContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#methodArgs}.
	 * @param ctx the parse tree
	 */
	void exitMethodArgs(SpelParser.MethodArgsContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#args}.
	 * @param ctx the parse tree
	 */
	void enterArgs(SpelParser.ArgsContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#args}.
	 * @param ctx the parse tree
	 */
	void exitArgs(SpelParser.ArgsContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#methodOrProperty}.
	 * @param ctx the parse tree
	 */
	void enterMethodOrProperty(SpelParser.MethodOrPropertyContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#methodOrProperty}.
	 * @param ctx the parse tree
	 */
	void exitMethodOrProperty(SpelParser.MethodOrPropertyContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#projection}.
	 * @param ctx the parse tree
	 */
	void enterProjection(SpelParser.ProjectionContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#projection}.
	 * @param ctx the parse tree
	 */
	void exitProjection(SpelParser.ProjectionContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#selection}.
	 * @param ctx the parse tree
	 */
	void enterSelection(SpelParser.SelectionContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#selection}.
	 * @param ctx the parse tree
	 */
	void exitSelection(SpelParser.SelectionContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#startNode}.
	 * @param ctx the parse tree
	 */
	void enterStartNode(SpelParser.StartNodeContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#startNode}.
	 * @param ctx the parse tree
	 */
	void exitStartNode(SpelParser.StartNodeContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#literal}.
	 * @param ctx the parse tree
	 */
	void enterLiteral(SpelParser.LiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#literal}.
	 * @param ctx the parse tree
	 */
	void exitLiteral(SpelParser.LiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#numericLiteral}.
	 * @param ctx the parse tree
	 */
	void enterNumericLiteral(SpelParser.NumericLiteralContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#numericLiteral}.
	 * @param ctx the parse tree
	 */
	void exitNumericLiteral(SpelParser.NumericLiteralContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#parenspelExpr}.
	 * @param ctx the parse tree
	 */
	void enterParenspelExpr(SpelParser.ParenspelExprContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#parenspelExpr}.
	 * @param ctx the parse tree
	 */
	void exitParenspelExpr(SpelParser.ParenspelExprContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#typeReference}.
	 * @param ctx the parse tree
	 */
	void enterTypeReference(SpelParser.TypeReferenceContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#typeReference}.
	 * @param ctx the parse tree
	 */
	void exitTypeReference(SpelParser.TypeReferenceContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#possiblyQualifiedId}.
	 * @param ctx the parse tree
	 */
	void enterPossiblyQualifiedId(SpelParser.PossiblyQualifiedIdContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#possiblyQualifiedId}.
	 * @param ctx the parse tree
	 */
	void exitPossiblyQualifiedId(SpelParser.PossiblyQualifiedIdContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#nullReference}.
	 * @param ctx the parse tree
	 */
	void enterNullReference(SpelParser.NullReferenceContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#nullReference}.
	 * @param ctx the parse tree
	 */
	void exitNullReference(SpelParser.NullReferenceContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#constructorReference}.
	 * @param ctx the parse tree
	 */
	void enterConstructorReference(SpelParser.ConstructorReferenceContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#constructorReference}.
	 * @param ctx the parse tree
	 */
	void exitConstructorReference(SpelParser.ConstructorReferenceContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#constructorArgs}.
	 * @param ctx the parse tree
	 */
	void enterConstructorArgs(SpelParser.ConstructorArgsContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#constructorArgs}.
	 * @param ctx the parse tree
	 */
	void exitConstructorArgs(SpelParser.ConstructorArgsContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#inlineListOrMap}.
	 * @param ctx the parse tree
	 */
	void enterInlineListOrMap(SpelParser.InlineListOrMapContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#inlineListOrMap}.
	 * @param ctx the parse tree
	 */
	void exitInlineListOrMap(SpelParser.InlineListOrMapContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#listBindings}.
	 * @param ctx the parse tree
	 */
	void enterListBindings(SpelParser.ListBindingsContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#listBindings}.
	 * @param ctx the parse tree
	 */
	void exitListBindings(SpelParser.ListBindingsContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#listBinding}.
	 * @param ctx the parse tree
	 */
	void enterListBinding(SpelParser.ListBindingContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#listBinding}.
	 * @param ctx the parse tree
	 */
	void exitListBinding(SpelParser.ListBindingContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#mapBindings}.
	 * @param ctx the parse tree
	 */
	void enterMapBindings(SpelParser.MapBindingsContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#mapBindings}.
	 * @param ctx the parse tree
	 */
	void exitMapBindings(SpelParser.MapBindingsContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#mapBinding}.
	 * @param ctx the parse tree
	 */
	void enterMapBinding(SpelParser.MapBindingContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#mapBinding}.
	 * @param ctx the parse tree
	 */
	void exitMapBinding(SpelParser.MapBindingContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#beanReference}.
	 * @param ctx the parse tree
	 */
	void enterBeanReference(SpelParser.BeanReferenceContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#beanReference}.
	 * @param ctx the parse tree
	 */
	void exitBeanReference(SpelParser.BeanReferenceContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#inputParameter}.
	 * @param ctx the parse tree
	 */
	void enterInputParameter(SpelParser.InputParameterContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#inputParameter}.
	 * @param ctx the parse tree
	 */
	void exitInputParameter(SpelParser.InputParameterContext ctx);
	/**
	 * Enter a parse tree produced by {@link SpelParser#propertyPlaceHolder}.
	 * @param ctx the parse tree
	 */
	void enterPropertyPlaceHolder(SpelParser.PropertyPlaceHolderContext ctx);
	/**
	 * Exit a parse tree produced by {@link SpelParser#propertyPlaceHolder}.
	 * @param ctx the parse tree
	 */
	void exitPropertyPlaceHolder(SpelParser.PropertyPlaceHolderContext ctx);
}