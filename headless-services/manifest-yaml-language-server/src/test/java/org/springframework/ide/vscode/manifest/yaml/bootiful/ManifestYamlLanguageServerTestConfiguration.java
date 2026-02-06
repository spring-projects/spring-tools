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
package org.springframework.ide.vscode.manifest.yaml.bootiful;

import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ide.vscode.commons.cloudfoundry.client.CloudFoundryClientFactory;
import org.springframework.ide.vscode.commons.cloudfoundry.client.cftarget.ClientParamsProvider;
import org.springframework.ide.vscode.commons.languageserver.LanguageServerRunner;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.HarnessLanguageServerRunner;
import org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness;
import org.springframework.ide.vscode.manifest.yaml.MockCloudfoundry;

import com.google.gson.GsonBuilder;

@Configuration
public class ManifestYamlLanguageServerTestConfiguration {

	@Bean public MockCloudfoundry cloudfoundry() {
		return new MockCloudfoundry();
	}

	@Bean public CloudFoundryClientFactory cloudfoundryClientFactory(MockCloudfoundry cf) {
		return cf.factory;
	}

	@Bean public ClientParamsProvider cloudfoundryParams(MockCloudfoundry cf) {
		return cf.defaultParamsProvider;
	}

	@Bean public LanguageServerHarness harness(SimpleLanguageServer server) throws Exception {
		LanguageServerHarness harness = new LanguageServerHarness(
				server,
				LanguageId.CF_MANIFEST
		);
		return harness;
	}
	
	@ConditionalOnMissingBean(LanguageServerRunner.class)
	@Bean
	HarnessLanguageServerRunner harnessLanguageServerRunner(Optional<Consumer<GsonBuilder>> configGsonOpt) {
		return new HarnessLanguageServerRunner(configGsonOpt);
	}


}