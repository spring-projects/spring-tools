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
public class AddConfigurationIfBeansPresentReconcilingLoadBalancerConfigCaseTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-cloud-loadbalancer/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
	@Test
	void testNoErrorOnReferencedLoadBalancerConfigClass() throws Exception {
		String docUri = directory.toPath().resolve("src/main/java/com/example/cloud/loadbalancer/LoadBalancerConfigExample.java").toUri().toString();

		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(docUri);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();

		assertEquals(0, diagnostics.size());
	}
    
	@Test
	void testErrorOnNotReferencedLoadBalancerConfigClass() throws Exception {
		String docUri = directory.toPath().resolve("src/main/java/com/example/cloud/loadbalancer/LoadBalancerConfigNotRegistered.java").toUri().toString();

		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(docUri);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();

		assertEquals(1, diagnostics.size());
		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION.getCode(), diagnostics.get(0).getCode().getLeft());
	}
    
    @Test
    void testErrorGoesAwayWhenLoadBalancerClientMentionsLoadBalancerConfig() throws Exception {
		String loadBalancerClientDocUri = directory.toPath().resolve("src/main/java/com/example/cloud/loadbalancer/LoadBalancerClientExample.java").toUri().toString();
		String loadBalancerConfigRegisterd = directory.toPath().resolve("src/main/java/com/example/cloud/loadbalancer/LoadBalancerConfigExample.java").toUri().toString();
		String loadBalancerConfigNotRegisterd = directory.toPath().resolve("src/main/java/com/example/cloud/loadbalancer/LoadBalancerConfigNotRegistered.java").toUri().toString();

        // now change the config class source code and update doc
        TestFileScanListener fileScanListener = new TestFileScanListener();
        indexer.getJavaIndexer().setFileScanListener(fileScanListener);

        String loadBalancerClientSource = FileUtils.readFileToString(UriUtil.toFile(loadBalancerClientDocUri), Charset.defaultCharset());
        String updatedLoadBalancerClientSource = loadBalancerClientSource.replace("@LoadBalancerClient(name = \"first\", configuration = LoadBalancerConfigExample.class)",
        		"@LoadBalancerClient(name = \"first\", configuration = {LoadBalancerConfigExample.class, LoadBalancerConfigNotRegistered.class})");

        CompletableFuture<Void> updateFuture = indexer.updateDocument(loadBalancerClientDocUri, updatedLoadBalancerClientSource, "test triggered");
        updateFuture.get(5, TimeUnit.SECONDS);

        fileScanListener.assertScannedUri(loadBalancerClientDocUri, 1);
        fileScanListener.assertScannedUri(loadBalancerConfigNotRegisterd, 1);
        fileScanListener.assertScannedUri(loadBalancerConfigRegisterd, 1);
        fileScanListener.assertFileScanCount(3);
        
        // check diagnostics result
		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(loadBalancerConfigNotRegisterd);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();
		assertEquals(0, diagnostics.size());
    }
    
    @Test
    void testErrorAppearsWhenFeignClientNotMentionsFeignConfigAnymore() throws Exception {
		String loadBalancerClientDocUri = directory.toPath().resolve("src/main/java/com/example/cloud/loadbalancer/LoadBalancerClientExample.java").toUri().toString();
		String loadBalancerConfigRegisterd = directory.toPath().resolve("src/main/java/com/example/cloud/loadbalancer/LoadBalancerConfigExample.java").toUri().toString();

        // now change the config class source code and update doc
        TestFileScanListener fileScanListener = new TestFileScanListener();
        indexer.getJavaIndexer().setFileScanListener(fileScanListener);

        String loadBalancerClientSource = FileUtils.readFileToString(UriUtil.toFile(loadBalancerClientDocUri), Charset.defaultCharset());
        String updatedLoadBalancerClientSource = loadBalancerClientSource.replace("@LoadBalancerClient(name = \"first\", configuration = LoadBalancerConfigExample.class)",
        		"@LoadBalancerClient(name = \"first\")");

        CompletableFuture<Void> updateFuture = indexer.updateDocument(loadBalancerClientDocUri, updatedLoadBalancerClientSource, "test triggered");
        updateFuture.get(5, TimeUnit.SECONDS);

        fileScanListener.assertScannedUri(loadBalancerClientDocUri, 1);
        fileScanListener.assertScannedUri(loadBalancerConfigRegisterd, 1);
        fileScanListener.assertFileScanCount(2);
        
        // check diagnostics result
		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(loadBalancerConfigRegisterd);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();
		assertEquals(1, diagnostics.size());
		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION.getCode(), diagnostics.get(0).getCode().getLeft());
    }
    
    @Test
    void testQuickFixAddToLoadBalancerClientConfigurationOffered() throws Exception {
		String docUri = directory.toPath().resolve("src/main/java/com/example/cloud/loadbalancer/LoadBalancerConfigNotRegistered.java").toUri().toString();

		Editor editor = harness.newEditorFromFileUri(docUri, LanguageId.JAVA);
		Diagnostic problem = editor.assertProblem("LoadBalancerConfigNotRegistered");
		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION.getCode(), problem.getCode().getLeft());

		List<CodeAction> codeActions = editor.getCodeActions(problem);
		assertTrue(codeActions.size() >= 3);
		assertTrue(codeActions.stream().anyMatch(ca -> ca.getLabel().contains("@LoadBalancerClient")));
    }

    @Test
    void testApplyQuickFixAddToLoadBalancerClientConfiguration() throws Exception {
		String docUri = directory.toPath().resolve("src/main/java/com/example/cloud/loadbalancer/LoadBalancerConfigNotRegistered.java").toUri().toString();

		Editor editor = harness.newEditorFromFileUri(docUri, LanguageId.JAVA);
		Diagnostic problem = editor.assertProblem("LoadBalancerConfigNotRegistered");
		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION.getCode(), problem.getCode().getLeft());

		List<CodeAction> codeActions = editor.getCodeActions(problem);
		CodeAction addToLbClientFix = codeActions.stream()
				.filter(ca -> ca.getLabel().contains("@LoadBalancerClient"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Should have '@LoadBalancerClient' quickfix"));

		Path targetFile = directory.toPath().resolve("src/main/java/com/example/cloud/loadbalancer/LoadBalancerClientExample.java");
		String originalContent = Files.readString(targetFile);
		try {
			addToLbClientFix.perform();

			assertEquals("""
					package com.example.cloud.loadbalancer;
					
					import org.springframework.cloud.client.loadbalancer.LoadBalanced;
					import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
					import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
					import org.springframework.context.annotation.Bean;
					import org.springframework.context.annotation.Configuration;
					import org.springframework.web.reactive.function.client.WebClient;
					
					@Configuration
					@LoadBalancerClients({@LoadBalancerClient(name = "first", configuration = {LoadBalancerConfigExample.class, LoadBalancerConfigNotRegistered.class})})
					public class LoadBalancerClientExample {
					
					\t@Bean
					\t@LoadBalanced
					\tWebClient.Builder loadBalancedWebClientBuilder() {
					\t\treturn WebClient.builder();
					\t}
					
					}
					""", Files.readString(targetFile).replace("\r", ""));
		} finally {
			Files.writeString(targetFile, originalContent);
		}
    }

    @Test
    void testApplyQuickFixAddToLoadBalancerClientConfigurationWithImport() throws Exception {
		String source = """
				package com.example.cloud.other;
				
				import org.springframework.context.annotation.Bean;
				
				public class OtherPackageConfig {
				
					@Bean
					BeanType someBean() {
						return new BeanType();
					}
				
				}
				""";

		Path javaFile = directory.toPath().resolve("src/main/java/com/example/cloud/other/OtherPackageConfig.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, source, javaFile.toUri().toASCIIString());
		Diagnostic problem = editor.assertProblem("OtherPackageConfig");
		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION.getCode(), problem.getCode().getLeft());

		List<CodeAction> codeActions = editor.getCodeActions(problem);
		CodeAction addToLbClientFix = codeActions.stream()
				.filter(ca -> ca.getLabel().contains("@LoadBalancerClient"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Should have '@LoadBalancerClient' quickfix"));

		Path targetFile = directory.toPath().resolve("src/main/java/com/example/cloud/loadbalancer/LoadBalancerClientExample.java");
		String originalContent = Files.readString(targetFile);
		try {
			addToLbClientFix.perform();

			assertEquals("""
					package com.example.cloud.loadbalancer;
					
					import com.example.cloud.other.OtherPackageConfig;
					import org.springframework.cloud.client.loadbalancer.LoadBalanced;
					import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
					import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
					import org.springframework.context.annotation.Bean;
					import org.springframework.context.annotation.Configuration;
					import org.springframework.web.reactive.function.client.WebClient;
					
					@Configuration
					@LoadBalancerClients({@LoadBalancerClient(name = "first", configuration = {LoadBalancerConfigExample.class, OtherPackageConfig.class})})
					public class LoadBalancerClientExample {
					
					\t@Bean
					\t@LoadBalanced
					\tWebClient.Builder loadBalancedWebClientBuilder() {
					\t\treturn WebClient.builder();
					\t}
					
					}
					""", Files.readString(targetFile).replace("\r", ""));
		} finally {
			Files.writeString(targetFile, originalContent);
		}
    }

    @Test
    void testErrorAppearsWhenFeignClientAnnotationGoesAwayEntirely() throws Exception {
		String loadBalancerClientDocUri = directory.toPath().resolve("src/main/java/com/example/cloud/loadbalancer/LoadBalancerClientExample.java").toUri().toString();
		String loadBalancerConfigRegisterd = directory.toPath().resolve("src/main/java/com/example/cloud/loadbalancer/LoadBalancerConfigExample.java").toUri().toString();

        // now change the config class source code and update doc
        TestFileScanListener fileScanListener = new TestFileScanListener();
        indexer.getJavaIndexer().setFileScanListener(fileScanListener);

        String loadBalancerClientSource = FileUtils.readFileToString(UriUtil.toFile(loadBalancerClientDocUri), Charset.defaultCharset());
        String updatedLoadBalancerClientSource = loadBalancerClientSource.replace("@LoadBalancerClient(name = \"first\", configuration = LoadBalancerConfigExample.class)",
        		"");

        CompletableFuture<Void> updateFuture = indexer.updateDocument(loadBalancerClientDocUri, updatedLoadBalancerClientSource, "test triggered");
        updateFuture.get(5, TimeUnit.SECONDS);

        fileScanListener.assertScannedUri(loadBalancerClientDocUri, 1);
        fileScanListener.assertScannedUri(loadBalancerConfigRegisterd, 1);
        fileScanListener.assertFileScanCount(2);
        
        // check diagnostics result
		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(loadBalancerConfigRegisterd);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();
		assertEquals(1, diagnostics.size());
		assertEquals(Boot2JavaProblemType.MISSING_CONFIGURATION_ANNOTATION.getCode(), diagnostics.get(0).getCode().getLeft());
    }
    
}
