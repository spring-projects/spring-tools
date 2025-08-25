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
package org.springframework.ide.vscode.boot.index.test;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexElement;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.DocumentElement;
import org.springframework.ide.vscode.commons.protocol.spring.SimpleSymbolElement;
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
public class SpringIndexStructureTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private SpringSymbolIndex indexer;
	
	private static final String PROJECT_NAME = "test-spring-index-structure";

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/" + PROJECT_NAME + "/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(55555555, TimeUnit.SECONDS);
	}

	@Test
	@Disabled
	void testStructureMainClass() {
		String docUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();
		
		/*
		 * @SpringBootApplication (mainClass) - bean node
		 * - @Value("server.port") - default node
		 * - @Bean (bean1) - bean node
		 */

		// document node
		DocumentElement document = springIndex.getDocument(docUri);
		List<SpringIndexElement> docChildren = document.getChildren();
		assertEquals(1, docChildren.size());

		// mainClass - bean node
		Bean[] mainClassBean = springIndex.getBeansWithName(PROJECT_NAME, "mainClass");
		assertEquals(1, mainClassBean.length);
		assertEquals("mainClass", mainClassBean[0].getName());
		assertSame(mainClassBean[0], docChildren.get(0));
		
		// mainClass children
		List<SpringIndexElement> mainChildren = mainClassBean[0].getChildren();
		assertEquals(2, mainChildren.size());
		
		SpringIndexElement valueFieldNode = mainChildren.get(0);
		SpringIndexElement beanMethodNode = mainChildren.get(1);
		
		assertTrue(valueFieldNode instanceof SimpleSymbolElement);
		assertTrue(beanMethodNode instanceof Bean);
		
		SimpleSymbolElement valueField = (SimpleSymbolElement) valueFieldNode;
		Bean beanMethod = (Bean) beanMethodNode;
		
		assertEquals("bean1", beanMethod.getName());
		assertEquals("@Value(\"server.port\")", valueField.getDocumentSymbol().getName());
	}

	@Test
	void testStructureRestController() {
		String docUri = directory.toPath().resolve("src/main/java/org/test/RestControllerExample.java").toUri().toString();
		
		/*
		 * @RestController (mainClass) - bean node
		 * - /owners/find -- GET -> @GetMapping("/owners/find")
		 * - /owners/new -- POST -> @PostMapping("/owners/new")
		 */

		// document node
		DocumentElement document = springIndex.getDocument(docUri);
        List<Bean> docChildren = SpringMetamodelIndex.getNodesOfType(Bean.class, List.of(document));
		assertEquals(1, docChildren.size());

		// rest controller node
		Bean[] mainClassBean = springIndex.getBeansWithName(PROJECT_NAME, "restControllerExample");
		assertEquals(1, mainClassBean.length);
		assertEquals("restControllerExample", mainClassBean[0].getName());
		assertSame(mainClassBean[0], docChildren.get(0));
		
		// rest controller children
		List<SpringIndexElement> mainChildren = mainClassBean[0].getChildren();
		assertEquals(3, mainChildren.size());
		
		RequestMappingIndexElement getMappingNode = (RequestMappingIndexElement) mainChildren.get(0);
		RequestMappingIndexElement postMappingNode = (RequestMappingIndexElement) mainChildren.get(1);
		RequestMappingIndexElement genericMappingNode = (RequestMappingIndexElement) mainChildren.get(2);
		
		assertEquals("/owners/find", getMappingNode.getPath());
		assertEquals("/owners/new", postMappingNode.getPath());
		assertEquals("/owners/{ownerId}/edit", genericMappingNode.getPath());
	}

}
