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
 * Live hovers and code lenses for request mappings that are versioned via a path segment
 * (<code>usePathSegment(1)</code> in the web config of the project), using mappings actuator data in the Spring Boot 4 format
 *
 * @author Seonwoo Jung
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class ApiVersionLiveHoverTest {

	private static final String PROJECT_NAME = "test-api-version-path-segment";
	private static final String CONTROLLER = "org.test.versions.GreetingController";
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
	}

	@Test
	void versionIsPutIntoPathSegmentOfLiveUrl() throws Exception {
		liveDataProvider.add("processkey", liveData(
				mapping("{GET [/api/{version}/greeting], version [1]}", "/api/{version}/greeting", "hello1"),
				mapping("{GET [/api/{version}/greeting], version [1.1+]}", "/api/{version}/greeting", "hello1_1")));

		Editor editor = openEditor("src/main/java/org/test/versions/GreetingController.java");

		editor.assertHoverContains("@GetMapping(path = \"/greeting\", version = \"1\")", link("https://cfapps.io:1111/api/1/greeting") + PROCESS);
		editor.assertHoverContains("@GetMapping(path = \"/greeting\", version = \"1.1+\")", link("https://cfapps.io:1111/api/1.1/greeting") + PROCESS);

		editor.assertLiveCodeLensContains("@GetMapping(path = \"/greeting\", version = \"1\")", "https://cfapps.io:1111/api/1/greeting");
		editor.assertLiveCodeLensContains("@GetMapping(path = \"/greeting\", version = \"1.1+\")", "https://cfapps.io:1111/api/1.1/greeting");
	}

	@Test
	void unversionedMappingKeepsPath() throws Exception {
		liveDataProvider.add("processkey", liveData(
				mapping("{GET [/api/{version}/unversioned]}", "/api/{version}/unversioned", "unversioned")));

		Editor editor = openEditor("src/main/java/org/test/versions/GreetingController.java");

		editor.assertHoverContains("@GetMapping(path = \"/unversioned\")", link("https://cfapps.io:1111/api/{version}/unversioned") + PROCESS);
	}

	private Editor openEditor(String file) throws Exception {
		String docUri = directory.toPath().resolve(file).toUri().toString();
		return harness.newEditorFromFileUri(docUri, LanguageId.JAVA);
	}

	private static String link(String url) {
		return "[" + url + "](" + url + ")  \n";
	}

	private static String mapping(String predicate, String pattern, String methodName) {
		return """
			{
			  "predicate": "%s",
			  "handler": "%s#%s()",
			  "details": {
			    "handlerMethod": {
			      "className": "%s",
			      "name": "%s",
			      "descriptor": "()Ljava/lang/String;"
			    },
			    "requestMappingConditions": {
			      "consumes": [],
			      "headers": [],
			      "methods": [ "GET" ],
			      "params": [],
			      "patterns": [ "%s" ],
			      "produces": []
			    }
			  }
			}
			""".formatted(predicate, CONTROLLER, methodName, CONTROLLER, methodName, pattern);
	}

	private static SpringProcessLiveData liveData(String... mappings) {
		String json = """
			{
			  "contexts": {
			    "application": {
			      "mappings": {
			        "dispatcherServlets": {
			          "dispatcherServlet": [ %s ]
			        }
			      }
			    }
			  }
			}
			""".formatted(String.join(",", mappings));

		return new SpringProcessLiveDataBuilder()
				.port("1111")
				.processID("22022")
				.host("cfapps.io")
				.urlScheme("https")
				.processName(PROJECT_NAME)
				.requestMappings(LiveRequestMappingBoot2xParser.parse(new JSONObject(json)))
				.build();
	}

}
