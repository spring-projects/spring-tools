/*******************************************************************************
 * Copyright (c) 2018, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.languageserver.starter;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.ide.vscode.commons.languageserver.LanguageServerRunner;
import org.springframework.ide.vscode.commons.languageserver.config.LanguageServerProperties;
import org.springframework.ide.vscode.commons.languageserver.util.ParentProcessWatcher;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;

import com.google.gson.GsonBuilder;

@AutoConfiguration
public class LanguageServerRunnerAutoConf {
	
	@ConditionalOnMissingClass("org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness")
	@Bean
	Function<MessageConsumer, MessageConsumer> messageConsumer(SimpleLanguageServer languageServer, LanguageServerProperties properties) {
		if (!properties.isStandalone()) {
			return new ParentProcessWatcher(languageServer);
		}
		return LanguageServerRunner.DEFAULT_MESSAGE_CONSUMER;
	}
	
	@ConditionalOnMissingClass("org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness")
	@Bean
	LanguageServerRunner serverApp(
			LanguageServerProperties properties, 
			SimpleLanguageServer languageServerFactory,
			Function<MessageConsumer, MessageConsumer> messageConsumer,
			Optional<Consumer<GsonBuilder>> configureGson
	) {
		return new LanguageServerRunner(properties, languageServerFactory, messageConsumer, configureGson);
	}
	
}
