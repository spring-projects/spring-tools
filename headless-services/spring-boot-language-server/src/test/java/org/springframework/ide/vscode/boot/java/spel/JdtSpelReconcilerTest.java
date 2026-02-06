/*******************************************************************************
 * Copyright (c) 2024 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.spel;

import java.io.File;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class JdtSpelReconcilerTest {

	@Autowired
	private BootLanguageServerHarness harness;
	@Autowired
	private JavaProjectFinder projectFinder;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/boot-mysql/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();
		
		harness.changeConfiguration("""
				{
				"spring-boot": {
					"ls": {
						"problem": {
							"spel": {
								"JAVA_SPEL_EXPRESSION_SYNTAX": "ERROR"
							}
						}
					}
				}
			}	
			""");

	}

	@Test
	void noErrors_ConditionalOnExpression_1() throws Exception {
		String source = """
				package example.demo;

				import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

				@ConditionalOnExpression("${server.port:8080} == 5678")
				public class A {
				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/A.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);
		editor.assertProblems();
	}

	@Test
	void noErrors_ConditionalOnExpression_2() throws Exception {
		String source = """
				package example.demo;

				import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

				@ConditionalOnExpression("#{${server.port:8080} == 5678}")
				public class A {
				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/A.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);
		editor.assertProblems();
	}
	
	@Test
	void errors_ConditionalOnExpression_1() throws Exception {
		String source = """
				package example.demo;

				import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

				@ConditionalOnExpression("${server.port:8080 == 5678")
				public class A {
				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/A.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);
		editor.assertProblems("\"|SPEL: mismatched input '<EOF>'");
	}

	@Test
	void errors_ConditionalOnExpression_2() throws Exception {
		String source = """
				package example.demo;

				import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

				@ConditionalOnExpression("#{${server.port:8080 == 5678}")
				public class A {
				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/A.java").toUri()
				.toString();
		Editor editor = harness.newEditor(LanguageId.JAVA, source, docUri);
		editor.assertProblems("}|SPEL: mismatched input '<EOF>'");
	}
}
