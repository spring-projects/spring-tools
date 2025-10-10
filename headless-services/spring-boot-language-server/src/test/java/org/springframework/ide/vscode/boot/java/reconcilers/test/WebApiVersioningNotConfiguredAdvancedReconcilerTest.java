/*******************************************************************************
 * Copyright (c) 2025 Broadcom
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
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Diagnostic;
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
import org.springframework.ide.vscode.boot.java.Boot4JavaProblemType;
import org.springframework.ide.vscode.boot.java.utils.test.TestFileScanListener;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
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
public class WebApiVersioningNotConfiguredAdvancedReconcilerTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/sf7-validation/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

	@Test
	void testNoErrorOnControllerClass() throws Exception {
		String docUri = directory.toPath().resolve("src/main/java/com/example/demo/apiversioning/TestController.java").toUri().toString();

		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(docUri);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();

		assertEquals(0, diagnostics.size());
	}
	
	@Test
	void testErrorAppearsWhenWebConfigClassRemovesVersioningConfig() throws Exception {
		String webConfigUri = directory.toPath().resolve("src/main/java/com/example/demo/apiversioning/WebConfig.java").toUri().toString();
		String controllerUri = directory.toPath().resolve("src/main/java/com/example/demo/apiversioning/TestController.java").toUri().toString();

		TestFileScanListener fileScanListener = new TestFileScanListener();
		indexer.getJavaIndexer().setFileScanListener(fileScanListener);

		// now change the web config class source code and update doc

		String webConfigSource = FileUtils.readFileToString(UriUtil.toFile(webConfigUri), Charset.defaultCharset());
		String updatedWebConfigSource = webConfigSource.replace("configurer.useRequestHeader(\"X-API-Version\");",
				"");

		CompletableFuture<Void> updateFuture = indexer.updateDocument(webConfigUri, updatedWebConfigSource, "test triggered");
		updateFuture.get(5, TimeUnit.SECONDS);

		// check if the controller has been re-scanned
		fileScanListener.assertScannedUri(webConfigUri, 1);
		fileScanListener.assertScannedUri(controllerUri, 1);
		fileScanListener.assertFileScanCount(2);

		// check diagnostics result
		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(controllerUri);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();
		assertEquals(1, diagnostics.size());
		assertEquals(Boot4JavaProblemType.API_VERSIONING_NOT_CONFIGURED.getCode(), diagnostics.get(0).getCode().getLeft());
	}

	@Test
	void testErrorDisappearsAgainWhenWebConfigClassRemovesAndAddsVersioningConfig() throws Exception {
		String webConfigUri = directory.toPath().resolve("src/main/java/com/example/demo/apiversioning/WebConfig.java").toUri().toString();
		String controllerUri = directory.toPath().resolve("src/main/java/com/example/demo/apiversioning/TestController.java").toUri().toString();

		TestFileScanListener fileScanListener = new TestFileScanListener();
		indexer.getJavaIndexer().setFileScanListener(fileScanListener);

		// now remove the versioning config change the web config class source code and update doc

		String webConfigSource = FileUtils.readFileToString(UriUtil.toFile(webConfigUri), Charset.defaultCharset());
		String updatedWebConfigSource = webConfigSource.replace("configurer.useRequestHeader(\"X-API-Version\");",
				";;");

		CompletableFuture<Void> removeConfigFuture = indexer.updateDocument(webConfigUri, updatedWebConfigSource, "test triggered 1");
		removeConfigFuture.get(5, TimeUnit.SECONDS);

		CompletableFuture<Void> addedAgainConfigFuture = indexer.updateDocument(webConfigUri, webConfigSource, "test triggered 2");
		addedAgainConfigFuture.get(5, TimeUnit.SECONDS);

		// check if the controller has been re-scanned twice
		fileScanListener.assertScannedUri(webConfigUri, 2);
		fileScanListener.assertScannedUri(controllerUri, 2);
		fileScanListener.assertFileScanCount(4);

		// check diagnostics result
		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(controllerUri);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();

		assertEquals(0, diagnostics.size());
	}
	
	@Test
	void testControllerReconciledOnlyOnce() throws Exception {
		String webConfigUri = directory.toPath().resolve("src/main/java/com/example/demo/apiversioning/WebConfig.java").toUri().toString();
		String controllerUri = directory.toPath().resolve("src/main/java/com/example/demo/apiversioning/TestController.java").toUri().toString();

		TestFileScanListener fileScanListener = new TestFileScanListener();
		indexer.getJavaIndexer().setFileScanListener(fileScanListener);

		CompletableFuture<Void> updateFuture = indexer.updateDocuments(new String[] {controllerUri, webConfigUri}, "test triggered");
		updateFuture.get(5, TimeUnit.SECONDS);

		// check if the controller has been re-scanned
		fileScanListener.assertScannedUri(webConfigUri, 1);
		fileScanListener.assertScannedUri(controllerUri, 1);
		fileScanListener.assertFileScanCount(2);
	}
	
	@Test
	void testValidationRunsOnPropertyChanges() throws Exception {
		String webConfigUri = directory.toPath().resolve("src/main/java/com/example/demo/apiversioning/WebConfig.java").toUri().toString();
		String controllerUri = directory.toPath().resolve("src/main/java/com/example/demo/apiversioning/TestController.java").toUri().toString();
		String applicationPropertiesUri = directory.toPath().resolve("src/main/resources/application.properties").toUri().toString();

		// now change the web config class source code and update doc

		String webConfigSource = FileUtils.readFileToString(UriUtil.toFile(webConfigUri), Charset.defaultCharset());
		String updatedWebConfigSource = webConfigSource.replace("configurer.useRequestHeader(\"X-API-Version\");",
				"");

		CompletableFuture<Void> updateFuture = indexer.updateDocument(webConfigUri, updatedWebConfigSource, "test triggered");
		updateFuture.get(5, TimeUnit.SECONDS);
		
		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(controllerUri);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();
		assertEquals(1, diagnostics.size());

		//
		// next step is to update the properties file on disc
		//
		
        File propFile = new File(new URI(applicationPropertiesUri));
        String originalContent = FileUtils.readFileToString(propFile, Charset.defaultCharset());
        FileTime modifiedTime = Files.getLastModifiedTime(propFile.toPath());

        try {
            String newContent = originalContent.replace("spring.application.name=sf7-validation",
            		"""
            		spring.application.name=sf7-validation
            		spring.mvc.apiversion.use.header=X-API-Version
            		""");
            FileUtils.writeStringToFile(new File(new URI(applicationPropertiesUri)), newContent, Charset.defaultCharset());
            Files.setLastModifiedTime(propFile.toPath(), FileTime.fromMillis(modifiedTime.toMillis() + 1000));

            TestFileScanListener fileScanListener = new TestFileScanListener();
            indexer.getJavaIndexer().setFileScanListener(fileScanListener);

            CompletableFuture<Void> updatePropFileFuture = indexer.updateDocuments(new String[] {applicationPropertiesUri}, "test triggered");
            updatePropFileFuture.get(5, TimeUnit.SECONDS);

    		// check diagnostics result
    		diagnosticsResult = harness.getDiagnostics(controllerUri);
    		diagnostics = diagnosticsResult.getDiagnostics();
    		assertEquals(0, diagnostics.size());
        }
        finally {
            FileUtils.writeStringToFile(new File(new URI(applicationPropertiesUri)), originalContent, Charset.defaultCharset());
        }

	}
    
}
