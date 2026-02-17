/*******************************************************************************
 * Copyright (c) 2026 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.beans.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Map;
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
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationAttributeValue;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class CloudLoadBalancerIndexingTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-cloud-loadbalancer/").toURI());
		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testClientIndexElements() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/com/example/cloud/loadbalancer/LoadBalancerClientExample.java").toUri().toString();
        
        Bean[] beans = springIndex.getBeansOfDocument(docUri, "loadBalancerClientExample");
        assertEquals(1, beans.length);

        assertEquals("com.example.cloud.loadbalancer.LoadBalancerClientExample", beans[0].getType());
        
        AnnotationMetadata[] annotations = beans[0].getAnnotations();
        assertTrue(annotations.length >= 5);
        
        // @Configuration on class
        AnnotationMetadata configAnnotation1 = annotations[0];
        assertEquals(Annotations.CONFIGURATION, configAnnotation1.getAnnotationType());
        assertEquals(0, configAnnotation1.getAttributes().size());
        
        // @LoadBalancerClients({..})
        AnnotationMetadata loadBalancerAnnotation = annotations[1];
        assertEquals("org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients", loadBalancerAnnotation.getAnnotationType());
        
        Map<String, AnnotationAttributeValue[]> loadBalancerClientsAttributes = loadBalancerAnnotation.getAttributes();
        assertEquals(1, loadBalancerClientsAttributes.size());

        AnnotationAttributeValue[] balanderValueAttributeValues = loadBalancerClientsAttributes.get("value");
        assertEquals(1, balanderValueAttributeValues.length);
        assertEquals("@LoadBalancerClient(name=\"first\",configuration=LoadBalancerConfigExample.class)", balanderValueAttributeValues[0].getName());

        // embedded @LoadBalancerClient(name="first",configuration=LoadBalancerConfigExample.class)
        AnnotationMetadata loadBalancerClient = annotations[2];
        assertEquals("org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient", loadBalancerClient.getAnnotationType());
        
        Map<String, AnnotationAttributeValue[]> balancerClientAttributes = loadBalancerClient.getAttributes();
        assertEquals(2, balancerClientAttributes.size());
        
        assertEquals("first", balancerClientAttributes.get("name")[0].getName());
        assertEquals("com.example.cloud.loadbalancer.LoadBalancerConfigExample", balancerClientAttributes.get("configuration")[0].getName());
    }

}
