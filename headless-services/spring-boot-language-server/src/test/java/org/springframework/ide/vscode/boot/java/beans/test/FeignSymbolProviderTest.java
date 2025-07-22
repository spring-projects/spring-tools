/*******************************************************************************
 * Copyright (c) 2018, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.beans.test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.List;
import java.util.Map;
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
import org.springframework.ide.vscode.boot.java.utils.test.SpringIndexerTest;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationAttributeValue;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
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
public class FeignSymbolProviderTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-feign-indexing/").toURI());
		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testSimpleFeignClientSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignClientExample.java").toUri().toString();
        
        List<? extends WorkspaceSymbol> symbols = indexer.getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(SpringIndexerTest.containsSymbol(symbols, "@+ 'stores' (@FeignClient) FeignClientExample", docUri, 8, 0, 8, 71));
        assertTrue(SpringIndexerTest.containsSymbol(symbols, "@/stores -- GET", docUri, 11, 1, 11, 63));
    }

    @Test
    void testSimpleFeignClientIndexElements() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignClientExample.java").toUri().toString();
        
        Bean[] beans = springIndex.getBeansOfDocument(docUri);
        assertEquals(1, beans.length);
        
        AnnotationMetadata[] annotations = beans[0].getAnnotations();
        assertEquals(1, annotations.length);
        
        AnnotationMetadata annotation = annotations[0];
        assertEquals("org.springframework.cloud.openfeign.FeignClient", annotation.getAnnotationType());
        
        Map<String, AnnotationAttributeValue[]> attributes = annotation.getAttributes();
        assertEquals(2, attributes.size());
        
        AnnotationAttributeValue[] nameAttributeValue = attributes.get("name");
        assertEquals(1, nameAttributeValue.length);
        assertEquals("stores", nameAttributeValue[0].getName());
        
        AnnotationAttributeValue[] configurationAttributeValue = attributes.get("configuration");
        assertEquals(1, configurationAttributeValue.length);
        assertEquals("com.example.feign.demo.FeignConfigExample", configurationAttributeValue[0].getName());
    }

    @Test
    void testSimpleFeignClientMappingElements() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/com/example/feign/demo/FeignClientExample.java").toUri().toString();
        
        Bean[] beans = springIndex.getBeansOfDocument(docUri);

        List<SpringIndexElement> children = beans[0].getChildren();
        List<SpringIndexElement> mappingChildren = children.stream()
        	.filter(child -> child instanceof RequestMappingIndexElement)
        	.toList();
        
        assertEquals(1, mappingChildren.size());
        
        RequestMappingIndexElement mappingElement = (RequestMappingIndexElement) mappingChildren.get(0);
        assertEquals("/stores", mappingElement.getPath());
        assertArrayEquals(new String[] {"GET"}, mappingElement.getHttpMethods());
        
        assertEquals("@/stores -- GET", mappingElement.getDocumentSymbol().getName());
    }
}
