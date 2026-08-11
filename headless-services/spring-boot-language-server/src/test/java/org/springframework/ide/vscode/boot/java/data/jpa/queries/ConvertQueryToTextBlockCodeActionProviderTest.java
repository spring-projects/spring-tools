/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.jpa.queries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.CodeAction;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class ConvertQueryToTextBlockCodeActionProviderTest {

	private static final String TITLE = "Convert to text block";

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	private IJavaProject useProject(String projectName) throws Exception {
		IJavaProject testProject = ProjectsHarness.INSTANCE.mavenProject(projectName);
		harness.useProject(testProject);
		harness.intialize(null);

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(testProject.getLocationUri().toASCIIString())).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);

		return testProject;
	}

	private static List<CodeAction> withTitle(List<CodeAction> actions) {
		return actions.stream().filter(a -> TITLE.equals(a.getLabel())).collect(Collectors.toList());
	}

	@Test
	void convertJpaQueryToTextBlock() throws Exception {
		IJavaProject testProject = useProject("aot-data-repositories-jpa");

		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/UserRepository.java");
		Editor editor = harness.newEditor(LanguageId.JAVA,
				new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());

		List<CodeAction> codeActions = withTitle(editor.getCodeActions("SELECT u FROM example.springdata.aot.User u", 1));
		assertEquals(1, codeActions.size());
		CodeAction ca = codeActions.get(0);

		ca.perform();

		String result = editor.getRawText().replace("\r", "");
		assertTrue(result.contains("@Query(\"\"\"\n"), result);
		assertTrue(result.contains("SELECT"), result);
		assertTrue(result.contains("\"\"\")\n    List<User> usersWithUsernamesStartingWith(String username);"), result);
	}

	@Test
	void convertJdbcQueryToTextBlock() throws Exception {
		IJavaProject testProject = useProject("aot-data-repositories-jdbc");

		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/CategoryRepository.java");
		Editor editor = harness.newEditor(LanguageId.JAVA,
				new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());

		List<CodeAction> codeActions = withTitle(editor.getCodeActions("SELECT * FROM category", 1));
		assertEquals(1, codeActions.size());
		CodeAction ca = codeActions.get(0);

		ca.perform();

		String result = editor.getRawText().replace("\r", "");
		assertTrue(result.contains("@Query(\"\"\"\n"), result);
		assertTrue(result.contains("SELECT"), result);
	}

	@Test
	void noConversionForAlreadyTextBlockQuery() throws Exception {
		IJavaProject testProject = useProject("aot-data-repositories-jpa");

		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/UserRepository.java");
		String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8).replace(
				"@Query(\"SELECT u FROM example.springdata.aot.User u WHERE u.username LIKE ?1%\")",
				"@Query(\"\"\"\n    SELECT u FROM example.springdata.aot.User u WHERE u.username LIKE ?1%\n    \"\"\")");
		Editor editor = harness.newEditor(LanguageId.JAVA, content, filePath.toUri().toASCIIString());

		List<CodeAction> codeActions = withTitle(editor.getCodeActions("SELECT u FROM example.springdata.aot.User u", 1));
		assertEquals(0, codeActions.size());
	}

	@Test
	void noConversionForUnrelatedAnnotation() throws Exception {
		IJavaProject testProject = useProject("aot-data-repositories-jpa");

		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/UserRepository.java");
		Editor editor = harness.newEditor(LanguageId.JAVA,
				new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());

		List<CodeAction> codeActions = withTitle(editor.getCodeActions("findUserByUsername", 1));
		assertEquals(0, codeActions.size());
	}

}
