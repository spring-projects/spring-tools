/*******************************************************************************
 * Copyright (c) 2023, 2025 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.index.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
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
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class SpringMetamodelIndexingTest {
	
	public static final int NO_OF_EXPECTED_BEANS = 31;

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
	void testUpdateNotificationAfterProjectCreation() {
		assertEquals(1, harness.getIndexUpdatedCount());
		assertEquals(1, harness.getIndexUpdatedDetails(0).getAffectedProjects().size());
		assertEquals("test-spring-indexing", harness.getIndexUpdatedDetails(0).getAffectedProjects().iterator().next());
	}
	
    @Test
    void testDeleteProject() throws Exception {
    	Bean[] beans = springIndex.getBeansOfProject("test-spring-indexing");
    	assertEquals(NO_OF_EXPECTED_BEANS, beans.length);

    	CompletableFuture<Void> deleteProject = indexer.deleteProject(project);
        deleteProject.get(5, TimeUnit.SECONDS);
        
        Bean[] noBeansAnymore = springIndex.getBeansOfProject("test-spring-indexing");
        assertEquals(0, noBeansAnymore.length);
        
		assertEquals(2, harness.getIndexUpdatedCount()); // 1x project created, 1x project deleted
		assertEquals(1, harness.getIndexUpdatedDetails(0).getAffectedProjects().size());
		assertEquals(1, harness.getIndexUpdatedDetails(1).getAffectedProjects().size());
		assertEquals("test-spring-indexing", harness.getIndexUpdatedDetails(0).getAffectedProjects().iterator().next());
		assertEquals("test-spring-indexing", harness.getIndexUpdatedDetails(1).getAffectedProjects().iterator().next());
    }
    
    @Test
    void testRemoveSymbolsFromDeletedDocument() throws Exception {
        // update document and update index
        String deletedDocURI = directory.toPath().resolve("src/main/java/org/test/injections/ConstructorInjectionService.java").toUri().toString();

        Bean[] allBeansOfProject = springIndex.getBeansOfProject("test-spring-indexing");
        assertEquals(NO_OF_EXPECTED_BEANS, allBeansOfProject.length);

        Bean[] beans = springIndex.getBeansOfDocument(deletedDocURI);
        assertEquals(1, beans.length);
        
        CompletableFuture<Void> deleteFuture = indexer.deleteDocument(deletedDocURI);
        deleteFuture.get(5, TimeUnit.SECONDS);

        // check for updated index per document
        Bean[] noBeansAnymore = springIndex.getBeansOfDocument(deletedDocURI);
        assertEquals(0, noBeansAnymore.length);

        // check for updated index in all symbols
        Bean[] lessBeansOfProject = springIndex.getBeansOfProject("test-spring-indexing");
        assertEquals(allBeansOfProject.length - 1, lessBeansOfProject.length);
        
		assertEquals(2, harness.getIndexUpdatedCount()); // 1x project created, 1x document deleted
		assertEquals(1, harness.getIndexUpdatedDetails(0).getAffectedProjects().size());
		assertEquals(1, harness.getIndexUpdatedDetails(1).getAffectedProjects().size());
		assertEquals("test-spring-indexing", harness.getIndexUpdatedDetails(0).getAffectedProjects().iterator().next());
		assertEquals("test-spring-indexing", harness.getIndexUpdatedDetails(1).getAffectedProjects().iterator().next());
    }
    
    @Test
    void testUpdateChangedDocument() throws Exception {
        // update document and update index
        String changedDocURI = directory.toPath().resolve("src/main/java/org/test/injections/ConfigurationWithoutInjection.java").toUri().toString();

        Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "beanWithoutInjections");
        assertEquals(1, beans.length);
        assertEquals(0, beans[0].getInjectionPoints().length);

        String newContent = FileUtils.readFileToString(new File(new URI(changedDocURI)), Charset.defaultCharset()).replace("beanWithoutInjections()", "beanNowWithOneInjection(BeanClass1 bean1)");
        CompletableFuture<Void> updateFuture = indexer.updateDocument(changedDocURI, newContent, "test triggered");
        updateFuture.get(5, TimeUnit.SECONDS);
        
        Bean[] oldBeans = springIndex.getBeansWithName("test-spring-indexing", "beanWithoutInjections");
        assertEquals(0, oldBeans.length);
        
        Bean[] updatedBeans = springIndex.getBeansWithName("test-spring-indexing", "beanNowWithOneInjection");
        assertEquals(1, updatedBeans.length);
        assertEquals("beanNowWithOneInjection", updatedBeans[0].getName());
        assertEquals("org.test.BeanClass1", updatedBeans[0].getType());
        
        InjectionPoint[] updatedInjectionPoints = updatedBeans[0].getInjectionPoints();
        assertEquals(1, updatedInjectionPoints.length);
        assertEquals("org.test.BeanClass1", updatedInjectionPoints[0].getType());
        assertEquals("bean1", updatedInjectionPoints[0].getName());

		assertEquals(2, harness.getIndexUpdatedCount()); // 1x project created, 1x document updated
		assertEquals(1, harness.getIndexUpdatedDetails(0).getAffectedProjects().size());
		assertEquals(1, harness.getIndexUpdatedDetails(1).getAffectedProjects().size());
		assertEquals("test-spring-indexing", harness.getIndexUpdatedDetails(0).getAffectedProjects().iterator().next());
		assertEquals("test-spring-indexing", harness.getIndexUpdatedDetails(1).getAffectedProjects().iterator().next());
    }

    @Test
    void testUpdatedDocumentHasNoIndexElementsAnymore() throws Exception {
        String changedDocURI = directory.toPath().resolve("src/main/java/org/test/SimpleComponentClass.java").toUri().toString();

        Bean[] beans = springIndex.getBeansOfDocument(changedDocURI);
        assertEquals(1, beans.length);
        assertEquals("simpleComponentClass", beans[0].getName());
        
        String newContent = FileUtils.readFileToString(new File(new URI(changedDocURI)), Charset.defaultCharset()).replace("@Component", "");
        CompletableFuture<Void> updateFuture = indexer.updateDocument(changedDocURI, newContent, "test triggered");
        updateFuture.get(5, TimeUnit.SECONDS);
        
        Bean[] updatedBeans = springIndex.getBeansOfDocument(changedDocURI);
        assertEquals(0, updatedBeans.length);
    }

    @Test
    void testNewDocumentCreated() throws Exception {

        // TODO
    	
        String createdDocURI = directory.toPath().resolve("src/main/java/org/test/CreatedClass.java").toUri().toString();

        try {
            // create document and update index
            String content = "package org.test;\n"
            		+ "\n"
            		+ "import org.springframework.context.annotation.Bean;\n"
            		+ "import org.springframework.context.annotation.Configuration;\n"
            		+ "import org.test.BeanClass1;\n"
            		+ "\n"
            		+ "@Configuration\n"
            		+ "public class CreatedClass {\n"
            		+ "\n"
            		+ "	@Bean\n"
            		+ "	BeanClass1 createdClassBean() {\n"
            		+ "		return new BeanClass1();\n"
            		+ "	}\n"
            		+ "\n"
            		+ "}\n"
            		+ "" +
                    "";
            FileUtils.write(new File(new URI(createdDocURI)), content, Charset.defaultCharset());
            CompletableFuture<Void> createFuture = indexer.createDocument(createdDocURI);
            createFuture.get(5, TimeUnit.SECONDS);

            // check for updated index per document
            Bean[] newBeans = springIndex.getBeansOfDocument(createdDocURI);
            assertEquals(2, newBeans.length);
            
            assertEquals("createdClass", newBeans[0].getName());
            assertEquals("org.test.CreatedClass", newBeans[0].getType());

            assertEquals("createdClassBean", newBeans[1].getName());
            assertEquals("org.test.BeanClass1", newBeans[1].getType());
            
    		assertEquals(2, harness.getIndexUpdatedCount()); // 1x project created, 1x new document created
    		assertEquals(1, harness.getIndexUpdatedDetails(0).getAffectedProjects().size());
    		assertEquals(1, harness.getIndexUpdatedDetails(1).getAffectedProjects().size());
    		assertEquals("test-spring-indexing", harness.getIndexUpdatedDetails(0).getAffectedProjects().iterator().next());
    		assertEquals("test-spring-indexing", harness.getIndexUpdatedDetails(1).getAffectedProjects().iterator().next());
        }
        finally {
            FileUtils.deleteQuietly(new File(new URI(createdDocURI)));
        }
    }


}
