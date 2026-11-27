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
package org.springframework.ide.vscode.boot.java.conditionals.test;

import java.io.File;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import org.eclipse.lsp4j.CompletionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.AdHocPropertyHarnessTestConf;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author viniciusmachak
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import({AdHocPropertyHarnessTestConf.class, ConditionalOnClassTestConf.class})
public class ConditionalOnClassCompletionTest {

	/** only look at proposals for types of the test project, the fuzzy search also finds library types */
	private static final Predicate<CompletionItem> PROJECT_TYPES = item -> item.getTextEdit().getLeft().getNewText().contains("org.test.");

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private MavenJavaProject testProject;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = Paths.get(testProject.getLocationUri()).toFile();
	}

	@Test
	void testStringLiteralCompletion() throws Exception {
		Editor editor = editor("@ConditionalOnClass(name = \"ClassConditionTar<*>\")");
		editor.assertCompletions(PROJECT_TYPES,
				source("@ConditionalOnClass(name = \"org.test.ClassConditionTarget<*>\")"),
				source("@ConditionalOnClass(name = \"org.test.ClassConditionTarget$Nested<*>\")"));
	}

	@Test
	void testSimpleNameCompletion() throws Exception {
		Editor editor = editor("@ConditionalOnClass(name = ClassConditionTar<*>)");
		editor.assertCompletions(PROJECT_TYPES,
				source("@ConditionalOnClass(name = \"org.test.ClassConditionTarget\"<*>)"),
				source("@ConditionalOnClass(name = \"org.test.ClassConditionTarget$Nested\"<*>)"));
	}

	@Test
	void testQualifiedNameCompletion() throws Exception {
		Editor editor = editor("@ConditionalOnClass(name = org.test.ClassConditionTar<*>)");
		editor.assertCompletions(PROJECT_TYPES,
				source("@ConditionalOnClass(name = \"org.test.ClassConditionTarget\"<*>)"),
				source("@ConditionalOnClass(name = \"org.test.ClassConditionTarget$Nested\"<*>)"));
	}

	@Test
	void testStringLiteralInArrayCompletion() throws Exception {
		Editor editor = editor("@ConditionalOnClass(name = {\"ClassConditionTar<*>\"})");
		editor.assertCompletions(PROJECT_TYPES,
				source("@ConditionalOnClass(name = {\"org.test.ClassConditionTarget<*>\"})"),
				source("@ConditionalOnClass(name = {\"org.test.ClassConditionTarget$Nested<*>\"})"));
	}

	@Test
	void testAlreadyMentionedValueIsNotProposed() throws Exception {
		Editor editor = editor("@ConditionalOnClass(name = {\"org.test.ClassConditionTarget\", \"ClassConditionTar<*>\"})");
		editor.assertCompletions(PROJECT_TYPES,
				source("@ConditionalOnClass(name = {\"org.test.ClassConditionTarget\", \"org.test.ClassConditionTarget$Nested<*>\"})"));
	}

	@Test
	void testNestedClassIsInsertedWithBinaryName() throws Exception {
		Editor editor = editor("@ConditionalOnClass(name = \"Nested<*>\")");
		editor.assertCompletions(PROJECT_TYPES,
				source("@ConditionalOnClass(name = \"org.test.ClassConditionTarget$Nested<*>\")"));
	}

	@Test
	void testNoCompletionsForEmptyValue() throws Exception {
		Editor editor = editor("@ConditionalOnClass(name = <*>)");
		editor.assertCompletions(PROJECT_TYPES);
	}

	@Test
	void testNoCompletionsForEmptyStringLiteral() throws Exception {
		Editor editor = editor("@ConditionalOnClass(name = \"<*>\")");
		editor.assertCompletions(PROJECT_TYPES);
	}

	@Test
	void testNoCompletionsForWrongAttribute() throws Exception {
		Editor editor = editor("@ConditionalOnClass(wrong = ClassConditionTar<*>)");
		editor.assertCompletions(PROJECT_TYPES);
	}

	@Test
	void testNoCompletionsForValueAttribute() throws Exception {
		// value is of type Class[], so class literals are expected here instead of strings
		Editor editor = editor("@ConditionalOnClass(value = ClassConditionTar<*>)");
		editor.assertCompletions(PROJECT_TYPES);
	}

	@Test
	void testCompletionTurnedOffByFeatureFlag() throws Exception {
		harness.changeConfiguration(Map.of("boot-java", Map.of("feature-flags", Map.of("class-name-completion", false))));
		// a configuration change re-indexes the project, wait for it before asserting
		indexer.waitOperation().get(5, TimeUnit.SECONDS);

		Editor editor = editor("@ConditionalOnClass(name = \"ClassConditionTar<*>\")");
		editor.assertCompletions(PROJECT_TYPES);
	}

	private Editor editor(String annotation) throws Exception {
		String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempConditionalOnClassCompletion.java").toUri().toString();
		return harness.newEditor(LanguageId.JAVA, source(annotation), tempJavaDocUri);
	}

	private static String source(String annotation) {
		return """
				package org.test;

				import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
				import org.springframework.context.annotation.Configuration;

				@Configuration
				%s
				public class TempConditionalOnClassCompletion {
				}
				""".formatted(annotation);
	}

}
