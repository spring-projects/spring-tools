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
package org.springframework.ide.vscode.boot.java.requestmapping.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
import org.springframework.ide.vscode.boot.java.requestmapping.WebfluxHandlerMethodIndexElement;
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
public class WebMvcFunctionalEndpointsDefinitionsTest {
	
	private static final String PROJECT_NAME = "test-web-functional-endpoints";

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;

	private File directory;
	private String testDocUri;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		directory = new File(ProjectsHarness.class.getResource("/test-projects/" + PROJECT_NAME + "/").toURI());
		String projectDir = directory.toURI().toString();

		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
		
		testDocUri = directory.toPath().resolve("src/main/java/org/test/TestRouter.java").toUri().toString();
	}

	@Test
	void testMvcStaticSimpleMethodRoute() throws Exception {
		String routerMethod = """
			@Bean
			RouterFunction<ServerResponse> r1() {
				HandlerImplementation handler = new HandlerImplementation();

				return route(GET("/hello").and(accept(TEXT_PLAIN)), handler::getPerson);
			}
			""";
		
		Bean bean = indexRouterMethod("r1", routerMethod);
		List<SpringIndexElement> children = bean.getChildren();
		
		WebfluxHandlerMethodIndexElement route = getWebfluxIndexElements(children, "/hello", "GET").get(0);
		assertNotNull(route);
		assertEquals("/hello", route.getPath());
		assertEquals("[GET]", Arrays.toString(route.getHttpMethods()));
		assertEquals("[TEXT_PLAIN]", Arrays.toString(route.getAcceptTypes()));

        assertEquals("org.test.webmvc.HandlerImplementation", route.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse getPerson(org.springframework.web.servlet.function.ServerRequest)", route.getHandlerMethod());
	}
	
	@Test
	void testMvcBuilderSimpleMethodRoute() throws Exception {
		String routerMethod = """
			@Bean
			RouterFunction<ServerResponse> r2() {
				HandlerImplementation handler = new HandlerImplementation();

				return RouterFunctions.route()
					.GET("/hello", accept(TEXT_PLAIN), handler::getPerson)
					.build();
			}
			""";
		
		Bean bean = indexRouterMethod("r2", routerMethod);
		List<SpringIndexElement> children = bean.getChildren();
		
		WebfluxHandlerMethodIndexElement route = getWebfluxIndexElements(children, "/hello", "GET").get(0);
		assertNotNull(route);
		assertEquals("/hello", route.getPath());
		assertEquals("[GET]", Arrays.toString(route.getHttpMethods()));
		assertEquals("[TEXT_PLAIN]", Arrays.toString(route.getAcceptTypes()));

        assertEquals("org.test.webmvc.HandlerImplementation", route.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse getPerson(org.springframework.web.servlet.function.ServerRequest)", route.getHandlerMethod());
	}
	
	@Test
	void testMvcStaticMultipleRoutesWithDifferentMethods() throws Exception {
		String routerMethod = """
			@Bean
			RouterFunction<ServerResponse> multipleRoutes() {
				HandlerImplementation handler = new HandlerImplementation();

				return route(GET("/hello").and(accept(TEXT_PLAIN)), handler::getPerson)
					.andRoute(POST("/echo").and(accept(TEXT_PLAIN).and(contentType(TEXT_PLAIN))), handler::createPerson)
					.andRoute(GET("/quotes").and(accept(APPLICATION_JSON)), handler::listPeople);
			}
			""";
		
		Bean bean = indexRouterMethod("multipleRoutes", routerMethod);
		List<SpringIndexElement> children = bean.getChildren();
		
		assertEquals(6, children.size());
		
		WebfluxHandlerMethodIndexElement route1 = getWebfluxIndexElements(children, "/hello", "GET").get(0);
		assertEquals("/hello", route1.getPath());
		assertEquals("[GET]", Arrays.toString(route1.getHttpMethods()));
        assertEquals("org.test.webmvc.HandlerImplementation", route1.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse getPerson(org.springframework.web.servlet.function.ServerRequest)", route1.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route2 = getWebfluxIndexElements(children, "/echo", "POST").get(0);
		assertEquals("/echo", route2.getPath());
		assertEquals("[POST]", Arrays.toString(route2.getHttpMethods()));
		assertEquals("[TEXT_PLAIN]", Arrays.toString(route2.getContentTypes()));
        assertEquals("org.test.webmvc.HandlerImplementation", route2.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse createPerson(org.springframework.web.servlet.function.ServerRequest)", route2.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route3 = getWebfluxIndexElements(children, "/quotes", "GET").get(0);
		assertEquals("/quotes", route3.getPath());
		assertEquals("[APPLICATION_JSON]", Arrays.toString(route3.getAcceptTypes()));
        assertEquals("org.test.webmvc.HandlerImplementation", route3.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse listPeople(org.springframework.web.servlet.function.ServerRequest)", route3.getHandlerMethod());
	}

	@Test
	void testMvcBuilderMultipleRoutesWithDifferentMethods() throws Exception {
		String routerMethod = """
			@Bean
			RouterFunction<ServerResponse> multipleRoutes() {
				HandlerImplementation handler = new HandlerImplementation();

				return RouterFunctions.route()
					.GET("/hello", accept(TEXT_PLAIN), handler::getPerson)
					.POST("/echo", accept(TEXT_PLAIN), handler::createPerson)
					.GET("/person", accept(APPLICATION_JSON).and(contentType(TEXT_PLAIN)), handler::listPeople)
					.build();

			}
			""";
		
		Bean bean = indexRouterMethod("multipleRoutes", routerMethod);
		List<SpringIndexElement> children = bean.getChildren();
		
		assertEquals(6, children.size());
		
		WebfluxHandlerMethodIndexElement route1 = getWebfluxIndexElements(children, "/hello", "GET").get(0);
		assertEquals("/hello", route1.getPath());
		assertEquals("[GET]", Arrays.toString(route1.getHttpMethods()));
        assertEquals("org.test.webmvc.HandlerImplementation", route1.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse getPerson(org.springframework.web.servlet.function.ServerRequest)", route1.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route2 = getWebfluxIndexElements(children, "/echo", "POST").get(0);
		assertEquals("/echo", route2.getPath());
		assertEquals("[POST]", Arrays.toString(route2.getHttpMethods()));
		assertEquals("[TEXT_PLAIN]", Arrays.toString(route2.getAcceptTypes()));
        assertEquals("org.test.webmvc.HandlerImplementation", route2.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse createPerson(org.springframework.web.servlet.function.ServerRequest)", route2.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route3 = getWebfluxIndexElements(children, "/person", "GET").get(0);
		assertEquals("/person", route3.getPath());
		assertEquals("[APPLICATION_JSON]", Arrays.toString(route3.getAcceptTypes()));
		assertEquals("[TEXT_PLAIN]", Arrays.toString(route3.getContentTypes()));
        assertEquals("org.test.webmvc.HandlerImplementation", route3.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse listPeople(org.springframework.web.servlet.function.ServerRequest)", route3.getHandlerMethod());
	}

	@Test
	void testMvcStaticNestedRoutes() throws Exception {
		String routerMethod = """
			@Bean
			RouterFunction<ServerResponse> nestedRoute() {
				HandlerImplementation handler = new HandlerImplementation();

				return nest(accept(APPLICATION_JSON),
					nest(path("/person"),
						route(GET("/{id}"), handler::getPerson)
						.andRoute(method(HttpMethod.GET), handler::listPeople)
					).andRoute(POST("/").and(contentType(APPLICATION_JSON)), handler::createPerson));
			}
			""";
		
		Bean bean = indexRouterMethod("nestedRoute", routerMethod);
		List<SpringIndexElement> children = bean.getChildren();
		
		WebfluxHandlerMethodIndexElement route1 = getWebfluxIndexElements(children, "/person/{id}", "GET").get(0);
		assertEquals("/person/{id}", route1.getPath());
		assertEquals("[APPLICATION_JSON]", Arrays.toString(route1.getAcceptTypes()));
        assertEquals("org.test.webmvc.HandlerImplementation", route1.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse getPerson(org.springframework.web.servlet.function.ServerRequest)", route1.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route2 = getWebfluxIndexElements(children, "/person", "GET").get(0);
		assertEquals("/person", route2.getPath());
        assertEquals("org.test.webmvc.HandlerImplementation", route2.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse listPeople(org.springframework.web.servlet.function.ServerRequest)", route2.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route3 = getWebfluxIndexElements(children, "/", "POST").get(0);
		assertEquals("/", route3.getPath());
		assertEquals("[APPLICATION_JSON]", Arrays.toString(route3.getContentTypes()));
        assertEquals("org.test.webmvc.HandlerImplementation", route3.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse createPerson(org.springframework.web.servlet.function.ServerRequest)", route3.getHandlerMethod());
	}

	@Test
	void testMvcBuilderNestedRoutes() throws Exception {
		String routerMethod = """
			@Bean
			RouterFunction<ServerResponse> nestedRoute() {
			    HandlerImplementation handler = new HandlerImplementation();

			    return RouterFunctions.route()
			        .nest(accept(APPLICATION_JSON), jsonBuilder -> jsonBuilder
			            .nest(path("/person"), personBuilder -> personBuilder
			                .GET("/{id}", handler::getPerson)
			                .GET("", method(HttpMethod.GET), handler::listPeople))
			            .POST("/", contentType(APPLICATION_JSON), handler::createPerson))
			        .build();
			}
			""";
		
		Bean bean = indexRouterMethod("nestedRoute", routerMethod);
		List<SpringIndexElement> children = bean.getChildren();
		
		WebfluxHandlerMethodIndexElement route1 = getWebfluxIndexElements(children, "/person/{id}", "GET").get(0);
		assertEquals("/person/{id}", route1.getPath());
		assertEquals("[APPLICATION_JSON]", Arrays.toString(route1.getAcceptTypes()));
        assertEquals("org.test.webmvc.HandlerImplementation", route1.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse getPerson(org.springframework.web.servlet.function.ServerRequest)", route1.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route2 = getWebfluxIndexElements(children, "/person", "GET").get(0);
		assertEquals("/person", route2.getPath());
        assertEquals("org.test.webmvc.HandlerImplementation", route2.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse listPeople(org.springframework.web.servlet.function.ServerRequest)", route2.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route3 = getWebfluxIndexElements(children, "/", "POST").get(0);
		assertEquals("/", route3.getPath());
		assertEquals("[APPLICATION_JSON]", Arrays.toString(route3.getContentTypes()));
        assertEquals("org.test.webmvc.HandlerImplementation", route3.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse createPerson(org.springframework.web.servlet.function.ServerRequest)", route3.getHandlerMethod());
	}

	@Test
	void testMvcStaticComplicatedNestedRoutes() throws Exception {
		String routerMethod = """
			@Bean
			RouterFunction<ServerResponse> complicatedNested() {
				HandlerImplementation handler = new HandlerImplementation();

				return nest(path("/person"),
					nest(path("/sub1"),
					  nest(path("/sub2"),
						nest(accept(APPLICATION_JSON),
						  route(GET("/{id}"), handler::getPerson)
						  .andRoute(method(HttpMethod.GET), handler::listPeople))
						.andRoute(GET("/nestedGet"), handler::getPerson))
					  .and(nest(path("/andNestPath"),
						route(GET("/andNestPathGET"), handler::getPerson))))
					.andRoute(POST("/").and(contentType(APPLICATION_JSON)), handler::createPerson))
				  .and(nest(method(HttpMethod.DELETE), route(path("/nestedDelete"), handler::deletePerson)));
			}
			""";
		
		Bean bean = indexRouterMethod("complicatedNested", routerMethod);
		List<SpringIndexElement> children = bean.getChildren();
		
		WebfluxHandlerMethodIndexElement route1 = getWebfluxIndexElements(children, "/person/sub1/sub2/{id}", "GET").get(0);
		assertEquals("/person/sub1/sub2/{id}", route1.getPath());
        assertEquals("org.test.webmvc.HandlerImplementation", route1.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse getPerson(org.springframework.web.servlet.function.ServerRequest)", route1.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route2 = getWebfluxIndexElements(children, "/person/sub1/sub2", "GET").get(0);
		assertEquals("/person/sub1/sub2", route2.getPath());
        assertEquals("org.test.webmvc.HandlerImplementation", route2.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse listPeople(org.springframework.web.servlet.function.ServerRequest)", route2.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route3 = getWebfluxIndexElements(children, "/person/sub1/sub2/nestedGet", "GET").get(0);
		assertEquals("/person/sub1/sub2/nestedGet", route3.getPath());
        assertEquals("org.test.webmvc.HandlerImplementation", route3.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse getPerson(org.springframework.web.servlet.function.ServerRequest)", route3.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route4 = getWebfluxIndexElements(children, "/person/sub1/andNestPath/andNestPathGET", "GET").get(0);
		assertEquals("/person/sub1/andNestPath/andNestPathGET", route4.getPath());
        assertEquals("org.test.webmvc.HandlerImplementation", route4.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse getPerson(org.springframework.web.servlet.function.ServerRequest)", route4.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route5 = getWebfluxIndexElements(children, "/person/", "POST").get(0);
		assertEquals("/person/", route5.getPath());
        assertEquals("org.test.webmvc.HandlerImplementation", route5.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse createPerson(org.springframework.web.servlet.function.ServerRequest)", route5.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route6 = getWebfluxIndexElements(children, "/nestedDelete", "DELETE").get(0);
		assertEquals("/nestedDelete", route6.getPath());
        assertEquals("org.test.webmvc.HandlerImplementation", route6.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse deletePerson(org.springframework.web.servlet.function.ServerRequest)", route6.getHandlerMethod());
	}

	@Test
	void testMvcBuilderComplicatedNestedRoutes() throws Exception {
		String routerMethod = """
			@Bean
			RouterFunction<ServerResponse> complicatedNested() {
			    HandlerImplementation handler = new HandlerImplementation();

			    return RouterFunctions.route()
			        .nest(path("/person"), personBuilder -> personBuilder
			            .nest(path("/sub1"), sub1Builder -> sub1Builder
			                .nest(path("/sub2"), sub2Builder -> sub2Builder
			                    .nest(accept(APPLICATION_JSON), jsonBuilder -> jsonBuilder
			                        .GET("/{id}", handler::getPerson)
			                        .GET("", method(HttpMethod.GET), handler::listPeople))
			                    .GET("/nestedGet", handler::getPerson))
			                .nest(path("/andNestPath"), andNestBuilder -> andNestBuilder
			                    .GET("/andNestPathGET", handler::getPerson)))
			            .POST("/", contentType(APPLICATION_JSON), handler::createPerson))
			        .nest(method(HttpMethod.DELETE), deleteBuilder -> deleteBuilder
			            .DELETE("/nestedDelete", handler::deletePerson))
			        .build();
			}
			""";
		
		Bean bean = indexRouterMethod("complicatedNested", routerMethod);
		List<SpringIndexElement> children = bean.getChildren();
		
		WebfluxHandlerMethodIndexElement route1 = getWebfluxIndexElements(children, "/person/sub1/sub2/{id}", "GET").get(0);
		assertEquals("/person/sub1/sub2/{id}", route1.getPath());
        assertEquals("org.test.webmvc.HandlerImplementation", route1.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse getPerson(org.springframework.web.servlet.function.ServerRequest)", route1.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route2 = getWebfluxIndexElements(children, "/person/sub1/sub2", "GET").get(0);
		assertEquals("/person/sub1/sub2", route2.getPath());
        assertEquals("org.test.webmvc.HandlerImplementation", route2.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse listPeople(org.springframework.web.servlet.function.ServerRequest)", route2.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route3 = getWebfluxIndexElements(children, "/person/sub1/sub2/nestedGet", "GET").get(0);
		assertEquals("/person/sub1/sub2/nestedGet", route3.getPath());
        assertEquals("org.test.webmvc.HandlerImplementation", route3.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse getPerson(org.springframework.web.servlet.function.ServerRequest)", route3.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route4 = getWebfluxIndexElements(children, "/person/sub1/andNestPath/andNestPathGET", "GET").get(0);
		assertEquals("/person/sub1/andNestPath/andNestPathGET", route4.getPath());
        assertEquals("org.test.webmvc.HandlerImplementation", route4.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse getPerson(org.springframework.web.servlet.function.ServerRequest)", route4.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route5 = getWebfluxIndexElements(children, "/person/", "POST").get(0);
		assertEquals("/person/", route5.getPath());
        assertEquals("org.test.webmvc.HandlerImplementation", route5.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse createPerson(org.springframework.web.servlet.function.ServerRequest)", route5.getHandlerMethod());
		
		WebfluxHandlerMethodIndexElement route6 = getWebfluxIndexElements(children, "/nestedDelete", "DELETE").get(0);
		assertEquals("/nestedDelete", route6.getPath());
        assertEquals("org.test.webmvc.HandlerImplementation", route6.getHandlerClass());
        assertEquals("public org.springframework.web.servlet.function.ServerResponse deletePerson(org.springframework.web.servlet.function.ServerRequest)", route6.getHandlerMethod());
	}

	// Helper method to wrap router method in a complete class and index it
	private Bean indexRouterMethod(String beanName, String routerMethod) throws Exception {
		String source = """
			package org.test;
			
			import static org.springframework.http.MediaType.*;
			import static org.springframework.web.servlet.function.RequestPredicates.*;
			import static org.springframework.web.servlet.function.RouterFunctions.*;
			
			import org.springframework.context.annotation.Bean;
			import org.springframework.context.annotation.Configuration;
			import org.springframework.http.HttpMethod;
			import org.springframework.web.servlet.function.RouterFunction;
			import org.springframework.web.servlet.function.RouterFunctions;
			import org.springframework.web.servlet.function.ServerResponse;
			
			import org.test.webmvc.HandlerImplementation;
			
			@Configuration
			public class TestRouter {
				
			%s
			}
			""".formatted(routerMethod);
		
		CompletableFuture<Void> updateFuture = indexer.updateDocument(testDocUri, source, "test triggered");
 		updateFuture.get(5, TimeUnit.SECONDS);
		
		Bean[] beans = springIndex.getBeansOfDocument(testDocUri, beanName);
		assertEquals(1, beans.length, "Expected exactly one bean named: " + beanName);
		
		return beans[0];
	}
	
	private List<WebfluxHandlerMethodIndexElement> getWebfluxIndexElements(
			List<SpringIndexElement> children, String path, String httpMethod) {
		return children.stream()
			.filter(obj -> obj instanceof WebfluxHandlerMethodIndexElement)
			.map(obj -> (WebfluxHandlerMethodIndexElement) obj)
			.filter(addon -> addon.getPath().equals(path) && 
				   Arrays.asList(addon.getHttpMethods()).contains(httpMethod))
			.collect(Collectors.toList());
	}
}
