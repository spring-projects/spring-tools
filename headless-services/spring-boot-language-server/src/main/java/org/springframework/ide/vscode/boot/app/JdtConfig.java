/*******************************************************************************
 * Copyright (c) 2023, 2025 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.app;

import java.util.Collection;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.codeaction.JdtAstCodeActionProvider;
import org.springframework.ide.vscode.boot.java.codeaction.JdtCodeActionHandler;
import org.springframework.ide.vscode.boot.java.cron.CronExpressionsInlayHintsProvider;
import org.springframework.ide.vscode.boot.java.cron.CronReconciler;
import org.springframework.ide.vscode.boot.java.cron.CronSemanticTokens;
import org.springframework.ide.vscode.boot.java.cron.JdtCronReconciler;
import org.springframework.ide.vscode.boot.java.cron.JdtCronSemanticTokensProvider;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.HqlSemanticTokens;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JdtDataQueriesInlayHintsProvider;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JdtDataQuerySemanticTokensProvider;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JdtQueryDocHighlightsProvider;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JpqlSemanticTokens;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.JpqlSupportState;
import org.springframework.ide.vscode.boot.java.data.jpa.queries.QueryJdtAstReconciler;
import org.springframework.ide.vscode.boot.java.handlers.Reconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.AddConfigurationIfBeansPresentReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.AuthorizeHttpRequestsReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.AutowiredFieldIntoConstructorParameterReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.BeanMethodNotPublicReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.BeanPostProcessingIgnoreInAotReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.BeanRegistrarDeclarationReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.Boot3NotSupportedTypeReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.EntityIdForRepoReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.FeignClientReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.HttpSecurityLambdaDslReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.ImplicitWebAnnotationNamesReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.ModulithTypeReferenceViolationReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.NoAutowiredOnConstructorReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.NoRepoAnnotationReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.NoRequestMappingAnnotationReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.NotRegisteredBeansReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.PathInControllerAnnotationReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.PreciseBeanTypeReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.ServerHttpSecurityLambdaDslReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.UnnecessarySpringExtensionReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.WebApiVersioningReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.WebSecurityConfigurerAdapterReconciler;
import org.springframework.ide.vscode.boot.java.semantictokens.EmbeddedLanguagesSemanticTokensSupport;
import org.springframework.ide.vscode.boot.java.semantictokens.JavaSemanticTokensProvider;
import org.springframework.ide.vscode.boot.java.spel.JdtSpelReconciler;
import org.springframework.ide.vscode.boot.java.spel.JdtSpelSemanticTokensProvider;
import org.springframework.ide.vscode.boot.java.spel.SpelReconciler;
import org.springframework.ide.vscode.boot.java.spel.SpelSemanticTokens;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.commons.languageserver.util.LspClient;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

@Configuration(proxyBeanMethods = false)
public class JdtConfig {
	
	@Bean BeanMethodNotPublicReconciler beanMethodNotPublicReconciler(SimpleLanguageServer server) {
		return new BeanMethodNotPublicReconciler(server.getQuickfixRegistry());
	}
	
	@Bean AddConfigurationIfBeansPresentReconciler addConfigurationIfBeansPresentReconciler(SimpleLanguageServer server, SpringMetamodelIndex springIndex) {
		return new AddConfigurationIfBeansPresentReconciler(server.getQuickfixRegistry(), springIndex);
	}
	
	@Bean BeanRegistrarDeclarationReconciler beanRegistrarDeclarationReconciler(SimpleLanguageServer server,
			SpringMetamodelIndex springIndex) {
		return new BeanRegistrarDeclarationReconciler(server.getQuickfixRegistry(), springIndex);
	}
	
	@Bean FeignClientReconciler feignClientReconciler(SpringMetamodelIndex springIndex) {
		return new FeignClientReconciler(springIndex);
	}
	
	@Bean AutowiredFieldIntoConstructorParameterReconciler autowiredFieldIntoConstructorParameterReconciler(SimpleLanguageServer server) {
		return new AutowiredFieldIntoConstructorParameterReconciler(server.getQuickfixRegistry());
	}
	
	@Bean Boot3NotSupportedTypeReconciler boot3NotSupportedTypeReconciler() {
		return new Boot3NotSupportedTypeReconciler();
	}
	
	@Bean NoAutowiredOnConstructorReconciler noAutowiredOnConstructorReconciler(SimpleLanguageServer server) {
		return new NoAutowiredOnConstructorReconciler(server.getQuickfixRegistry());
	}
	
	@Bean PathInControllerAnnotationReconciler pathInControllerAnnotationReconciler(SimpleLanguageServer server) {
		return new PathInControllerAnnotationReconciler(server.getQuickfixRegistry());
	}
	
	@Bean WebApiVersioningReconciler webApiVersioningReconciler(SpringMetamodelIndex springIndex) {
		return new WebApiVersioningReconciler(springIndex);
	}
	
	@Bean WebSecurityConfigurerAdapterReconciler webSecurityConfigurerAdapterReconciler(SimpleLanguageServer server) {
		return new WebSecurityConfigurerAdapterReconciler(server.getQuickfixRegistry());
	}
	
	@Bean PreciseBeanTypeReconciler preciseBeanTypeReconciler(SimpleLanguageServer server) {
		return new PreciseBeanTypeReconciler(server.getQuickfixRegistry());
	}
	
	@Bean NoRequestMappingAnnotationReconciler noRequestMappingAnnotationReconciler(SimpleLanguageServer server) {
		return new NoRequestMappingAnnotationReconciler(server.getQuickfixRegistry());
	}
	
	@Bean ModulithTypeReferenceViolationReconciler modulithTypeReferenceViolationReconciler() {
		return new ModulithTypeReferenceViolationReconciler();
	}
	
	@Bean NoRepoAnnotationReconciler noRepoAnnotationReconciler(SimpleLanguageServer server) {
		return new NoRepoAnnotationReconciler(server.getQuickfixRegistry());
	}
	
	@Bean UnnecessarySpringExtensionReconciler unnecessarySpringExtensionReconciler(SimpleLanguageServer server) {
		return new UnnecessarySpringExtensionReconciler(server.getQuickfixRegistry());
	}
	
	@Bean HttpSecurityLambdaDslReconciler httpSecurityLamdaDslReconciler(SimpleLanguageServer server) {
		return new HttpSecurityLambdaDslReconciler(server.getQuickfixRegistry());
	}
	
	@Bean ServerHttpSecurityLambdaDslReconciler serverHttpSecurityLambdaDslReconciler(SimpleLanguageServer server) {
		return new ServerHttpSecurityLambdaDslReconciler(server.getQuickfixRegistry());
	}
	
	@Bean AuthorizeHttpRequestsReconciler authorizeHttpRequestsReconciler(SimpleLanguageServer server) {
		return new AuthorizeHttpRequestsReconciler(server.getQuickfixRegistry());
	}
	
	@Bean BeanPostProcessingIgnoreInAotReconciler beanPostProcessingIgnoreInAotReconciler(SimpleLanguageServer server) {
		return new BeanPostProcessingIgnoreInAotReconciler(server.getQuickfixRegistry());
	}
	
	@Bean NotRegisteredBeansReconciler notRegisteredBeansReconciler(SimpleLanguageServer server, SpringMetamodelIndex springIndex) {
		return new NotRegisteredBeansReconciler(server.getQuickfixRegistry(), springIndex);
	}
	
	@Bean EntityIdForRepoReconciler entityIdForRepoReconciler(SimpleLanguageServer server) {
		return new EntityIdForRepoReconciler();
	}
	
	@Conditional(LspClient.OnNotEclipseClient.class)
	@Bean JavaSemanticTokensProvider javaSemanticTokens() {
		return new JavaSemanticTokensProvider();
	}
	
	@Bean JdtDataQuerySemanticTokensProvider jpqlJdtSemanticTokensProvider(JpqlSemanticTokens jpqlProvider, HqlSemanticTokens hqlProvider, JpqlSupportState supportState, Optional<SpelSemanticTokens> spelSemanticTokens) {
		return new JdtDataQuerySemanticTokensProvider(jpqlProvider, hqlProvider, supportState, spelSemanticTokens);
	}
	
	@Bean JdtDataQueriesInlayHintsProvider jdtDataQueriesInlayHintsProvider(JdtDataQuerySemanticTokensProvider semanticTokensProvider) {
		return new JdtDataQueriesInlayHintsProvider(semanticTokensProvider);
	}
	
	@Bean CronExpressionsInlayHintsProvider cronExpressionsInlayHintsProvider(BootJavaConfig config) {
		return new CronExpressionsInlayHintsProvider(config);
	}
	
	@Bean JdtQueryDocHighlightsProvider jdtDocHighlightsProvider(JdtDataQuerySemanticTokensProvider semanticTokensProvider) {
		return new JdtQueryDocHighlightsProvider(semanticTokensProvider);
	}
	
	@Bean JdtCronSemanticTokensProvider jdtCronSemanticTokensProvider(CronSemanticTokens cronProvider) {
		return new JdtCronSemanticTokensProvider(cronProvider);
	}
	
	@Bean QueryJdtAstReconciler dataQueryReconciler(
			@Qualifier("hqlReconciler") Reconciler hqlReconciler,
			@Qualifier("jpqlReconciler") Reconciler jpqlReconciler,
			Optional<SpelReconciler> spelReconciler) {
		return new QueryJdtAstReconciler(hqlReconciler, jpqlReconciler, spelReconciler);
	}

	@Bean EmbeddedLanguagesSemanticTokensSupport embbededLanguagesSyntaxHighlighting(SimpleLanguageServer server, BootJavaConfig config) {
		return new EmbeddedLanguagesSemanticTokensSupport(server, config);
	}

	@Bean JdtSpelSemanticTokensProvider jdtSpelSemanticTokensProvider(SpelSemanticTokens spelSemanticTokens) {
		return new JdtSpelSemanticTokensProvider(spelSemanticTokens);
	}
	
	@Bean JdtSpelReconciler jdtSpelReconciler(SpelReconciler spelReconciler) {
		return new JdtSpelReconciler(spelReconciler);
	}
	
	@Bean ImplicitWebAnnotationNamesReconciler implicitWebAnnotationNamesReconciler(SimpleLanguageServer server) {
		return new ImplicitWebAnnotationNamesReconciler(server.getQuickfixRegistry());
	}
	
	@Bean JdtCronReconciler jdtCronReconciler(CronReconciler cronReconciler) {
		return new JdtCronReconciler(cronReconciler);
	}
	
	@Bean JdtCodeActionHandler jdtCodeActionHandler(CompilationUnitCache cuCache, Collection<JdtAstCodeActionProvider> providers) {
		return new JdtCodeActionHandler(cuCache, providers);
	}
}
