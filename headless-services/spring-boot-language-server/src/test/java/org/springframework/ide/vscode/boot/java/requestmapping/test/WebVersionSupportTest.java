/*******************************************************************************
 * Copyright (c) 2017, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping.test;

import static org.junit.Assert.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.WebConfigIndexElement;
import org.springframework.ide.vscode.boot.java.utils.test.SpringIndexerTest;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
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
public class WebVersionSupportTest {
	
	private static final String PROJECT_NAME = "test-web-config-support";
	
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/" + PROJECT_NAME + "/").toURI());
		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
	private List<? extends WorkspaceSymbol> getSymbols(String docUri) {
		return indexer.getWorkspaceSymbolsFromSymbolIndex(docUri);
	}

    @Test
    void testSymbolsForRequestMappingsWithVersions() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/versions/MappingClassWithMultipleVersions.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = getSymbols(docUri);
        assertEquals(3, symbols.size());
        assertTrue(containsSymbol(symbols, "@/greeting -- GET - Version: 1", docUri));
        assertTrue(containsSymbol(symbols, "@/greeting -- GET - Version: 1.1+", docUri));
    }
    
    @Test
    void testIndexElementsForRequestMappingsWithVersion() throws Exception {
        Bean[] beans = springIndex.getBeansWithName(PROJECT_NAME, "mappingClassWithMultipleVersions");
        assertEquals(1, beans.length);
        
        List<SpringIndexElement> children = beans[0].getChildren();
        List<SpringIndexElement> mappingChildren = children.stream()
        	.filter(child -> child instanceof RequestMappingIndexElement)
        	.toList();
        
        assertEquals(2, mappingChildren.size());
        
        RequestMappingIndexElement mapping1 = (RequestMappingIndexElement) mappingChildren.get(0);
        assertEquals("/greeting", mapping1.getPath());
        assertEquals("1", mapping1.getVersion());

        RequestMappingIndexElement mapping2 = (RequestMappingIndexElement) mappingChildren.get(1);
        assertEquals("/greeting", mapping2.getPath());
        assertEquals("1.1+", mapping2.getVersion());
    }
    
    @Test
    void testSymbolsForHttpExchangeWithClassLevelAnnotation() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/versions/HttpExchangeExampleWithClassLevelAnnotation.java").toUri().toString();
        
        List<? extends WorkspaceSymbol> symbols = indexer.getSymbols(docUri);
        assertEquals(1, symbols.size());
        assertTrue(SpringIndexerTest.containsSymbol(symbols, "@/stores/all -- GET - Version: 2", docUri));
    }
    
    @Test
    void testWebMvcConfigIndexElement() throws Exception {
    	Bean[] webConfigBean = springIndex.getMatchingBeans(PROJECT_NAME, "org.springframework.web.servlet.config.annotation.WebMvcConfigurer");
    	assertEquals(1, webConfigBean.length);
    	
    	Bean[] webConfigBeanViaName = springIndex.getBeansWithName(PROJECT_NAME, "webConfig");
    	assertEquals(1, webConfigBeanViaName.length);
    	
    	assertSame(webConfigBean[0], webConfigBeanViaName[0]);
    	
    	List<WebConfigIndexElement> webConfigElements = SpringMetamodelIndex.getNodesOfType(WebConfigIndexElement.class, List.of(webConfigBean[0]));
    	assertEquals(1, webConfigElements.size());
    	
    	WebConfigIndexElement webConfigElement = webConfigElements.get(0);
    	
    	List<String> versionSupportStrategies = webConfigElement.getVersionSupportStrategies();
    	assertEquals(2, versionSupportStrategies.size());
    	assertTrue(versionSupportStrategies.contains("Request Header: X-API-Version"));
    	assertTrue(versionSupportStrategies.contains("Path Segment: 0"));
    	
    	List<String> supportedVersions = webConfigElement.getSupportedVersions();
		assertEquals(2, supportedVersions.size());
    	assertTrue(supportedVersions.contains("1.1"));
    	assertTrue(supportedVersions.contains("1.2"));
    }

    @Test
    void testWebFluxConfigIndexElement() throws Exception {
    	Bean[] webConfigBean = springIndex.getMatchingBeans(PROJECT_NAME, "org.springframework.web.reactive.config.WebFluxConfigurer");
    	assertEquals(1, webConfigBean.length);
    	
    	Bean[] webConfigBeanViaName = springIndex.getBeansWithName(PROJECT_NAME, "webfluxConfig");
    	assertEquals(1, webConfigBeanViaName.length);
    	
    	assertSame(webConfigBean[0], webConfigBeanViaName[0]);
    	
    	List<WebConfigIndexElement> webConfigElements = SpringMetamodelIndex.getNodesOfType(WebConfigIndexElement.class, List.of(webConfigBean[0]));
    	assertEquals(1, webConfigElements.size());
    	
    	WebConfigIndexElement webConfigElement = webConfigElements.get(0);
    	
    	List<String> versionSupportStrategies = webConfigElement.getVersionSupportStrategies();
    	assertEquals(2, versionSupportStrategies.size());
    	assertTrue(versionSupportStrategies.contains("Request Header: Webflux-X-API-Version"));
    	assertTrue(versionSupportStrategies.contains("Path Segment: 0"));
    	
    	List<String> supportedVersions = webConfigElement.getSupportedVersions();
		assertEquals(2, supportedVersions.size());
    	assertTrue(supportedVersions.contains("2.1"));
    	assertTrue(supportedVersions.contains("2.2"));
    }

	private boolean containsSymbol(List<? extends WorkspaceSymbol> symbols, String name, String uri) {
		for (Iterator<? extends WorkspaceSymbol> iterator = symbols.iterator(); iterator.hasNext();) {
			WorkspaceSymbol symbol = iterator.next();

			if (symbol.getName().equals(name)
					&& symbol.getLocation().getLeft().getUri().equals(uri)) {
				return true;
			}
 		}

		return false;
	}
	
}
