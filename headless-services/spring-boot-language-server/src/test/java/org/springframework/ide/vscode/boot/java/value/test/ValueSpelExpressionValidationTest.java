/*******************************************************************************
 * Copyright (c) 2020, 2024 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.value.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.gradle.internal.impldep.com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.BootLanguageServerParams;
import org.springframework.ide.vscode.boot.bootiful.AdHocPropertyHarnessTestConf;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.editor.harness.PropertyIndexHarness;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheVoid;
import org.springframework.ide.vscode.boot.java.SpelProblemType;
import org.springframework.ide.vscode.boot.java.handlers.BootJavaReconcileEngine;
import org.springframework.ide.vscode.boot.java.links.SourceLinkFactory;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.reconcilers.JavaReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtAstReconciler;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtReconciler;
import org.springframework.ide.vscode.boot.java.spel.JdtSpelReconciler;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.boot.java.utils.test.MockProjectObserver;
import org.springframework.ide.vscode.boot.metadata.ValueProviderRegistry;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.java.ProjectObserver;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import({AdHocPropertyHarnessTestConf.class, ValueSpelExpressionValidationTest.TestConf.class})
public class ValueSpelExpressionValidationTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private CompilationUnitCache compilationUnitCache;
	@Autowired private SimpleLanguageServer server;
	@Autowired private BootJavaConfig config;
	@Autowired private JdtSpelReconciler jdtSpelReconciler;

	private File directory;
	private String docUri;
	private TestProblemCollector problemCollector;
	private BootJavaReconcileEngine reconcileEngine;

	@Configuration
	static class TestConf {

		//Somewhat strange test setup, test provides a specific test project.
		//The project finder finds this test project,
		//But it is not used in the indexProvider/harness.
		//this is a bit odd... but we preserved the strangeness how it was.

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
					// TODO Auto-generated method stub
					return testProject == null ? Collections.emptyList() : ImmutableList.of(testProject);
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

		@Bean SourceLinks sourceLinks(SimpleTextDocumentService documents, CompilationUnitCache cuCache) {
			return SourceLinkFactory.NO_SOURCE_LINKS;
		}

	}

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		harness.changeConfiguration("""
				{
				"boot-java": {
					"validation": {
						"java": {
							"reconcilers": true
						},
						"spel": {
							"on": "ON"
						}
					}
				}
			}	
			""");
		
		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-annotations/").toURI());
		docUri = directory.toPath().resolve("src/main/java/org/test/TestValueCompletion.java").toUri().toASCIIString();

		problemCollector = new TestProblemCollector();
		reconcileEngine = new BootJavaReconcileEngine(projectFinder, new JavaReconciler[] {
				new JdtReconciler(compilationUnitCache, config, new JdtAstReconciler[] {
						jdtSpelReconciler
				}, new MockProjectObserver())
		});
	}
	
	@AfterEach
	public void closeDoc() throws Exception {
		TextDocumentIdentifier identifier = new TextDocumentIdentifier(docUri);
		DidCloseTextDocumentParams closeParams = new DidCloseTextDocumentParams(identifier);
		server.getTextDocumentService().didClose(closeParams);
		server.getAsync().waitForAll();
	}

    @Test
    void testNoSpelExpressionFound() throws Exception {
        TextDocument doc = prepareDocument("@Value(\"onField\")", "@Value(\"something\")");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(0, problems.size());
    }

    @Test
    void testCorrectSpelExpressionFound() throws Exception {
        TextDocument doc = prepareDocument("@Value(\"onField\")", "@Value(\"#{new String('hello world').toUpperCase()}\")");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(0, problems.size());
    }

    @Test
    void testCorrectSpelExpressionFoundWithParamName() throws Exception {
        TextDocument doc = prepareDocument("@Value(\"onField\")", "@Value(value=\"#{new String('hello world').toUpperCase()}\")");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(0, problems.size());
    }

    @Test
    void testIncorrectSpelExpressionFound() throws Exception {
        TextDocument doc = prepareDocument("@Value(\"onField\")", "@Value(\"#{new String('hello world).toUpperCase()}\")");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(1, problems.size());
    }

    @Test
    void testIncorrectSpelExpressionFoundWithParamName() throws Exception {
        TextDocument doc = prepareDocument("@Value(\"onField\")", "@Value(value=\"#{new String('hello world).toUpperCase()}\")");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(1, problems.size());
    }

    @Test
    void testIncorrectSpelExpressionFoundOnMethodParameter() throws Exception {
        TextDocument doc = prepareDocument("@Value(\"onParameter\")", "@Value(\"#{new String('hello world).toUpperCase()}\")");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(1, problems.size());
    }

    @Test
    void testIncorrectSpelExpressionFoundOnMethodParameterWithParamName() throws Exception {
        TextDocument doc = prepareDocument("@Value(\"onParameter\")", "@Value(value=\"#{new String('hello world).toUpperCase()}\")");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(1, problems.size());
    }

    @Test
    void testIncorrectSpelExpressionFoundOnSpelParamOfCachableAnnotation() throws Exception {
        TextDocument doc = prepareDocument("@Value(\"onField\")", "@Cacheable(condition=\"new String('hello world).toUpperCase()\")");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(1, problems.size());
    }

    @Test
    void testIncorrectSpelExpressionNotFoundOnNonSpelParamOfCachableAnnotation() throws Exception {
        TextDocument doc = prepareDocument("@Value(\"onField\")", "@Cacheable(keyGenerator=\"new String('hello world).toUpperCase()\")");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(0, problems.size());
    }

    @Test
    void testIncorrectSpelExpressionFoundOnSpelParamOfCachableAnnotationAmongOtherParams() throws Exception {
        TextDocument doc = prepareDocument("@Value(\"onField\")", "@Cacheable(keyGenerator=\"somekey\", condition=\"new String('hello world).toUpperCase()\")");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(1, problems.size());
    }

    @Test
    void testIncorrectSpelExpressionFoundOnMultipleSpelParamsOfCachableAnnotation() throws Exception {
        TextDocument doc = prepareDocument("@Value(\"onField\")", "@Cacheable(unless=\"new String('hello world).toUpperCase()\", condition=\"new String('hello world).toUpperCase()\")");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(2, problems.size());
    }

    @Test
    void testCorrectSpelExpressionFoundOnCustomAnnotation() throws Exception {
        TextDocument doc = prepareDocument("@Value(\"onMethod\")", "@CustomEventListener(condition=\"new String('hello world').toUpperCase()\")");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(0, problems.size());
    }

    @Test
    void testIncorrectSpelExpressionFoundOnCustomAnnotation() throws Exception {
        TextDocument doc = prepareDocument("@Value(\"onMethod\")", "@CustomEventListener(condition=\"new String('hello world).toUpperCase()\")");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(1, problems.size());
    }

    @Test
    void testSpelExpressionsWithPropertyPlaceholder_noErrors() throws Exception {
        TextDocument doc = prepareDocument("@Value(\"onField\")", "@Value(value=\"#{${property.hello:false}}\")");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(0, problems.size());
    }
	
    @Test
    void testSpelExpressionsWithPropertyPlaceholder_withErrors() throws Exception {
        TextDocument doc = prepareDocument("@Value(\"onField\")", "@Value(value=\"#{${property.}}\")");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(1, problems.size());
        
        ReconcileProblem problem = problems.get(0);
        String lookingFor = "#{${property.";
        int offset = doc.get().indexOf(lookingFor);
        assertEquals(offset + lookingFor.length(), problem.getOffset());
        assertEquals(0, problem.getLength());
        assertEquals(SpelProblemType.PROPERTY_PLACE_HOLDER_SYNTAX, problem.getType());
        assertTrue(problem.getMessage().startsWith("Place-Holder:"));
    }
    
	private TextDocument prepareDocument(String selectedAnnotation, String annotationStatementBeforeTest) throws Exception {
		String content = IOUtils.toString(new URI(docUri), StandardCharsets.UTF_8);

		TextDocumentItem docItem = new TextDocumentItem(docUri, LanguageId.JAVA.toString(), 0, content);
		DidOpenTextDocumentParams openParams = new DidOpenTextDocumentParams(docItem);
		server.getTextDocumentService().didOpen(openParams);
		server.getAsync().waitForAll();
		
		TextDocument doc = server.getTextDocumentService().getLatestSnapshot(docUri);
		
		int position = content.indexOf(selectedAnnotation);
		doc.replace(position, selectedAnnotation.length(), annotationStatementBeforeTest);
		
		return doc;
	}
	
	public static class TestProblemCollector implements IProblemCollector {
		
		private List<ReconcileProblem> problems = new ArrayList<>();

		@Override
		public void beginCollecting() {
		}

		@Override
		public void endCollecting() {
		}

		@Override
		public void accept(ReconcileProblem problem) {
			problems.add(problem);
		}
		
		public List<ReconcileProblem> getCollectedProblems() {
			return problems;
		}
		
	}

}
