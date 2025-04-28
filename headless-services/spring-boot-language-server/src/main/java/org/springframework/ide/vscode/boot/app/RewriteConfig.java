/*******************************************************************************
 * Copyright (c) 2022, 2023 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ide.vscode.boot.java.data.DataRepositoryAotMetadataService;
import org.springframework.ide.vscode.boot.java.data.QueryMethodCodeActionProvider;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.ReconcileProblemCodeActionProvider;
import org.springframework.ide.vscode.boot.java.rewrite.RewriteRecipeRepository;
import org.springframework.ide.vscode.boot.java.rewrite.RewriteRefactorings;
import org.springframework.ide.vscode.boot.java.rewrite.SpringBootUpgrade;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

@Configuration(proxyBeanMethods = false)
public class RewriteConfig {

	@Bean RewriteRecipeRepository rewriteRecipesRepository(SimpleLanguageServer server, JavaProjectFinder projectFinder, BootJavaConfig config) {
		return new RewriteRecipeRepository(server, projectFinder, config);
	}
	
	@ConditionalOnBean(RewriteRecipeRepository.class)
	@Bean RewriteRefactorings rewriteRefactorings(SimpleLanguageServer server, JavaProjectFinder projectFinder, RewriteRecipeRepository recipeRepo) {
		return new RewriteRefactorings(server, projectFinder, recipeRepo);
	}
	
	@ConditionalOnBean(RewriteRecipeRepository.class)
	@Bean ReconcileProblemCodeActionProvider reconcileProblemCodeActionProvider(JdtReconciler reconciler, SimpleLanguageServer server) {
		return new ReconcileProblemCodeActionProvider(reconciler, server.getDiagnosticSeverityProvider());
	}
	
	@ConditionalOnBean(RewriteRecipeRepository.class)
	@Bean SpringBootUpgrade springBootUpgrade(SimpleLanguageServer server, RewriteRecipeRepository recipeRepo, JavaProjectFinder projectFinder) {
		return new SpringBootUpgrade(server, recipeRepo, projectFinder);
	}
	
	@ConditionalOnBean(RewriteRefactorings.class)
	@Bean QueryMethodCodeActionProvider queryMethodCodeActionProvider(DataRepositoryAotMetadataService dataRepoAotService, RewriteRefactorings refactorings) {
		return new QueryMethodCodeActionProvider(dataRepoAotService, refactorings);
	}

}
