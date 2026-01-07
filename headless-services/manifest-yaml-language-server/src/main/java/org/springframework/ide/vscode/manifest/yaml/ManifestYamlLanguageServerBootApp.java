/*******************************************************************************
 * Copyright (c) 2018, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.manifest.yaml;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.ide.vscode.commons.languageserver.LanguageServerRunner;
import org.springframework.ide.vscode.commons.languageserver.completion.VscodeCompletionEngineAdapter.CompletionFilter;
import org.springframework.ide.vscode.commons.util.Unicodes;
import org.springframework.ide.vscode.commons.yaml.reconcile.ASTTypeCache;

@SpringBootApplication
public class ManifestYamlLanguageServerBootApp {

	private static final String SERVER_NAME = "manifest-yaml-language-server";

	public static void main(String[] args) throws Exception {
		System.setProperty(LanguageServerRunner.SYSPROP_LANGUAGESERVER_NAME, SERVER_NAME); //makes it easy to recognize language server processes - and set this as early as possible
		SpringApplication.run(ManifestYamlLanguageServerBootApp.class, args);
	}

	@Bean
	public CompletionFilter completionFilter() {
		return (proposal) -> {
			// Exclude proposals that start with an arrow due to this bug:
			// PT 162292472
			if (proposal != null &&
					(proposal.getLabel().startsWith(Unicodes.RIGHT_ARROW+"") ||
							proposal.getLabel().startsWith(Unicodes.LEFT_ARROW+""))) {
				return false;
			}
			return true;
		};
	}

	@Bean ASTTypeCache astTypeCache() {
		return new ASTTypeCache();
	}
}
