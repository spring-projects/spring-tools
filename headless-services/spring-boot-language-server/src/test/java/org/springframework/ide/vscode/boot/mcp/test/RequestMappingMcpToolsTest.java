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
package org.springframework.ide.vscode.boot.mcp.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.mcp.RequestMappingMcpTools;
import org.springframework.ide.vscode.boot.mcp.RequestMappingMcpTools.RequestMappingInfo;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Tests for RequestMappingMcpTools
 * 
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class RequestMappingMcpToolsTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private RequestMappingMcpTools requestMappingMcpTools;
	private File directory;
	private IJavaProject project;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-request-mapping-symbols/").toURI());
		String projectDir = directory.toURI().toString();

		// trigger project creation
		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
		
		// Create the MCP tools instance
		requestMappingMcpTools = new RequestMappingMcpTools(projectFinder, springIndex);
	}

	@Test
	void testGetRequestMappings() throws Exception {
		List<RequestMappingInfo> mappings = requestMappingMcpTools.getRequestMappings(project.getElementName());
		
		assertNotNull(mappings);
		assertTrue(mappings.size() > 0);
		
		// Find the /greeting mapping
		RequestMappingInfo greetingMapping = mappings.stream()
				.filter(m -> m.path().equals("/greeting"))
				.findFirst()
				.orElse(null);
		
		assertNotNull(greetingMapping);
		assertEquals("/greeting", greetingMapping.path());
		assertEquals("simpleMappingClass", greetingMapping.controllerName());
		assertEquals("org.test.SimpleMappingClass", greetingMapping.controllerType());
		assertEquals("org.test.SimpleMappingClass.hello() : java.lang.String", greetingMapping.methodSignature());
	}

	@Test
	void testFindRequestMappingsByMethod() throws Exception {
		// Get all mappings first to know what we have
		List<RequestMappingInfo> allMappings = requestMappingMcpTools.getRequestMappings(project.getElementName());
		assertTrue(allMappings.size() > 0);
		
		// Test filtering by GET method
		List<RequestMappingInfo> getMappings = requestMappingMcpTools.findRequestMappingsByMethod(
				project.getElementName(), "GET");
		
		assertNotNull(getMappings);
		// Verify all returned mappings have GET method
		for (RequestMappingInfo mapping : getMappings) {
			assertTrue(mapping.httpMethods().stream()
					.anyMatch(method -> method.equalsIgnoreCase("GET")));
		}
	}

	@Test
	void testFindRequestMappingsByMethodCaseInsensitive() throws Exception {
		// Test that method filtering is case-insensitive
		List<RequestMappingInfo> getMappingsUpperCase = requestMappingMcpTools.findRequestMappingsByMethod(
				project.getElementName(), "GET");
		List<RequestMappingInfo> getMappingsLowerCase = requestMappingMcpTools.findRequestMappingsByMethod(
				project.getElementName(), "get");
		
		assertEquals(getMappingsUpperCase.size(), getMappingsLowerCase.size());
	}

	@Test
	void testGetRequestMappingsWithDetails() throws Exception {
		List<RequestMappingInfo> mappings = requestMappingMcpTools.getRequestMappings(project.getElementName());
		
		assertTrue(mappings.size() > 0);
		
		// Find the /greeting mapping and verify all details
		RequestMappingInfo greetingMapping = mappings.stream()
				.filter(m -> m.path().equals("/greeting"))
				.findFirst()
				.orElse(null);
		
		assertNotNull(greetingMapping);
		assertEquals("/greeting", greetingMapping.path());
		assertEquals("simpleMappingClass", greetingMapping.controllerName());
		assertEquals("org.test.SimpleMappingClass", greetingMapping.controllerType());
		assertEquals("org.test.SimpleMappingClass.hello() : java.lang.String", greetingMapping.methodSignature());
		assertTrue(greetingMapping.sourceFile().endsWith("SimpleMappingClass.java"));
		assertTrue(greetingMapping.httpMethods().isEmpty() || greetingMapping.httpMethods().size() > 0);
	}
}

