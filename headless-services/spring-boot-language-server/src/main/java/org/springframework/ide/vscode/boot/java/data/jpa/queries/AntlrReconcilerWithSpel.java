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
package org.springframework.ide.vscode.boot.java.data.jpa.queries;

import java.util.Optional;

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ErrorNode;
import org.antlr.v4.runtime.tree.ParseTreeListener;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.springframework.ide.vscode.boot.java.spel.SpelReconciler;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ProblemType;

public class AntlrReconcilerWithSpel extends AntlrReconciler {
	
	private final Optional<SpelReconciler> spelReconciler;
	private final int spelTokenType;

	public AntlrReconcilerWithSpel(String prefix, Class<? extends Parser> parserClass, Class<? extends Lexer> lexerClass,
			String parseMethod, ProblemType problemType, Optional<SpelReconciler> spelReconciler, int spelTokenType) {
		super(prefix, parserClass, lexerClass, parseMethod, problemType);
		this.spelReconciler = spelReconciler;
		this.spelTokenType = spelTokenType;
	}

	@Override
	protected Parser createParser(String text, int startPosition, IProblemCollector problemCollector) throws Exception {
		Parser parser = super.createParser(text, startPosition, problemCollector);
		
		// Reconcile embedded SPEL
		spelReconciler.ifPresent(r -> parser.addParseListener(new ParseTreeListener() {
			
			private void processTerminal(TerminalNode node) {
				if (node.getSymbol().getType() == spelTokenType) {
					AntlrReconcilerWithSpel.reconcileEmbeddedSpelNode(node, startPosition, r, problemCollector);
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
			
		}));
		
		return parser;
	}

	private static void reconcileEmbeddedSpelNode(TerminalNode node, int initialOffset, SpelReconciler spelReconciler, IProblemCollector problemCollector) {
		int startPosition = initialOffset + node.getSymbol().getStartIndex();
		String spelContent = node.getSymbol().getText().substring(2, node.getSymbol().getText().length() - 1);
		spelReconciler.reconcile(spelContent, startPosition, problemCollector);
	}

}
