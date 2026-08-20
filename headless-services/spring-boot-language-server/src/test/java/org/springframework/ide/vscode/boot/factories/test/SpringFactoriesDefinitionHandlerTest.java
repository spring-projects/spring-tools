/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.factories.test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.BootLanguageServerParams;
import org.springframework.ide.vscode.boot.bootiful.AdHocPropertyHarnessTestConf;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
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
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import({AdHocPropertyHarnessTestConf.class, SpringFactoriesDefinitionHandlerTest.TestConf.class})
public class SpringFactoriesDefinitionHandlerTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private MavenJavaProject testProject;
	@Autowired private DefinitionLinkAsserts definitionLinkAsserts;

	@Configuration
	static class TestConf {

		@Bean MavenJavaProject testProject() throws Exception {
			return ProjectsHarness.INSTANCE.mavenProject("test-annotations");
		}

		@Bean PropertyIndexHarness indexHarness(ValueProviderRegistry valueProviders) {
			return new PropertyIndexHarness(valueProviders);
		}

		@Bean JavaProjectFinder projectFinder(MavenJavaProject testProject) {
			return new JavaProjectFinder() {

				@Override
				public Optional<IJavaProject> find(TextDocumentIdentifier doc) {
					return Optional.ofNullable(testProject);
				}

				@Override
				public Collection<? extends IJavaProject> all() {
					return testProject == null ? Collections.emptyList() : List.of(testProject);
				}
			};
		}

		@Bean BootLanguageServerHarness harness(SimpleLanguageServer server, BootLanguageServerParams serverParams, PropertyIndexHarness indexHarness, JavaProjectFinder projectFinder) throws Exception {
			return new BootLanguageServerHarness(server, serverParams, indexHarness, projectFinder, LanguageId.SPRING_FACTORIES, ".factories");
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

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
	}

	@Test
	void testLinkFromValueToType() throws Exception {
		Editor editor = factoriesEditor("org.test.Scheduler=org.test.TestValueCompletion\n");

		definitionLinkAsserts.assertLinkTargets(editor, "org.test.TestValueCompletion", testProject,
				editor.rangeOf("org.test.TestValueCompletion"), "org.test.TestValueCompletion");
	}

	@Test
	void testLinkFromKeyToType() throws Exception {
		Editor editor = factoriesEditor("org.test.Scheduler=org.test.TestValueCompletion\n");

		definitionLinkAsserts.assertLinkTargets(editor, "org.test.Scheduler", testProject,
				editor.rangeOf("org.test.Scheduler"), "org.test.Scheduler");
	}

	@Test
	void testLinkFromValueWithSurroundingSpacesToType() throws Exception {
		Editor editor = factoriesEditor("org.test.Scheduler =   org.test.TestValueCompletion   \n");

		definitionLinkAsserts.assertLinkTargets(editor, "org.test.TestValueCompletion", testProject,
				editor.rangeOf("org.test.TestValueCompletion"), "org.test.TestValueCompletion");
	}

	@Test
	void testLinkFromMultiLineValueListToType() throws Exception {
		Editor editor = factoriesEditor("""
				org.test.Scheduler=\\
				org.test.TestValueCompletion,\\
				org.test.TestScopeCompletion,\\
				org.test.CronScheduler
				""");

		definitionLinkAsserts.assertLinkTargets(editor, "org.test.TestValueCompletion", testProject,
				editor.rangeOf("org.test.TestValueCompletion"), "org.test.TestValueCompletion");

		definitionLinkAsserts.assertLinkTargets(editor, "org.test.TestScopeCompletion", testProject,
				editor.rangeOf("org.test.TestScopeCompletion"), "org.test.TestScopeCompletion");

		definitionLinkAsserts.assertLinkTargets(editor, "org.test.CronScheduler", testProject,
				editor.rangeOf("org.test.CronScheduler"), "org.test.CronScheduler");
	}

	@Test
	void testNoLinkForUnknownType() throws Exception {
		Editor editor = factoriesEditor("org.test.Scheduler=org.test.DoesNotExist\n");

		editor.assertNoDefinitionLinkTargets("org.test.DoesNotExist");
	}

	@Test
	void testNoLinkInsideComment() throws Exception {
		Editor editor = factoriesEditor("# org.test.TestValueCompletion\norg.test.Scheduler=org.test.CronScheduler\n");

		editor.assertNoDefinitionLinkTargets("org.test.TestValueCompletion");
	}

	private Editor factoriesEditor(String content) throws Exception {
		Path factoriesFile = Paths.get(testProject.getLocationUri())
				.resolve("src/main/resources/META-INF/spring.factories");
		return harness.newEditor(LanguageId.SPRING_FACTORIES, content, factoriesFile.toUri().toASCIIString());
	}

}
