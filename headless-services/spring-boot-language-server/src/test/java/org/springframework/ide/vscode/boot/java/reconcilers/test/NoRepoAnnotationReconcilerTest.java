/*******************************************************************************
 * Copyright (c) 2023, 2026 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.reconcilers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
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
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.java.Boot2JavaProblemType;
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
public class NoRepoAnnotationReconcilerTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		harness.changeConfiguration("{\"boot-java\": {\"validation\": {\"java\": { \"reconcilers\": true}}}}");
		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-validations/").toURI());
		String projectDir = directory.toURI().toString();
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();
		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

	@Test
	void sanityTest() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/org/test/RepoWithUnnecessaryAnnotation.java").toUri().toString();

		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import org.springframework.data.repository.Repository;

				@org.springframework.stereotype.Repository
				interface A extends Repository {
				}
				""", docUri);

		Diagnostic problem = editor.assertProblem("@org.springframework.stereotype.Repository");
		assertNotNull(problem);
		assertEquals(Boot2JavaProblemType.JAVA_REPOSITORY.getCode(), problem.getCode().getLeft());

		List<CodeAction> codeActions = editor.getCodeActions(problem);
		assertEquals(1, codeActions.size());

		harness.executeCommand(codeActions.get(0).getCommand());

		assertEquals("""
				package org.test;

				import org.springframework.data.repository.Repository;

				interface A extends Repository {
				}
				""", editor.getRawText());

		editor.assertProblems();
	}

	@Test
	void inverseSanityTest() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/org/test/InverseSanity.java").toUri().toString();

		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import org.springframework.stereotype.Repository;

				@Repository
				interface A extends org.springframework.data.repository.Repository {
				}
				""", docUri);

		Diagnostic problem = editor.assertProblem("@Repository");
		assertNotNull(problem);
		assertEquals(Boot2JavaProblemType.JAVA_REPOSITORY.getCode(), problem.getCode().getLeft());

		List<CodeAction> codeActions = editor.getCodeActions(problem);
		assertEquals(1, codeActions.size());

		harness.executeCommand(codeActions.get(0).getCommand());

		assertEquals("""
				package org.test;

				interface A extends org.springframework.data.repository.Repository {
				}
				""", editor.getRawText());

		editor.assertProblems();
	}

	@Test
	void emptyRepoAnnotation() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/org/test/EmptyRepoAnnotation.java").toUri().toString();

		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import org.springframework.data.repository.Repository;

				@org.springframework.stereotype.Repository
				interface A extends Repository {
				}
				""", docUri);

		Diagnostic problem = editor.assertProblem("@org.springframework.stereotype.Repository");
		assertNotNull(problem);
		assertEquals(Boot2JavaProblemType.JAVA_REPOSITORY.getCode(), problem.getCode().getLeft());

		List<CodeAction> codeActions = editor.getCodeActions(problem);
		assertEquals(1, codeActions.size());

		harness.executeCommand(codeActions.get(0).getCommand());

		assertEquals("""
				package org.test;

				import org.springframework.data.repository.Repository;

				interface A extends Repository {
				}
				""", editor.getRawText());

		editor.assertProblems();
	}

}
