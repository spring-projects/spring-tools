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
import org.springframework.ide.vscode.boot.mcp.ComponentAnalysisMcpTools;
import org.springframework.ide.vscode.boot.mcp.ComponentAnalysisMcpTools.BeanUsageInfo;
import org.springframework.ide.vscode.boot.mcp.ComponentAnalysisMcpTools.ComponentInfo;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Tests for ComponentAnalysisMcpTools
 * 
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class ComponentAnalysisMcpToolsTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private ComponentAnalysisMcpTools componentAnalysisMcpTools;
	private File directory;
	private IJavaProject project;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-annotation-indexing-beans/").toURI());
		String projectDir = directory.toURI().toString();

		// trigger project creation
		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
		
		// Create the MCP tools instance
		componentAnalysisMcpTools = new ComponentAnalysisMcpTools(projectFinder, springIndex);
	}

	@Test
	void testGetBeanUsageInfo() throws Exception {
		// Get usage info for a known bean
		List<BeanUsageInfo> usageInfos = componentAnalysisMcpTools.getBeanUsageInfo(
				project.getElementName(), "simpleComponent");
		
		assertTrue(usageInfos.size() > 0);
		
		BeanUsageInfo usageInfo = usageInfos.get(0);
		assertEquals("simpleComponent", usageInfo.beanName());
		assertEquals("org.test.SimpleComponent", usageInfo.beanType());
		
		ComponentInfo definition = usageInfo.definition();
		assertEquals("simpleComponent", definition.name());
		assertEquals("org.test.SimpleComponent", definition.type());
		assertTrue(definition.annotations().contains("org.springframework.stereotype.Component"));
		assertTrue(definition.sourceFile().contains("SimpleComponent.java"));
		
		// Injection points should be a list (may be empty)
		assertTrue(usageInfo.injectionPoints() != null);
	}

	@Test
	void testFindBeansByType() throws Exception {
		// Find beans with a known type
		List<ComponentInfo> beansByType = componentAnalysisMcpTools.findBeansByType(
				project.getElementName(), "org.test.SimpleComponent");
		
		assertTrue(beansByType.size() > 0);
		
		ComponentInfo component = beansByType.get(0);
		assertEquals("org.test.SimpleComponent", component.type());
		assertEquals("simpleComponent", component.name());
		assertTrue(component.annotations().contains("org.springframework.stereotype.Component"));
		assertTrue(component.sourceFile().contains("SimpleComponent.java"));
	}

	@Test
	void testComponentInfoDetails() throws Exception {
		// Find SimpleController bean
		List<ComponentInfo> components = componentAnalysisMcpTools.findBeansByType(
				project.getElementName(), "org.test.SimpleController");
		
		assertTrue(components.size() > 0);
		
		ComponentInfo component = components.get(0);
		
		assertEquals("simpleController", component.name());
		assertEquals("org.test.SimpleController", component.type());
		assertTrue(component.annotations().contains("org.springframework.stereotype.Controller"));
		assertTrue(component.sourceFile().contains("SimpleController.java"));
		assertTrue(component.startLine() >= 0);
		assertTrue(component.startColumn() >= 0);
	}
}

