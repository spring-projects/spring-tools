/*******************************************************************************
 * Copyright (c) 2023 VMware, Inc.
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.BeansParams;
import org.springframework.ide.vscode.commons.protocol.spring.MatchingBeansParams;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class SpringIndexViaLSPMethodTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-indexing/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

//		CompletableFuture<Void> initProject = indexer.waitOperation();
//		initProject.get(5, TimeUnit.SECONDS);
	}

	@Test
	void testSpringModelServiceBeansForNonExistingProject() throws Exception {
		BeansParams params = new BeansParams();
		params.setProjectName("random-project");
		CompletableFuture<List<Bean>> result = indexer.beans(params);

		List<Bean> list = result.get();
		assertNull(list);
	}

	@Test
	void testBeansNameAndTypeFromBeanAnnotatedMethod() throws Exception {
		BeansParams params = new BeansParams();
		params.setProjectName("test-spring-indexing");
		CompletableFuture<List<Bean>> result = indexer.beans(params);

		List<Bean> beans = result.get(5, TimeUnit.SECONDS);

		assertNotNull(beans);
		assertEquals(SpringMetamodelIndexingTest.NO_OF_EXPECTED_BEANS, beans.size());
	}

	@Test
	void testMatchingBeansForObject() throws Exception {
		MatchingBeansParams params = new MatchingBeansParams();
		params.setProjectName("test-spring-indexing");
		params.setBeanTypeToMatch("java.lang.Object");

		CompletableFuture<List<Bean>> result = indexer.matchingBeans(params);

		List<Bean> beans = result.get(5, TimeUnit.SECONDS);

		assertNotNull(beans);
		assertEquals(SpringMetamodelIndexingTest.NO_OF_EXPECTED_BEANS - 2, beans.size());
	}

	@Test
	void testMatchingBeansForSpecificSupertype() throws Exception {
		MatchingBeansParams params = new MatchingBeansParams();
		params.setProjectName("test-spring-indexing");
		params.setBeanTypeToMatch("org.test.springdata.CustomerRepository");

		CompletableFuture<List<Bean>> result = indexer.matchingBeans(params);

		List<Bean> beans = result.get(5, TimeUnit.SECONDS);

		assertNotNull(beans);
		assertEquals(1, beans.size());
		
		assertEquals("customerRepository", beans.get(0).getName());
		assertEquals("org.test.springdata.CustomerRepository", beans.get(0).getType());
	}

}
