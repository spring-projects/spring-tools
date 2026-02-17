/*******************************************************************************
 * Copyright (c) 2017, 2019 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.utils.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
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
import org.springframework.ide.vscode.boot.java.utils.SymbolIndexConfig;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.Assert;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class SpringIndexerTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private JavaProjectFinder projectFinder;

	private File directory;
	private String projectDir;
	private IJavaProject project;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		indexer.configureIndexer(SymbolIndexConfig.builder().scanXml(false).build());
		indexer.getJavaIndexer().setScanChunkSize(2);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-annotation-indexing-parent/test-annotation-indexing/").toURI());
		projectDir = directory.toURI().toString();

		// trigger project creation
		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testScanningAllAnnotationsSimpleProjectUpfront() throws Exception {
        List<? extends WorkspaceSymbol> allSymbols = indexer.getAllSymbols("");

        assertEquals(25, allSymbols.size());

        String docUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@+ 'mainClass' (@SpringBootApplication <: @SpringBootConfiguration, @Configuration, @Component) MainClass", docUri, 7, 13, 7, 22));
        assertTrue(containsSymbol(allSymbols, "@/embedded-foo-mapping", docUri, 17, 1, 17, 41));
        assertTrue(containsSymbol(allSymbols, "@/foo-root-mapping/embedded-foo-mapping-with-root", docUri, 27, 1, 27, 51));

        docUri = directory.toPath().resolve("src/main/java/org/test/SimpleMappingClass.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@/mapping1", docUri, 6, 1, 6, 28));
        assertTrue(containsSymbol(allSymbols, "@/mapping2", docUri, 11, 1, 11, 28));

        docUri = directory.toPath().resolve("src/main/java/org/test/sub/MappingClassSubpackage.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@/classlevel/mapping-subpackage", docUri, 7, 1, 7, 38));

        docUri = directory.toPath().resolve("src/main/java/org/test/ClassWithDefaultSymbol.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@Configurable", docUri, 5, 0, 5, 13));
    }

    @Test
    void testScanTestJavaSources() throws Exception {
        indexer.configureIndexer(SymbolIndexConfig.builder().scanTestJavaSources(true).build());

        List<? extends WorkspaceSymbol> allSymbols = indexer.getAllSymbols("");
        assertEquals(26, allSymbols.size());
        String docUri = directory.toPath().resolve("src/test/java/demo/ApplicationTests.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@SpringBootTest", docUri, 8, 0, 8, 15));

        indexer.configureIndexer(SymbolIndexConfig.builder().scanTestJavaSources(false).build());
        allSymbols = indexer.getAllSymbols("");
        assertEquals(25, allSymbols.size());
        assertFalse(containsSymbol(allSymbols, "@SpringBootTest", docUri, 8, 0, 8, 15));
    }

    @Test
    void testRetrievingSymbolsPerDocument() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = indexer.getSymbols(docUri);
        assertEquals(3, symbols.size());
        assertTrue(containsSymbol(symbols, "@+ 'mainClass' (@SpringBootApplication <: @SpringBootConfiguration, @Configuration, @Component) MainClass", docUri, 7, 13, 7, 22));
        assertTrue(containsSymbol(symbols, "@/embedded-foo-mapping", docUri, 17, 1, 17, 41));
        assertTrue(containsSymbol(symbols, "@/foo-root-mapping/embedded-foo-mapping-with-root", docUri, 27, 1, 27, 51));

        docUri = directory.toPath().resolve("src/main/java/org/test/SimpleMappingClass.java").toUri().toString();
        symbols = indexer.getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/mapping1", docUri, 6, 1, 6, 28));
        assertTrue(containsSymbol(symbols, "@/mapping2", docUri, 11, 1, 11, 28));

        docUri = directory.toPath().resolve("src/main/java/org/test/sub/MappingClassSubpackage.java").toUri().toString();
        symbols = indexer.getSymbols(docUri);
        assertEquals(1, symbols.size());
        assertTrue(containsSymbol(symbols, "@/classlevel/mapping-subpackage", docUri, 7, 1, 7, 38));
    }

    @Test
    void testScanningAllAnnotationsMultiModuleProjectUpfront() throws Exception {
        List<? extends WorkspaceSymbol> allSymbols = indexer.getAllSymbols("");

        assertEquals(25, allSymbols.size());

        String docUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@+ 'mainClass' (@SpringBootApplication <: @SpringBootConfiguration, @Configuration, @Component) MainClass", docUri, 7, 13, 7, 22));
        assertTrue(containsSymbol(allSymbols, "@/embedded-foo-mapping", docUri, 17, 1, 17, 41));
        assertTrue(containsSymbol(allSymbols, "@/foo-root-mapping/embedded-foo-mapping-with-root", docUri, 27, 1, 27, 51));

        docUri = directory.toPath().resolve("src/main/java/org/test/SimpleMappingClass.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@/mapping1", docUri, 6, 1, 6, 28));
        assertTrue(containsSymbol(allSymbols, "@/mapping2", docUri, 11, 1, 11, 28));

        docUri = directory.toPath().resolve("src/main/java/org/test/sub/MappingClassSubpackage.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@/classlevel/mapping-subpackage", docUri, 7, 1, 7, 38));


        docUri = directory.toPath().resolve("src/main/java/org/test/ClassWithDefaultSymbol.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@Configurable", docUri, 5, 0, 5, 13));
    }

    @Test
    void testUpdateChangedDocument() throws Exception {
        // update document and update index
        String changedDocURI = directory.toPath().resolve("src/main/java/org/test/SimpleMappingClass.java").toUri().toString();

        assertTrue(containsSymbol(indexer.getSymbols(changedDocURI), "@/mapping1", changedDocURI));

        String newContent = FileUtils.readFileToString(new File(new URI(changedDocURI)), Charset.defaultCharset()).replace("mapping1", "mapping1-CHANGED");
        CompletableFuture<Void> updateFuture = indexer.updateDocument(changedDocURI, newContent, "test triggered");
        updateFuture.get(5, TimeUnit.SECONDS);

        // check for updated index per document
        List<? extends WorkspaceSymbol> symbols = indexer.getSymbols(changedDocURI);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/mapping1-CHANGED", changedDocURI, 6, 1, 6, 36));
        assertTrue(containsSymbol(symbols, "@/mapping2", changedDocURI, 11, 1, 11, 28));

        // check for updated index in all symbols
        List<? extends WorkspaceSymbol> allSymbols = indexer.getAllSymbols("");
        assertEquals(25, allSymbols.size());

        String docUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@+ 'mainClass' (@SpringBootApplication <: @SpringBootConfiguration, @Configuration, @Component) MainClass", docUri, 7, 13, 7, 22));
        assertTrue(containsSymbol(allSymbols, "@/embedded-foo-mapping", docUri, 17, 1, 17, 41));
        assertTrue(containsSymbol(allSymbols, "@/foo-root-mapping/embedded-foo-mapping-with-root", docUri, 27, 1, 27, 51));

        docUri = directory.toPath().resolve("src/main/java/org/test/SimpleMappingClass.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@/mapping1-CHANGED", docUri, 6, 1, 6, 36));
        assertTrue(containsSymbol(allSymbols, "@/mapping2", docUri, 11, 1, 11, 28));

        docUri = directory.toPath().resolve("src/main/java/org/test/sub/MappingClassSubpackage.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@/classlevel/mapping-subpackage", docUri, 7, 1, 7, 38));

        docUri = directory.toPath().resolve("src/main/java/org/test/ClassWithDefaultSymbol.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@Configurable", docUri, 5, 0, 5, 13));
    }

    @Test
    void testNewDocumentCreated() throws Exception {
        String createdDocURI = directory.toPath().resolve("src/main/java/org/test/CreatedClass.java").toUri().toString();
        // check for document to not be created yet
        List<? extends WorkspaceSymbol> symbols = indexer.getSymbols(createdDocURI);
        assertNotNull(symbols);
        assertEquals(0, symbols.size());

        List<? extends WorkspaceSymbol> allSymbols = indexer.getAllSymbols("");
        assertEquals(25, allSymbols.size());

        try {
            // create document and update index
            String content = "package org.test;\n" +
                    "\n" +
                    "import org.springframework.web.bind.annotation.RequestMapping;\n" +
                    "\n" +
                    "public class SimpleMappingClass {\n" +
                    "	\n" +
                    "	@RequestMapping(\"created-mapping1\")\n" +
                    "	public String hello1() {\n" +
                    "		return \"hello1\";\n" +
                    "	}\n" +
                    "\n" +
                    "	@RequestMapping(\"created-mapping2\")\n" +
                    "	public String hello2() {\n" +
                    "		return \"hello2\";\n" +
                    "	}\n" +
                    "\n" +
                    "}\n" +
                    "";
            FileUtils.write(new File(new URI(createdDocURI)), content, Charset.defaultCharset());
            CompletableFuture<Void> createFuture = indexer.createDocument(createdDocURI);
            createFuture.get(5, TimeUnit.SECONDS);

            // check for updated index per document
            symbols = indexer.getSymbols(createdDocURI);
            assertEquals(2, symbols.size());
            assertTrue(containsSymbol(symbols, "@/created-mapping1", createdDocURI, 6, 1, 6, 36));
            assertTrue(containsSymbol(symbols, "@/created-mapping2", createdDocURI, 11, 1, 11, 36));

            // check for updated index in all symbols
            allSymbols = indexer.getAllSymbols("");
            assertEquals(27, allSymbols.size());

            String docUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();
            assertTrue(containsSymbol(allSymbols, "@+ 'mainClass' (@SpringBootApplication <: @SpringBootConfiguration, @Configuration, @Component) MainClass", docUri, 7, 13, 7, 22));
            assertTrue(containsSymbol(allSymbols, "@/embedded-foo-mapping", docUri, 17, 1, 17, 41));
            assertTrue(containsSymbol(allSymbols, "@/foo-root-mapping/embedded-foo-mapping-with-root", docUri, 27, 1, 27, 51));

            docUri = directory.toPath().resolve("src/main/java/org/test/SimpleMappingClass.java").toUri().toString();
            assertTrue(containsSymbol(allSymbols, "@/mapping1", docUri, 6, 1, 6, 28));
            assertTrue(containsSymbol(allSymbols, "@/mapping2", docUri, 11, 1, 11, 28));

            docUri = directory.toPath().resolve("src/main/java/org/test/sub/MappingClassSubpackage.java").toUri().toString();
            assertTrue(containsSymbol(allSymbols, "@/classlevel/mapping-subpackage", docUri, 7, 1, 7, 38));

            docUri = directory.toPath().resolve("src/main/java/org/test/ClassWithDefaultSymbol.java").toUri().toString();
            assertTrue(containsSymbol(allSymbols, "@Configurable", docUri, 5, 0, 5, 13));

            assertTrue(containsSymbol(allSymbols, "@/created-mapping1", createdDocURI, 6, 1, 6, 36));
            assertTrue(containsSymbol(allSymbols, "@/created-mapping2", createdDocURI, 11, 1, 11, 36));
        }
        finally {
            FileUtils.deleteQuietly(new File(new URI(createdDocURI)));
        }
    }

    @Test
    void testRemoveSymbolsFromDeletedDocument() throws Exception {
        // update document and update index
        String deletedDocURI = directory.toPath().resolve("src/main/java/org/test/SimpleMappingClass.java").toUri().toString();

        assertFalse(indexer.getSymbols(deletedDocURI).isEmpty()); //We have symbols before deletion?
        CompletableFuture<Void> deleteFuture = indexer.deleteDocument(deletedDocURI);
        deleteFuture.get(5, TimeUnit.MINUTES);

        // check for updated index per document
        Assert.noElements(indexer.getSymbols(deletedDocURI));

        // check for updated index in all symbols
        List<? extends WorkspaceSymbol> allSymbols = indexer.getAllSymbols("");
        assertEquals(23, allSymbols.size());

        String docUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@+ 'mainClass' (@SpringBootApplication <: @SpringBootConfiguration, @Configuration, @Component) MainClass", docUri, 7, 13, 7, 22));
        assertTrue(containsSymbol(allSymbols, "@/embedded-foo-mapping", docUri, 17, 1, 17, 41));
        assertTrue(containsSymbol(allSymbols, "@/foo-root-mapping/embedded-foo-mapping-with-root", docUri, 27, 1, 27, 51));

        docUri = directory.toPath().resolve("src/main/java/org/test/sub/MappingClassSubpackage.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@/classlevel/mapping-subpackage", docUri, 7, 1, 7, 38));

        docUri = directory.toPath().resolve("src/main/java/org/test/ClassWithDefaultSymbol.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@Configurable", docUri, 5, 0, 5, 13));
    }

    @Test
    void testRemoveSymbolsFromDeletedFolder() throws Exception {
        // update document and update index
        String deletedFolderURI = directory.toPath().resolve("src/main/java/org/test/sub").toUri().toString();
        String fileInFolderDocURI = directory.toPath().resolve("src/main/java/org/test/sub/MappingClassSubpackage.java").toUri().toString();

        // indexer has symbols for doc in folder
        List<? extends WorkspaceSymbol> symbolsInDoc = indexer.getSymbols(fileInFolderDocURI);
        assertEquals(1, symbolsInDoc.size());
        
        int symbolCountBeforeDelete = indexer.getAllSymbols("").size();

        // delete folder
        CompletableFuture<Void> deleteFuture = indexer.deleteDocuments(new String[] {deletedFolderURI});
        deleteFuture.get(5, TimeUnit.MINUTES);

        // doc symbols are not around anymore
        List<? extends WorkspaceSymbol> symbolsInDocAfterDelete = indexer.getSymbols(fileInFolderDocURI);
        assertEquals(0, symbolsInDocAfterDelete.size());

        // check for updated index in all symbols
        List<? extends WorkspaceSymbol> allSymbols = indexer.getAllSymbols("");
        assertEquals(symbolCountBeforeDelete - symbolsInDoc.size(), allSymbols.size());

        String docUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@+ 'mainClass' (@SpringBootApplication <: @SpringBootConfiguration, @Configuration, @Component) MainClass", docUri, 7, 13, 7, 22));
        assertTrue(containsSymbol(allSymbols, "@/embedded-foo-mapping", docUri, 17, 1, 17, 41));
        assertTrue(containsSymbol(allSymbols, "@/foo-root-mapping/embedded-foo-mapping-with-root", docUri, 27, 1, 27, 51));

        docUri = directory.toPath().resolve("src/main/java/org/test/sub/MappingClassSubpackage.java").toUri().toString();
        assertFalse(containsSymbol(allSymbols, "@/classlevel/mapping-subpackage", docUri, 7, 1, 7, 38));

        docUri = directory.toPath().resolve("src/main/java/org/test/ClassWithDefaultSymbol.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@Configurable", docUri, 5, 0, 5, 13));
    }

    @Test
    void testAddSymbolsFromCreatedFolder() throws Exception {
        // update document and update index
        String deletedFolderURI = directory.toPath().resolve("src/main/java/org/test/sub").toUri().toString();
        String fileInFolderDocURI = directory.toPath().resolve("src/main/java/org/test/sub/MappingClassSubpackage.java").toUri().toString();

        int symbolCountBeforeDelete = indexer.getAllSymbols("").size();

        // delete folder first
        CompletableFuture<Void> deleteFuture = indexer.deleteDocuments(new String[] {deletedFolderURI});
        deleteFuture.get(5, TimeUnit.SECONDS);

        // add folder back (if it was created)
        CompletableFuture<Void> createdFuture = indexer.createDocuments(new String[] {deletedFolderURI});
        createdFuture.get(5, TimeUnit.SECONDS);
        
        // check for symbol from doc in created folder
        List<? extends WorkspaceSymbol> symbolsInDoc = indexer.getSymbols(fileInFolderDocURI);
        assertEquals(1, symbolsInDoc.size());

        // check for updated index in all symbols
        List<? extends WorkspaceSymbol> allSymbols = indexer.getAllSymbols("");
        assertEquals(symbolCountBeforeDelete, allSymbols.size());

        String docUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@+ 'mainClass' (@SpringBootApplication <: @SpringBootConfiguration, @Configuration, @Component) MainClass", docUri, 7, 13, 7, 22));
        assertTrue(containsSymbol(allSymbols, "@/embedded-foo-mapping", docUri, 17, 1, 17, 41));
        assertTrue(containsSymbol(allSymbols, "@/foo-root-mapping/embedded-foo-mapping-with-root", docUri, 27, 1, 27, 51));

        docUri = directory.toPath().resolve("src/main/java/org/test/sub/MappingClassSubpackage.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@/classlevel/mapping-subpackage", docUri, 7, 1, 7, 38));

        docUri = directory.toPath().resolve("src/main/java/org/test/ClassWithDefaultSymbol.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@Configurable", docUri, 5, 0, 5, 13));
    }

    @Test
    void testFilterSymbolsUsingQueryString() throws Exception {
        List<? extends WorkspaceSymbol> allSymbols = indexer.getAllSymbols("mapp");

        assertEquals(24, allSymbols.size());

        String docUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@/embedded-foo-mapping", docUri, 17, 1, 17, 41));
        assertTrue(containsSymbol(allSymbols, "@/foo-root-mapping/embedded-foo-mapping-with-root", docUri, 27, 1, 27, 51));

        docUri = directory.toPath().resolve("src/main/java/org/test/SimpleMappingClass.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@/mapping1", docUri, 6, 1, 6, 28));
        assertTrue(containsSymbol(allSymbols, "@/mapping2", docUri, 11, 1, 11, 28));

        docUri = directory.toPath().resolve("src/main/java/org/test/sub/MappingClassSubpackage.java").toUri().toString();
        assertTrue(containsSymbol(allSymbols, "@/classlevel/mapping-subpackage", docUri, 7, 1, 7, 38));
    }

    @Test
    void testFilterSymbolsUsingQueryStringSplittedResult() throws Exception {
        List<? extends WorkspaceSymbol> allSymbols = indexer.getAllSymbols("@/foo-root-mapping");

        assertEquals(1, allSymbols.size());

        String docUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();

        assertTrue(containsSymbol(allSymbols, "@/foo-root-mapping/embedded-foo-mapping-with-root", docUri, 27, 1, 27, 51));
    }

    @Test
    void testFilterSymbolsUsingQueryStringFullSymbolString() throws Exception {
        List<? extends WorkspaceSymbol> allSymbols = indexer.getAllSymbols("@/foo-root-mapping/embedded-foo-mapping-with-root");

        assertEquals(1, allSymbols.size());

        String docUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();

        assertTrue(containsSymbol(allSymbols, "@/foo-root-mapping/embedded-foo-mapping-with-root", docUri, 27, 1, 27, 51));
    }

    @Test
    void testDeleteProject() throws Exception {
        List<? extends WorkspaceSymbol> allSymbols = indexer.getAllSymbols("");
        assertEquals(25, allSymbols.size());

        CompletableFuture<Void> deleteProject = indexer.deleteProject(project);
        deleteProject.get(5, TimeUnit.SECONDS);

        allSymbols = indexer.getAllSymbols("");
        assertEquals(0, allSymbols.size());
    }
    
    @Test
    void testSimpleUnfoldUris() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/sub").toUri().toString();
        
        String[] docs = SpringSymbolIndex.unfold(docUri);
        assertEquals(1, docs.length);
        
        String docToFindUri = directory.toPath().resolve("src/main/java/org/test/sub/MappingClassSubpackage.java").toUri().toString();
        assertEquals(docs[0], docToFindUri);
    }

    @Test
    void testDeepUnfoldUris() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/subdir").toUri().toString();
        
        Set<String> docs = Set.of(SpringSymbolIndex.unfold(docUri));
        assertEquals(4, docs.size());
        
        assertTrue(docs.contains(directory.toPath().resolve("src/main/java/org/test/subdir/subfile-level1.txt").toUri().toString()));
        assertTrue(docs.contains(directory.toPath().resolve("src/main/java/org/test/subdir/subdir2/subfile-level2.txt").toUri().toString()));
        assertTrue(docs.contains(directory.toPath().resolve("src/main/java/org/test/subdir/subdir2/subdir3/subdir4/subfile-level4.txt").toUri().toString()));
        assertTrue(docs.contains(directory.toPath().resolve("src/main/java/org/test/subdir/subdir2/subdir3/subdir4/subfile2-level4.txt").toUri().toString()));
    }

	public static boolean containsSymbol(List<? extends WorkspaceSymbol> symbols, String name, String uri) {
		for (Iterator<? extends WorkspaceSymbol> iterator = symbols.iterator(); iterator.hasNext();) {
			WorkspaceSymbol symbol = iterator.next();

			if (
					symbol.getName().equals(name) &&
					symbol.getLocation().getLeft().getUri().equals(uri)
			) {
				return true;
			}
 		}

		return false;
	}

	public static boolean containsSymbol(List<? extends WorkspaceSymbol> symbols, String name, String uri, int startLine, int startCHaracter, int endLine, int endCharacter) {
		for (Iterator<? extends WorkspaceSymbol> iterator = symbols.iterator(); iterator.hasNext();) {
			WorkspaceSymbol symbol = iterator.next();

			if (symbol.getName().equals(name)
					&& symbol.getLocation().getLeft().getUri().equals(uri)
					&& symbol.getLocation().getLeft().getRange().getStart().getLine() == startLine
					&& symbol.getLocation().getLeft().getRange().getStart().getCharacter() == startCHaracter
					&& symbol.getLocation().getLeft().getRange().getEnd().getLine() == endLine
					&& symbol.getLocation().getLeft().getRange().getEnd().getCharacter() == endCharacter) {
				return true;
			}
 		}

		return false;
	}

}
