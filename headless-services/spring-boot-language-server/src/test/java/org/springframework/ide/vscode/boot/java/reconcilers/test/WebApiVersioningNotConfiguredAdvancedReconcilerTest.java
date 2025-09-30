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
import java.nio.charset.Charset;
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

    
//	@Test
//	void testErrorOnNotReferencedFeignConfigClass() throws Exception {
//		String docUri = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignConfigNotRegistered.java").toUri().toString();
//
//		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(docUri);
//		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();
//
//		assertEquals(1, diagnostics.size());
//		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION.getCode(), diagnostics.get(0).getCode().getLeft());
//	}
//    
//    @Test
//    void testErrorGoesAwayWhenFeignClientMentionsFeignConfig() throws Exception {
//		String feignClientDocUri = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignClientExample.java").toUri().toString();
//		String feignConfigRegisterd = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignConfigExample.java").toUri().toString();
//		String feignConfigNotRegisterd = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignConfigNotRegistered.java").toUri().toString();
//
//        // now change the config class source code and update doc
//        TestFileScanListener fileScanListener = new TestFileScanListener();
//        indexer.getJavaIndexer().setFileScanListener(fileScanListener);
//
//        String feignClientSource = FileUtils.readFileToString(UriUtil.toFile(feignClientDocUri), Charset.defaultCharset());
//        String updatedFeignClientSource = feignClientSource.replace("@FeignClient(name = \"stores\", configuration = FeignConfigExample.class)",
//        		"@FeignClient(name = \"stores\", configuration = {FeignConfigNotRegistered.class, FeignConfigExample.class})");
//
//        CompletableFuture<Void> updateFuture = indexer.updateDocument(feignClientDocUri, updatedFeignClientSource, "test triggered");
//        updateFuture.get(5, TimeUnit.SECONDS);
//
//        // check if the bean registrar files have been re-scanned
//        fileScanListener.assertScannedUri(feignClientDocUri, 1);
//        fileScanListener.assertScannedUri(feignConfigNotRegisterd, 1);
//        fileScanListener.assertScannedUri(feignConfigRegisterd, 1);
//        fileScanListener.assertFileScanCount(3);
//        
//        // check diagnostics result
//		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(feignConfigNotRegisterd);
//		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();
//		assertEquals(0, diagnostics.size());
//    }
//    
//    @Test
//    void testErrorAppearsWhenFeignClientNotMentionsFeignConfigAnymore() throws Exception {
//		String feignClientDocUri = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignClientExample.java").toUri().toString();
//		String feignConfigRegisterd = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignConfigExample.java").toUri().toString();
//
//        // now change the config class source code and update doc
//        TestFileScanListener fileScanListener = new TestFileScanListener();
//        indexer.getJavaIndexer().setFileScanListener(fileScanListener);
//
//        String feignClientSource = FileUtils.readFileToString(UriUtil.toFile(feignClientDocUri), Charset.defaultCharset());
//        String updatedFeignClientSource = feignClientSource.replace("@FeignClient(name = \"stores\", configuration = FeignConfigExample.class)",
//        		"@FeignClient(name = \"stores\")");
//
//        CompletableFuture<Void> updateFuture = indexer.updateDocument(feignClientDocUri, updatedFeignClientSource, "test triggered");
//        updateFuture.get(5, TimeUnit.SECONDS);
//
//        // check if the bean registrar files have been re-scanned
//        fileScanListener.assertScannedUri(feignClientDocUri, 1);
//        fileScanListener.assertScannedUri(feignConfigRegisterd, 1);
//        fileScanListener.assertFileScanCount(2);
//        
//        // check diagnostics result
//		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(feignConfigRegisterd);
//		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();
//		assertEquals(1, diagnostics.size());
//		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION.getCode(), diagnostics.get(0).getCode().getLeft());
//    }
//    
//    @Test
//    void testErrorAppearsWhenFeignClientAnnotationDoesAwayEntirely() throws Exception {
//		String feignClientDocUri = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignClientExample.java").toUri().toString();
//		String feignConfigRegisterd = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignConfigExample.java").toUri().toString();
//
//        // now change the config class source code and update doc
//        TestFileScanListener fileScanListener = new TestFileScanListener();
//        indexer.getJavaIndexer().setFileScanListener(fileScanListener);
//
//        String feignClientSource = FileUtils.readFileToString(UriUtil.toFile(feignClientDocUri), Charset.defaultCharset());
//        String updatedFeignClientSource = feignClientSource.replace("@FeignClient(name = \"stores\", configuration = FeignConfigExample.class)",
//        		"");
//
//        CompletableFuture<Void> updateFuture = indexer.updateDocument(feignClientDocUri, updatedFeignClientSource, "test triggered");
//        updateFuture.get(5, TimeUnit.SECONDS);
//
//        // check if the bean registrar files have been re-scanned
//        fileScanListener.assertScannedUri(feignClientDocUri, 1);
//        fileScanListener.assertScannedUri(feignConfigRegisterd, 1);
//        fileScanListener.assertFileScanCount(2);
//        
//        // check diagnostics result
//		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(feignConfigRegisterd);
//		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();
//		assertEquals(1, diagnostics.size());
//		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION.getCode(), diagnostics.get(0).getCode().getLeft());
//    }
//    
}
