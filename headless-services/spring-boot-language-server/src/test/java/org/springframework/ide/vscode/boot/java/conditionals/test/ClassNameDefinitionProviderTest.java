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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.AdHocPropertyHarnessTestConf;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.test.DefinitionLinkAsserts;
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
public class ClassNameDefinitionProviderTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private MavenJavaProject project;
	@Autowired private DefinitionLinkAsserts definitionLinkAsserts;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = Paths.get(project.getLocationUri()).toFile();
	}

	@Test
	void testLinkToProjectType() throws Exception {
		Editor editor = editor("@ConditionalOnClass(name = \"org.test.ClassConditionTarget\")");

		definitionLinkAsserts.assertLinkTargets(editor, "org.test.ClassConditionTarget", project,
				editor.rangeOf("\"org.test.ClassConditionTarget\""), "org.test.ClassConditionTarget");
	}

	@Test
	void testLinkToNestedProjectType() throws Exception {
		Editor editor = editor("@ConditionalOnClass(name = \"org.test.ClassConditionTarget$Nested\")");

		definitionLinkAsserts.assertLinkTargets(editor, "org.test.ClassConditionTarget$Nested", project,
				editor.rangeOf("\"org.test.ClassConditionTarget$Nested\""), "org.test.ClassConditionTarget$Nested");
	}

	@Test
	void testLinkFromArrayElement() throws Exception {
		Editor editor = editor("@ConditionalOnClass(name = {\"org.test.Unknown\", \"org.test.ClassConditionTarget\"})");

		definitionLinkAsserts.assertLinkTargets(editor, "org.test.ClassConditionTarget", project,
				editor.rangeOf("\"org.test.ClassConditionTarget\""), "org.test.ClassConditionTarget");
	}

	@Test
	void testNoLinkForUnknownType() throws Exception {
		Editor editor = editor("@ConditionalOnClass(name = \"org.test.DoesNotExist\")");

		editor.assertDefinitionLinkTargets("org.test.DoesNotExist", List.of());
	}

	@Test
	void testLinkFromConditionalOnMissingClass() throws Exception {
		Editor editor = editor("@ConditionalOnMissingClass(\"org.test.ClassConditionTarget\")");

		definitionLinkAsserts.assertLinkTargets(editor, "org.test.ClassConditionTarget", project,
				editor.rangeOf("\"org.test.ClassConditionTarget\""), "org.test.ClassConditionTarget");
	}

	@Test
	void testLinkFromConditionalOnMissingClassValueAttribute() throws Exception {
		Editor editor = editor("@ConditionalOnMissingClass(value = {\"org.test.Unknown\", \"org.test.ClassConditionTarget\"})");

		definitionLinkAsserts.assertLinkTargets(editor, "org.test.ClassConditionTarget", project,
				editor.rangeOf("\"org.test.ClassConditionTarget\""), "org.test.ClassConditionTarget");
	}

	@Test
	void testNoLinkForOtherAttribute() throws Exception {
		// a string attribute that holds a property name, not a class name
		Editor editor = editor("@ConditionalOnProperty(name = \"org.test.ClassConditionTarget\")");

		editor.assertDefinitionLinkTargets("org.test.ClassConditionTarget", List.of());
	}

	@Test
	void testLinkTurnedOffByFeatureFlag() throws Exception {
		harness.changeConfiguration(Map.of("boot-java", Map.of("feature-flags", Map.of("class-name-navigation", false))));
		// a configuration change re-indexes the project, wait for it before asserting
		indexer.waitOperation().get(5, TimeUnit.SECONDS);

		Editor editor = editor("@ConditionalOnClass(name = \"org.test.ClassConditionTarget\")");

		editor.assertDefinitionLinkTargets("org.test.ClassConditionTarget", List.of());
	}

	private Editor editor(String annotation) throws Exception {
		String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempConditionalOnClassDefinition.java").toUri().toString();
		return harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
				import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
				import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
				import org.springframework.context.annotation.Configuration;

				@Configuration
				%s
				public class TempConditionalOnClassDefinition {
				}
				""".formatted(annotation), tempJavaDocUri);
	}

}
