/*******************************************************************************
 * Copyright (c) 2018, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.languageserver.starter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.ide.vscode.commons.languageserver.completion.VscodeCompletionEngineAdapter.CompletionFilter;
import org.springframework.ide.vscode.commons.languageserver.config.LanguageServerProperties;
import org.springframework.ide.vscode.commons.languageserver.reconcile.DiagnosticSeverityProvider;
import org.springframework.ide.vscode.commons.languageserver.util.CompletionServerCapabilityRegistration;
import org.springframework.ide.vscode.commons.languageserver.util.DefinitionHandler;
import org.springframework.ide.vscode.commons.languageserver.util.DocumentSymbolHandler;
import org.springframework.ide.vscode.commons.languageserver.util.ImplementationHandler;
import org.springframework.ide.vscode.commons.languageserver.util.LanguageSpecific;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;

@AutoConfiguration
@EnableConfigurationProperties(LanguageServerProperties.class)
public class LanguageServerAutoConf {
	
	@ConditionalOnMissingBean
	@Bean(destroyMethod = "")
	public SimpleLanguageServer languageServer(
			LanguageServerProperties props, 
			Optional<CompletionFilter> completionFilter,
			ApplicationContext appContext
	) throws Exception {
		SimpleLanguageServer server = new SimpleLanguageServer(props.getExtensionId(), appContext, props);
		server.setCompletionFilter(completionFilter);
		return server;
	}
	
	@Bean CompletionServerCapabilityRegistration completionCapabilities(SimpleLanguageServer server, LanguageServerProperties props) {
		return new CompletionServerCapabilityRegistration(server, props);
	}

	@Bean SimpleTextDocumentService documents(SimpleLanguageServer ls) {
		return ls.getTextDocumentService();
	}

	@ConditionalOnBean(DefinitionHandler.class)
	@Bean
	InitializingBean registerDefinitionHandler(SimpleTextDocumentService documents,
			List<DefinitionHandler> definitionHandlers) {
		if (definitionHandlers.size() == 1) {
			return () -> documents.onDefinition(definitionHandlers.get(0));
		} else {
			Map<LanguageId, DefinitionHandler> handlers = new HashMap<>(definitionHandlers.size());
			for (DefinitionHandler h : definitionHandlers) {
				Assert.isInstanceOf(LanguageSpecific.class, h, "Only language specific defintion handlers supported!");
				for (LanguageId l : ((LanguageSpecific)h).supportedLanguages()) {
					Assert.isTrue(!handlers.containsKey(l), "Multiple definition handlers for the same language not supported!");
					handlers.put(l, h);
				}
			}

			ImmutableMap<LanguageId, DefinitionHandler> immutableMap = ImmutableMap.copyOf(handlers);
			return () -> documents.onDefinition((cancelToken, position) -> {
				TextDocument doc = documents.getLatestSnapshot(position);

				if (doc != null) {
					LanguageId language = doc.getLanguageId();
					DefinitionHandler handler = immutableMap.get(language);
					if (handler != null) {
						return handler.handle(cancelToken, position);
					}
				}
				return null;
 			});
		}
	}
	
	@ConditionalOnBean(ImplementationHandler.class)
	@Bean
	InitializingBean registerImplementationHandler(SimpleTextDocumentService documents,
			List<ImplementationHandler> implHandlers) {
		if (implHandlers.size() == 1) {
			return () -> documents.onImplementation(implHandlers.get(0));
		} else {
			Map<LanguageId, ImplementationHandler> handlers = new HashMap<>(implHandlers.size());
			for (ImplementationHandler h : implHandlers) {
				Assert.isInstanceOf(LanguageSpecific.class, h, "Only language specific defintion handlers supported!");
				for (LanguageId l : ((LanguageSpecific)h).supportedLanguages()) {
					Assert.isTrue(!handlers.containsKey(l), "Multiple definition handlers for the same language not supported!");
					handlers.put(l, h);
				}
			}

			ImmutableMap<LanguageId, ImplementationHandler> immutableMap = ImmutableMap.copyOf(handlers);
			return () -> documents.onImplementation((cancelToken, position) -> {
				TextDocument doc = documents.getLatestSnapshot(position);

				if (doc != null) {
					LanguageId language = doc.getLanguageId();
					ImplementationHandler handler = immutableMap.get(language);
					if (handler != null) {
						return handler.handle(cancelToken, position);
					}
				}
				return null;
 			});
		}
	}
	
	@ConditionalOnBean(DocumentSymbolHandler.class)
	@Bean
	InitializingBean registerDocumentSymbolHandler(SimpleTextDocumentService documents, DocumentSymbolHandler handler) {
		return () -> {
			documents.onDocumentSymbol(handler);
		};
	}
	
	@ConditionalOnMissingBean(DiagnosticSeverityProvider.class)
	@Bean
	DiagnosticSeverityProvider defaultDiagnosticSeverityProvider() {
		return DiagnosticSeverityProvider.DEFAULT;
	}
	
}
