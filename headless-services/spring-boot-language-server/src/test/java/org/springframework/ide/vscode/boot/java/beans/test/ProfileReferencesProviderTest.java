/*******************************************************************************
 * Copyright (c) 2024 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.beans.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Location;
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
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class ProfileReferencesProviderTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-indexing/").toURI());

		String projectDir = directory.toURI().toString();
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
	@Test
	public void testProfileRefersToOtherProfiles() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.stereotype.Component;
				import org.springframework.context.annotation.Profile;

				@Component
				@Profile("pro<*>file1")
				public class TestDependsOnClass {
				}""", tempJavaDocUri);
		
		List<? extends Location> references = editor.getReferences();
		assertEquals(2, references.size());
		
		String expectedDefinitionUri1 = directory.toPath().resolve("src/main/java/org/test/profiles/ProfilesClass1.java").toUri().toString();
		Location expectedLocation1 = new Location(expectedDefinitionUri1,
				new Range(new Position(6, 9), new Position(6, 19)));
		
		assertTrue(references.contains(expectedLocation1));

		String expectedDefinitionUri2 = directory.toPath().resolve("src/main/java/org/test/profiles/ProfilesClassWithArray.java").toUri().toString();
		Location expectedLocation2 = new Location(expectedDefinitionUri2,
				new Range(new Position(6, 18), new Position(6, 28)));

		assertTrue(references.contains(expectedLocation2));
	}

	@Test
	public void testProfileRefersToOtherProfilesWithConcatenatedString() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.stereotype.Component;
				import org.springframework.context.annotation.Profile;

				@Component
				@Profile("pro" + "f<*>ile1")
				public class TestDependsOnClass {
				}""", tempJavaDocUri);
		
		List<? extends Location> references = editor.getReferences();
		assertEquals(2, references.size());
		
		String expectedDefinitionUri1 = directory.toPath().resolve("src/main/java/org/test/profiles/ProfilesClass1.java").toUri().toString();
		Location expectedLocation1 = new Location(expectedDefinitionUri1,
				new Range(new Position(6, 9), new Position(6, 19)));
		
		assertTrue(references.contains(expectedLocation1));

		String expectedDefinitionUri2 = directory.toPath().resolve("src/main/java/org/test/profiles/ProfilesClassWithArray.java").toUri().toString();
		Location expectedLocation2 = new Location(expectedDefinitionUri2,
				new Range(new Position(6, 18), new Position(6, 28)));

		assertTrue(references.contains(expectedLocation2));
	}

	@Test
	public void testProfileWithinArrayRefersToOtherProfiles() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.stereotype.Component;
				import org.springframework.context.annotation.Profile;

				@Component
				@Profile(value = {"profile2", "pro<*>file1"})
				public class TestDependsOnClass {
				}""", tempJavaDocUri);
		
		List<? extends Location> references = editor.getReferences();
		assertEquals(2, references.size());

		String expectedDefinitionUri1 = directory.toPath().resolve("src/main/java/org/test/profiles/ProfilesClass1.java").toUri().toString();
		Location expectedLocation1 = new Location(expectedDefinitionUri1,
				new Range(new Position(6, 9), new Position(6, 19)));

		assertTrue(references.contains(expectedLocation1));
		
		String expectedDefinitionUri2 = directory.toPath().resolve("src/main/java/org/test/profiles/ProfilesClassWithArray.java").toUri().toString();
		Location expectedLocation2 = new Location(expectedDefinitionUri2,
				new Range(new Position(6, 18), new Position(6, 28)));
		
		assertTrue(references.contains(expectedLocation2));
	}

}
