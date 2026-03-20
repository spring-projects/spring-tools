// Generated from PropertyPlaceHolder.g4 by ANTLR 4.13.2
package org.springframework.ide.vscode.parser.placeholder;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link PropertyPlaceHolderParser}.
 */
public interface PropertyPlaceHolderListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link PropertyPlaceHolderParser#start}.
	 * @param ctx the parse tree
	 */
	void enterStart(PropertyPlaceHolderParser.StartContext ctx);
	/**
	 * Exit a parse tree produced by {@link PropertyPlaceHolderParser#start}.
	 * @param ctx the parse tree
	 */
	void exitStart(PropertyPlaceHolderParser.StartContext ctx);
	/**
	 * Enter a parse tree produced by {@link PropertyPlaceHolderParser#line}.
	 * @param ctx the parse tree
	 */
	void enterLine(PropertyPlaceHolderParser.LineContext ctx);
	/**
	 * Exit a parse tree produced by {@link PropertyPlaceHolderParser#line}.
	 * @param ctx the parse tree
	 */
	void exitLine(PropertyPlaceHolderParser.LineContext ctx);
	/**
	 * Enter a parse tree produced by {@link PropertyPlaceHolderParser#keyDefaultValuePair}.
	 * @param ctx the parse tree
	 */
	void enterKeyDefaultValuePair(PropertyPlaceHolderParser.KeyDefaultValuePairContext ctx);
	/**
	 * Exit a parse tree produced by {@link PropertyPlaceHolderParser#keyDefaultValuePair}.
	 * @param ctx the parse tree
	 */
	void exitKeyDefaultValuePair(PropertyPlaceHolderParser.KeyDefaultValuePairContext ctx);
	/**
	 * Enter a parse tree produced by {@link PropertyPlaceHolderParser#defaultValue}.
	 * @param ctx the parse tree
	 */
	void enterDefaultValue(PropertyPlaceHolderParser.DefaultValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link PropertyPlaceHolderParser#defaultValue}.
	 * @param ctx the parse tree
	 */
	void exitDefaultValue(PropertyPlaceHolderParser.DefaultValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link PropertyPlaceHolderParser#key}.
	 * @param ctx the parse tree
	 */
	void enterKey(PropertyPlaceHolderParser.KeyContext ctx);
	/**
	 * Exit a parse tree produced by {@link PropertyPlaceHolderParser#key}.
	 * @param ctx the parse tree
	 */
	void exitKey(PropertyPlaceHolderParser.KeyContext ctx);
	/**
	 * Enter a parse tree produced by {@link PropertyPlaceHolderParser#identifier}.
	 * @param ctx the parse tree
	 */
	void enterIdentifier(PropertyPlaceHolderParser.IdentifierContext ctx);
	/**
	 * Exit a parse tree produced by {@link PropertyPlaceHolderParser#identifier}.
	 * @param ctx the parse tree
	 */
	void exitIdentifier(PropertyPlaceHolderParser.IdentifierContext ctx);
	/**
	 * Enter a parse tree produced by {@link PropertyPlaceHolderParser#value}.
	 * @param ctx the parse tree
	 */
	void enterValue(PropertyPlaceHolderParser.ValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link PropertyPlaceHolderParser#value}.
	 * @param ctx the parse tree
	 */
	void exitValue(PropertyPlaceHolderParser.ValueContext ctx);
}