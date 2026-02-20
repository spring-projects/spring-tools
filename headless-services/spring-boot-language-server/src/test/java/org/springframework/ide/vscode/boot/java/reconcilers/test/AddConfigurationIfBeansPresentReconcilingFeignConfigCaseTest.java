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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
import org.springframework.ide.vscode.boot.java.utils.test.TestFileScanListener;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.UriUtil;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.CodeAction;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class AddConfigurationIfBeansPresentReconcilingFeignConfigCaseTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-feign-indexing/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
	@Test
	void testNoErrorOnReferencedFeignConfigClass() throws Exception {
		String docUri = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignConfigExample.java").toUri().toString();

		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(docUri);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();

		assertEquals(0, diagnostics.size());
	}
    
	@Test
	void testErrorOnNotReferencedFeignConfigClass() throws Exception {
		String docUri = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignConfigNotRegistered.java").toUri().toString();

		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(docUri);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();

		assertEquals(1, diagnostics.size());
		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION.getCode(), diagnostics.get(0).getCode().getLeft());
	}
    
    @Test
    void testErrorGoesAwayWhenFeignClientMentionsFeignConfig() throws Exception {
		String feignClientDocUri = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignClientExample.java").toUri().toString();
		String feignConfigRegisterd = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignConfigExample.java").toUri().toString();
		String feignConfigNotRegisterd = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignConfigNotRegistered.java").toUri().toString();

        // now change the config class source code and update doc
        TestFileScanListener fileScanListener = new TestFileScanListener();
        indexer.getJavaIndexer().setFileScanListener(fileScanListener);

        String feignClientSource = FileUtils.readFileToString(UriUtil.toFile(feignClientDocUri), Charset.defaultCharset());
        String updatedFeignClientSource = feignClientSource.replace("@FeignClient(name = \"stores\", configuration = FeignConfigExample.class)",
        		"@FeignClient(name = \"stores\", configuration = {FeignConfigNotRegistered.class, FeignConfigExample.class})");

        CompletableFuture<Void> updateFuture = indexer.updateDocument(feignClientDocUri, updatedFeignClientSource, "test triggered");
        updateFuture.get(5, TimeUnit.SECONDS);

        fileScanListener.assertScannedUri(feignClientDocUri, 1);
        fileScanListener.assertScannedUri(feignConfigNotRegisterd, 1);
        fileScanListener.assertScannedUri(feignConfigRegisterd, 1);
        fileScanListener.assertFileScanCount(3);
        
        // check diagnostics result
		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(feignConfigNotRegisterd);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();
		assertEquals(0, diagnostics.size());
    }
    
    @Test
    void testErrorAppearsWhenFeignClientNotMentionsFeignConfigAnymore() throws Exception {
		String feignClientDocUri = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignClientExample.java").toUri().toString();
		String feignConfigRegisterd = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignConfigExample.java").toUri().toString();

        // now change the config class source code and update doc
        TestFileScanListener fileScanListener = new TestFileScanListener();
        indexer.getJavaIndexer().setFileScanListener(fileScanListener);

        String feignClientSource = FileUtils.readFileToString(UriUtil.toFile(feignClientDocUri), Charset.defaultCharset());
        String updatedFeignClientSource = feignClientSource.replace("@FeignClient(name = \"stores\", configuration = FeignConfigExample.class)",
        		"@FeignClient(name = \"stores\")");

        CompletableFuture<Void> updateFuture = indexer.updateDocument(feignClientDocUri, updatedFeignClientSource, "test triggered");
        updateFuture.get(5, TimeUnit.SECONDS);

        fileScanListener.assertScannedUri(feignClientDocUri, 1);
        fileScanListener.assertScannedUri(feignConfigRegisterd, 1);
        fileScanListener.assertFileScanCount(2);
        
        // check diagnostics result
		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(feignConfigRegisterd);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();
		assertEquals(1, diagnostics.size());
		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION.getCode(), diagnostics.get(0).getCode().getLeft());
    }
    
    @Test
    void testQuickFixAddToFeignClientConfigurationOffered() throws Exception {
		String docUri = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignConfigNotRegistered.java").toUri().toString();

		Editor editor = harness.newEditorFromFileUri(docUri, LanguageId.JAVA);
		Diagnostic problem = editor.assertProblem("FeignConfigNotRegistered");
		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION.getCode(), problem.getCode().getLeft());

		List<CodeAction> codeActions = editor.getCodeActions(problem);
		assertTrue(codeActions.size() >= 3);
		assertTrue(codeActions.stream().anyMatch(ca -> ca.getLabel().contains("@FeignClient")));
    }

    @Test
    void testApplyQuickFixAddToFeignClientConfiguration() throws Exception {
		String docUri = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignConfigNotRegistered.java").toUri().toString();

		Editor editor = harness.newEditorFromFileUri(docUri, LanguageId.JAVA);
		Diagnostic problem = editor.assertProblem("FeignConfigNotRegistered");
		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION.getCode(), problem.getCode().getLeft());

		List<CodeAction> codeActions = editor.getCodeActions(problem);
		CodeAction addToFeignClientFix = codeActions.stream()
				.filter(ca -> ca.getLabel().contains("@FeignClient"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Should have '@FeignClient' quickfix"));

		Path targetFile = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignClientExample.java");
		String originalContent = Files.readString(targetFile);
		try {
			addToFeignClientFix.perform();

			assertEquals("""
					package com.example.feign.demo;
					
					import java.util.List;
					
					import org.springframework.cloud.openfeign.FeignClient;
					import org.springframework.web.bind.annotation.RequestMapping;
					import org.springframework.web.bind.annotation.RequestMethod;
					
					@FeignClient(name = "stores", configuration = {FeignConfigExample.class, FeignConfigNotRegistered.class})
					public interface FeignClientExample {
					
					\t@RequestMapping(method = RequestMethod.GET, value = "/stores")
					\tList<String> getStores();
					
					}
					""", Files.readString(targetFile));
		} finally {
			Files.writeString(targetFile, originalContent);
		}
    }

    @Test
    void testErrorAppearsWhenFeignClientAnnotationGoesAwayEntirely() throws Exception {
		String feignClientDocUri = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignClientExample.java").toUri().toString();
		String feignConfigRegisterd = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignConfigExample.java").toUri().toString();

        // now change the config class source code and update doc
        TestFileScanListener fileScanListener = new TestFileScanListener();
        indexer.getJavaIndexer().setFileScanListener(fileScanListener);

        String feignClientSource = FileUtils.readFileToString(UriUtil.toFile(feignClientDocUri), Charset.defaultCharset());
        String updatedFeignClientSource = feignClientSource.replace("@FeignClient(name = \"stores\", configuration = FeignConfigExample.class)",
        		"");

        CompletableFuture<Void> updateFuture = indexer.updateDocument(feignClientDocUri, updatedFeignClientSource, "test triggered");
        updateFuture.get(5, TimeUnit.SECONDS);

        fileScanListener.assertScannedUri(feignClientDocUri, 1);
        fileScanListener.assertScannedUri(feignConfigRegisterd, 1);
        fileScanListener.assertFileScanCount(2);
        
        // check diagnostics result
		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(feignConfigRegisterd);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();
		assertEquals(1, diagnostics.size());
		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION.getCode(), diagnostics.get(0).getCode().getLeft());
    }
    
}
