/*******************************************************************************
 * Copyright (c) 2024 Broadcom
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

import java.io.File;
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
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
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
public class SpringIndexerJakartaJavaxAnnotationsTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-annotation-symbols-for-jakarta-javax/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testScanSimpleConfigurationClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/jakarta/SimpleMovieLister.java").toUri().toString();

        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
        		SpringIndexerHarness.symbol("@Component", "@+ 'simpleMovieLister' (@Component) SimpleMovieLister"),
        		SpringIndexerHarness.symbol("@Resource", "@Resource"),
                SpringIndexerHarness.symbol("@Resource", "@Resource"),
                SpringIndexerHarness.symbol("@Resource(name=\"myMovieFinder\")", "@Resource(name=\"myMovieFinder\")"),
                SpringIndexerHarness.symbol("@Inject", "@Inject"),
                SpringIndexerHarness.symbol("@Named(\"specificFinder\")", "@Named(\"specificFinder\")")
        );
     }

    @Test
    void testNamedAnnotationJakarta() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/jakarta/NamedComponentWithSpecificName.java").toUri().toString();
        
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("@Named(\"specificallyNamedComponent\")", "@+ 'specificallyNamedComponent' (@Named) NamedComponentWithSpecificName")
        );

        Bean[] beans = springIndex.getBeansOfDocument(docUri);
        assertEquals(1, beans.length);
        
        assertEquals("specificallyNamedComponent", beans[0].getName());
        assertEquals("org.test.jakarta.NamedComponentWithSpecificName", beans[0].getType());
     }

    @Test
    void testNamedAnnotationJavax() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/javax/NamedComponentWithSpecificName.java").toUri().toString();
        
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("@Named(\"specificallyNamedComponent\")", "@+ 'specificallyNamedComponent' (@Named) NamedComponentWithSpecificName")
        );

        Bean[] beans = springIndex.getBeansOfDocument(docUri);
        assertEquals(1, beans.length);
        
        assertEquals("specificallyNamedComponent", beans[0].getName());
        assertEquals("org.test.javax.NamedComponentWithSpecificName", beans[0].getType());
     }

}
