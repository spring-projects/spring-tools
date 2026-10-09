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
package org.springframework.ide.vscode.boot.java.livehover.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.ide.vscode.boot.java.livehover.v2.LiveFunctionalRoute;
import org.springframework.ide.vscode.boot.java.livehover.v2.LiveRequestMapping;
import org.springframework.ide.vscode.boot.java.livehover.v2.LiveRequestMappingBoot2xParser;

/**
 * @author Martin Lippert
 */
public class LiveFunctionalRouteTest {

	private static final String LAMBDA_CLASS = "org.test.RouterConfig$$Lambda/0x000001f8013c5a28";

	@Test
	void testSimpleRoute() {
		LiveFunctionalRoute route = LiveFunctionalRoute.parse(LAMBDA_CLASS, "((GET && /hello) && Accept: text/plain)");
		assertEquals("/hello", route.path());
		assertEquals(Set.of("GET"), route.httpMethods());
		assertEquals("org.test.RouterConfig", route.getDeclaringClassName());
	}

	@Test
	void testNestedRoutesAreConcatenated() {
		LiveFunctionalRoute route = LiveFunctionalRoute.parse(LAMBDA_CLASS,
				"((((/person && /sub1) && /sub2) && Accept: application/json) && (GET && /{id}))");
		assertEquals("/person/sub1/sub2/{id}", route.path());
		assertEquals(Set.of("GET"), route.httpMethods());
	}

	@Test
	void testNestedRouteWithoutOwnPath() {
		LiveFunctionalRoute route = LiveFunctionalRoute.parse(LAMBDA_CLASS, "((/person && (Accept: application/json && 1.0)) && GET)");
		assertEquals("/person", route.path());
		assertEquals(Set.of("GET"), route.httpMethods());
	}

	@Test
	void testRouteWithoutPathAndMethod() {
		LiveFunctionalRoute route = LiveFunctionalRoute.parse(LAMBDA_CLASS, "Accept: [text/plain, application/json]");
		assertEquals("", route.path());
		assertEquals(Set.of(), route.httpMethods());
	}

	@Test
	void testMultipleHttpMethods() {
		assertEquals(Set.of("GET", "HEAD"), LiveFunctionalRoute.parse(LAMBDA_CLASS, "((GET && HEAD) && Accept: text/plain)").httpMethods());
		assertEquals(Set.of("GET", "POST"), LiveFunctionalRoute.parse(LAMBDA_CLASS, "([GET, POST] && /hello)").httpMethods());
	}

	@Test
	void testSlashesAreNormalized() {
		assertEquals("/person/", LiveFunctionalRoute.parse(LAMBDA_CLASS, "(/person && ((POST && /) && Content-Type: application/json))").path());
		assertEquals("/person/x", LiveFunctionalRoute.parse(LAMBDA_CLASS, "(/person/ && (GET && /x))").path());
	}

	@Test
	void testOrAndNegationAreNotSupported() {
		assertNull(LiveFunctionalRoute.parse(LAMBDA_CLASS, "((GET && /a) || (GET && /b))"));
		assertNull(LiveFunctionalRoute.parse(LAMBDA_CLASS, "(GET && !/a)"));
	}

	@Test
	void testDeclaringClassName() {
		assertEquals("org.test.Outer.Inner", LiveFunctionalRoute.parse("org.test.Outer$Inner$$Lambda/0x0000000801234567", "/a").getDeclaringClassName());
		assertEquals("org.test.RouterConfig", LiveFunctionalRoute.parse("org.test.RouterConfig$$Lambda$123/0x0000000801234567", "/a").getDeclaringClassName());
		assertEquals("org.test.MyHandlerFunction", LiveFunctionalRoute.parse("org.test.MyHandlerFunction", "/a").getDeclaringClassName());
	}

	@Test
	void testParseBoot4MappingsJson() {
		String json = """
			{
			  "contexts": {
			    "application": {
			      "mappings": {
			        "dispatcherHandlers": {
			          "webHandler": [
			            {
			              "predicate": "((Accept: application/json && /person) && (GET && /{id}))",
			              "handler": "org.test.RouterConfig$$Lambda/0x000001f8013c5a28@4b2c5e8f",
			              "details": {
			                "handlerFunction": {
			                  "className": "org.test.RouterConfig$$Lambda/0x000001f8013c5a28"
			                }
			              }
			            },
			            {
			              "predicate": "{GET [/hello]}",
			              "handler": "org.test.HelloController#hello()",
			              "details": {
			                "handlerMethod": {
			                  "className": "org.test.HelloController",
			                  "name": "hello",
			                  "descriptor": "()Ljava/lang/String;"
			                },
			                "requestMappingConditions": {
			                  "consumes": [], "headers": [], "methods": ["GET"], "params": [], "patterns": ["/hello"], "produces": []
			                }
			              }
			            }
			          ]
			        }
			      }
			    }
			  }
			}
			""";

		LiveRequestMapping[] mappings = LiveRequestMappingBoot2xParser.parse(new JSONObject(json));
		assertEquals(2, mappings.length);

		LiveRequestMapping functional = mappings[0];
		assertNotNull(functional.getFunctionalRoute());
		assertArrayEquals(new String[] { "/person/{id}" }, functional.getSplitPath());
		assertEquals(Set.of("GET"), functional.getRequestMethods());

		LiveRequestMapping annotated = mappings[1];
		assertNull(annotated.getFunctionalRoute());
		assertArrayEquals(new String[] { "/hello" }, annotated.getSplitPath());
		assertEquals("hello", annotated.getMethodName());
	}

}
