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
package org.springframework.ide.vscode.boot.java.utils.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.java.reconcilers.CachedDiagnostic;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerConfigFiles;
import org.springframework.ide.vscode.boot.mcp.DiagnosticsMcpTools;
import org.springframework.ide.vscode.boot.mcp.DiagnosticsMcpTools.ProjectDiagnostic;
import org.springframework.ide.vscode.boot.mcp.ProjectLookup;
import org.springframework.ide.vscode.boot.validation.generations.ProjectVersionDiagnosticProvider;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class SpringIndexerConfigFilesTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SimpleLanguageServer server;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private ProjectVersionDiagnosticProvider versionDiagnosticProvider;

	private File directory;
	private IJavaProject project;
	private SpringIndexerConfigFiles configFilesIndexer;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-config-files-diagnostics/").toURI());
		String projectDir = directory.toURI().toString();

		// trigger project creation
		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		indexer.waitOperation().get(10, TimeUnit.SECONDS);
		configFilesIndexer = indexer.getConfigFilesIndexer();
	}

	@Test
	void reconcilesMainConfigFilesOfTheProject() throws Exception {
		List<CachedDiagnostic> diagnostics = configFilesIndexer.getDiagnostics(project);

		String propertiesUri = uri("src/main/resources/application.properties");
		String yamlUri = uri("src/main/resources/application-dev.yml");
		String factoriesUri = uri("src/main/resources/META-INF/spring.factories");

		assertEquals(Set.of(propertiesUri, yamlUri, factoriesUri), diagnostics.stream().map(CachedDiagnostic::getDocURI).collect(Collectors.toSet()));

		assertEquals(Set.of("PROP_UNKNOWN_PROPERTY", "PROP_VALUE_TYPE_MISMATCH"), codes(diagnostics, propertiesUri));
		assertEquals(Set.of("YAML_UNKNOWN_PROPERTY", "YAML_SHOULD_ESCAPE"), codes(diagnostics, yamlUri));
		assertEquals(Set.of("FACTORIES_KEY_NOT_SUPPORTED"), codes(diagnostics, factoriesUri));

		Diagnostic unknownProperty = diagnostics.stream()
				.filter(d -> propertiesUri.equals(d.getDocURI()) && "PROP_UNKNOWN_PROPERTY".equals(d.getDiagnostic().getCode().getLeft()))
				.map(CachedDiagnostic::getDiagnostic)
				.findFirst().get();
		assertEquals(DiagnosticSeverity.Warning, unknownProperty.getSeverity());
		assertEquals(2, unknownProperty.getRange().getStart().getLine());
		assertEquals(server.EXTENSION_ID, unknownProperty.getSource());
	}

	@Test
	void ignoresTestResourcesAndUnrelatedPropertyFiles() throws Exception {
		Set<String> uris = configFilesIndexer.getDiagnostics(project).stream().map(CachedDiagnostic::getDocURI).collect(Collectors.toSet());

		assertFalse(uris.contains(uri("src/test/resources/application.properties")));
		assertFalse(uris.contains(uri("src/main/resources/messages.properties")));
		assertFalse(configFilesIndexer.isInterestedIn(uri("src/main/resources/messages.properties")));
		assertTrue(configFilesIndexer.isInterestedIn(uri("src/main/resources/application.properties")));
		assertTrue(configFilesIndexer.isInterestedIn(uri("src/main/resources/bootstrap-local.yaml")));
		assertTrue(configFilesIndexer.isInterestedIn(uri("src/main/resources/META-INF/spring.factories")));
	}

	@Test
	void updatesDiagnosticsWhenAConfigFileChanges() throws Exception {
		String propertiesUri = uri("src/main/resources/application.properties");

		// content is passed in explicitly, the fixture on disk is not modified
		indexer.updateDocument(propertiesUri, "spring.application.name=fixed\nserver.port=8080\n", "test").get(10, TimeUnit.SECONDS);
		assertEquals(Set.of(), codes(configFilesIndexer.getDiagnostics(project), propertiesUri));

		indexer.updateDocument(propertiesUri, "server.port=8080\nserver.port=9090\n", "test").get(10, TimeUnit.SECONDS);
		assertEquals(Set.of("PROP_DUPLICATE_KEY"), codes(configFilesIndexer.getDiagnostics(project), propertiesUri));
	}

	@Test
	void indexesCreatedConfigFilesAndForgetsDeletedOnes() throws Exception {
		Path file = directory.toPath().resolve("src/main/resources/application-tmp.properties");
		String docUri = file.toUri().toASCIIString();

		Files.writeString(file, "server.port=nope\n");
		try {
			indexer.createDocument(docUri).get(10, TimeUnit.SECONDS);
			assertEquals(Set.of("PROP_VALUE_TYPE_MISMATCH"), codes(configFilesIndexer.getDiagnostics(project), docUri));

			// deletions are only propagated for documents with index elements, so the indexer has to notice on its own
			Files.delete(file);
			assertEquals(Set.of(), codes(configFilesIndexer.getDiagnostics(project), docUri));
		} finally {
			Files.deleteIfExists(file);
			indexer.deleteDocument(docUri).get(10, TimeUnit.SECONDS);
		}
	}

	@Test
	void mcpToolReturnsConfigFileDiagnostics() throws Exception {
		DiagnosticsMcpTools tools = new DiagnosticsMcpTools(new ProjectLookup(projectFinder), indexer, versionDiagnosticProvider);

		List<ProjectDiagnostic> result = tools.getProjectDiagnostics(project.getElementName());

		Set<String> codes = result.stream().map(ProjectDiagnostic::code).collect(Collectors.toSet());
		assertTrue(codes.containsAll(Set.of("PROP_UNKNOWN_PROPERTY", "PROP_VALUE_TYPE_MISMATCH", "YAML_UNKNOWN_PROPERTY", "YAML_SHOULD_ESCAPE", "FACTORIES_KEY_NOT_SUPPORTED")), codes.toString());

		ProjectDiagnostic typeMismatch = result.stream().filter(d -> "PROP_VALUE_TYPE_MISMATCH".equals(d.code())).findFirst().get();
		assertEquals(uri("src/main/resources/application.properties"), typeMismatch.uri());
		assertEquals("error", typeMismatch.severity());
		assertEquals(3, typeMismatch.startLine());
		assertEquals("Expecting 'int'", typeMismatch.message());
	}

	private String uri(String relativePath) {
		return directory.toPath().resolve(relativePath).toUri().toASCIIString();
	}

	private static Set<String> codes(List<CachedDiagnostic> diagnostics, String docUri) {
		return diagnostics.stream()
				.filter(d -> docUri.equals(d.getDocURI()))
				.map(d -> d.getDiagnostic().getCode().getLeft())
				.collect(Collectors.toSet());
	}

}
