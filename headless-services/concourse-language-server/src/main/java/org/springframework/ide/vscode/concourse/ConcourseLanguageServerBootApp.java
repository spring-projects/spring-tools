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
package org.springframework.ide.vscode.concourse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.ide.vscode.commons.languageserver.LanguageServerRunner;
import org.springframework.ide.vscode.commons.languageserver.util.DocumentSymbolHandler;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.yaml.reconcile.ASTTypeCache;
import org.springframework.ide.vscode.commons.yaml.reconcile.TypeBasedYamlHierarchicalSymbolHandler;
import org.springframework.ide.vscode.commons.yaml.reconcile.TypeBasedYamlSymbolHandler;
import org.springframework.ide.vscode.concourse.github.DefaultGithubInfoProvider;
import org.springframework.ide.vscode.concourse.github.GithubInfoProvider;

@SpringBootApplication
public class ConcourseLanguageServerBootApp {

	private static final String SERVER_NAME = "concourse-language-server";

	public static void main(String[] args) throws Exception {
		System.setProperty(LanguageServerRunner.SYSPROP_LANGUAGESERVER_NAME, SERVER_NAME); //makes it easy to recognize language server processes. Set this as early as possible.
		SpringApplication.run(ConcourseLanguageServerBootApp.class, args);
	}

	@Bean GithubInfoProvider github() {
		return new DefaultGithubInfoProvider();
	}

	@Bean ConcourseModel concourseModel(SimpleLanguageServer server, ASTTypeCache astTypeCache) {
		return new ConcourseModel(server, astTypeCache);
	}

	@Bean ASTTypeCache astTypeCache() {
		return new ASTTypeCache();
	}

	@Bean DocumentSymbolHandler documentSymbolHandler(SimpleTextDocumentService documents, ASTTypeCache astTypeCache, PipelineYmlSchema schema, SimpleLanguageServer server) {
		TypeBasedYamlSymbolHandler baseHandler = new TypeBasedYamlSymbolHandler(documents, astTypeCache, schema.getDefinitionTypes());
		return new TypeBasedYamlHierarchicalSymbolHandler(baseHandler, schema.getHierarchicalDefinitionTypes(), server);
	}

	@Bean PipelineYmlSchema pipelineYmlSchema(ConcourseModel models, GithubInfoProvider github) {
		return new PipelineYmlSchema(models, github);
	}
}
