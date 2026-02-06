/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class DataRepositoryAotMetadataCodeLensProviderTest {
	
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	
	private IJavaProject testProject;

	@BeforeEach
	public void setup() throws Exception {
		testProject = ProjectsHarness.INSTANCE.mavenProject("data-repositories-jpa-4");
		harness.useProject(testProject);
		harness.intialize(null);
		
		harness.changeConfiguration(Map.of("boot-java", Map.of("java", Map.of("codelens-over-query-methods", true))));

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(testProject.getLocationUri().toASCIIString())).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
	@Test
	void generateMetadataCodeLens() throws Exception {		
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/UserRepository.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());
		
		List<CodeLens> cls = editor.getCodeLenses("findUserByUsername", 1);
		assertEquals(1,  cls.size());
		Command cmd = cls.get(0).getCommand();
		assertEquals("Show AOT-generated Implementation, Query, etc...", cmd.getTitle());
		cls = editor.getCodeLenses("findAll", 1);
		assertEquals(1,  cls.size());
		cmd = cls.get(0).getCommand();
		assertEquals("Show AOT-generated Implementation, Query, etc...", cmd.getTitle());
		
		harness.getServer().getWorkspaceService().executeCommand(new ExecuteCommandParams(cmd.getCommand(), cmd.getArguments())).get();
		
		Path metadataFile = Paths.get(testProject.getLocationUri()).resolve("target/spring-aot/main/resources/example/springdata/aot/UserRepository.json");
		
		// Trigger file update and wait for the event handling to complete
		assertTrue(Files.isRegularFile(metadataFile), "AOT mtadata JSON file should be generated");
		harness.createFile(metadataFile.toUri().toASCIIString());
		harness.getServer().getAsync().waitForAll();
		
		cls = editor.getCodeLenses("findUserByUsername", 1);
		assertTrue(cls.size() > 1);
		assertEquals("Turn into @Query", cls.get(0).getCommand().getTitle());
		assertEquals("Go To Implementation", cls.get(1).getCommand().getTitle());
		assertEquals("SELECT u FROM users u WHERE u.username = :username", cls.get(2).getCommand().getTitle());
		assertEquals("Refresh AOT Metadata", cls.get(3).getCommand().getTitle());

		cls = editor.getCodeLenses("findAll", 1);
		assertTrue(cls.isEmpty(), "Node CodeLens expected. Not even `Refresh AOT Metadata'");
	}

}
