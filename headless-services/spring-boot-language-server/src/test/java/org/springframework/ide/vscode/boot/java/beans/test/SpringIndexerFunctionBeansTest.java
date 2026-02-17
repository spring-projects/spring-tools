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
package org.springframework.ide.vscode.boot.java.beans.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Arrays;
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
public class SpringIndexerFunctionBeansTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-annotation-indexing-beans/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testScanSimpleFunctionBean() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/FunctionClass.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("FunctionClass", "@+ 'functionClass' (@Configuration <: @Component) FunctionClass"),
                SpringIndexerHarness.symbol("@Bean", "@+ 'uppercase' (@Bean) Function<String,String>")
        );

        Bean[] beans = springIndex.getBeansOfDocument(docUri);
        assertEquals(2, beans.length);
        
        Bean configBean = Arrays.stream(beans).filter(bean -> bean.getName().equals("functionClass")).findFirst().get();
        Bean functionBean = Arrays.stream(beans).filter(bean -> bean.getName().equals("uppercase")).findFirst().get();
        
        assertEquals("org.test.FunctionClass", configBean.getType());
        assertEquals("java.util.function.Function<java.lang.String,java.lang.String>", functionBean.getType());
    }

    @Test
    void testScanSimpleFunctionClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/ScannedFunctionClass.java").toUri().toString();

        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri);
        assertEquals(0, springIndex.getBeansOfDocument(docUri).length);
    }

    @Test
    void testScanSimpleFunctionClassWithComponentAnnotation() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/ScannedFunctionClassWithAnnotation.java").toUri().toString();

        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("ScannedFunctionClassWithAnnotation", "@+ 'scannedFunctionClassWithAnnotation' (@Component) ScannedFunctionClassWithAnnotation")
        );
        
        Bean[] beans = springIndex.getBeansOfDocument(docUri);
        assertEquals(1, beans.length);
        
        Bean functionClassBean = Arrays.stream(beans).filter(bean -> bean.getName().equals("scannedFunctionClassWithAnnotation")).findFirst().get();
        
        assertEquals("org.test.ScannedFunctionClassWithAnnotation", functionClassBean.getType());
    }

    @Test
    void testScanSpecializedFunctionClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/FunctionFromSpecializedClass.java").toUri().toString();

        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri);
        assertEquals(0, springIndex.getBeansOfDocument(docUri).length);
    }

    @Test
    void testScanSpecializedFunctionInterface() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/FunctionFromSpecializedInterface.java").toUri().toString();

        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri);
        assertEquals(0, springIndex.getBeansOfDocument(docUri).length);
    }

    @Test
    void testNoSymbolForAbstractClasses() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SpecializedFunctionClass.java").toUri().toString();

        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri);
        assertEquals(0, springIndex.getBeansOfDocument(docUri).length);
    }

    @Test
    void testNoSymbolForSubInterfaces() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SpecializedFunctionInterface.java").toUri().toString();

        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri);
        assertEquals(0, springIndex.getBeansOfDocument(docUri).length);
    }

}
