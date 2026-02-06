/*******************************************************************************
 * Copyright (c) 2024, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.conditionals.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Location;
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
import org.springframework.ide.vscode.boot.java.Annotations;
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
public class ConditionalOnPropertyReferenceFinderTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;
	private String tempJavaDocUri;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-indexing/").toURI());

		String projectDir = directory.toURI().toString();
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();
		
        tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
    @Test
    void testFindReferencesToPropertyFromAnnotation() throws Exception {
    	harness.getServer().getWorkspaceService().setWorkspaceFolders(List.of(new WorkspaceFolder(directory.toURI().toString(), directory.getName())));
    	
    	List<? extends Location> references = getReferences("@ConditionalOnProperty(\"my.<*>prop\")");
    	
    	assertEquals(1, references.size());
    	
    	Location location = references.get(0);
    	assertEquals(directory.toPath().resolve("src/main/java/application.properties").toUri().toString(), location.getUri());
    	assertEquals(0, location.getRange().getStart().getLine());
    	assertEquals(0, location.getRange().getStart().getCharacter());
    	assertEquals(0, location.getRange().getEnd().getLine());
    	assertEquals(7, location.getRange().getEnd().getCharacter());
    }
    
    @Test
    void testFindReferencesToPropertyFromAnnotationWithNameAttribute() throws Exception {
    	harness.getServer().getWorkspaceService().setWorkspaceFolders(List.of(new WorkspaceFolder(directory.toURI().toString(), directory.getName())));
    	
    	List<? extends Location> references = getReferences("@ConditionalOnProperty(name = \"my.<*>prop\")");
    	
    	assertEquals(1, references.size());
    	
    	Location location = references.get(0);
    	assertEquals(directory.toPath().resolve("src/main/java/application.properties").toUri().toString(), location.getUri());
    	assertEquals(0, location.getRange().getStart().getLine());
    	assertEquals(0, location.getRange().getStart().getCharacter());
    	assertEquals(0, location.getRange().getEnd().getLine());
    	assertEquals(7, location.getRange().getEnd().getCharacter());
    }
    
    @Test
    void testFindReferencesToPropertyFromAnnotationWithPrefix() throws Exception {
    	harness.getServer().getWorkspaceService().setWorkspaceFolders(List.of(new WorkspaceFolder(directory.toURI().toString(), directory.getName())));
    	
    	List<? extends Location> references = getReferences("@ConditionalOnProperty(prefix=\"my\", name=\"<*>prop\")");
    	
    	assertEquals(1, references.size());
    	
    	Location location = references.get(0);
    	assertEquals(directory.toPath().resolve("src/main/java/application.properties").toUri().toString(), location.getUri());
    	assertEquals(0, location.getRange().getStart().getLine());
    	assertEquals(0, location.getRange().getStart().getCharacter());
    	assertEquals(0, location.getRange().getEnd().getLine());
    	assertEquals(7, location.getRange().getEnd().getCharacter());
    }
    
    @Test
    void testFindReferencesToPropertyFromAnnotationWithPrefixOnPrefix() throws Exception {
    	harness.getServer().getWorkspaceService().setWorkspaceFolders(List.of(new WorkspaceFolder(directory.toURI().toString(), directory.getName())));
    	
    	List<? extends Location> references = getReferences("@ConditionalOnProperty(prefix=\"<*>my\", name=\"prop\")");
    	
    	assertEquals(1, references.size());
    	
    	Location location = references.get(0);
    	assertEquals(directory.toPath().resolve("src/main/java/application.properties").toUri().toString(), location.getUri());
    	assertEquals(0, location.getRange().getStart().getLine());
    	assertEquals(0, location.getRange().getStart().getCharacter());
    	assertEquals(0, location.getRange().getEnd().getLine());
    	assertEquals(7, location.getRange().getEnd().getCharacter());
    }
    
    private List<? extends Location> getReferences(String completionLine) throws Exception {
    	harness.getServer().getWorkspaceService().setWorkspaceFolders(List.of(new WorkspaceFolder(directory.toURI().toString(), directory.getName())));
    	
    	String editorContent = """
    			package org.test;

    			import 
    			""" +
    			Annotations.CONDITIONAL_ON_PROPERTY + ";" +
    			"""

    			@Component
    			""" +
    			completionLine + "\n" +
    			"""
    			public class TestDependsOnClass {
    			}
    			""";

    	Editor editor = harness.newEditor(LanguageId.JAVA, editorContent, tempJavaDocUri);
    	return editor.getReferences();
    }
}
