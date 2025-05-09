/*******************************************************************************
 * Copyright (c) 2023, 2024 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.value.test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.AdHocPropertyHarnessTestConf;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import({ AdHocPropertyHarnessTestConf.class, ValueCompletionTest.TestConf.class })
public class ValueDefinitionProviderTest {

	@Autowired
	private BootLanguageServerHarness harness;
	@Autowired
	private IJavaProject testProject;

	private Set<Path> createdFiles = new HashSet<>();

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
	}

	@AfterEach
	public void tearDown() throws Exception {
		for (Path f : createdFiles) {
			Files.deleteIfExists(f);
		}
		createdFiles.clear();
	}

	private Path projectFile(String relativePath, String content) throws IOException {
		Path projectPath = Paths.get(testProject.getLocationUri());
		Path filePath = projectPath.resolve(relativePath);
		Files.createDirectories(filePath.getParent());
		Files.write(filePath, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE);
		createdFiles.add(filePath);
		return filePath;
	}

	@Test
	void propertiesCase_ValueAnnotation() throws Exception {
		Path propertiesFilePath = projectFile("src/main/resources/application.properties", "some.prop=5");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import org.springframework.beans.factory.annotation.Value;

				public class TestValueCompletion {

					@Value("${some.prop}")
					private String value1;
				}""");

		LocationLink expectedLocation = new LocationLink(propertiesFilePath.toUri().toASCIIString(),
				new Range(new Position(0, 0), new Position(0, 11)), new Range(new Position(0, 10), new Position(0, 11)),
				new Range(new Position(6, 8), new Position(6, 22)));

		editor.assertDefinitionLinkTargets("some.prop", List.of(expectedLocation));
	}

	@Test
	void yamlCase_ValueAnnotation() throws Exception {
		Path yamlFilePath = projectFile("src/main/resources/application.yml", """
				some:
				  prop: 5
				""");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import org.springframework.beans.factory.annotation.Value;

				public class TestValueCompletion {

					@Value("${some.prop}")
					private String value1;
				}""");

		LocationLink expectedLocation = new LocationLink(yamlFilePath.toUri().toASCIIString(),
				new Range(new Position(1, 2), new Position(1, 9)), new Range(new Position(1, 8), new Position(1, 9)),
				new Range(new Position(6, 8), new Position(6, 22)));

		editor.assertDefinitionLinkTargets("some.prop", List.of(expectedLocation));
	}
	
	@Test 
	void combinedCase_ValueAnnotation() throws Exception {
		Path propertiesFilePath = projectFile("src/main/resources/application.properties", "some.prop=5");
		Path yamlFilePath = projectFile("src/main/resources/application.yml", """
				some:
				  prop: 5
				""");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import org.springframework.beans.factory.annotation.Value;

				public class TestValueCompletion {

					@Value("${some.prop}")
					private String value1;
				}""");

		LocationLink expectedPropsLocation = new LocationLink(propertiesFilePath.toUri().toASCIIString(),
				new Range(new Position(0, 0), new Position(0, 11)), new Range(new Position(0, 10), new Position(0, 11)),
				new Range(new Position(6, 8), new Position(6, 22)));
		LocationLink expectedYamlLocation = new LocationLink(yamlFilePath.toUri().toASCIIString(),
				new Range(new Position(1, 2), new Position(1, 9)), new Range(new Position(1, 8), new Position(1, 9)),
				new Range(new Position(6, 8), new Position(6, 22)));
		
		editor.assertDefinitionLinkTargets("some.prop", List.of(expectedYamlLocation, expectedPropsLocation));

	}
	
	@Test
	void noValueCase_ConditionOnPropertyAnnotation() throws Exception {
		Path propertiesFilePath = projectFile("src/main/resources/application.properties", "some.prop=5");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

				@ConditionalOnProperty("some.prop")
				public class TestValueCompletion {

					private String value1;
				}""");

		LocationLink expectedLocation = new LocationLink(propertiesFilePath.toUri().toASCIIString(),
				new Range(new Position(0, 0), new Position(0, 11)), new Range(new Position(0, 10), new Position(0, 11)),
				new Range(new Position(4, 23), new Position(4, 34)));

		editor.assertDefinitionLinkTargets("some.prop", List.of(expectedLocation));
	}

	@Test
	void valueCase_ConditionOnPropertyAnnotation() throws Exception {
		Path propertiesFilePath = projectFile("src/main/resources/application.properties", "some.prop=5");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

				@ConditionalOnProperty(value = "some.prop")
				public class TestValueCompletion {

					private String value1;
				}""");

		LocationLink expectedLocation = new LocationLink(propertiesFilePath.toUri().toASCIIString(),
				new Range(new Position(0, 0), new Position(0, 11)), new Range(new Position(0, 10), new Position(0, 11)),
				new Range(new Position(4, 31), new Position(4, 42)));

		editor.assertDefinitionLinkTargets("some.prop", List.of(expectedLocation));
	}

	@Test
	void nameAndPrefixCase_ConditionOnPropertyAnnotation() throws Exception {
		Path propertiesFilePath = projectFile("src/main/resources/application.properties", "some.prop=5");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

				@ConditionalOnProperty(prefix = "some", name = "prop")
				public class TestValueCompletion {

					private String value1;
				}""");

		LocationLink expectedLocation = new LocationLink(propertiesFilePath.toUri().toASCIIString(),
				new Range(new Position(0, 0), new Position(0, 11)), new Range(new Position(0, 10), new Position(0, 11)),
				new Range(new Position(4, 47), new Position(4, 53)));

		editor.assertDefinitionLinkTargets("prop", List.of(expectedLocation));
	}

	@Test
	void testFindClasspathResource() throws Exception {
		Path randomResourceFilePath = projectFile("src/main/resources/random-resource.md", "");
		Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import org.springframework.beans.factory.annotation.Value;

				public class TestValueCompletion {

					@Value("classpath:random-resource.md")
					private String value1;
				}""");

		LocationLink expectedLocation = new LocationLink(randomResourceFilePath.toUri().toASCIIString(),
				new Range(new Position(0, 0), new Position(0, 0)),
				new Range(new Position(0, 0), new Position(0, 0)),
				new Range(new Position(6, 8), new Position(6, 38)));

		editor.assertDefinitionLinkTargets("classpath:random-resource.md", List.of(expectedLocation));
	}



}
