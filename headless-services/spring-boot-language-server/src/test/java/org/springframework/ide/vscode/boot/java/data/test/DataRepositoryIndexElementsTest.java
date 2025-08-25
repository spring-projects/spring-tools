/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.data.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.data.QueryMethodIndexElement;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.DocumentElement;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class DataRepositoryIndexElementsTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-data-symbols/").toURI());
		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testSimpleRepositoryElements() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/CustomerRepository.java").toUri().toString();
        
        DocumentElement document = springIndex.getDocument(docUri);
        List<Bean> children = SpringMetamodelIndex.getNodesOfType(Bean.class, List.of(document));
        Bean repositoryElement = children.get(0);
        assertEquals("customerRepository", repositoryElement.getName());
        assertEquals(1, children.size());
        
        Bean[] repoBean = this.springIndex.getBeansWithName("test-spring-data-symbols", "customerRepository");
        assertEquals(1, repoBean.length);
        assertEquals("customerRepository", repoBean[0].getName());
        assertEquals("org.test.CustomerRepository", repoBean[0].getType());
        
        Bean[] matchingBeans = springIndex.getMatchingBeans("test-spring-data-symbols", "org.springframework.data.repository.CrudRepository");
        assertEquals(5, matchingBeans.length);
        ArrayUtils.contains(matchingBeans, repoBean[0]);
    }

    @Test
    void testSimpleQueryMethodElements() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/CustomerRepository.java").toUri().toString();
        
        DocumentElement document = springIndex.getDocument(docUri);
        List<Bean> children = SpringMetamodelIndex.getNodesOfType(Bean.class, List.of(document));
        Bean repositoryElement = children.get(0);
        
        List<SpringIndexElement> queryMethods = repositoryElement.getChildren();
        assertEquals(1, queryMethods.size());
        
        QueryMethodIndexElement queryMethod = (QueryMethodIndexElement) queryMethods.get(0);
        assertEquals("findByLastName(String) : List<Customer>", queryMethod.getMethodName());
    }

    @Test
    void testSimpleQueryMethodElementsWithTwoParams() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/CustomerRepositoryWithTwoParamsMethod.java").toUri().toString();
        
        DocumentElement document = springIndex.getDocument(docUri);
        List<Bean> children = SpringMetamodelIndex.getNodesOfType(Bean.class, List.of(document));
        Bean repositoryElement = children.get(0);
        
        List<SpringIndexElement> queryMethods = repositoryElement.getChildren();
        assertEquals(1, queryMethods.size());
        
        QueryMethodIndexElement queryMethod = (QueryMethodIndexElement) queryMethods.get(0);
        assertEquals("findByLastNameAndStatus(String, Status) : List<Customer>", queryMethod.getMethodName());
    }

    @Test
    void testQueryMethodElementWithQueryString() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/CustomerRepositoryWithQuery.java").toUri().toString();
        
        DocumentElement document = springIndex.getDocument(docUri);
        List<Bean> children = SpringMetamodelIndex.getNodesOfType(Bean.class, List.of(document));
        Bean repositoryElement = children.get(0);
        
        List<SpringIndexElement> queryMethods = repositoryElement.getChildren();
        assertEquals(1, queryMethods.size());
        
        QueryMethodIndexElement queryMethod = (QueryMethodIndexElement) queryMethods.get(0);
        assertEquals("findPetTypes() : List<Object>", queryMethod.getMethodName());
        assertEquals("SELECT ptype FROM PetType ptype ORDER BY ptype.name", queryMethod.getQueryString());
    }

    @Test
    void testNoRepositoryBeanAnnotationResultsInNoBeanIndexElement() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/CustomerRepositoryParentInterface.java").toUri().toString();
        
        DocumentElement document = springIndex.getDocument(docUri);
        assertTrue(document == null || SpringMetamodelIndex.getNodesOfType(Bean.class, List.of(document)).size() == 0);
    }

    @Test
    @Disabled // query methods from superclasses or interfaces not yet implemented, maybe requires a different way to think about this (separate index elements instead of one element with all query methods)
    void testQueryMethodsFromParentInterfaces() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/CustomerRepositoryWithParentInterfaces.java").toUri().toString();
        
        DocumentElement document = springIndex.getDocument(docUri);
        List<Bean> children = SpringMetamodelIndex.getNodesOfType(Bean.class, List.of(document));
        Bean repositoryElement = children.get(0);
        
        List<SpringIndexElement> queryMethods = repositoryElement.getChildren();
        assertEquals(2, queryMethods.size());
        
        QueryMethodIndexElement queryMethod = (QueryMethodIndexElement) queryMethods.get(0);
        assertEquals("findConcretePetTypes() : List<Object>", queryMethod.getMethodName());
        assertEquals("CONCRETE REPO SELECT STATEMENT", queryMethod.getQueryString());

        QueryMethodIndexElement parentQueryMethod = (QueryMethodIndexElement) queryMethods.get(1);
        assertEquals("findParentPetTypes() : List<Object>", parentQueryMethod.getMethodName());
        assertEquals("PARENT REPO INTERFACE QUERY STATEMENT", parentQueryMethod.getQueryString());
    }

}
