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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Diagnostic;
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
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.rewrite.java.FixDescriptor;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.CodeAction;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class WebApiVersioningQuickfixIntegrationTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	
	private IJavaProject testProject;

	@BeforeEach
	public void setup() throws Exception {
		testProject = ProjectsHarness.INSTANCE.mavenProject("sf7-no-api-versioning");
		Path projectRoot = Paths.get(testProject.getLocationUri());
		
		// Clean up any leftover files from previous tests
		Files.walk(projectRoot.resolve("src/main/java/com/example/demo"))
			.filter(p -> p.getFileName().toString().endsWith("WebMvcConfigurer.java"))
			.forEach(f -> {
				try {
					Files.delete(f);
				} catch (IOException e) {
					// ignore
				}
			});		
		Files.walk(projectRoot.resolve("src/main/resources"))
			.filter(p -> p.getFileName().toString().matches("application.*\\.*"))
			.forEach(f -> {
				try {
					Files.delete(f);
				} catch (IOException e) {
					// ignore
				}
			});
		
		harness.useProject(testProject);
		harness.intialize(null);

		// Trigger project creation and wait for indexing
		projectFinder.find(new TextDocumentIdentifier(testProject.getLocationUri().toASCIIString())).get();
		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
		
	}
	
	@Test
	void applyBeanBasedHeaderQuickfix() throws Exception {
		// Create a controller with version annotation but no API versioning configured
		String source = """
				package com.example.demo;
				
				import org.springframework.web.bind.annotation.RestController;
				import org.springframework.web.bind.annotation.RequestMapping;
				
				@RestController
				@RequestMapping(path = "/api", version = "1")
				public class VersionedController {
				}
				""";
		
		Path projectRoot = Paths.get(testProject.getLocationUri());
		Path javaFile = projectRoot.resolve("src/main/java/com/example/demo/VersionedController.java");
		assertFalse(Files.exists(projectRoot.resolve("src/main/java/com/example/demo/Sf7NoApiVersioningWebMvcConfigurer.java")));
		
		// Create the editor with the source
		Editor editor = harness.newEditor(LanguageId.JAVA, source, javaFile.toUri().toASCIIString());
		
		Diagnostic problem = editor.assertProblem("version = \"1\"");
				
		assertTrue(Editor.markupEitherToString(problem.getMessage()).contains("API versioning not configured"), 
				"Diagnostic should be about API versioning not configured");
		
		// Get code actions for this diagnostic
		List<CodeAction> codeActions = editor.getCodeActions(problem);
		assertTrue(codeActions.size() >= 8, "Should have at least 8 quickfixes (4 property-based + 4 bean-based)");
		
		// Find the bean-based header quickfix
		CodeAction beanHeaderFix = codeActions.stream()
				.filter(ca -> ca.getLabel().equals("Create WebMVC config bean with versioning via request header"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Should have 'Create WebMVC config bean with versioning via request header' quickfix"));
		
		// Apply the quickfix
		beanHeaderFix.perform();
		
		// Verify the generated WebMvcConfigurer class was created
		Path configurerPath = projectRoot.resolve("src/main/java/com/example/demo/Sf7NoApiVersioningWebMvcConfigurer.java");
		assertTrue(Files.exists(configurerPath), 
				"WebMvcConfigurer class should be created at " + configurerPath);
		
		// Verify the complete generated content
		assertEquals("""
			package com.example.demo;
			
			import org.springframework.context.annotation.Configuration;
			import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
			import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
			
			@Configuration
			public class Sf7NoApiVersioningWebMvcConfigurer implements WebMvcConfigurer {
			    @Override
			    public void configureApiVersioning(ApiVersionConfigurer configurer) {
			        configurer.useRequestHeader("X-API-Version");
			    }
			}
			""", Files.readString(configurerPath));
	}
	
	@Test
	void applyBeanBasedHeaderQuickfixWhenConfigurerAlreadyExists() throws Exception {
		Path projectRoot = Paths.get(testProject.getLocationUri());
		
		// Pre-create a WebMvcConfigurer bean WITHOUT the configureApiVersioning method
		Path existingConfigurerPath = projectRoot.resolve("src/main/java/com/example/demo/ExistingWebMvcConfigurer.java");
		Files.writeString(existingConfigurerPath, """
				package com.example.demo;
				
				import org.springframework.context.annotation.Configuration;
				import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
				
				@Configuration
				public class ExistingWebMvcConfigurer implements WebMvcConfigurer {
					// Existing configurer without API versioning
				}
				""");
		harness.createFile(existingConfigurerPath.toUri().toASCIIString());
		harness.changeFile(existingConfigurerPath.toUri().toASCIIString());
		
		// Re-index to pick up the new configurer
		CompletableFuture<Void> reindex = indexer.waitOperation();
		reindex.get(5, TimeUnit.SECONDS);
		
		// Create a controller with version annotation but no API versioning configured
		String controllerSource = """
				package com.example.demo;
				
				import org.springframework.web.bind.annotation.RestController;
				import org.springframework.web.bind.annotation.RequestMapping;
				
				@RestController
				@RequestMapping(path = "/api", version = "1")
				public class VersionedController2 {
				}
				""";
		
		Path controllerFile = projectRoot.resolve("src/main/java/com/example/demo/VersionedController2.java");
		
		// Create the editor with the controller source
		Editor editor = harness.newEditor(LanguageId.JAVA, controllerSource, controllerFile.toUri().toASCIIString());
		
		Diagnostic problem = editor.assertProblem("version = \"1\"");
		assertTrue(Editor.markupEitherToString(problem.getMessage()).contains("API versioning not configured"), 
				"Diagnostic should be about API versioning not configured");
		
		// Get code actions for this diagnostic
		List<CodeAction> codeActions = editor.getCodeActions(problem);
		assertTrue(codeActions.size() >= 8, "Should have at least 8 quickfixes (4 property-based + 4 bean-based)");
		
		// Find the bean-based header quickfix - should include the bean name as prefix
		CodeAction beanHeaderFix = codeActions.stream()
				.filter(ca -> ca.getLabel().equals("ExistingWebMvcConfigurer: Add WebMVC versioning via request header to existing bean"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Should have 'ExistingWebMvcConfigurer: Add WebMVC versioning via request header to existing bean' quickfix"));
		
		// Apply the quickfix
		beanHeaderFix.perform();
		
		// Verify the existing WebMvcConfigurer was updated (not a new file created)
		assertTrue(Files.exists(existingConfigurerPath), 
				"Existing WebMvcConfigurer should still exist at " + existingConfigurerPath);
		
		// Verify the complete updated content
		assertEquals("""
			package com.example.demo;
			
			import org.springframework.context.annotation.Configuration;
			import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
			import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
			
			@Configuration
			public class ExistingWebMvcConfigurer implements WebMvcConfigurer {
			    @Override
			    public void configureApiVersioning(ApiVersionConfigurer configurer) {
			        configurer.useRequestHeader("X-API-Version");
			    }
				// Existing configurer without API versioning
			}
			""", Files.readString(existingConfigurerPath));
		
	}
	
	@Test
	void quickfixToApplicationPropsFilePreferred() throws Exception {
		Path projectRoot = Paths.get(testProject.getLocationUri());
		Path applicationProperties = projectRoot.resolve("src/main/resources/application.properties");
		Path applicationYml = projectRoot.resolve("src/main/resources/application.yml");
		Files.writeString(applicationProperties, "");
		Files.writeString(applicationYml, "");
		Files.writeString(projectRoot.resolve("src/main/resources/application-cloud.properties"), "");
		Files.writeString(projectRoot.resolve("src/main/resources/application-dev.yaml"), "");


		// Create a controller with version annotation but no API versioning configured
		String controllerSource = """
				package com.example.demo;
				
				import org.springframework.web.bind.annotation.RestController;
				import org.springframework.web.bind.annotation.RequestMapping;
				
				@RestController
				@RequestMapping(path = "/api", version = "1")
				public class VersionedController3 {
				}
				""";
		
		Path controllerFile = projectRoot.resolve("src/main/java/com/example/demo/VersionedController3.java");
		
		// Create the editor with the controller source
		Editor editor = harness.newEditor(LanguageId.JAVA, controllerSource, controllerFile.toUri().toASCIIString());
		
		Diagnostic problem = editor.assertProblem("version = \"1\"");
		assertEquals(Boot4JavaProblemType.API_VERSIONING_NOT_CONFIGURED, Boot4JavaProblemType.valueOf(problem.getCode().getLeft()),
				"Diagnostic should be about API versioning not configured");
		
		// Get code actions for this diagnostic
		List<CodeAction> codeActions = editor.getCodeActions(problem);
		assertTrue(codeActions.size() >= 8, "Should have at least 8 quickfixes (4 property-based + 4 bean-based)");
		
		// Find the properties-based header quickfix
		CodeAction propertiesHeaderFix = codeActions.stream()
				.filter(ca -> ca.getLabel().equals("Add WebMVC versioning via request header using properties"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Should have 'Add WebMVC versioning via request header using properties' quickfix"));
		
		FixDescriptor fix = (FixDescriptor) propertiesHeaderFix.getCommand().getArguments().get(1);
		
		assertThat(fix.getDocUris()).containsExactlyInAnyOrder(
				projectRoot.resolve("src/main/resources/application.yml").toUri().toASCIIString(),
				projectRoot.resolve("src/main/resources/application.properties").toUri().toASCIIString()
		);

		// Apply the quickfix
		propertiesHeaderFix.perform();
		
		assertEquals("""
				spring:
				  mvc:
				    apiversion:
				      use:
				        header: X-API-Version
				""".trim(), Files.readString(applicationYml).trim());
		
		assertEquals("spring.mvc.apiversion.use.header=X-API-Version",
				Files.readString(applicationProperties).trim());
		
		assertEquals("", Files.readString(projectRoot.resolve("src/main/resources/application-dev.yaml")).trim());		
		assertEquals("", Files.readString(projectRoot.resolve("src/main/resources/application-cloud.properties")).trim());
	}
	
	@Test
	void quickfixForSpecialProfilesPropfiles() throws Exception {
		Path projectRoot = Paths.get(testProject.getLocationUri());
		
		Files.writeString(projectRoot.resolve("src/main/resources/application-cloud.properties"), "");
		Files.writeString(projectRoot.resolve("src/main/resources/application-dev.yaml"), "");

		
		// Create a controller with version annotation but no API versioning configured
		String controllerSource = """
				package com.example.demo;
				
				import org.springframework.web.bind.annotation.RestController;
				import org.springframework.web.bind.annotation.RequestMapping;
				
				@RestController
				@RequestMapping(path = "/api", version = "1")
				public class VersionedController3 {
				}
				""";
		
		Path controllerFile = projectRoot.resolve("src/main/java/com/example/demo/VersionedController3.java");
		
		// Create the editor with the controller source
		Editor editor = harness.newEditor(LanguageId.JAVA, controllerSource, controllerFile.toUri().toASCIIString());
		
		Diagnostic problem = editor.assertProblem("version = \"1\"");
		assertEquals(Boot4JavaProblemType.API_VERSIONING_NOT_CONFIGURED, Boot4JavaProblemType.valueOf(problem.getCode().getLeft()),
				"Diagnostic should be about API versioning not configured");
		
		// Get code actions for this diagnostic
		List<CodeAction> codeActions = editor.getCodeActions(problem);
		assertTrue(codeActions.size() >= 8, "Should have at least 8 quickfixes (4 property-based + 4 bean-based)");
		
		// Find the properties-based header quickfix
		CodeAction propertiesHeaderFix = codeActions.stream()
				.filter(ca -> ca.getLabel().equals("Add WebMVC versioning via request header using properties"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Should have 'Add WebMVC versioning via request header using properties' quickfix"));
		
		FixDescriptor fix = (FixDescriptor) propertiesHeaderFix.getCommand().getArguments().get(1);
		
		assertThat(fix.getDocUris()).containsExactlyInAnyOrder(
				projectRoot.resolve("src/main/resources/application-dev.yaml").toUri().toASCIIString(),
				projectRoot.resolve("src/main/resources/application-cloud.properties").toUri().toASCIIString()
		);
		
		// Apply the quickfix
		propertiesHeaderFix.perform();
		
		String expectedYaml = """
				spring:
				  mvc:
				    apiversion:
				      use:
				        header: X-API-Version
				""";
		
		String expectedProperties = "spring.mvc.apiversion.use.header=X-API-Version";
		
		assertEquals(expectedYaml.trim(), Files.readString(projectRoot.resolve("src/main/resources/application-dev.yaml")).trim());		
		assertEquals(expectedProperties, Files.readString(projectRoot.resolve("src/main/resources/application-cloud.properties")).trim());
		
	}

}
