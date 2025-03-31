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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.java.SpringAotJavaProblemType;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.Settings;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class NotRegisteredBeansAdvancedReconcilingTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private BootJavaConfig config;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		JsonElement json = new Gson().fromJson("""
				{
					"boot-java": {
						"validation": {
							"java": {
                				"boot2": "AUTO",
                				"boot3": "AUTO",
                				"spring-aot": "ON",
                				"version-validation": "ON"
							}
						}
					}
				}
				""", JsonElement.class);
		config.handleConfigurationChange(new Settings(json));

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-validations/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

	@Test
	void testBasicValidationOfAotProcessorNotRegistered() throws Exception {
		String docUri = directory.toPath().resolve("src/main/java/org/test/aot/NotRegisteredBeanRegistrationAotProcessor.java").toUri().toString();

		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(docUri);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();

		assertEquals(1, diagnostics.size());
		assertEquals(SpringAotJavaProblemType.JAVA_BEAN_NOT_REGISTERED_IN_AOT.getCode(), diagnostics.get(0).getCode().getLeft());
	}
    
	@Test
	void testBasicValidationOfAotProcessorRegisteredAsComponent() throws Exception {
		String docUri = directory.toPath().resolve("src/main/java/org/test/aot/RegisteredAsComponentBeanRegistrationAotProcessor.java").toUri().toString();

		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(docUri);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();

		assertEquals(0, diagnostics.size());
	}
    
	@Test
	void testBasicValidationOfAotProcessorRegisteredViaConfig() throws Exception {
		String docUri = directory.toPath().resolve("src/main/java/org/test/aot/RegistetedViaConfigBeanRegistrationAotProcessor.java").toUri().toString();

		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(docUri);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();

		assertEquals(0, diagnostics.size());
	}
    
	@Test
	void testBasicValidationOfAotProcessorRegisteredViaFactoriesFile() throws Exception {
		String docUri = directory.toPath().resolve("src/main/java/org/test/aot/RegisteredViaFactoriesBeanRegistrationAotProcessor.java").toUri().toString();

		PublishDiagnosticsParams diagnosticsResult = harness.getDiagnostics(docUri);
		List<Diagnostic> diagnostics = diagnosticsResult.getDiagnostics();

		assertEquals(0, diagnostics.size());
	}
	
	@Test
	void testValidationDisappearsWhenComponentAnnotationIsAdded() throws Exception {
		// TODO
	}
    
	@Test
	void testValidationAppearsWhenComponentAnnotationIsRemoved() throws Exception {
		// TODO
	}
    
	@Test
	void testValidationDisappearsWhenBeanMethodIsAddedToConfig() throws Exception {
		// TODO
	}
    
	@Test
	void testValidationAppearsWhenBeanMethodIsRemovedFromConfig() throws Exception {
		// TODO
	}
    
}
