/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.stereotypes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jmolecules.stereotype.catalog.StereotypeGroup;
import org.jmolecules.stereotype.catalog.StereotypeGroups;
import org.jmolecules.stereotype.catalog.support.JsonPathStereotypeCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class ProjectBasedCatalogSourceTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private File directory;
	private IJavaProject project;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-stereotypes-support/").toURI());
		String projectDir = directory.toURI().toString();

		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testCatalogLookupFromSource() throws Exception {
    	var source = new ProjectBasedCatalogSource(project);
		
		Stream<URL> sources = source.getSources();
		List<URL> list = sources.toList();
		
		URL url1 = new File(directory, "src/main/resources/META-INF/jmolecules-stereotypes.json").toURI().toURL();
		URL url2 = new File(directory, "src/main/resources/META-INF/jmolecules-stereotype-groups.json").toURI().toURL();
		
		assertTrue(list.contains(url1));
		assertTrue(list.contains(url2));
    }
    
    @Test
    void testCatalogLookupFromLibraries() throws Exception {
    	var source = new ProjectBasedCatalogSource(project);
		var catalog = new JsonPathStereotypeCatalog(source);
		
		StereotypeGroups groups = catalog.getGroups("org.jmolecules.ddd");
		StereotypeGroup primary = groups.getPrimary();
		
		assertEquals("Domain-Driven Design", primary.getDisplayName());
    }
    
}
