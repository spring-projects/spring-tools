// Generated from JavaProperties.g4 by ANTLR 4.13.2
package org.springframework.ide.vscode.java.properties.antlr.parser;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link JavaPropertiesParser}.
 */
public interface JavaPropertiesListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link JavaPropertiesParser#parse}.
	 * @param ctx the parse tree
	 */
	void enterParse(JavaPropertiesParser.ParseContext ctx);
	/**
	 * Exit a parse tree produced by {@link JavaPropertiesParser#parse}.
	 * @param ctx the parse tree
	 */
	void exitParse(JavaPropertiesParser.ParseContext ctx);
	/**
	 * Enter a parse tree produced by {@link JavaPropertiesParser#line}.
	 * @param ctx the parse tree
	 */
	void enterLine(JavaPropertiesParser.LineContext ctx);
	/**
	 * Exit a parse tree produced by {@link JavaPropertiesParser#line}.
	 * @param ctx the parse tree
	 */
	void exitLine(JavaPropertiesParser.LineContext ctx);
	/**
	 * Enter a parse tree produced by {@link JavaPropertiesParser#propertyLine}.
	 * @param ctx the parse tree
	 */
	void enterPropertyLine(JavaPropertiesParser.PropertyLineContext ctx);
	/**
	 * Exit a parse tree produced by {@link JavaPropertiesParser#propertyLine}.
	 * @param ctx the parse tree
	 */
	void exitPropertyLine(JavaPropertiesParser.PropertyLineContext ctx);
	/**
	 * Enter a parse tree produced by {@link JavaPropertiesParser#commentLine}.
	 * @param ctx the parse tree
	 */
	void enterCommentLine(JavaPropertiesParser.CommentLineContext ctx);
	/**
	 * Exit a parse tree produced by {@link JavaPropertiesParser#commentLine}.
	 * @param ctx the parse tree
	 */
	void exitCommentLine(JavaPropertiesParser.CommentLineContext ctx);
	/**
	 * Enter a parse tree produced by {@link JavaPropertiesParser#emptyLine}.
	 * @param ctx the parse tree
	 */
	void enterEmptyLine(JavaPropertiesParser.EmptyLineContext ctx);
	/**
	 * Exit a parse tree produced by {@link JavaPropertiesParser#emptyLine}.
	 * @param ctx the parse tree
	 */
	void exitEmptyLine(JavaPropertiesParser.EmptyLineContext ctx);
	/**
	 * Enter a parse tree produced by {@link JavaPropertiesParser#keyValuePair}.
	 * @param ctx the parse tree
	 */
	void enterKeyValuePair(JavaPropertiesParser.KeyValuePairContext ctx);
	/**
	 * Exit a parse tree produced by {@link JavaPropertiesParser#keyValuePair}.
	 * @param ctx the parse tree
	 */
	void exitKeyValuePair(JavaPropertiesParser.KeyValuePairContext ctx);
	/**
	 * Enter a parse tree produced by {@link JavaPropertiesParser#key}.
	 * @param ctx the parse tree
	 */
	void enterKey(JavaPropertiesParser.KeyContext ctx);
	/**
	 * Exit a parse tree produced by {@link JavaPropertiesParser#key}.
	 * @param ctx the parse tree
	 */
	void exitKey(JavaPropertiesParser.KeyContext ctx);
	/**
	 * Enter a parse tree produced by {@link JavaPropertiesParser#keyChar}.
	 * @param ctx the parse tree
	 */
	void enterKeyChar(JavaPropertiesParser.KeyCharContext ctx);
	/**
	 * Exit a parse tree produced by {@link JavaPropertiesParser#keyChar}.
	 * @param ctx the parse tree
	 */
	void exitKeyChar(JavaPropertiesParser.KeyCharContext ctx);
	/**
	 * Enter a parse tree produced by {@link JavaPropertiesParser#separatorAndValue}.
	 * @param ctx the parse tree
	 */
	void enterSeparatorAndValue(JavaPropertiesParser.SeparatorAndValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link JavaPropertiesParser#separatorAndValue}.
	 * @param ctx the parse tree
	 */
	void exitSeparatorAndValue(JavaPropertiesParser.SeparatorAndValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link JavaPropertiesParser#valueChar}.
	 * @param ctx the parse tree
	 */
	void enterValueChar(JavaPropertiesParser.ValueCharContext ctx);
	/**
	 * Exit a parse tree produced by {@link JavaPropertiesParser#valueChar}.
	 * @param ctx the parse tree
	 */
	void exitValueChar(JavaPropertiesParser.ValueCharContext ctx);
	/**
	 * Enter a parse tree produced by {@link JavaPropertiesParser#anyChar}.
	 * @param ctx the parse tree
	 */
	void enterAnyChar(JavaPropertiesParser.AnyCharContext ctx);
	/**
	 * Exit a parse tree produced by {@link JavaPropertiesParser#anyChar}.
	 * @param ctx the parse tree
	 */
	void exitAnyChar(JavaPropertiesParser.AnyCharContext ctx);
}