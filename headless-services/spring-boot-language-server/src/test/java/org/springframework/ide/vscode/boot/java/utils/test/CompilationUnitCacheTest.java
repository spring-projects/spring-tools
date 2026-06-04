/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.BootLanguageServerInitializer;
import org.springframework.ide.vscode.boot.app.BootLanguageServerParams;
import org.springframework.ide.vscode.boot.bootiful.AdHocPropertyHarnessTestConf;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.editor.harness.PropertyIndexHarness;
import org.springframework.ide.vscode.boot.index.cache.IndexCache;
import org.springframework.ide.vscode.boot.index.cache.IndexCacheVoid;
import org.springframework.ide.vscode.boot.java.BootJavaLanguageServerComponents;
import org.springframework.ide.vscode.boot.java.annotations.AnnotationHierarchies;
import org.springframework.ide.vscode.boot.java.links.SourceLinkFactory;
import org.springframework.ide.vscode.boot.java.links.SourceLinks;
import org.springframework.ide.vscode.boot.java.utils.CompilationUnitCache;
import org.springframework.ide.vscode.boot.metadata.ValueProviderRegistry;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleTextDocumentService;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * CU Cache tests
 *
 * @author Alex Boyko
 *
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import({AdHocPropertyHarnessTestConf.class, CompilationUnitCacheTest.TestConf.class})
public class CompilationUnitCacheTest {

	ProjectsHarness projects = ProjectsHarness.INSTANCE;

	@Autowired
	private BootLanguageServerHarness harness;

	@Autowired
	private BootLanguageServerInitializer serverInit;

	@Autowired
	private MockProjectObserver projectObserver;

	@Configuration static class TestConf {

		@Bean IndexCache symbolCache() {
			return new IndexCacheVoid();
		}

		@Bean PropertyIndexHarness indexHarness(ValueProviderRegistry valueProviders) {
			return new PropertyIndexHarness(valueProviders);
		}

		@Bean JavaProjectFinder projectFinder(BootLanguageServerParams serverParams) {
			return serverParams.projectFinder;
		}

		@Bean MockProjectObserver projectObserver() {
			return new MockProjectObserver();
		}

		@Bean BootLanguageServerHarness harness(SimpleLanguageServer server, BootLanguageServerParams serverParams, PropertyIndexHarness indexHarness, JavaProjectFinder projectFinder) throws Exception {
			return new BootLanguageServerHarness(server, serverParams, indexHarness, projectFinder, LanguageId.JAVA, ".java");
		}

		@Bean BootLanguageServerParams serverParams(SimpleLanguageServer server, MockProjectObserver projectObserver, ValueProviderRegistry valueProviders, PropertyIndexHarness indexHarness) {
			BootLanguageServerParams testDefaults = BootLanguageServerHarness.createTestDefault(server, valueProviders);
			return new BootLanguageServerParams(
					indexHarness.getProjectFinder(),
					projectObserver,
					indexHarness.getIndexProvider(),
					testDefaults.typeUtilProvider
			);
		}

		@Bean SourceLinks sourceLinks(SimpleTextDocumentService documents, CompilationUnitCache cuCache) {
			return SourceLinkFactory.NO_SOURCE_LINKS;
		}

	}

    @Test
    void cu_cached() throws Exception {
        harness.useProject(ProjectsHarness.dummyProject());
        harness.intialize(null);

        TextDocument doc = new TextDocument(harness.createTempUri(null), LanguageId.JAVA, 0, "package my.package\n" +
                "\n" +
                "public class SomeClass {\n" +
                "\n" +
                "}\n");
        CompilationUnit cu = getCompilationUnit(doc);
        assertNotNull(cu);

        CompilationUnit cuAnother = getCompilationUnit(doc);
        assertTrue(cu == cuAnother);
    }

    @Test
    void cu_not_generated_without_project() throws Exception {
        harness.intialize(null);

        TextDocument doc = new TextDocument(harness.createTempUri(null), LanguageId.JAVA, 0, "package my.package\n" +
                "\n" +
                "public class SomeClass {\n" +
                "\n" +
                "}\n");
        CompilationUnit cu = getCompilationUnit(doc);
        assertNull(cu);
    }

	private CompilationUnit getCompilationUnit(TextDocument doc) {
		harness.getServer().getAsync().waitForAll();
		return serverInit.getComponents().get(BootJavaLanguageServerComponents.class).getCompilationUnitCache().withCompilationUnit(doc, cu -> cu);
	}

    @Test
    void cu_cache_invalidated_by_doc_change() throws Exception {
        harness.useProject(ProjectsHarness.dummyProject());
        harness.intialize(null);

        TextDocument doc = new TextDocument(harness.createTempUri(null), LanguageId.JAVA, 0, "package my.package\n" +
                "\n" +
                "public class SomeClass {\n" +
                "\n" +
                "}\n");

        harness.newEditorFromFileUri(doc.getUri(), doc.getLanguageId());
        CompilationUnit cu = getCompilationUnit(doc);
        assertNotNull(cu);

        harness.changeDocument(doc.getUri(), 0, 0, "     ");
        CompilationUnit cuAnother = getCompilationUnit(doc);
        assertNotNull(cuAnother);
        assertNotNull(cuAnother);

        CompilationUnit cuYetAnother = getCompilationUnit(doc);
        assertTrue(cuAnother == cuYetAnother);
    }

    @Test
    void cu_cache_invalidated_by_doc_close() throws Exception {
        harness.useProject(ProjectsHarness.dummyProject());
        harness.intialize(null);

        TextDocument doc = new TextDocument(harness.createTempUri(null), LanguageId.JAVA, 0, "package my.package\n" +
                "\n" +
                "public class SomeClass {\n" +
                "\n" +
                "}\n");

        harness.newEditorFromFileUri(doc.getUri(), doc.getLanguageId());
        CompilationUnit cu = getCompilationUnit(doc);
        assertNotNull(cu);

        harness.closeDocument(doc.getId());
        CompilationUnit cuAnother = getCompilationUnit(doc);
        assertNotNull(cuAnother);
        assertNotNull(cuAnother);

        CompilationUnit cuYetAnother = getCompilationUnit(doc);
        assertTrue(cuAnother == cuYetAnother);
    }

    @Test
    void cu_cache_invalidated_by_project_change() throws Exception {
        File directory = new File(
                ProjectsHarness.class.getResource("/test-projects/test-request-mapping-live-hover/").toURI());
        String docUri = directory.toPath().resolve("src/main/java/example/HelloWorldController.java").toUri().toString();
        MavenJavaProject project = projects.mavenProject("test-request-mapping-live-hover");
        harness.useProject(project);
        harness.intialize(directory);

        URI fileUri = new URI(docUri);
        Path path = Paths.get(fileUri);
        String content = new String(Files.readAllBytes(path));

        TextDocument document = new TextDocument(docUri, LanguageId.JAVA, 0, content);

        CompilationUnit cu = getCompilationUnit(document);
        assertNotNull(cu);
        CompilationUnit cuAnother = getCompilationUnit(document);
        assertTrue(cu == cuAnother);

        projectObserver.doWithListeners(l -> l.changed(project, false));
        cuAnother = getCompilationUnit(document);
        assertNotNull(cuAnother);
        assertNotNull(cuAnother);
    }

    @Test
    void cu_cache_invalidated_by_project_deletion() throws Exception {
        File directory = new File(
                ProjectsHarness.class.getResource("/test-projects/test-request-mapping-live-hover/").toURI());
        String docUri = directory.toPath().resolve("src/main/java/example/HelloWorldController.java").toUri().toString();
        MavenJavaProject project = projects.mavenProject("test-request-mapping-live-hover");
        harness.useProject(project);
        harness.intialize(directory);

        URI fileUri = new URI(docUri);
        Path path = Paths.get(fileUri);
        String content = new String(Files.readAllBytes(path));

        TextDocument document = new TextDocument(docUri, LanguageId.JAVA, 0, content);

        CompilationUnit cu = getCompilationUnit(document);
        assertNotNull(cu);
        CompilationUnit cuAnother = getCompilationUnit(document);
        assertTrue(cu == cuAnother);

        projectObserver.doWithListeners(l -> l.deleted(project));
        cuAnother = getCompilationUnit(document);
        assertNotNull(cuAnother);
        assertNotNull(cuAnother);
    }
    
    @Test
    void annotation_hierarchies_unchanged_when_doc_changed() throws Exception {
        harness.useProject(ProjectsHarness.dummyProject());
        harness.intialize(null);

        TextDocument doc = new TextDocument(harness.createTempUri(null), LanguageId.JAVA, 0, "package my.package\n" +
                "\n" +
                "public class SomeClass {\n" +
                "\n" +
                "}\n");

        harness.newEditorFromFileUri(doc.getUri(), doc.getLanguageId());
        CompilationUnit cu = getCompilationUnit(doc);
        assertNotNull(cu);
        AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);

        harness.changeDocument(doc.getUri(), 0, 0, "     ");
        CompilationUnit cuAnother = getCompilationUnit(doc);
        assertNotNull(cuAnother);
        AnnotationHierarchies anotherAnnotationHierarchies = AnnotationHierarchies.get(cuAnother);
        assertTrue(anotherAnnotationHierarchies == annotationHierarchies);

        CompilationUnit cuYetAnother = getCompilationUnit(doc);
        AnnotationHierarchies yetAnotherAnnotationHierarchies = AnnotationHierarchies.get(cuYetAnother);
        assertTrue(anotherAnnotationHierarchies == yetAnotherAnnotationHierarchies);
    }
    
    @Test
    void annotation_hierarchies_reset_by_project_change() throws Exception {
        File directory = new File(
                ProjectsHarness.class.getResource("/test-projects/test-request-mapping-live-hover/").toURI());
        String docUri = directory.toPath().resolve("src/main/java/example/HelloWorldController.java").toUri().toString();
        MavenJavaProject project = projects.mavenProject("test-request-mapping-live-hover");
        harness.useProject(project);
        harness.intialize(directory);

        URI fileUri = new URI(docUri);
        Path path = Paths.get(fileUri);
        String content = new String(Files.readAllBytes(path));

        TextDocument document = new TextDocument(docUri, LanguageId.JAVA, 0, content);

        CompilationUnit cu = getCompilationUnit(document);
        assertNotNull(cu);
        AnnotationHierarchies annotationHierarchies = AnnotationHierarchies.get(cu);
        
        projectObserver.doWithListeners(l -> l.changed(project, false));
        CompilationUnit cuAnother = getCompilationUnit(document);
        AnnotationHierarchies anotherAnnotationHierarchies = AnnotationHierarchies.get(cuAnother);
        assertNotNull(cuAnother);
        assertTrue(anotherAnnotationHierarchies != annotationHierarchies);
    }


}
