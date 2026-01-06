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
package org.springframework.ide.vscode.bosh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.ide.vscode.bosh.models.BoshCommandCloudConfigProvider;
import org.springframework.ide.vscode.bosh.models.BoshCommandReleasesProvider;
import org.springframework.ide.vscode.bosh.models.BoshCommandStemcellsProvider;
import org.springframework.ide.vscode.commons.languageserver.LanguageServerRunner;
import org.springframework.ide.vscode.commons.yaml.reconcile.ASTTypeCache;

@SpringBootApplication
public class BoshLanguageServerBootApp {

	private static final String SERVER_NAME = "bosh-language-server";

	public static void main(String[] args) throws Exception {
		System.setProperty(LanguageServerRunner.SYSPROP_LANGUAGESERVER_NAME, SERVER_NAME); //makes it easy to recognize language server processes - and set this as early as possible		
		SpringApplication.run(BoshLanguageServerBootApp.class, args);
	}

	@Bean public ASTTypeCache astTypeCache() {
		return new ASTTypeCache();
	}

	@ConditionalOnMissingClass("org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness")
	@Bean BoshCommandCloudConfigProvider cloudConfg(BoshCliConfig cliConfig) {
		return new BoshCommandCloudConfigProvider(cliConfig);
	}

	@ConditionalOnMissingClass("org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness")
	@Bean BoshCommandStemcellsProvider stemcels(BoshCliConfig cliConfig) {
		return new BoshCommandStemcellsProvider(cliConfig);
	}

	@ConditionalOnMissingClass("org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness")
	@Bean BoshCommandReleasesProvider releases(BoshCliConfig cliConfig) {
		return new BoshCommandReleasesProvider(cliConfig);
	}

}
