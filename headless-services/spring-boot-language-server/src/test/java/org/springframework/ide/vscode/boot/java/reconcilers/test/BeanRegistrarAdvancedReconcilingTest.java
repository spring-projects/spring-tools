/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.java.Boot4JavaProblemType;
import org.springframework.ide.vscode.boot.java.utils.test.TestFileScanListener;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.UriUtil;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class BeanRegistrarAdvancedReconcilingTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-framework-7-indexing/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testNoErrorOnRegisteredRegistrar() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/com/example/MyBeanRegistrar.java").toUri().toString();
        PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(docUri);
        List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();
        
        assertEquals(0, diagnostics.size());
    }
    
    @Test
    void testErrorOnNonRegisteredRegistrar() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/com/example/NotRegisteredBeanRegistrar.java").toUri().toString();
        PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(docUri);
        List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();
        
        assertEquals(1, diagnostics.size());
        assertEquals(Boot4JavaProblemType.REGISTRAR_BEAN_DECLARATION.getCode(), diagnostics.get(0).getCode().getLeft());
    }
    
    @Test
    void testBeanRegistrarGetsReconciledWhenConfigChangesAddImportCase() throws Exception {
        String configDoc = directory.toPath().resolve("src/main/java/com/example/ConfigImportsBeanRegistrar.java").toUri().toString();
        String registeredRegistrarDocUri = directory.toPath().resolve("src/main/java/com/example/MyBeanRegistrar.java").toUri().toString();
        String nonRegisteredRegistrarDocUri = directory.toPath().resolve("src/main/java/com/example/NotRegisteredBeanRegistrar.java").toUri().toString();

        // bean index pre-check
        Bean[] beans = springIndex.getBeansOfDocument(registeredRegistrarDocUri);
        assertEquals(4, beans.length);

        // now change the config class source code and update doc
        TestFileScanListener fileScanListener = new TestFileScanListener();
        indexer.getJavaIndexer().setFileScanListener(fileScanListener);

        String configClassSource = FileUtils.readFileToString(UriUtil.toFile(configDoc), Charset.defaultCharset());
        String updatedConfigClassSource = configClassSource.replace("@Import(MyBeanRegistrar.class)", "@Import({MyBeanRegistrar.class, NotRegisteredBeanRegistrar.class})");

        CompletableFuture<Void> updateFuture = indexer.updateDocument(configDoc, updatedConfigClassSource, "test triggered");
        updateFuture.get(5, TimeUnit.SECONDS);

        // check if the bean registrar files have been re-scanned
        fileScanListener.assertScannedUri(configDoc, 1);
        fileScanListener.assertScannedUri(registeredRegistrarDocUri, 1);
        fileScanListener.assertScannedUri(nonRegisteredRegistrarDocUri, 1);
        fileScanListener.assertFileScanCount(3);
        
        // check diagnostics result
        PublishDiagnosticsParams diagnosticsResultForRegisteredRegistrar = harness.getDiagnostics(registeredRegistrarDocUri);
        List<Diagnostic> diagnosticsForAlreadyRegisteredRegistrar = diagnosticsResultForRegisteredRegistrar.getDiagnostics();
        assertEquals(0, diagnosticsForAlreadyRegisteredRegistrar.size());

        PublishDiagnosticsParams diagnosticsResultForNewlyRegisteredRegistrar = harness.getDiagnostics(nonRegisteredRegistrarDocUri);
        List<Diagnostic> diagnosticsForPreviouslyNotRegisteredRegistrar = diagnosticsResultForNewlyRegisteredRegistrar.getDiagnostics();
        assertEquals(0, diagnosticsForPreviouslyNotRegisteredRegistrar.size());

        // check if the beans are still in place correctly
        Bean[] beansAfterUpdate = springIndex.getBeansOfDocument(registeredRegistrarDocUri);
        assertEquals(4, beansAfterUpdate.length);
    }
    
    @Test
    void testBeanRegistrarGetsReconciledWhenConfigChangesRemoveImportCase() throws Exception {
        String configDoc = directory.toPath().resolve("src/main/java/com/example/ConfigImportsBeanRegistrar.java").toUri().toString();
        String registeredRegistrarDocUri = directory.toPath().resolve("src/main/java/com/example/MyBeanRegistrar.java").toUri().toString();

        TestFileScanListener fileScanListener = new TestFileScanListener();
        indexer.getJavaIndexer().setFileScanListener(fileScanListener);

        String configClassSource = FileUtils.readFileToString(UriUtil.toFile(configDoc), Charset.defaultCharset());
        String updatedConfigClassSource = configClassSource.replace("@Import(MyBeanRegistrar.class)", "");

        CompletableFuture<Void> updateFuture = indexer.updateDocument(configDoc, updatedConfigClassSource, "test triggered");
        updateFuture.get(5, TimeUnit.SECONDS);

        fileScanListener.assertScannedUri(configDoc, 1);
        fileScanListener.assertScannedUri(registeredRegistrarDocUri, 1);
        fileScanListener.assertFileScanCount(2);
        
        PublishDiagnosticsParams diagnosticsResultForPreviouslyRegisteredRegistrar = harness.getDiagnostics(registeredRegistrarDocUri);
        List<Diagnostic> diagnosticsForPreviouslyRegisteredRegistrar = diagnosticsResultForPreviouslyRegisteredRegistrar.getDiagnostics();
        assertEquals(1, diagnosticsForPreviouslyRegisteredRegistrar.size());
        assertEquals(Boot4JavaProblemType.REGISTRAR_BEAN_DECLARATION.getCode(), diagnosticsForPreviouslyRegisteredRegistrar.get(0).getCode().getLeft());
    }
    
}
