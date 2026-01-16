/*******************************************************************************
 * Copyright (c) 2017, 2026 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.requestmapping.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexElement;
import org.springframework.ide.vscode.boot.java.utils.SpringIndexerJavaDependencyTracker;
import org.springframework.ide.vscode.boot.java.utils.test.TestFileScanListener;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.util.UriUtil;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.commons.util.text.TextDocument;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.common.collect.ImmutableSet;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class RequestMappingSymbolProviderTest {
	
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private File directory;
	private IJavaProject project;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-request-mapping-symbols/").toURI());
		String projectDir = directory.toURI().toString();

		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
	private List<? extends WorkspaceSymbol> getSymbols(String docUri) {
		return indexer.getWorkspaceSymbolsFromSymbolIndex(docUri);
	}

    @Test
    void testSimpleRequestMappingSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SimpleMappingClass.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/greeting", docUri, 8, 1, 8, 29));
    }

    @Test
    void testRequestMappingIndexElements() throws Exception {
        Bean[] beans = springIndex.getBeansWithName("test-request-mapping-symbols", "simpleMappingClass");
        assertEquals(1, beans.length);
        
        List<SpringIndexElement> children = beans[0].getChildren();
        List<SpringIndexElement> mappingChildren = children.stream()
        	.filter(child -> child instanceof RequestMappingIndexElement)
        	.toList();
        
        assertEquals(1, mappingChildren.size());
        
        RequestMappingIndexElement mappingElement = (RequestMappingIndexElement) mappingChildren.get(0);
        assertEquals("/greeting", mappingElement.getPath());
    }

    @Test
    // TODO: Enable when JDT Core 3.41 or higher is adopted. See: https://github.com/eclipse-jdt/eclipse.jdt.core/pull/3416
    // additional comment: switched the string to not contain the "key" of the AST node, but the fully qualified type name, therefore enabling this again everywhere 
//	@DisabledOnOs(OS.WINDOWS)
    void testSimpleRequestMappingSymbolFromConstantInDifferentClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SimpleMappingClassWithConstantInDifferentClass.java").toUri().toString();
        String constantsUri = directory.toPath().resolve("src/main/java/org/test/Constants.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/path/from/constant", docUri, 8, 1, 8, 48));

        //Verify whether dependency tracker logics works properly for this example.
        SpringIndexerJavaDependencyTracker dt = indexer.getJavaIndexer().getDependencyTracker();
        assertEquals(ImmutableSet.of("org.test.Constants"), dt.getAllDependencies(project).get(UriUtil.toFileString(docUri)));

        TestFileScanListener fileScanListener = new TestFileScanListener();
        indexer.getJavaIndexer().setFileScanListener(fileScanListener);

        CompletableFuture<Void> updateFuture = indexer.updateDocument(constantsUri, FileUtils.readFileToString(UriUtil.toFile(constantsUri), Charset.defaultCharset()), "test triggered");
        updateFuture.get(5, TimeUnit.SECONDS);

        fileScanListener.assertScannedUris(constantsUri, docUri);
        fileScanListener.assertScannedUri(constantsUri, 1);
        fileScanListener.assertScannedUri(docUri, 1);
    }

    @Test
    //TODO: Enable when JDT Core 3.41 or higher is adopted. See: https://github.com/eclipse-jdt/eclipse.jdt.core/pull/3416
    // additional comment: switched the string to not contain the "key" of the AST node, but the fully qualified type name, therefore enabling this again everywhere 
//	@DisabledOnOs(OS.WINDOWS)
    void testUpdateDocumentWithConstantFromDifferentClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SimpleMappingClassWithConstantInDifferentClass.java").toUri().toString();
        String constantsUri = directory.toPath().resolve("src/main/java/org/test/Constants.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/path/from/constant", docUri, 8, 1, 8, 48));

        //Verify whether dependency tracker logics works properly for this example.
        SpringIndexerJavaDependencyTracker dt = indexer.getJavaIndexer().getDependencyTracker();
        assertEquals(ImmutableSet.of("org.test.Constants"), dt.getAllDependencies(project).get(UriUtil.toFileString(docUri)));

        TestFileScanListener fileScanListener = new TestFileScanListener();
        indexer.getJavaIndexer().setFileScanListener(fileScanListener);

        CompletableFuture<Void> updateFuture = indexer.updateDocument(docUri, FileUtils.readFileToString(UriUtil.toFile(docUri), Charset.defaultCharset()), "test triggered");
        updateFuture.get(5, TimeUnit.SECONDS);

        assertEquals(ImmutableSet.of("org.test.Constants"), dt.getAllDependencies(project).get(UriUtil.toFileString(docUri)));

        fileScanListener.assertScannedUris(docUri);
        fileScanListener.assertScannedUri(constantsUri, 0);
        fileScanListener.assertScannedUri(docUri, 1);
    }

    @Test
    //TODO: Enable when JDT Core 3.41 or higher is adopted. See: https://github.com/eclipse-jdt/eclipse.jdt.core/pull/3416
	@DisabledOnOs(OS.WINDOWS)
    void testCyclicalRequestMappingDependency() throws Exception {
        //Cyclical dependency:
        //file a => file b => file a
        //This has the potential to cause infinite loop.
        
        String pingUri = directory.toPath().resolve("src/main/java/org/test/PingConstantRequestMapping.java").toUri().toString();
        String pongUri = directory.toPath().resolve("src/main/java/org/test/PongConstantRequestMapping.java").toUri().toString();

        assertSymbol(pingUri, "@/pong -- GET", "@GetMapping(PongConstantRequestMapping.PONG)");
        assertSymbol(pongUri, "@/ping -- GET", "@GetMapping(PingConstantRequestMapping.PING)");

        TestFileScanListener fileScanListener = new TestFileScanListener();
        indexer.getJavaIndexer().setFileScanListener(fileScanListener);

        CompletableFuture<Void> updateFuture = indexer.updateDocument(pingUri, null, "test triggered");
        updateFuture.get(5, TimeUnit.SECONDS);

        fileScanListener.assertScannedUris(pingUri, pongUri);

        fileScanListener.reset();
        fileScanListener.assertScannedUris(/*none*/);

        CompletableFuture<Void> updateFuture2 = indexer.updateDocument(pongUri, null, "test triggered");
        updateFuture2.get(5, TimeUnit.SECONDS);

        fileScanListener.assertScannedUris(pingUri, pongUri);
    }

    @Test
    void testSimpleRequestMappingSymbolFromConstantInSameClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SimpleMappingClassWithConstantInSameClass.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/request/mapping/path/from/same/class/constant", docUri, 10, 1, 10, 52));

        SpringIndexerJavaDependencyTracker dt = indexer.getJavaIndexer().getDependencyTracker();
        assertEquals(ImmutableSet.of(), dt.getAllDependencies(project).get(UriUtil.toFileString(docUri)));
    }

    @Test
    void testSimpleRequestMappingSymbolFromConstantInBinaryType() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SimpleMappingClassWithConstantFromBinaryType.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/(inferred)", docUri, 9, 1, 9, 53));

        SpringIndexerJavaDependencyTracker dt = indexer.getJavaIndexer().getDependencyTracker();
        assertEquals(ImmutableSet.of(), dt.getAllDependencies(project).get(UriUtil.toFileString(docUri)));
    }

    @Test
    void testParentRequestMappingSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/ParentMappingClass.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/parent/greeting -- GET", docUri, 11, 1, 11, 51));
    }

    @Test
    void testEmptyPathWithParentRequestMappingSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/ParentMappingClass2.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/parent2 -- GET,POST,DELETE", docUri, 13, 1, 13, 16));
    }

    @Test
    void testParentRequestMappingSymbolWithPathAttribute() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/ParentMappingClass3.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/parent3/greeting -- GET", docUri, 11, 1, 11, 51));
    }

    @Test
    void testMappingPathFromSuperclass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/inheritance/SubclassWithMappingFromParent.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/superclasspath/", docUri, 8, 1, 8, 21));
    }

    @Test
    void testMappingPathFromSuperclassWithConstant() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/inheritance/SubclassWithMappingFromParentWithConstant.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/path/from/constant/", docUri, 8, 1, 8, 21));
    }

    @Test
    void testMappingPathFromSuperclassWithStringConcatenation() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/inheritance/SubclassWithMappingFromParentWithStringConcatenation.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/superpath/subclass", docUri, 8, 1, 8, 34));
    }

    @Test
    void testMappingPathFromSuperclassWithStringConcatenationPerAttribute() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/inheritance/SubclassWithMappingFromParentWithStringConcatenationPerAttribute.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/superpath/subclass", docUri, 8, 1, 8, 42));
    }

    @Test
    void testMappingPathFromSuperclassWithMethodsAndPathAttribute() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/inheritance/SubclassWithMappingFromParentWithMethods.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/superclasspath -- POST,PUT - Accept: testconsume - Content-Type: text/plain", docUri, 8, 1, 8, 16));
    }

    @Test
    void testMappingPathFromMultiLevelClassHierarchy() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/inheritance/SuperControllerLevel4.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/path-level2/final-subclass-path", docUri, 8, 1, 8, 39));
    }

    @Test
    void testMappingPathFromSuperInterfaceEvenIfSuperclassContainsMappingPath() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/inheritance/ControllerAsSubclassAndInterfaceHierarchy.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertEquals(2, symbols.size());
        assertTrue(containsSymbol(symbols, "@/superinterface-path/last-path-segment -- GET - Accept: testconsume - Content-Type: text/plain", docUri, 8, 1, 8, 33));
    }

    @Test
    void testMapoingIndexElementsWithDetails() throws Exception {
        Bean[] beans = springIndex.getBeansWithName("test-request-mapping-symbols", "controllerAsSubclassAndInterfaceHierarchy");
        assertEquals(1, beans.length);
        
        List<SpringIndexElement> children = beans[0].getChildren();
        List<SpringIndexElement> mappingChildren = children.stream()
        	.filter(child -> child instanceof RequestMappingIndexElement)
        	.toList();
        
        assertEquals(1, mappingChildren.size());
        
        RequestMappingIndexElement mappingElement = (RequestMappingIndexElement) mappingChildren.get(0);
        assertEquals("/superinterface-path/last-path-segment", mappingElement.getPath());

        assertEquals(1, mappingElement.getHttpMethods().length);
        assertEquals("GET", mappingElement.getHttpMethods()[0]);

        assertEquals(1, mappingElement.getAcceptTypes().length);
        assertEquals("testconsume", mappingElement.getAcceptTypes()[0]);

        assertEquals(1, mappingElement.getContentTypes().length);
        assertEquals("text/plain", mappingElement.getContentTypes()[0]);
    }

    @Test
    void testMultiRequestMappingSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/MultiRequestMappingClass.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertEquals(3, symbols.size());
        assertTrue(containsSymbol(symbols, "@/hello1", docUri, 8, 1, 8, 44));
        assertTrue(containsSymbol(symbols, "@/hello2", docUri, 8, 1, 8, 44));
    }

    @Test
    void testGetMappingSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/RequestMethodClass.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertTrue(containsSymbol(symbols, "@/getData -- GET", docUri, 16, 1, 16, 24));
    }

    @Test
    void testGetMappingSymbolWithoutPath() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/RequestMethodClass.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertTrue(containsSymbol(symbols, "@/ -- GET", docUri, 44, 1, 44, 16));
    }

    @Test
    void testGetMappingSymbolWithoutAnything() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/RequestMethodClass.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertTrue(containsSymbol(symbols, "@/ -- GET", docUri, 48, 1, 48, 14));
    }

    @Test
    void testDeleteMappingSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/RequestMethodClass.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertTrue(containsSymbol(symbols, "@/deleteData -- DELETE", docUri, 24, 1, 24, 30));
    }

    @Test
    void testPostMappingSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/RequestMethodClass.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertTrue(containsSymbol(symbols, "@/postData -- POST", docUri, 28, 1, 28, 26));
    }

    @Test
    void testPutMappingSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/RequestMethodClass.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertTrue(containsSymbol(symbols, "@/putData -- PUT", docUri, 20, 1, 20, 24));
    }

    @Test
    void testPatchMappingSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/RequestMethodClass.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = getSymbols(docUri);
        assertTrue(containsSymbol(symbols, "@/patchData -- PATCH", docUri, 32, 1, 32, 28));
    }

    @Test
    void testGetRequestMappingSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/RequestMethodClass.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertTrue(containsSymbol(symbols, "@/getHello -- GET", docUri, 36, 1, 36, 61));
    }

    @Test
    void testMultiRequestMethodMappingSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/RequestMethodClass.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertTrue(containsSymbol(symbols, "@/postAndPutHello -- POST,PUT", docUri, 40, 1, 40, 76));
    }

    @Test
    void testMediaTypes() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/RequestMappingMediaTypes.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = getSymbols(docUri);
        assertEquals(8, symbols.size());
        assertTrue(containsSymbol(symbols, "@/consume1 -- HEAD - Accept: testconsume", docUri, 10, 1, 10, 90));
        assertTrue(containsSymbol(symbols, "@/consume2 - Accept: text/plain", docUri, 15, 1, 15, 73));
        assertTrue(containsSymbol(symbols, "@/consume3 - Accept: text/plain,testconsumetype", docUri, 20, 1, 20, 94));
        assertTrue(containsSymbol(symbols, "@/produce1 - Content-Type: testproduce", docUri, 25, 1, 25, 60));
        assertTrue(containsSymbol(symbols, "@/produce2 - Content-Type: text/plain", docUri, 30, 1, 30, 73));
        assertTrue(containsSymbol(symbols, "@/produce3 - Content-Type: text/plain,testproducetype", docUri, 35, 1, 35, 94));
        assertTrue(containsSymbol(symbols, "@/everything - Accept: application/json,text/plain,testconsume - Content-Type: application/json", docUri, 40, 1, 40, 170));
    }
    
    @Test
    void testPathWithConcatenatedString() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/MappingsWithConcatenatedStrings.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertTrue(containsSymbol(symbols, "@/path1/path2 -- GET", docUri, 16, 1, 16, 33));
    }

    @Test
    void testPathWithConcatenatedStringAndConstantInvolved() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/MappingsWithConcatenatedStrings.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols =  getSymbols(docUri);
        assertTrue(containsSymbol(symbols, "@/path1/path/from/constant -- GET", docUri, 20, 1, 20, 56));
    }
    
	private boolean containsSymbol(List<? extends WorkspaceSymbol> symbols, String name, String uri, int startLine, int startCHaracter, int endLine, int endCharacter) {
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
	
	private void assertSymbol(String docUri, String name, String coveredText) throws Exception {
		List<? extends WorkspaceSymbol> symbols = indexer.getSymbols(docUri);
		Optional<? extends WorkspaceSymbol> maybeSymbol = symbols.stream().filter(s -> name.equals(s.getName())).findFirst();
		assertTrue(maybeSymbol.isPresent());
		
		TextDocument doc = new TextDocument(docUri, LanguageId.JAVA);
		doc.setText(FileUtils.readFileToString(UriUtil.toFile(docUri), Charset.defaultCharset()));
		
		WorkspaceSymbol symbol = maybeSymbol.get();
		Location loc = symbol.getLocation().getLeft();
		assertEquals(docUri, loc.getUri());
		int start = doc.toOffset(loc.getRange().getStart());
		int end = doc.toOffset(loc.getRange().getEnd());
		String actualCoveredText = doc.textBetween(start, end);
		assertEquals(coveredText, actualCoveredText);
	}
}
