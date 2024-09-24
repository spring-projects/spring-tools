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
package org.springframework.ide.vscode.boot.java.spel;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.springframework.ide.vscode.boot.java.SpelProblemType;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.AntlrReconciler;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.parser.placeholder.PropertyPlaceHolderLexer;
import org.springframework.ide.vscode.parser.placeholder.PropertyPlaceHolderParser;
import org.springframework.ide.vscode.parser.spel.SpelLexer;
import org.springframework.ide.vscode.parser.spel.SpelParser;

public class SpelReconciler extends AntlrReconciler {
	
	private boolean enabled;
	private AntlrReconciler propertyHolderReconciler; 
		
	public SpelReconciler() {
		super("SPEL", SpelParser.class, SpelLexer.class, "spelExpr", SpelProblemType.JAVA_SPEL_EXPRESSION_SYNTAX);
		this.errorOnUnrecognizedTokens = false;
		this.enabled = true;
		this.propertyHolderReconciler = new AntlrReconciler("Place-Holder", PropertyPlaceHolderParser.class, PropertyPlaceHolderLexer.class, "start", SpelProblemType.PROPERTY_PLACE_HOLDER_SYNTAX);
	}

	public void setEnabled(boolean spelExpressionValidationEnabled) {
		this.enabled = spelExpressionValidationEnabled;
	}

	@Override
	public void reconcile(String text, int startPosition, IProblemCollector problemCollector) {
		if (!enabled) {
			return;
		}
		super.reconcile(text, startPosition, problemCollector);
	}

	@Override
	protected Parser createParser(String text, int startPosition, IProblemCollector problemCollector) throws Exception {
		Parser parser = super.createParser(text, startPosition, problemCollector);
		
		// Reconcile embedded SPEL
		parser.addParseListener(new ParseTreeListener() {
			
			private void processTerminal(TerminalNode node) {
				if (node.getSymbol().getType() == SpelLexer.PROPERTY_PLACE_HOLDER) {
					int placeHolderStartPosition = startPosition + node.getSymbol().getStartIndex() + 2;
					String content = node.getSymbol().getText().substring(2, node.getSymbol().getText().length() - 1);
					propertyHolderReconciler.reconcile(content, placeHolderStartPosition, problemCollector);
				}
			}

			@Override
			public void visitTerminal(TerminalNode node) {
				processTerminal(node);
			}

			@Override
			public void visitErrorNode(ErrorNode node) {
				processTerminal(node);
			}

			@Override
			public void enterEveryRule(ParserRuleContext ctx) {
			}

			@Override
			public void exitEveryRule(ParserRuleContext ctx) {
			}
			
		});
		
		return parser;
	}

}
