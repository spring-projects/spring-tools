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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.CodeLens;
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
import org.springframework.ide.vscode.commons.languageserver.util.Settings;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.Gson;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class DataRepositoryAotMetadataCodeLensProviderMongoDbTest {
	
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	
	private IJavaProject testProject;

	@BeforeEach
	public void setup() throws Exception {
		testProject = ProjectsHarness.INSTANCE.mavenProject("aot-data-repositories-mongodb");
		harness.useProject(testProject);
		harness.intialize(null);

		harness.changeConfiguration(new Settings(new Gson()
				.toJsonTree(Map.of("boot-java", Map.of("java", Map.of("codelens-over-query-methods", true))))));

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(testProject.getLocationUri().toASCIIString())).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
	@Test
	void codeLensOverMethodWithJustThePlainFilter() throws Exception {		
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/UserRepository.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());
		
		List<CodeLens> cls = editor.getCodeLenses("findUserByUsername", 1);
		assertEquals("Turn into @Query", cls.get(0).getCommand().getTitle());
		assertEquals("Implementation", cls.get(1).getCommand().getTitle());
		assertEquals("{\"username\": ?0}", cls.get(2).getCommand().getTitle());
	}

	@Test
	void codeLensOverMethodWithMultipleElements() throws Exception {		
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/UserRepository.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());
		
		List<CodeLens> cls = editor.getCodeLenses("findUserByLastnameLikeOrderByFirstname", 1);
		assertEquals("Turn into @Query", cls.get(0).getCommand().getTitle());
		assertEquals("Implementation", cls.get(1).getCommand().getTitle());
		assertEquals("filter = \"{\"lastname\": ?0}\", sort = \"{\"firstname\": 1}\"", cls.get(2).getCommand().getTitle());
	}

	@Test
	void noCodeLensOverMethodWithQueryAnnotation() throws Exception {		
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/UserRepository.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());
		
		List<CodeLens> cls = editor.getCodeLenses("usersWithUsernamesStartingWith", 1);
		assertEquals(1, cls.size());
		assertEquals("Implementation", cls.get(0).getCommand().getTitle());
		assertEquals(1, cls.get(0).getCommand().getArguments().size());
	}
}
