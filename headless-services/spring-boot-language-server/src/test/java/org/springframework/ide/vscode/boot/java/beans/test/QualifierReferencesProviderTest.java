/*******************************************************************************
 * Copyright (c) 2024, 2025 Broadcom
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
import static org.junit.jupiter.api.Assertions.assertNull;

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
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
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
public class QualifierReferencesProviderTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;
	private IJavaProject project;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-indexing/").toURI());

		String projectDir = directory.toURI().toString();
		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
	@Test
	public void testQualifierRefersToBean() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.stereotype.Component;
				import org.springframework.beans.factory.annotation.Qualifier;

				@Component
				@Qualifier("be<*>an1")
				public class TestDependsOnClass {
				}""", tempJavaDocUri);
		
        String expectedDefinitionUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();
        
        Bean[] beans = springIndex.getBeansWithName(project.getElementName(), "bean1");
        assertEquals(1, beans.length);

		Location expectedLocation = new Location(expectedDefinitionUri,
				beans[0].getLocation().getRange());
		
		List<? extends Location> references = editor.getReferences();
		assertEquals(1, references.size());
		
		Location foundLocation = references.get(0);
		assertEquals(expectedLocation, foundLocation);
	}

	@Test
	public void testQualifierRefersToBeanFromNameAttribute() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.stereotype.Component;
				import org.springframework.beans.factory.annotation.Qualifier;

				@Component
				@Qualifier(value = "be<*>an1")
				public class TestDependsOnClass {
				}""", tempJavaDocUri);
		
        String expectedDefinitionUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();
        
        Bean[] beans = springIndex.getBeansWithName(project.getElementName(), "bean1");
        assertEquals(1, beans.length);

		Location expectedLocation = new Location(expectedDefinitionUri,
				beans[0].getLocation().getRange());
		
		List<? extends Location> references = editor.getReferences();
		assertEquals(1, references.size());
		
		Location foundLocation = references.get(0);
		assertEquals(expectedLocation, foundLocation);
	}

	@Test
	public void testQualifierRefersToOtherQualifier() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.stereotype.Component;
				import org.springframework.beans.factory.annotation.Qualifier;

				@Component
				@Qualifier("qualif<*>ier")
				public class TestDependsOnClass {
				}""", tempJavaDocUri);
		
        String expectedDefinitionUri = directory.toPath().resolve("src/main/java/org/test/injections/ConfigurationWithInjectionsAndAnnotations.java").toUri().toString();
        
		Location expectedLocation = new Location(expectedDefinitionUri,
				new Range(new Position(12, 11), new Position(12, 22)));

		List<? extends Location> references = editor.getReferences();
		assertEquals(1, references.size());

		Location foundLocation = references.get(0);
		assertEquals(expectedLocation, foundLocation);
	}

	@Test
	public void testQualifierWithConcatenatedStrings() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.stereotype.Component;
				import org.springframework.beans.factory.annotation.Qualifier;

				@Component
				@Qualifier("qual" + "if<*>ier")
				public class TestDependsOnClass {
				}""", tempJavaDocUri);
		
        String expectedDefinitionUri = directory.toPath().resolve("src/main/java/org/test/injections/ConfigurationWithInjectionsAndAnnotations.java").toUri().toString();
        
		Location expectedLocation = new Location(expectedDefinitionUri,
				new Range(new Position(12, 11), new Position(12, 22)));

		List<? extends Location> references = editor.getReferences();
		assertEquals(1, references.size());

		Location foundLocation = references.get(0);
		assertEquals(expectedLocation, foundLocation);
	}

	@Test
	public void testQualifierRefersToBeanFromNameAttributeWithConcatenatedStrings() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.stereotype.Component;
				import org.springframework.beans.factory.annotation.Qualifier;

				@Component
				@Qualifier(value = "be" + "<*>an1")
				public class TestDependsOnClass {
				}""", tempJavaDocUri);
		
        String expectedDefinitionUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();
        
        Bean[] beans = springIndex.getBeansWithName(project.getElementName(), "bean1");
        assertEquals(1, beans.length);

		Location expectedLocation = new Location(expectedDefinitionUri,
				beans[0].getLocation().getRange());
		
		List<? extends Location> references = editor.getReferences();
		assertEquals(1, references.size());
		
		Location foundLocation = references.get(0);
		assertEquals(expectedLocation, foundLocation);
	}

	@Test
	public void testQualifierRefersToNothing() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.stereotype.Component;
				import org.springframework.beans.factory.annotation.Qualifier;

				@Component
				@Qualifier("nonExisting<*>Bean")
				public class TestDependsOnClass {
				}""", tempJavaDocUri);
		
		List<? extends Location> references = editor.getReferences();
		assertNull(references);
	}

}
