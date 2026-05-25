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
package org.springframework.ide.vscode.boot.java.semantictokens;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.jspecify.annotations.NonNull;
import org.springframework.ide.vscode.boot.java.JdtSemanticTokensProvider;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokenData;
import org.springframework.ide.vscode.commons.util.Collector;
import org.springframework.ide.vscode.commons.util.text.TextDocument;

public class JavaSemanticTokensProvider implements JdtSemanticTokensProvider {

	@Override
	public List<String> getTokenTypes() {
		return Arrays.stream(TokenType.values()).map(TokenType::toString).collect(Collectors.toList());
	}
	
	@Override
	public List<String> getTokenModifiers() {
		return Arrays.stream(TokenModifier.values()).map(TokenModifier::toString).collect(Collectors.toList());
	}

	@Override
	public boolean isApplicable(IJavaProject project) {
		return true;
	}

	@Override
	public @NonNull ASTVisitor getTokensComputer(IJavaProject project, TextDocument doc, CompilationUnit cu,
			Collector<SemanticTokenData> collector) {
		return new SemanticTokensVisitor(project, doc, cu, collector);
	}

}
