/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.conditionals.test;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.springframework.context.annotation.Bean;
import org.springframework.ide.vscode.boot.app.BootLanguageServerParams;
import org.springframework.ide.vscode.boot.editor.harness.PropertyIndexHarness;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheVoid;
import org.springframework.ide.vscode.boot.java.links.JavaDocumentUriProvider;
import org.springframework.ide.vscode.boot.java.links.SourceLinkFactory;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.boot.metadata.ValueProviderRegistry;
import org.springframework.ide.vscode.boot.test.DefinitionLinkAsserts;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;

/**
 * Test configuration, imported by the {@code @ConditionalOnClass} tests. Unlike {@code IndexerTestConf}, the test
 * project is built, so that its own classes show up in the classpath index used for type search and lookup.
 *
 * @author viniciusmachak
 */
public class ConditionalOnClassTestConf {

	static final String TEST_PROJECT = "test-annotation-conditionalonresource";

	@Bean MavenJavaProject testProject() throws Exception {
		return ProjectsHarness.INSTANCE.mavenProject(TEST_PROJECT);
	}

	@Bean PropertyIndexHarness indexHarness(ValueProviderRegistry valueProviders) {
		return new PropertyIndexHarness(valueProviders);
	}

	@Bean JavaProjectFinder projectFinder(MavenJavaProject testProject) {
		return new JavaProjectFinder() {

			@Override
			public Optional<IJavaProject> find(TextDocumentIdentifier doc) {
				return Optional.of(testProject);
			}

			@Override
			public Collection<? extends IJavaProject> all() {
				return List.of(testProject);
			}
		};
	}

	@Bean BootLanguageServerHarness harness(SimpleLanguageServer server, BootLanguageServerParams serverParams, PropertyIndexHarness indexHarness, JavaProjectFinder projectFinder) throws Exception {
		return new BootLanguageServerHarness(server, serverParams, indexHarness, projectFinder, LanguageId.JAVA, ".java");
	}

	@Bean BootLanguageServerParams serverParams(SimpleLanguageServer server, JavaProjectFinder projectFinder, ValueProviderRegistry valueProviders, PropertyIndexHarness indexHarness) {
		BootLanguageServerParams testDefaults = BootLanguageServerHarness.createTestDefault(server, valueProviders);
		return new BootLanguageServerParams(
				projectFinder,
				ProjectObserver.NULL,
				indexHarness.getIndexProvider(),
				testDefaults.typeUtilProvider
		);
	}

	@Bean IndexCache symbolCache() {
		return new IndexCacheVoid();
	}

	@Bean SourceLinks sourceLinks() {
		return SourceLinkFactory.NO_SOURCE_LINKS;
	}

	@Bean DefinitionLinkAsserts definitionLinkAsserts(JavaDocumentUriProvider javaDocumentUriProvider, CompilationUnitCache cuCache) {
		return new DefinitionLinkAsserts(javaDocumentUriProvider, cuCache);
	}

}
