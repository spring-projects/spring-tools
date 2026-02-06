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
package org.springframework.ide.vscode.boot.xml.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.BootLanguageServerBootApp;
import org.springframework.ide.vscode.boot.bootiful.XmlBeansTestConf;
import org.springframework.ide.vscode.boot.java.spel.SpelReconciler;
import org.springframework.ide.vscode.boot.xml.SpringXMLReconcileEngine;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.reconcile.IProblemCollector;
import org.springframework.ide.vscode.commons.languageserver.reconcile.ReconcileProblem;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.languageserver.starter.LanguageServerAutoConf;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

/**
 * @author Martin Lippert
 */
@OverrideAutoConfiguration(enabled=false)
@Import({LanguageServerAutoConf.class, XmlBeansTestConf.class})
@SpringBootTest(classes={
		BootLanguageServerBootApp.class
})
@DirtiesContext(classMode=ClassMode.AFTER_EACH_TEST_METHOD)
public class XMLSpelExpressionValidationTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SimpleLanguageServer server;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private BootJavaConfig config;
	@Autowired private SpelReconciler spelReconciler;
	
	private ProjectsHarness projects = ProjectsHarness.INSTANCE;
	private MavenJavaProject project;
	private SpringXMLReconcileEngine reconcileEngine;
	private File directory;
	private String docUri;
	private TestProblemCollector problemCollector;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		
		Map<String, Object> supportXML = new HashMap<>();
		supportXML.put("on", true);
		supportXML.put("hyperlinks", true);
		supportXML.put("scan-folders", "/src/main/");
		Map<String, Object> bootJavaObj = new HashMap<>();
		bootJavaObj.put("support-spring-xml-config", supportXML);
		Map<String, Object> settings = new HashMap<>();
		settings.put("boot-java", bootJavaObj);
		
		harness.changeConfiguration(settings);

		project = projects.mavenProject("test-xml-validations");
		harness.useProject(project);
		
		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-xml-validations/").toURI());
		docUri = directory.toPath().resolve("src/main/webapp/WEB-INF/spring/root-context.xml").toUri().toASCIIString();

		problemCollector = new TestProblemCollector();
		reconcileEngine = new SpringXMLReconcileEngine(projectFinder, config, spelReconciler);
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
        TextDocument doc = prepareDocument("<SpEL>", "something");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(0, problems.size());
    }

    @Test
    void testCorrectSpelExpressionFound() throws Exception {
        TextDocument doc = prepareDocument("<SpEL>", "#{new String('hello world').toUpperCase()}");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(0, problems.size());
    }

    @Test
    void testIncorrectSpelExpressionFound() throws Exception {
        TextDocument doc = prepareDocument("<SpEL>", "#{new String('hello world).toUpperCase()}");
        assertNotNull(doc);

        reconcileEngine.reconcile(doc, problemCollector);

        List<ReconcileProblem> problems = problemCollector.getCollectedProblems();
        assertEquals(1, problems.size());
    }
		
	private TextDocument prepareDocument(String selectedAnnotation, String annotationStatementBeforeTest) throws Exception {
		String content = IOUtils.toString(new URI(docUri), StandardCharsets.UTF_8);

		TextDocumentItem docItem = new TextDocumentItem(docUri, LanguageId.XML.toString(), 0, content);
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
		
		protected List<ReconcileProblem> getCollectedProblems() {
			return problems;
		}
		
	}

}
