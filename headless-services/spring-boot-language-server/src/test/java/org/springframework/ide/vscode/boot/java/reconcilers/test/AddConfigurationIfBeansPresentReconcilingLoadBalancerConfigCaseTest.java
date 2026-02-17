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
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
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
