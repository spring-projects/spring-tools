/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.value.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.value.ValuePropertyReferencesProvider;
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
public class ValuePropertyReferenceFinderTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;
	private String tempJavaDocUri;
	private Path resourceDir;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-indexing/").toURI());

		String projectDir = directory.toURI().toString();
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();
		
        tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();
        resourceDir = directory.toPath().resolve("src/main/resources/");

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
	@Test
    void testFindReferenceAtBeginningPropFile() throws Exception {
        ValuePropertyReferencesProvider provider = new ValuePropertyReferencesProvider(projectFinder, springIndex);

        Path file = resourceDir.resolve("simple-case/application.properties");
        List<? extends Location> locations = provider.findReferences(file, "test.property");

        assertNotNull(locations);
        assertEquals(1, locations.size());
        Location location = locations.get(0);

        URI docURI = file.toUri();
        assertEquals(new Location(docURI.toString(), new Range(new Position(0, 0), new Position(0, 13))), location);
    }

    @Test
    void testFindReferenceAtBeginningYMLFile() throws Exception {
        ValuePropertyReferencesProvider provider = new ValuePropertyReferencesProvider(projectFinder, springIndex);

        Path file = resourceDir.resolve("simple-yml/application.yml");
        List<? extends Location> locations = provider.findReferences(file, "test.property");

        assertNotNull(locations);
        assertEquals(1, locations.size());
        Location location = locations.get(0);

        URI docURI = file.toUri();
        assertEquals(new Location(docURI.toString(), new Range(new Position(3, 2), new Position(3, 10))), location);
    }

    @Test
    void testFindReferenceWithinMultipleFiles() throws Exception {
        ValuePropertyReferencesProvider provider = new ValuePropertyReferencesProvider(projectFinder, springIndex);

        List<? extends Location> locations = provider.findReferencesFromPropertyFiles("appl1.prop");

        assertNotNull(locations);
        assertEquals(4, locations.size());

        Path file1 = resourceDir.resolve("mixed-multiple-files/application-dev.properties");
        Location location = getLocation(locations, file1.toUri());
        assertEquals(new Location(file1.toUri().toString(), new Range(new Position(1, 0), new Position(1, 10))), location);

        Path file2 = resourceDir.resolve("mixed-multiple-files/application.yml");
        location = getLocation(locations, file2.toUri());
        assertEquals(new Location(file2.toUri().toString(), new Range(new Position(3, 2), new Position(3, 6))), location);

        Path file3 = resourceDir.resolve("another-prop-folder/application.properties");
        location = getLocation(locations, file3.toUri());
        assertEquals(new Location(file3.toUri().toString(), new Range(new Position(1, 0), new Position(1, 10))), location);

        Path file4 = resourceDir.resolve("another-prop-folder/prod-application.properties");
        location = getLocation(locations, file4.toUri());
        assertEquals(new Location(file4.toUri().toString(), new Range(new Position(1, 0), new Position(1, 10))), location);
    }

	private Location getLocation(List<? extends Location> locations, URI docURI) {
		for (Location location : locations) {
			if (docURI.toString().equals(location.getUri())) {
				return location;
			}
		}

		return null;
	}

    @Test
    void testFindReferencesToPropertyFromValueAnnotationAtName() throws Exception {
    	harness.getServer().getWorkspaceService().setWorkspaceFolders(List.of(new WorkspaceFolder(directory.toURI().toString(), directory.getName())));
    	
    	String completionLine = "@Value(\"${my.<*>prop}\")";
    	
    	String editorContent = """
    			package org.test;

    			import 
    			""" +
    			Annotations.VALUE + ";" +
    			"""

    			@Component
    			""" +
    			completionLine + "\n" +
    			"""
    			public class TestDependsOnClass {
    			}
    			""";

    	Editor editor = harness.newEditor(LanguageId.JAVA, editorContent, tempJavaDocUri);
    	List<? extends Location> references = editor.getReferences();
    	
    	assertEquals(1, references.size());
    	
    	Location location = references.get(0);
    	assertEquals(directory.toPath().resolve("src/main/java/application.properties").toUri().toString(), location.getUri());
        assertEquals(new Location(location.getUri(), new Range(new Position(0, 0), new Position(0, 7))), location);
    }

    @Test
    void testFindReferencesToPropertyFromValueAnnotationAtPrefix() throws Exception {
    	harness.getServer().getWorkspaceService().setWorkspaceFolders(List.of(new WorkspaceFolder(directory.toURI().toString(), directory.getName())));
    	
    	String completionLine = "@Value(\"${<*>my.prop}\")";
    	
    	String editorContent = """
    			package org.test;

    			import 
    			""" +
    			Annotations.VALUE + ";" +
    			"""

    			@Component
    			""" +
    			completionLine + "\n" +
    			"""
    			public class TestDependsOnClass {
    			}
    			""";

    	Editor editor = harness.newEditor(LanguageId.JAVA, editorContent, tempJavaDocUri);
    	List<? extends Location> references = editor.getReferences();
    	
    	assertEquals(1, references.size());
    	
    	Location location = references.get(0);
    	assertEquals(directory.toPath().resolve("src/main/java/application.properties").toUri().toString(), location.getUri());
        assertEquals(new Location(location.getUri(), new Range(new Position(0, 0), new Position(0, 7))), location);
    }
    
    @Test
    void testFindReferenceForPropertiesUsedInAnnotations() throws Exception {
        ValuePropertyReferencesProvider provider = new ValuePropertyReferencesProvider(projectFinder, springIndex);

        List<? extends Location> locations = provider.findReferencesToPropertyKey("my.prop2");

        assertNotNull(locations);
        assertEquals(7, locations.size());

        URI propertiesFile = directory.toPath().resolve("src/main/java/application.properties").toUri();
        Location location = getLocation(locations, propertiesFile);
        assertEquals(new Location(propertiesFile.toString(), new Range(new Position(1, 0), new Position(1, 8))), location);

        URI javaFile = directory.toPath().resolve("src/main/java/org/test/properties/PropertyUsageWithValue.java").toUri();
        location = getLocation(locations, javaFile);
        assertEquals(new Location(javaFile.toString(), new Range(new Position(8, 8), new Position(8, 21))), location);

        javaFile = directory.toPath().resolve("src/main/java/org/test/properties/PropertyUsageWithConditional.java").toUri();
        location = getLocation(locations, javaFile);
        assertEquals(new Location(javaFile.toString(), new Range(new Position(6, 23), new Position(6, 33))), location);

        javaFile = directory.toPath().resolve("src/main/java/org/test/properties/PropertyUsageWithConditionalAndArray.java").toUri();
        location = getLocation(locations, javaFile);
        assertEquals(new Location(javaFile.toString(), new Range(new Position(6, 31), new Position(6, 41))), location);

        javaFile = directory.toPath().resolve("src/main/java/org/test/properties/PropertyUsageWithConditionalWithArrayAndPrefix.java").toUri();
        location = getLocation(locations, javaFile);
        assertEquals(new Location(javaFile.toString(), new Range(new Position(6, 46), new Position(6, 53))), location);

        javaFile = directory.toPath().resolve("src/main/java/org/test/properties/PropertyUsageWithConstantInConditionalAnnotation.java").toUri();
        location = getLocation(locations, javaFile);
        assertEquals(new Location(javaFile.toString(), new Range(new Position(6, 23), new Position(6, 52))), location);

        javaFile = directory.toPath().resolve("src/main/java/org/test/properties/PropertyUsageWithConstantInValueAnnotation.java").toUri();
        location = getLocation(locations, javaFile);
        assertEquals(new Location(javaFile.toString(), new Range(new Position(8, 8), new Position(8, 41))), location);
    }

}
