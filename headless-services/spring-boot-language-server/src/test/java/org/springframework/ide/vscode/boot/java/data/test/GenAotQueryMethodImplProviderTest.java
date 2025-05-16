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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
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
public class GenAotQueryMethodImplProviderTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	
	private IJavaProject testProject;

	@BeforeEach
	public void setup() throws Exception {
		testProject = ProjectsHarness.INSTANCE.mavenProject("aot-data-repositories-jpa");
		harness.useProject(testProject);
		harness.intialize(null);

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(testProject.getLocationUri().toASCIIString())).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
	@Test
	void nonAnnotatedMethod() throws Exception {
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/UserRepository.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());

		LocationLink ll = new LocationLink();
		ll.setTargetUri(Paths.get(testProject.getLocationUri())
				.resolve("target/spring-aot/main/sources/example/springdata/aot/UserRepositoryImpl__Aot.java").toUri()
				.toASCIIString());
		ll.setOriginSelectionRange(new Range(new Position(43, 15), new Position(43, 61)));
		ll.setTargetRange(new Range(new Position(137, 20), new Position(137, 66)));
		ll.setTargetSelectionRange(new Range(new Position(137, 20), new Position(137, 66)));
		editor.assertImplementationLinkTargets("findUserByLastnameStartingWith", List.of(ll));
	}

	@Test
	void annotatedMethod() throws Exception {
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/UserRepository.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());

		LocationLink ll = new LocationLink();
		ll.setTargetUri(Paths.get(testProject.getLocationUri())
				.resolve("target/spring-aot/main/sources/example/springdata/aot/UserRepositoryImpl__Aot.java").toUri()
				.toASCIIString());
		ll.setOriginSelectionRange(new Range(new Position(54, 15), new Position(54, 45)));
		ll.setTargetRange(new Range(new Position(180, 20), new Position(180, 50)));
		ll.setTargetSelectionRange(new Range(new Position(180, 20), new Position(180, 50)));
		editor.assertImplementationLinkTargets("usersWithUsernamesStartingWith", List.of(ll));
	}
	
	@Test
	void notApplicableInsideRepoInterface() throws Exception {
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/UserRepository.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());
		editor.assertImplementationLinkTargets("user", List.of());
	}

	@Test
	void methodOutsideRepoInterface() throws Exception {
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/User.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());
		editor.assertImplementationLinkTargets("getRegistrationDate", List.of());
	}
}
