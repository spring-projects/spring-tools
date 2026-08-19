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
package org.springframework.ide.vscode.boot.app;

import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.HqlSemanticTokens;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JpqlSemanticTokens;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JpqlSupportState;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.QueryProblemType;
import org.springframework.ide.vscode.boot.java.embedded.lang.AntlrReconcilerWithSpel;
import org.springframework.ide.vscode.boot.java.spel.SpelReconciler;
import org.springframework.ide.vscode.boot.java.spel.SpelSemanticTokens;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.parser.hql.HqlLexer;
import org.springframework.ide.vscode.parser.hql.HqlParser;
import org.springframework.ide.vscode.parser.jpql.JpqlLexer;
import org.springframework.ide.vscode.parser.jpql.JpqlParser;

@Configuration(proxyBeanMethods = false)
public class SpringDataConfig {
	
	@Bean
	JpqlSemanticTokens jpqlSemanticTokens(Optional<SpelSemanticTokens> optSpelTokensProvider) {
		return new JpqlSemanticTokens(optSpelTokensProvider);
	}
	
	@Bean
	HqlSemanticTokens hqlSemanticTokens(Optional<SpelSemanticTokens> optSpelTokensProvider) {
		return new HqlSemanticTokens(optSpelTokensProvider);
	}
	
	@Bean("hqlReconciler")
	AntlrReconcilerWithSpel hqlReconciler(Optional<SpelReconciler> spelReconciler) {
		return new AntlrReconcilerWithSpel("HQL", HqlParser.class, HqlLexer.class, "start", QueryProblemType.HQL_SYNTAX, spelReconciler, HqlLexer.SPEL);
	}
	
	@Bean("jpqlReconciler")
	AntlrReconcilerWithSpel jpqlReconciler(Optional<SpelReconciler> spelReconciler) {
		return new AntlrReconcilerWithSpel("JPQL", JpqlParser.class, JpqlLexer.class, "start", QueryProblemType.JPQL_SYNTAX, spelReconciler, JpqlLexer.SPEL);
	}
	
	@Bean
	JpqlSupportState jpqlSupportState(SimpleLanguageServer server, ProjectObserver projectObserver, BootJavaConfig config) {
		return new JpqlSupportState(server, projectObserver, config);
	}
	
}
