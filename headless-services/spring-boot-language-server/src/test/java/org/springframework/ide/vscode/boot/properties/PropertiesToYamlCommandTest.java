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
public class PropertiesToYamlCommandTest {

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

	void assertConversion(String propsContent, String yamlContent, boolean replace) throws Exception {
		Path yamlFilePath = directory.toPath().resolve("src/main/resources/application" + FILENAME_SUFFIX + ".yml");
		Path propsFilePath = directory.toPath().resolve("src/main/resources/application" + FILENAME_SUFFIX + ".properties");
		
		if (!Files.exists(propsFilePath)) {
			Files.createDirectories(propsFilePath.getParent());
			Files.createFile(propsFilePath);
			Files.write(propsFilePath, propsContent.getBytes(), StandardOpenOption.APPEND);
		}
		
		Command cmd = new Command();
		cmd.setCommand(PropertiesToYamlCommand.CMD_PROPS_TO_YAML);
		cmd.setArguments(List.of(propsFilePath.toUri().toASCIIString(), yamlFilePath.toUri().toASCIIString(), replace));
		cmd.setTitle("Convert .properties to .yaml");
		
		ShowDocumentResult res = (ShowDocumentResult) harness.perform(cmd);
		
		assertThat(res.isSuccess()).isTrue();
		
		assertThat(Files.exists(yamlFilePath)).isTrue();
		if (replace) {
			assertThat(Files.exists(propsFilePath)).isFalse();
		} else {
			assertThat(Files.exists(propsFilePath)).isTrue();
		}
		
		assertThat(Files.readString(yamlFilePath)).isEqualTo(yamlContent);
	}
	
	@Test void almostHasComments() throws Exception {
		assertConversion(
			"my.hello=Good morning!\n" +
			"my.goodbye=See ya # later\n"
			, // ==>
			"my:\n" +
			"  hello: Good morning!\n" +
			"  goodbye: 'See ya # later'\n",
			true
		);
	}


	@Test void listItems() throws Exception {
		assertConversion(
				"some.thing[0].a=first-a\n" +
				"some.thing[0].b=first-b\n" +
				"some.thing[1].a=second-a\n" +
				"some.thing[1].b=second-b\n"
				, // ==>
				"some:\n" +
				"  thing:\n" +
				"  - a: first-a\n" +
				"    b: first-b\n" +
				"  - a: second-a\n" +
				"    b: second-b\n",
				true
		);
	}

	@Test void simple() throws Exception {
		assertConversion(
				"some.thing=vvvv\n" +
				"some.other.thing=blah\n"
				, // ==>
				"some:\n" +
				"  thing: vvvv\n" +
				"  other:\n" +
				"    thing: blah\n",
				true
		);
	}

	@Test void noReplacement() throws Exception {
		assertConversion(
				"some.thing=vvvv\n" +
				"some.other.thing=blah\n"
				, // ==>
				"some:\n" +
				"  thing: vvvv\n" +
				"  other:\n" +
				"    thing: blah\n",
				false
		);
	}
	
	@Test void nonStringyValueConversion() throws Exception {
		//See: https://www.pivotaltracker.com/story/show/154181583
		//Test that we do not add unnecessary quotes around certain types of values.
		assertConversion(
				"exponated=123.4E-12\n" +
				"server.port=8888\n" +
				"foobar.enabled=true\n" +
				"foobar.nice=false\n" +
				"fractional=0.78\n" +
				"largenumber=989898989898989898989898989898989898989898989898989898989898\n" +
				"longfractional=-0.989898989898989898989898989898989898989898989898989898989898\n"
				, // ==>
				"exponated: '123.4E-12'\n" + //quotes are added because conversion to number changes the string value
				"server:\n" +
				"  port: 8888\n" +
				"foobar:\n" +
				"  enabled: true\n" +
				"  nice: false\n" +
				"fractional: 0.78\n" +
				"largenumber: 989898989898989898989898989898989898989898989898989898989898\n" +
				"longfractional: -0.989898989898989898989898989898989898989898989898989898989898\n",
				true
		);
	}

	@Test void emptyFileConversion() throws Exception {
		assertConversion(
				""
				, // ==>
				"",
				true
		);
	}

	@Test void multipleAssignmentProblem() throws Exception {
		assertConversion(
				"some.property=something\n" +
				"some.property=something-else"
				, // ==>
				"# Conversion to YAML from Properties formar report\n" +
				"# Warnings:\n" +
				"# - Multiple values [something, something-else] assigned to 'some.property'. Values are merged into a yaml sequence node.\n" +
				"some:\n" +
				"  property:\n" +
				"  - something\n" +
				"  - something-else\n"
 				,
 				true
		);
	}

	@Test void scalarAndMapConflict() throws Exception {
		assertConversion(
				"some.property=a-scalar\n" +
				"some.property.sub=sub-value"
				,
				"# Conversion to YAML from Properties formar report\n" +
				"# Errors:\n" +
				"# - Direct assignment 'some.property=a-scalar' can not be combined with sub-property assignment 'some.property.sub...'. Direct assignment is dropped!\n" +
				"some:\n" +
				"  property:\n" +
				"    sub: sub-value\n"
				,
				true
		);
	}

	@Test void scalarAndSequenceConflict() throws Exception {
		assertConversion(
				"some.property=a-scalar\n" +
				"some.property[0]=zero\n" +
				"some.property[1]=one\n"
				,
				"# Conversion to YAML from Properties formar report\n" +
				"# Errors:\n" +
				"# - Direct assignment 'some.property=a-scalar' can not be combined with sequence assignment 'some.property[0]...'. Direct assignments are dropped!\n" +
				"some:\n" +
				"  property:\n" +
				"  - zero\n" +
				"  - one\n"
				,
				true
		);
	}

	@Test public void mapAndSequenceConflict() throws Exception {
		assertConversion(
				"some.property.abc=val1\n" +
				"some.property.def=val2\n" +
				"some.property[0]=zero\n" +
				"some.property[1]=one\n"
				,
				"# Conversion to YAML from Properties formar report\n" +
				"# Warnings:\n" +
				"# - 'some.property' has some entries that look like list items and others that look like map entries. All these entries are treated as map entries!\n" +
				"some:\n" +
				"  property:\n" +
				"    abc: val1\n" +
				"    def: val2\n" +
				"    '0': zero\n" +
				"    '1': one\n"
				,
				true
		);
	}

	@Test public void scalarAndMapAndSequenceConflict() throws Exception {
		assertConversion(
				"some.property=a-scalar\n" +
				"some.property.abc=val1\n" +
				"some.property.def=val2\n" +
				"some.property[0]=zero\n" +
				"some.property[1]=one\n"
				,
				"# Conversion to YAML from Properties formar report\n" +
				"# Errors:\n" +
				"# - Direct assignment 'some.property=a-scalar' can not be combined with sub-property assignment 'some.property.abc...'. Direct assignment is dropped!\n" +
				"# Warnings:\n" +
				"# - 'some.property' has some entries that look like list items and others that look like map entries. All these entries are treated as map entries!\n" +
				"some:\n" +
				"  property:\n" +
				"    abc: val1\n" +
				"    def: val2\n" +
				"    '0': zero\n" +
				"    '1': one\n"
				,
				true
		);
	}
	
	@Test void lineComment() throws Exception {
		assertConversion(
				"some.thing=vvvv\n" +
				"# Line comment\n" +
				"some.other.thing=blah\n"
				, // ==>
				"# Conversion to YAML from Properties formar report\n" +
				"# Warnings:\n" +
				"# - The yaml file had comments which are lost in the refactoring!\n" +
				"some:\n" +
				"  thing: vvvv\n" +
				"  other:\n" +
				"    thing: blah\n",
				true
		);
	}

}
