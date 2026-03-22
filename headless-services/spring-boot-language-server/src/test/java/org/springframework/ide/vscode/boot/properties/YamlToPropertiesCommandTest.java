/*******************************************************************************
 * Copyright (c) 2024, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.languageserver.testharness.LanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import({IndexerTestConf.class})
public class YamlToPropertiesCommandTest {
	
	private static final String FILENAME_SUFFIX = "-conversion-test";
	
	@Autowired private LanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	
	private File directory;

	@BeforeEach public void setup() throws Exception {
		harness.intialize(null);
		
		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-validations/").toURI());
		
		Files.walk(directory.toPath(), Integer.MAX_VALUE).filter(Files::isRegularFile).filter(p -> p.getFileName().toString().contains(FILENAME_SUFFIX)).forEach(t -> {
			try {
				Files.delete(t);
			} catch (IOException e) {
				
			}
		});

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();
	}
	
	@AfterEach public void tearDown() throws Exception {
		if (directory != null && directory.exists()) {
			Files.walk(directory.toPath(), Integer.MAX_VALUE).filter(Files::isRegularFile).filter(p -> p.getFileName().toString().contains(FILENAME_SUFFIX)).forEach(t -> {
				try {
					Files.delete(t);
				} catch (IOException e) {
					
				}
			});
		}
	}

	@Test void simple() throws Exception {
		assertConversion("""
				some:
				  other:
				    thing: blah
				  thing: vvvv
				""",
				"""
				some.other.thing=blah
				some.thing=vvvv		
				""",
				true);
	}
	
	@Test void noReplacement() throws Exception {		
		assertConversion("""
				some:
				  other:
				    thing: blah
				  thing: vvvv
				""",
				"""
				some.other.thing=blah
				some.thing=vvvv		
				""",
				false);
	}
	
	@Test public void almostHasComments() throws Exception {
		assertConversion(
			"my:\n" +
			"  goodbye: 'See ya # later'\n" +
			"  hello: Good morning!\n"
			, // ==>
			"my.goodbye=See ya \\# later\n" +
			"my.hello=Good morning\\!\n",
			true
		);
	}
	
	@Test public void listItems() throws Exception {
		assertConversion(
				"some:\n" +
				"  thing:\n" +
				"  - a: first-a\n" +
				"    b: first-b\n" +
				"  - a: second-a\n" +
				"    b: second-b\n"
				, // ==>
				"some.thing[0].a=first-a\n" +
				"some.thing[0].b=first-b\n" +
				"some.thing[1].a=second-a\n" +
				"some.thing[1].b=second-b\n",
				true
		);
	}

	@Test public void list() throws Exception {
		assertConversion(
				"some:\n" +
				"  property:\n" +
				"  - something\n" +
				"  - something-else\n"
				, // ==>
				"some.property[0]=something\n" +
				"some.property[1]=something-else\n",
				true
		);
	}

	@Test public void mapAndSequenceConflict() throws Exception {
		assertConversion(
				"some:\n" +
				"  property:\n" +
				"    '0': zero\n" +
				"    '1': one\n" +
				"    abc: val1\n" +
				"    def: val2\n"
				,
				"some.property.0=zero\n" +
				"some.property.1=one\n" +
				"some.property.abc=val1\n" +
				"some.property.def=val2\n",
				true
		);
	}

	@Test public void multipleDocsConversion() throws Exception {
		assertConversion(
				"some:\n" +
				"  other:\n" +
				"    thing: blah\n" +
				"  thing: vvvv\n" +
				"\n" +
				"---\n" +
				"some:\n" +
				"  other:\n" +
				"    thing: blah\n" +
				"  thing: vvvv\n" +
				"\n" +
				"---\n" +
				"some:\n" +
				"  other:\n" +
				"    thing: blah\n" +
				"  thing: vvvv\n"
				, // ==>
				"some.other.thing=blah\n" +
				"some.thing=vvvv\n" +
				"#---\n" +
				"some.other.thing=blah\n" +
				"some.thing=vvvv\n" +
				"#---\n" +
				"some.other.thing=blah\n" +
				"some.thing=vvvv\n",
				true
		);
	}

	@Test void lineComment() throws Exception {
		assertConversion("""
				some:
				  # Comment about line	
				  other:
				    thing: blah
				  thing: vvvv
				""",
				"""
				# Conversion to YAML from Properties formar report
				# Warnings:
				# - The yaml file had comments which are lost in the refactoring!
				some.other.thing=blah
				some.thing=vvvv		
				""",
				true);
	}

	@Test void inlineComment() throws Exception {
		assertConversion("""
				some:	
				  other:
				    thing: blah # Inline Comment
				  thing: vvvv
				""",
				"""
				# Conversion to YAML from Properties formar report
				# Warnings:
				# - The yaml file had comments which are lost in the refactoring!
				some.other.thing=blah
				some.thing=vvvv		
				""",
				true);
	}
	
	void assertConversion(String yamlContent, String propsContent, boolean replace) throws Exception {
		Path yamlFilePath = directory.toPath().resolve("src/main/resources/application" + FILENAME_SUFFIX + ".yml");
		Path propsFilePath = directory.toPath().resolve("src/main/resources/application" + FILENAME_SUFFIX + ".properties");
		
		if (!Files.exists(yamlFilePath)) {
			Files.createDirectories(yamlFilePath.getParent());
			Files.createFile(yamlFilePath);
			Files.write(yamlFilePath, yamlContent.getBytes(), StandardOpenOption.APPEND);
		}
		
		Command cmd = new Command();
		cmd.setCommand(YamlToPropertiesCommand.CMD_YAML_TO_PROPS);
		cmd.setArguments(List.of(yamlFilePath.toUri().toASCIIString(), propsFilePath.toUri().toASCIIString(), replace));
		cmd.setTitle("Convert .yaml to .properties");
		
		ShowDocumentResult res = (ShowDocumentResult) harness.perform(cmd);
		
		assertThat(res.isSuccess()).isTrue();
		
		assertThat(Files.exists(propsFilePath)).isTrue();
		if (replace) {
			assertThat(Files.exists(yamlFilePath)).isFalse();
		} else {
			assertThat(Files.exists(yamlFilePath)).isTrue();
		}
		
		assertThat(Files.readString(propsFilePath).replace(System.lineSeparator(), "\n")).isEqualTo(propsContent.replace(System.lineSeparator(), "\n"));
	}

}
