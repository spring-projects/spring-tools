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

import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.jspecify.annotations.NonNull;
import org.springframework.ide.vscode.boot.java.JdtSemanticTokensProvider;
import org.springframework.ide.vscode.boot.java.embedded.lang.EmbeddedLanguageSnippet;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.java.SpringProjectUtil;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokenData;
import org.springframework.ide.vscode.commons.util.Collector;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class JdtCronSemanticTokensProvider implements JdtSemanticTokensProvider {
	
	final private CronSemanticTokens tokensProvider;
	
	static final String SCHEDULED_SIMPLE_NAME = "Scheduled";

	
	public JdtCronSemanticTokensProvider(CronSemanticTokens tokensProvider) {
		this.tokensProvider = tokensProvider;
	}

	@Override
	public List<String> getTokenTypes() {
		return tokensProvider.getTokenTypes();
	}

	@Override
	public List<String> getTokenModifiers() {
		return tokensProvider.getTypeModifiers();
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return SpringProjectUtil.hasDependencyStartingWith(project, "spring-context", null);
	}

	@Override
	public @NonNull ASTVisitor getTokensComputer(IJavaProject project, TextDocument doc, CompilationUnit cu,
			Collector<SemanticTokenData> collector) {
		return new ASTVisitor() {

			@Override
			public boolean visit(NormalAnnotation node) {
				EmbeddedLanguageSnippet e = JdtCronVisitorUtils.extractCron(node);
				if (e != null) {
					tokensProvider.computeTokens(e.getText()).stream()
						.flatMap(td -> e.toJavaRanges(td.range()).stream().map(r -> new SemanticTokenData(r,
								td.type(), td.modifiers())))
							.forEach(collector::accept);
				}
				return super.visit(node);
			}
			
		};
	}
	
}
