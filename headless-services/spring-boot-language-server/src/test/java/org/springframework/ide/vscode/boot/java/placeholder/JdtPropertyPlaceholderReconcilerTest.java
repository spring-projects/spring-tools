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
package org.springframework.ide.vscode.boot.java.placeholder;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.editor.harness.AdHocPropertyHarness;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class JdtPropertyPlaceholderReconcilerTest {

	private static final String UNRESOLVED = "has no default value and could not be found in the project's configuration";

	@Autowired
	private BootLanguageServerHarness harness;
	@Autowired
	private JavaProjectFinder projectFinder;
	@Autowired
	private AdHocPropertyHarness adHocProperties;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/boot-mysql/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		// the unresolved check is off by default
		harness.changeConfiguration("""
				{
				"spring-boot": {
					"ls": {
						"problem": {
							"spel": {
								"JAVA_UNRESOLVED_PLACEHOLDER": "WARNING"
							}
						}
					}
				}
			}
			""");
	}

	private Editor newEditor(String body) throws Exception {
		String source = """
				package example.demo;

				import org.springframework.beans.factory.annotation.Value;
				import org.springframework.context.annotation.PropertySource;
				import org.springframework.scheduling.annotation.Scheduled;

				public class A {
				""" + body + """
				}
				""";
		String docUri = directory.toPath().resolve("src/main/java/example/demo/A.java").toUri().toString();
		return harness.newEditor(LanguageId.JAVA, source, docUri);
	}

	@Test
	void unresolvedPlaceholderInValue() throws Exception {
		Editor editor = newEditor("""
					@Value("${db.url}")
					String url;
				""");
		editor.assertProblems("db.url|" + UNRESOLVED);
	}

	@Test
	void noProblems_definedKeys() throws Exception {
		adHocProperties.add("my.adhoc.key");
		Editor editor = newEditor("""
					@Value("${server.port}")
					int port;

					@Value("${my.adhoc.key}")
					String adhoc;

					@Value("${db.url:jdbc:h2:mem:test}")
					String withDefault;

					@Value("${random.int}")
					int random;

					@Value("${outer:${PID}}")
					String nested;

					@Value("plain text without placeholder")
					String plain;
				""");
		editor.assertProblems();
	}

	@Test
	void syntaxErrors() throws Exception {
		Editor editor = newEditor("""
					@Value("${db.url")
					String unclosed;

					@Value("${db url}")
					String space;
				""");
		editor.assertProblems(
				"${db.url|Placeholder is not closed",
				"url|Placeholder:");
	}

	@Test
	void placeholderInsideSpelIsLeftToTheSpelReconciler() throws Exception {
		Editor editor = newEditor("""
					@Value("#{${db.url}}")
					String spel;

					@Value("#{'${db.url}'.length()}-${db.other}")
					String mixed;
				""");
		editor.assertProblems("db.other|" + UNRESOLVED);
	}

	@Test
	void offsetsFollowEscapesInTheLiteral() throws Exception {
		Editor editor = newEditor("""
					@Value("a\\tb\\"c\\" ${db.url}")
					String escaped;
				""");
		editor.assertProblems("db.url|" + UNRESOLVED);
	}

	@Test
	void otherAnnotationsFromTheTable() throws Exception {
		Editor editor = newEditor("""
					@Scheduled(cron = "${app.cron}", zone = "${app.zone:UTC}")
					void cleanup() {}

					@Scheduled(fixedDelayString = "${app.delay}")
					void poll() {}

					@PropertySource({"classpath:fixed.properties", "${app.config.file}"})
					static class Config {}
				""");
		editor.assertProblems(
				"app.cron|" + UNRESOLVED,
				"app.delay|" + UNRESOLVED,
				"app.config.file|" + UNRESOLVED);
	}

	@Test
	void unlistedAttributeIsNotChecked() throws Exception {
		Editor editor = newEditor("""
					@Deprecated(since = "${not.checked}")
					String other;
				""");
		editor.assertProblems();
	}

	@Test
	void maskSpelExpressionsKeepsLength() {
		assertEquals("       -${b}", JdtPropertyPlaceholderReconciler.maskSpelExpressions("#{${a}}-${b}"));
		assertEquals("             -${b}", JdtPropertyPlaceholderReconciler.maskSpelExpressions("#{'}' + ${a}}-${b}"));
		assertEquals("      ", JdtPropertyPlaceholderReconciler.maskSpelExpressions("#{${a}"));
		assertEquals("${a}", JdtPropertyPlaceholderReconciler.maskSpelExpressions("${a}"));
	}

}
