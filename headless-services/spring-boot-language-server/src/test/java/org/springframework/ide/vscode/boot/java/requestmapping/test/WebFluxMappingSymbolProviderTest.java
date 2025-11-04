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
package org.springframework.ide.vscode.boot.java.requestmapping.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.requestmapping.WebfluxHandlerMethodIndexElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;
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
public class WebFluxMappingSymbolProviderTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;

	private File directory;
	private IJavaProject project;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-webflux-project/").toURI());
		String projectDir = directory.toURI().toString();

		// trigger project creation
		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testSimpleRequestMappingSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/UserController.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = indexer.getSymbols(docUri);
        assertEquals(4, symbols.size());
        assertTrue(containsSymbol(symbols, "@/users -- GET - Content-Type: application/json", docUri, 13, 1, 13, 74));
        assertTrue(containsSymbol(symbols, "@/users/{username} -- GET - Content-Type: application/json", docUri, 18, 1, 18, 85));

        Bean[] beans = springIndex.getBeansOfDocument(docUri);
        assertEquals(1, beans.length);
        assertEquals("userController", beans[0].getName());
        assertEquals("org.test.UserController", beans[0].getType());
    }

    @Test
    void testRoutesMappingSymbols() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/QuoteRouter.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = indexer.getSymbols(docUri);
        assertTrue(containsSymbol(symbols, "@/hello -- GET - Accept: text/plain", docUri, 23, 5, 23, 70));
        assertTrue(containsSymbol(symbols, "@/echo -- POST - Accept: text/plain - Content-Type: text/plain", docUri, 24, 5, 24, 101));
        assertTrue(containsSymbol(symbols, "@/quotes -- GET - Accept: application/json", docUri, 25, 5, 25, 86));
        assertTrue(containsSymbol(symbols, "@/quotes -- GET - Accept: application/stream+json", docUri, 26, 5, 26, 122));
        
        Bean[] routeBeans = springIndex.getBeansWithName(project.getElementName(), "route");
        assertEquals(1, routeBeans.length);
        assertEquals("route", routeBeans[0].getName());

        List<SpringIndexElement> children = routeBeans[0].getChildren();
        assertEquals(8, children.size());
        
        WebfluxHandlerMethodIndexElement handlerElement1 = getWebfluxIndexElements(children, "/hello", "GET").get(0);
        assertEquals("/hello", handlerElement1.getPath());
        assertEquals("[GET]", Arrays.toString(handlerElement1.getHttpMethods()));
        assertEquals(0, handlerElement1.getContentTypes().length);
        assertEquals("[TEXT_PLAIN]", Arrays.toString(handlerElement1.getAcceptTypes()));
        assertEquals("org.test.QuoteHandler", handlerElement1.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> hello(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement1.getHandlerMethod());

        WebfluxHandlerMethodIndexElement handlerElement2 = getWebfluxIndexElements(children, "/echo", "POST").get(0);
        assertEquals("/echo", handlerElement2.getPath());
        assertEquals("[POST]", Arrays.toString(handlerElement2.getHttpMethods()));
        assertEquals("[TEXT_PLAIN]", Arrays.toString(handlerElement2.getContentTypes()));
        assertEquals("[TEXT_PLAIN]", Arrays.toString(handlerElement2.getAcceptTypes()));
        assertEquals("org.test.QuoteHandler", handlerElement2.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> echo(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement2.getHandlerMethod());

        WebfluxHandlerMethodIndexElement handlerElement3 = getWebfluxIndexElements(children, "/quotes", "GET").get(0);
        assertEquals("/quotes", handlerElement3.getPath());
        assertEquals("[GET]", Arrays.toString(handlerElement3.getHttpMethods()));
        assertEquals(0, handlerElement3.getContentTypes().length);
        assertEquals("[APPLICATION_STREAM_JSON]", Arrays.toString(handlerElement3.getAcceptTypes()));
        assertEquals("org.test.QuoteHandler", handlerElement3.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> streamQuotes(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement3.getHandlerMethod());

        WebfluxHandlerMethodIndexElement handlerElement4 = getWebfluxIndexElements(children, "/quotes", "GET").get(1);
        assertEquals("/quotes", handlerElement4.getPath());
        assertEquals("[GET]", Arrays.toString(handlerElement4.getHttpMethods()));
        assertEquals(0, handlerElement4.getContentTypes().length);
        assertEquals("[APPLICATION_JSON]", Arrays.toString(handlerElement4.getAcceptTypes()));
        assertEquals("org.test.QuoteHandler", handlerElement4.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> fetchQuotes(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement4.getHandlerMethod());
    }

    @Test
    void testNestedRoutesMappingSymbols1() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/NestedRouter1.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = indexer.getSymbols(docUri);
        assertEquals(5, symbols.size());
        assertTrue(containsSymbol(symbols, "@/person/{id} -- GET - Accept: application/json", docUri, 27, 6, 27, 45));
        assertTrue(containsSymbol(symbols, "@/person/ -- POST - Content-Type: application/json", docUri, 29, 6, 29, 83));
        assertTrue(containsSymbol(symbols, "@/person -- GET - Accept: application/json", docUri, 28, 7, 28, 60));
        
        Bean[] routeBeans = springIndex.getBeansWithName(project.getElementName(), "routingFunction1");
        assertEquals(1, routeBeans.length);
        assertEquals("routingFunction1", routeBeans[0].getName());

        List<SpringIndexElement> children = routeBeans[0].getChildren();
        assertEquals(6, children.size());
        
        WebfluxHandlerMethodIndexElement handlerElement1 = getWebfluxIndexElements(children, "/person/{id}", "GET").get(0);
        assertEquals("/person/{id}", handlerElement1.getPath());
        assertEquals("[GET]", Arrays.toString(handlerElement1.getHttpMethods()));
        assertEquals(0, handlerElement1.getContentTypes().length);
        assertEquals("[APPLICATION_JSON]", Arrays.toString(handlerElement1.getAcceptTypes()));
        assertEquals("org.test.PersonHandler1", handlerElement1.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> getPerson(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement1.getHandlerMethod());

        WebfluxHandlerMethodIndexElement handlerElement2 = getWebfluxIndexElements(children, "/person/", "POST").get(0);
        assertEquals("/person/", handlerElement2.getPath());
        assertEquals("[POST]", Arrays.toString(handlerElement2.getHttpMethods()));
        assertEquals("[APPLICATION_JSON]", Arrays.toString(handlerElement2.getContentTypes()));
        assertEquals(0, handlerElement2.getAcceptTypes().length);
        assertEquals("org.test.PersonHandler1", handlerElement2.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> createPerson(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement2.getHandlerMethod());

        WebfluxHandlerMethodIndexElement handlerElement3 = getWebfluxIndexElements(children, "/person", "GET").get(0);
        assertEquals("/person", handlerElement3.getPath());
        assertEquals("[GET]", Arrays.toString(handlerElement3.getHttpMethods()));
        assertEquals(0, handlerElement3.getContentTypes().length);
        assertEquals("[APPLICATION_JSON]", Arrays.toString(handlerElement3.getAcceptTypes()));
        assertEquals("org.test.PersonHandler1", handlerElement3.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> listPeople(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement3.getHandlerMethod());
    }

    @Test
    void testNestedRoutesMappingSymbols2() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/NestedRouter2.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = indexer.getSymbols(docUri);
        assertEquals(5, symbols.size());
        assertTrue(containsSymbol(symbols, "@/person/{id} -- GET - Accept: application/json", docUri, 29, 6, 29, 45));
        assertTrue(containsSymbol(symbols, "@/ -- POST - Accept: application/json - Content-Type: application/json,application/pdf", docUri, 31, 6, 31, 117));
        assertTrue(containsSymbol(symbols, "@/person -- GET,HEAD - Accept: text/plain,application/json", docUri, 30, 7, 30, 113));
        
        Bean[] routeBeans = springIndex.getBeansWithName(project.getElementName(), "routingFunction2");
        assertEquals(1, routeBeans.length);
        assertEquals("routingFunction2", routeBeans[0].getName());

        List<SpringIndexElement> children = routeBeans[0].getChildren();
        assertEquals(6, children.size());
        
        WebfluxHandlerMethodIndexElement handlerelement1 = getWebfluxIndexElements(children, "/person/{id}", "GET").get(0);
        assertEquals("/person/{id}", handlerelement1.getPath());
        assertEquals("[GET]", Arrays.toString(handlerelement1.getHttpMethods()));
        assertEquals(0, handlerelement1.getContentTypes().length);
        assertEquals("[APPLICATION_JSON]", Arrays.toString(handlerelement1.getAcceptTypes()));
        assertEquals("org.test.PersonHandler2", handlerelement1.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> getPerson(org.springframework.web.reactive.function.server.ServerRequest)", handlerelement1.getHandlerMethod());

        WebfluxHandlerMethodIndexElement handlerElement2 = getWebfluxIndexElements(children, "/", "POST").get(0);
        assertEquals("/", handlerElement2.getPath());
        assertEquals("[POST]", Arrays.toString(handlerElement2.getHttpMethods()));
        assertEquals("[APPLICATION_JSON, APPLICATION_PDF]", Arrays.toString(handlerElement2.getContentTypes()));
        assertEquals("[APPLICATION_JSON]", Arrays.toString(handlerElement2.getAcceptTypes()));
        assertEquals("org.test.PersonHandler2", handlerElement2.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> createPerson(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement2.getHandlerMethod());

        WebfluxHandlerMethodIndexElement handlerElement3 = getWebfluxIndexElements(children, "/person", "HEAD").get(0);
        assertEquals("/person", handlerElement3.getPath());
        assertEquals("[GET, HEAD]", Arrays.toString(handlerElement3.getHttpMethods()));
        assertEquals(0, handlerElement3.getContentTypes().length);
        assertEquals("[TEXT_PLAIN, APPLICATION_JSON]", Arrays.toString(handlerElement3.getAcceptTypes()));
        assertEquals("org.test.PersonHandler2", handlerElement3.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> listPeople(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement3.getHandlerMethod());
    }

    @Test
    void testNestedRoutesMappingSymbols3() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/NestedRouter3.java").toUri().toString();
        List<? extends WorkspaceSymbol> symbols = indexer.getSymbols(docUri);
        assertEquals(8, symbols.size());

        assertTrue(containsSymbol(symbols, "@/person/sub1/sub2/{id} -- GET - Accept: application/json", docUri, 29, 7, 29, 46));
        assertTrue(containsSymbol(symbols, "@/person/sub1/sub2 -- GET - Accept: application/json", docUri, 30, 8, 30, 61));
        assertTrue(containsSymbol(symbols, "@/person/sub1/sub2/nestedGet -- GET", docUri, 31, 9, 31, 56));
        assertTrue(containsSymbol(symbols, "@/person/sub1/andNestPath/andNestPathGET -- GET", docUri, 33, 5, 33, 54));
        assertTrue(containsSymbol(symbols, "@/person/ -- POST - Content-Type: application/json", docUri, 34, 5, 34, 82));
        assertTrue(containsSymbol(symbols, "@/nestedDelete -- DELETE", docUri, 35, 42, 35, 93));
        
        Bean[] routeBeans = springIndex.getBeansWithName(project.getElementName(), "routingFunction");
        assertEquals(1, routeBeans.length);
        assertEquals("routingFunction", routeBeans[0].getName());

        List<SpringIndexElement> children = routeBeans[0].getChildren();
        assertEquals(12, children.size());
        
        WebfluxHandlerMethodIndexElement handlerElement1 = getWebfluxIndexElements(children, "/person/sub1/sub2/{id}", "GET").get(0);
        assertEquals("/person/sub1/sub2/{id}", handlerElement1.getPath());
        assertEquals("[GET]", Arrays.toString(handlerElement1.getHttpMethods()));
        assertEquals(0, handlerElement1.getContentTypes().length);
        assertEquals("[APPLICATION_JSON]", Arrays.toString(handlerElement1.getAcceptTypes()));
        assertEquals("org.test.PersonHandler3", handlerElement1.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> getPerson(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement1.getHandlerMethod());

        WebfluxHandlerMethodIndexElement handlerElement2 = getWebfluxIndexElements(children, "/person/sub1/sub2", "GET").get(0);
        assertEquals("/person/sub1/sub2", handlerElement2.getPath());
        assertEquals("[GET]", Arrays.toString(handlerElement2.getHttpMethods()));
        assertEquals(0, handlerElement2.getContentTypes().length);
        assertEquals("[APPLICATION_JSON]", Arrays.toString(handlerElement2.getAcceptTypes()));
        assertEquals("org.test.PersonHandler3", handlerElement1.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> listPeople(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement2.getHandlerMethod());

        WebfluxHandlerMethodIndexElement handlerElement3 = getWebfluxIndexElements(children, "/person/sub1/sub2/nestedGet", "GET").get(0);
        assertEquals("/person/sub1/sub2/nestedGet", handlerElement3.getPath());
        assertEquals("[GET]", Arrays.toString(handlerElement3.getHttpMethods()));
        assertEquals(0, handlerElement3.getContentTypes().length);
        assertEquals(0, handlerElement3.getAcceptTypes().length);
        assertEquals("org.test.PersonHandler3", handlerElement1.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> getPerson(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement3.getHandlerMethod());

        WebfluxHandlerMethodIndexElement handlerElement4 = getWebfluxIndexElements(children, "/person/sub1/andNestPath/andNestPathGET", "GET").get(0);
        assertEquals("/person/sub1/andNestPath/andNestPathGET", handlerElement4.getPath());
        assertEquals("[GET]", Arrays.toString(handlerElement4.getHttpMethods()));
        assertEquals(0, handlerElement4.getContentTypes().length);
        assertEquals(0, handlerElement4.getAcceptTypes().length);
        assertEquals("org.test.PersonHandler3", handlerElement4.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> getPerson(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement4.getHandlerMethod());

        WebfluxHandlerMethodIndexElement handlerElement5 = getWebfluxIndexElements(children, "/person/", "POST").get(0);
        assertEquals("/person/", handlerElement5.getPath());
        assertEquals("[POST]", Arrays.toString(handlerElement5.getHttpMethods()));
        assertEquals("[APPLICATION_JSON]", Arrays.toString(handlerElement5.getContentTypes()));
        assertEquals(0, handlerElement5.getAcceptTypes().length);
        assertEquals("org.test.PersonHandler3", handlerElement5.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> createPerson(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement5.getHandlerMethod());

        WebfluxHandlerMethodIndexElement handlerElement6 = getWebfluxIndexElements(children, "/nestedDelete", "DELETE").get(0);
        assertEquals("/nestedDelete", handlerElement6.getPath());
        assertEquals("[DELETE]", Arrays.toString(handlerElement6.getHttpMethods()));
        assertEquals(0, handlerElement6.getContentTypes().length);
        assertEquals(0, handlerElement6.getAcceptTypes().length);
        assertEquals("org.test.PersonHandler3", handlerElement6.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> deletePerson(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement6.getHandlerMethod());
    }
    
    @Test
    void testUpdatedRouteInChangedDocument() throws Exception {
        // update document and update index
        String changedDocURI = directory.toPath().resolve("src/main/java/org/test/QuoteRouter.java").toUri().toString();

        String newContent = FileUtils.readFileToString(new File(new URI(changedDocURI)), Charset.defaultCharset()).replace("/hello", "/hello-updated");
        CompletableFuture<Void> updateFuture = indexer.updateDocument(changedDocURI, newContent, "test triggered");
        updateFuture.get(5, TimeUnit.SECONDS);
        
        Bean[] routeBeans = springIndex.getBeansWithName(project.getElementName(), "route");
        assertEquals(1, routeBeans.length);
        assertEquals("route", routeBeans[0].getName());

        List<SpringIndexElement> children = routeBeans[0].getChildren();
        assertEquals(8, children.size());
        
        WebfluxHandlerMethodIndexElement handlerElement1 = getWebfluxIndexElements(children, "/hello-updated", "GET").get(0);
        assertEquals("/hello-updated", handlerElement1.getPath());
        assertEquals("[GET]", Arrays.toString(handlerElement1.getHttpMethods()));
        assertEquals(0, handlerElement1.getContentTypes().length);
        assertEquals("[TEXT_PLAIN]", Arrays.toString(handlerElement1.getAcceptTypes()));
        assertEquals("org.test.QuoteHandler", handlerElement1.getHandlerClass());
        assertEquals("public Mono<org.springframework.web.reactive.function.server.ServerResponse> hello(org.springframework.web.reactive.function.server.ServerRequest)", handlerElement1.getHandlerMethod());

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

	private List<WebfluxHandlerMethodIndexElement> getWebfluxIndexElements(List<SpringIndexElement> children, String path, String httpMethod) {
		return children.stream()
				.filter((obj) -> obj instanceof WebfluxHandlerMethodIndexElement)
				.map((obj -> (WebfluxHandlerMethodIndexElement) obj))
				.filter((addon) -> addon.getPath().equals(path) && Arrays.asList(addon.getHttpMethods()).contains(httpMethod))
				.collect(Collectors.toList());
	}

}
