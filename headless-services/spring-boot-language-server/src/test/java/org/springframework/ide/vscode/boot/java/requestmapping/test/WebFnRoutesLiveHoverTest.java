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

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.java.livehover.v2.LiveRequestMappingBoot2xParser;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessLiveData;
import org.springframework.ide.vscode.boot.java.livehover.v2.SpringProcessLiveDataProvider;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.ide.vscode.project.harness.SpringProcessLiveDataBuilder;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Live hovers for functional (WebFlux.fn / WebMvc.fn) router bean methods, using mappings actuator data in the Spring Boot 4 format
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class WebFnRoutesLiveHoverTest {

	private static final String PROJECT_NAME = "test-web-functional-endpoints";

	private static final String FLUX_STATIC_LAMBDA = "org.test.webflux.WebfluxRouterDefinitionsUsingStaticMethods$$Lambda/0x000001f8013c5a28";
	private static final String FLUX_BUILDER_LAMBDA = "org.test.webflux.WebfluxRouterDefinitionsUsingBuilderPattern$$Lambda/0x000001f8013c7b10";
	private static final String MVC_BUILDER_LAMBDA = "org.test.webmvc.WebmvcRouterDefinitionsUsingBuilderPattern$$Lambda/0x000001f8013d1e40";

	private static final String PROCESS = "Process [PID=22022, name=`" + PROJECT_NAME + "`]";

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringProcessLiveDataProvider liveDataProvider;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		directory = new File(ProjectsHarness.class.getResource("/test-projects/" + PROJECT_NAME + "/").toURI());

		projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

	@AfterEach
	public void tearDown() throws Exception {
		liveDataProvider.remove("processkey");
		liveDataProvider.remove("processkey2");
	}

	@Test
	void testWebFluxStaticRouterMethods() throws Exception {
		liveDataProvider.add("processkey", liveData("1111", "22022", "dispatcherHandlers", "webHandler",
				route(FLUX_STATIC_LAMBDA, "((GET && /hello) && Accept: text/plain)"),
				route(FLUX_STATIC_LAMBDA, "((GET && /hello) && Accept: text/plain)"),
				route(FLUX_STATIC_LAMBDA, "((POST && /echo) && (Accept: text/plain && Content-Type: text/plain))"),
				route(FLUX_STATIC_LAMBDA, "((GET && /quotes) && Accept: application/json)"),
				route(FLUX_STATIC_LAMBDA, "((GET && /quotes) && Accept: application/x-ndjson)"),
				route(FLUX_STATIC_LAMBDA, "((((/person && /sub1) && /sub2) && Accept: application/json) && (GET && /{id}))"),
				route(FLUX_STATIC_LAMBDA, "((((/person && /sub1) && /sub2) && Accept: application/json) && GET)"),
				route(FLUX_STATIC_LAMBDA, "(((/person && /sub1) && /sub2) && (GET && /nestedGet))"),
				route(FLUX_STATIC_LAMBDA, "(((/person && /sub1) && /andNestPath) && (GET && /andNestPathGET))"),
				route(FLUX_STATIC_LAMBDA, "(/person && ((POST && /) && Content-Type: application/json))"),
				route(FLUX_STATIC_LAMBDA, "(DELETE && /nestedDelete)")));

		Editor editor = openEditor("src/main/java/org/test/webflux/WebfluxRouterDefinitionsUsingStaticMethods.java");

		editor.assertTrimmedHover("simpleFluxStaticMethodRoute",
				link("https://cfapps.io:1111/hello") + PROCESS);

		// same path with different accept types shows up only once
		editor.assertTrimmedHover("multipleFluxStaticMethodRoutes",
				link("https://cfapps.io:1111/hello") +
				link("https://cfapps.io:1111/echo") +
				link("https://cfapps.io:1111/quotes") +
				PROCESS);

		editor.assertTrimmedHover("complicatedNestedFluxStaticMethodRoute",
				link("https://cfapps.io:1111/person/sub1/sub2/{id}") +
				link("https://cfapps.io:1111/person/sub1/sub2") +
				link("https://cfapps.io:1111/person/sub1/sub2/nestedGet") +
				link("https://cfapps.io:1111/person/sub1/andNestPath/andNestPathGET") +
				link("https://cfapps.io:1111/person/") +
				link("https://cfapps.io:1111/nestedDelete") +
				PROCESS);

		editor.assertLiveCodeLensContains("simpleFluxStaticMethodRoute", "https://cfapps.io:1111/hello");
		editor.assertLiveCodeLensContains("complicatedNestedFluxStaticMethodRoute", "https://cfapps.io:1111/person/sub1/sub2/{id}");
	}

	@Test
	void testRoutesFromOtherClassesAreIgnored() throws Exception {
		// same path and method as the route defined in WebfluxRouterDefinitionsUsingStaticMethods, but declared in a different class
		liveDataProvider.add("processkey", liveData("2222", "33033", "dispatcherHandlers", "webHandler",
				route(FLUX_BUILDER_LAMBDA, "((GET && /hello) && Accept: text/plain)")));

		Editor editor = openEditor("src/main/java/org/test/webflux/WebfluxRouterDefinitionsUsingStaticMethods.java");
		editor.assertNoHover("simpleFluxStaticMethodRoute");
	}

	@Test
	void testWebMvcBuilderRouterMethods() throws Exception {
		liveDataProvider.add("processkey", liveData("1111", "22022", "dispatcherServlets", "dispatcherServlet",
				route(MVC_BUILDER_LAMBDA, "((GET && /person/{id}) && (Accept: application/json && 1.0.0))"),
				route(MVC_BUILDER_LAMBDA, "((GET && /person/{id}) && Accept: application/json)"),
				route(MVC_BUILDER_LAMBDA, "((GET && /person) && (Accept: application/json && Content-Type: text/plain))"),
				route(MVC_BUILDER_LAMBDA, "(POST && /person)"),
				route(MVC_BUILDER_LAMBDA, "((/person && (Accept: application/json && 1.0)) && (GET && /{id}))"),
				route(MVC_BUILDER_LAMBDA, "((/person && (Accept: application/json && 1.0)) && GET)"),
				route(MVC_BUILDER_LAMBDA, "(/person && POST)")));

		Editor editor = openEditor("src/main/java/org/test/webmvc/WebmvcRouterDefinitionsUsingBuilderPattern.java");

		editor.assertTrimmedHover("simpleRoute",
				link("https://cfapps.io:1111/person/{id}") + PROCESS);

		editor.assertTrimmedHover("routes()",
				link("https://cfapps.io:1111/person/{id}") +
				link("https://cfapps.io:1111/person") +
				PROCESS);
	}

	private Editor openEditor(String file) throws Exception {
		String docUri = directory.toPath().resolve(file).toUri().toString();
		return harness.newEditorFromFileUri(docUri, LanguageId.JAVA);
	}

	private static String link(String url) {
		return "[" + url + "](" + url + ")  \n";
	}

	private static String route(String handlerClass, String predicate) {
		return """
			{
			  "predicate": "%s",
			  "handler": "%s@4b2c5e8f",
			  "details": {
			    "handlerFunction": {
			      "className": "%s"
			    }
			  }
			}
			""".formatted(predicate, handlerClass, handlerClass);
	}

	private static SpringProcessLiveData liveData(String port, String processId, String dispatcherKey, String handlerKey, String... routes) {
		String json = """
			{
			  "contexts": {
			    "application": {
			      "mappings": {
			        "%s": {
			          "%s": [ %s ]
			        }
			      }
			    }
			  }
			}
			""".formatted(dispatcherKey, handlerKey, String.join(",", routes));

		return new SpringProcessLiveDataBuilder()
				.port(port)
				.processID(processId)
				.host("cfapps.io")
				.urlScheme("https")
				.processName(PROJECT_NAME)
				.requestMappings(LiveRequestMappingBoot2xParser.parse(new JSONObject(json)))
				.build();
	}

}
