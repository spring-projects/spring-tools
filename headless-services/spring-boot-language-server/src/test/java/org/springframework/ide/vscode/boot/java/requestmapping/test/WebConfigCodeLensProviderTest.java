/*******************************************************************************
 * Copyright (c) 2025, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.BootLanguageServerInitializer;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class WebConfigCodeLensProviderTest {
	
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	
	private IJavaProject testProject;

	@BeforeEach
	public void setup() throws Exception {
		testProject = ProjectsHarness.INSTANCE.mavenProject("test-web-config-support");
		harness.useProject(testProject);
		harness.intialize(null);
		
		harness.changeConfiguration(Map.of("boot-java", Map.of("java", Map.of("codelens-web-configs-on-controller-classes", true))));

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(testProject.getLocationUri().toASCIIString())).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
	@Test
	void codeLensOverMethodFromWebMvcConfig() throws Exception {		
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/org/test/versions/MappingClassWithMultipleVersions.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());
		
		List<CodeLens> cls = editor.getCodeLenses("MappingClassWithMultipleVersions", 1);
		assertTrue(contains(cls, "Web Config - Path Prefix: /{version} - Versioning via Request Header: X-API-Version, Path Segment: 0 - Supported Versions: 1.1, 1.2"));
	}
	
	@Test
	void codeLensOverMethodFromWebFluxConfig() throws Exception {		
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/org/test/versions/MappingClassWithMultipleVersions.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());
		
		List<CodeLens> cls = editor.getCodeLenses("MappingClassWithMultipleVersions", 1);
		assertTrue(contains(cls, "Web Config - Path Prefix: /{webflux-variant-version} - Versioning via Request Header: Webflux-X-API-Version, Path Segment: 0 - Supported Versions: 2.1, 2.2"));
	}
	
	@Test
	void codeLensOverMethodFromProperties() throws Exception {		
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/org/test/versions/MappingClassWithMultipleVersions.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());
		
		List<CodeLens> cls = editor.getCodeLenses("MappingClassWithMultipleVersions", 1);
		assertTrue(contains(cls, "Properties Config - Versioning via Request Header: X-API-Version - Supported Versions: 1"));
	}
	
	@Test
	void codeLensOverMethodFromPropertiesForWebFlux() throws Exception {		
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/org/test/versions/MappingClassWithMultipleVersions.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());
		
		List<CodeLens> cls = editor.getCodeLenses("MappingClassWithMultipleVersions", 1);
		assertTrue(contains(cls, "Properties Config - Versioning via Request Header: Webflux-X-API-Version - Supported Versions: 2"));
	}
	
	@Test
	void codeLensOverMethodFromPropertiesYaml() throws Exception {		
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/org/test/versions/MappingClassWithMultipleVersions.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());
		
		List<CodeLens> cls = editor.getCodeLenses("MappingClassWithMultipleVersions", 1);
		assertTrue(contains(cls, "Properties Config - Versioning via Path Segment: 3, Request Header: X-API-HEADER-VIA-YML - Supported Versions: 2, 3"));
	}
	
	@Test
	void codeLensAnnotationPredicateDoesNotMatchPlainController() throws Exception {
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/org/test/versions/SomePlainController.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());

		List<CodeLens> cls = editor.getCodeLenses("SomePlainController", 1);
		// WebConfig uses forAnnotation(RestController.class) — SomePlainController is only
		// @Controller, so the path prefix does NOT apply
		assertFalse(containsPathPrefix(cls, "/{version}"),
				"Plain @Controller should not receive the MVC path prefix guarded by @RestController predicate");
		// …but the versioning config is not gated by the predicate and still shows
		assertTrue(contains(cls, "Web Config - Versioning via Request Header: X-API-Version, Path Segment: 0 - Supported Versions: 1.1, 1.2"));
	}

	@Test
	void codeLensChainedPredicateDoesNotMatchForClassInExcludedPackage() throws Exception {
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/org/test/versions/MappingClassWithMultipleVersions.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());

		List<CodeLens> cls = editor.getCodeLenses("MappingClassWithMultipleVersions", 1);
		// WebConfigWithChainedPredicate requires @RestController AND NOT in org.test.versions;
		// MappingClassWithMultipleVersions IS in org.test.versions → predicate does NOT match
		assertFalse(containsPathPrefix(cls, "/api/v{version}"),
				"RestController in excluded package should not receive path prefix from chained predicate config");
	}

	@Test
	void codeLensChainedPredicateMatchesForClassNotInExcludedPackage() throws Exception {
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/org/test/other/RestControllerInOtherPackage.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());

		List<CodeLens> cls = editor.getCodeLenses("RestControllerInOtherPackage", 1);
		// WebConfigWithChainedPredicate requires @RestController AND NOT in org.test.versions;
		// RestControllerInOtherPackage is in org.test.other → predicate MATCHES
		assertTrue(contains(cls, "Web Config - Path Prefix: /api/v{version}"),
				"RestController outside excluded package should receive path prefix from chained predicate config");
	}

	@Test
	void codeLensAnnotationPredicateMatchesForRestController() throws Exception {
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/org/test/other/RestControllerInOtherPackage.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());

		List<CodeLens> cls = editor.getCodeLenses("RestControllerInOtherPackage", 1);
		// WebConfig uses forAnnotation(RestController.class) — RestControllerInOtherPackage IS
		// a @RestController → path prefix /{version} and versioning both apply
		assertTrue(contains(cls, "Web Config - Path Prefix: /{version} - Versioning via Request Header: X-API-Version, Path Segment: 0 - Supported Versions: 1.1, 1.2"),
				"@RestController should receive the MVC path prefix and versioning from WebConfig");
	}

	@Test
	void codeLensShowsForWebConfigWithoutSummary() throws Exception {
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/org/test/versions/SomePlainController.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());

		List<CodeLens> cls = editor.getCodeLenses("SomePlainController", 1);
		// EmptyWebConfig has no configurePathMatch/configureApiVersioning overrides, so it has
		// no summary to report, but it's still a web config class, so the navigation shortcut
		// to it should show up regardless
		assertTrue(contains(cls, "Web Config"),
				"a web config class without a summary should still get a bare navigation shortcut");
	}

	@Test
	void codeLensNavigationCommandUsesCrossClientShowDocument() throws Exception {
		// the navigation command must be the generic sts/show/document command
		// (backed by the standard LSP window/showDocument request), not the VSCode-only
		// "vscode.open" command, so navigation also works from Eclipse.
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/org/test/versions/MappingClassWithMultipleVersions.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());

		List<CodeLens> cls = editor.getCodeLenses("MappingClassWithMultipleVersions", 1);
		Command command = cls.stream()
				.map(CodeLens::getCommand)
				.filter(cmd -> cmd.getTitle().startsWith("Web Config"))
				.findAny()
				.orElseThrow();

		assertEquals(BootLanguageServerInitializer.CMD_SHOW_DOC, command.getCommand());
		assertEquals(1, command.getArguments().size());
		Object argument = command.getArguments().get(0);
		assertInstanceOf(ShowDocumentParams.class, argument);

		ShowDocumentParams showDocParams = (ShowDocumentParams) argument;
		assertNotNull(showDocParams.getUri());
		assertNotNull(showDocParams.getSelection());
	}

	private boolean contains(List<CodeLens> cls, String title) {
		return cls.stream().filter(cl -> cl.getCommand().getTitle().equals(title)).findAny().isPresent();
	}

	private boolean containsPathPrefix(List<CodeLens> cls, String prefix) {
		return cls.stream().anyMatch(cl -> cl.getCommand().getTitle().contains("Path Prefix: " + prefix));
	}

}
