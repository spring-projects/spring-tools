/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class SpringValidationBeanMethodNotPublicTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;

	private File directory;
	@Autowired private SpringSymbolIndex indexer;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		
		harness.changeConfiguration("{\"boot-java\": {\"validation\": {\"java\": { \"reconcilers\": true}}}}");

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-validations/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testFindPublicBeanMethodInConfigClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/BeanMethodNotPublic.java").toUri().toString();
        
        PublishDiagnosticsParams diagnosticsMessage = harness.getDiagnostics(docUri);
        List<Diagnostic> diagnostics = diagnosticsMessage.getDiagnostics();
        
        Diagnostic diagnostic = diagnostics.get(0);

        assertEquals(Boot2JavaProblemType.JAVA_PUBLIC_BEAN_METHOD.getCode(), diagnostic.getCode().getLeft());
        assertEquals(Boot2JavaProblemType.JAVA_PUBLIC_BEAN_METHOD.getLabel(), diagnostic.getMessage().getLeft());

        assertEquals(9, diagnostic.getRange().getStart().getLine());
        assertEquals(1, diagnostic.getRange().getStart().getCharacter());
        assertEquals(9, diagnostic.getRange().getEnd().getLine());
        assertEquals(7, diagnostic.getRange().getEnd().getCharacter());
        
        assertEquals(1, diagnostics.size());
    }

    @Test
    void testChangingSeverityOfFindPublicBeanMethodInConfigClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/BeanMethodNotPublic.java").toUri().toString();
        
        PublishDiagnosticsParams diagnosticsMessage = harness.getDiagnostics(docUri);
        List<Diagnostic> diagnostics = diagnosticsMessage.getDiagnostics();
        
        Diagnostic diagnostic = diagnostics.get(0);
        assertEquals(Boot2JavaProblemType.JAVA_PUBLIC_BEAN_METHOD.getCode(), diagnostic.getCode().getLeft());
        assertEquals(1, diagnostics.size());
        assertEquals(DiagnosticSeverity.Hint, diagnostic.getSeverity());
        
        harness.changeConfiguration("{\"spring-boot\": {\"ls\": {\"problem\": {\"boot2\": {\"JAVA_PUBLIC_BEAN_METHOD\": \"ERROR\"}}}}, \"boot-java\": {\"validation\": {\"java\": { \"reconcilers\": true}}}}");

        CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
		
        diagnosticsMessage = harness.getDiagnostics(docUri);
        diagnostics = diagnosticsMessage.getDiagnostics();
        
        diagnostic = diagnostics.get(0);
        assertEquals(Boot2JavaProblemType.JAVA_PUBLIC_BEAN_METHOD.getCode(), diagnostic.getCode().getLeft());
        assertEquals(1, diagnostics.size());
        assertEquals(DiagnosticSeverity.Error, diagnostic.getSeverity());
    }

    @Test
    void testPublishEmptyDiagnosticsWhenNoProblemsAreFound() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/BeanClass1.java").toUri().toString();
        
        PublishDiagnosticsParams diagnosticsMessage = harness.getDiagnostics(docUri);
        assertNotNull(diagnosticsMessage);
        
        List<Diagnostic> diagnostics = diagnosticsMessage.getDiagnostics();
        assertEquals(0, diagnostics.size());
    }
}
