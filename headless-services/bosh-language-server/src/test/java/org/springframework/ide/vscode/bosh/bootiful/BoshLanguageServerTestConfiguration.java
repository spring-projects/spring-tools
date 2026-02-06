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
package org.springframework.ide.vscode.bosh.bootiful;

import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ide.vscode.bosh.BoshCliConfig;
import org.springframework.ide.vscode.bosh.mocks.MockCloudConfigProvider;
import org.springframework.ide.vscode.commons.languageserver.LanguageServerRunner;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.HarnessLanguageServerRunner;
import org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness;

import com.google.gson.GsonBuilder;

@Configuration
public class BoshLanguageServerTestConfiguration {

	@Bean MockCloudConfigProvider cloudConfig(BoshCliConfig cliConfig) {
		return new MockCloudConfigProvider(cliConfig);
	}

	@Bean public LanguageServerHarness harness(SimpleLanguageServer server) throws Exception {
		LanguageServerHarness harness = new LanguageServerHarness(
				server,
				LanguageId.BOSH_DEPLOYMENT
		);
		return harness;
	}
	
	@ConditionalOnMissingBean(LanguageServerRunner.class)
	@Bean
	HarnessLanguageServerRunner harnessLanguageServerRunner(Optional<Consumer<GsonBuilder>> configGsonOpt) {
		return new HarnessLanguageServerRunner(configGsonOpt);
	}

}