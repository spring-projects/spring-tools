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
package org.springframework.ide.vscode.boot.java.data.test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.Settings;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.Gson;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class DataRepositoryAotMetadataCodeLensProviderJdbcTest {
	
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	
	private IJavaProject testProject;

	@BeforeEach
	public void setup() throws Exception {
		testProject = ProjectsHarness.INSTANCE.mavenProject("aot-data-repositories-jdbc");
		harness.useProject(testProject);
		harness.intialize(null);
		
		harness.changeConfiguration(new Settings(new Gson()
				.toJsonTree(Map.of("boot-java", Map.of("java", Map.of("codelens-over-query-methods", true))))));

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(testProject.getLocationUri().toASCIIString())).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
	@Test
	void codeLensOverMethod() throws Exception {		
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/CategoryRepository.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());
		
		List<CodeLens> cls = editor.getCodeLenses("findAllByNameContaining", 1);
		assertEquals("Turn into @Query", cls.get(0).getCommand().getTitle());
		assertEquals("Go To Implementation", cls.get(1).getCommand().getTitle());
		assertEquals("SELECT \"CATEGORY\".\"ID\" AS \"ID\", \"CATEGORY\".\"NAME\" AS \"NAME\", \"CATEGORY\".\"CREATED\" AS \"CREATED\", \"CATEGORY\".\"INSERTED\" AS \"INSERTED\", \"CATEGORY\".\"DESCRIPTION\" AS \"DESCRIPTION\" FROM \"CATEGORY\" WHERE \"CATEGORY\".\"NAME\" LIKE :name", cls.get(2).getCommand().getTitle());
	}

	@Test
	void noCodeLensOverMethodWithQueryAnnotation() throws Exception {		
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/CategoryRepository.java");
		Editor editor = harness.newEditor(LanguageId.JAVA, new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8), filePath.toUri().toASCIIString());
		
		List<CodeLens> cls = editor.getCodeLenses("findWithDeclaredQuery", 1);
		assertEquals(2, cls.size());
		assertEquals("Go To Implementation", cls.get(0).getCommand().getTitle());
		assertEquals(1, cls.get(0).getCommand().getArguments().size());
		assertEquals("Refresh AOT Metadata", cls.get(1).getCommand().getTitle());
		assertEquals(2, cls.get(1).getCommand().getArguments().size());
	}

	/**
	 * Verify that text blocks are generated when the query string contains quotes on Java 15 or above.
	 */
	@Test
	void turnIntoQueryUsesTextBlockWhenQuotesPresentAndJava15OrAbove() throws Exception {
		Path filePath = Paths.get(testProject.getLocationUri())
				.resolve("src/main/java/example/springdata/aot/CategoryRepository.java");

		Editor editor = harness.newEditor(
				LanguageId.JAVA,
				new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8),
				filePath.toUri().toASCIIString()
		);

		List<CodeLens> cls = editor.getCodeLenses("findAllByNameContaining", 1);

		String queryValue = extractValueFromAttributes(cls.get(0));
		assertNotNull(queryValue, "Query value should not be null");

		int javaVersion = 8;
		String versionStr = testProject.getClasspath().getJre().version();
		if (versionStr.startsWith("1.")) {
			javaVersion = Integer.parseInt(versionStr.substring(2, 3));
		} else {
			javaVersion = Integer.parseInt(versionStr.split("\\.")[0]);
		}

		// JDBC query has quotes: SELECT "CATEGORY"."ID" ...
		if (javaVersion >= 15) {
			assertTrue(queryValue.startsWith("\"\"\""), "Text block must be used for Java >= 15 when quotes are present");
		} else {
			assertFalse(queryValue.startsWith("\"\"\""), "Text block must NOT be used for Java < 15");
		}
	}


	private String extractValueFromAttributes(CodeLens codeLens) {
		Object args = codeLens.getCommand().getArguments().get(1);
		if (args instanceof JsonObject) {
			JsonObject params = (JsonObject) args;
			if (params.has("parameters") && params.get("parameters").isJsonObject()) {
				JsonObject parameters = params.getAsJsonObject("parameters");
				if (parameters.has("attributes") && parameters.get("attributes").isJsonArray()) {
					JsonArray attributes = parameters.getAsJsonArray("attributes");
					for (JsonElement element : attributes) {
						if (element.isJsonObject()) {
							JsonObject attr = element.getAsJsonObject();
							if (attr.has("name") && "value".equals(attr.get("name").getAsString())) {
								return attr.get("value").getAsString();
							}
						}
					}
				}
			}
		}
		return null;
	}
}
