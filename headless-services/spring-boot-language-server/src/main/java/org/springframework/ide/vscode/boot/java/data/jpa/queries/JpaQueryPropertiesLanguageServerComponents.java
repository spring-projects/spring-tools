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
import java.util.Set;

import org.springframework.ide.vscode.boot.java.handlers.Reconciler;
import org.springframework.ide.vscode.commons.languageserver.composable.LanguageServerComponents;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IReconcileEngine;
import org.springframework.ide.vscode.commons.languageserver.semantic.tokens.SemanticTokensHandler;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.util.text.LanguageId;

public class JpaQueryPropertiesLanguageServerComponents implements LanguageServerComponents {
	
	private final QueryPropertiesSemanticTokensHandler semanticTokensHandler;
	private final NamedQueryPropertiesReconcileEngine reconcileEngine;

	public JpaQueryPropertiesLanguageServerComponents(SimpleTextDocumentService documents, JavaProjectFinder projectsFinder,
			JpqlSemanticTokens jpqlSemanticTokensProvider, HqlSemanticTokens hqlSematicTokensProvider, JpqlSupportState supportState,
			Reconciler hqlReconciler, Reconciler jpqlReconciler) {
		this.semanticTokensHandler = new QueryPropertiesSemanticTokensHandler(projectsFinder, jpqlSemanticTokensProvider, hqlSematicTokensProvider, supportState);
		this.reconcileEngine = new NamedQueryPropertiesReconcileEngine(projectsFinder, hqlReconciler, jpqlReconciler);
	}
	
	@Override
	public Set<LanguageId> getInterestingLanguages() {
		return Set.of(LanguageId.JPA_QUERY_PROPERTIES);
	}

	@Override
	public Optional<SemanticTokensHandler> getSemanticTokensHandler() {
		return Optional.of(semanticTokensHandler);
	}

	@Override
	public Optional<IReconcileEngine> getReconcileEngine() {
		return Optional.of(reconcileEngine);
	}

}
