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
package org.springframework.ide.vscode.boot.java.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.beans.test.SpringIndexerHarness;
import org.springframework.ide.vscode.boot.java.springai.SpringAiAnnotationIndexElement.AnnotationType;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.DocumentElement;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class SpringAiIndexerTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-ai-indexing/").toURI());
		String projectDir = directory.toURI().toString();

		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

	@Test
	void testToolMethodSymbolsWithinComponent() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/ToolsWithinComponent.java")
				.toUri().toString();

		SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
				SpringIndexerHarness.symbol("ToolsWithinComponent", "@+ 'toolsWithinComponent' (@Component) ToolsWithinComponent"),
				SpringIndexerHarness.symbol("add", "@Tool add"),
				SpringIndexerHarness.symbol("getWeather", "@Tool getWeather"));
	}

	@Test
	void testMcpToolAndPromptSymbolsWithinComponent() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/McpToolsWithinComponent.java")
				.toUri().toString();

		SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
				SpringIndexerHarness.symbol("McpToolsWithinComponent", "@+ 'mcpToolsWithinComponent' (@Component) McpToolsWithinComponent"),
				SpringIndexerHarness.symbol("calculate", "@McpTool calculate"),
				SpringIndexerHarness.symbol("greeting", "@McpPrompt greeting"));
	}

	@Test
	void testToolIndexElementAsChildOfBean() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/ToolsWithinComponent.java")
				.toUri().toString();

		Bean[] beans = springIndex.getBeansOfDocument(docUri);
		assertEquals(1, beans.length);

		Bean componentBean = Arrays.stream(beans)
				.filter(bean -> bean.getName().equals("toolsWithinComponent"))
				.findFirst().get();
		assertEquals("com.example.springai.demo.ToolsWithinComponent", componentBean.getType());

		List<SpringIndexElement> children = componentBean.getChildren();
		assertEquals(2, children.size());

		SpringAiAnnotationIndexElement addTool = children.stream()
				.filter(e -> e instanceof SpringAiAnnotationIndexElement)
				.map(e -> (SpringAiAnnotationIndexElement) e)
				.filter(e -> "add".equals(e.getName()))
				.findFirst().orElse(null);
		assertNotNull(addTool);
		assertEquals("add", addTool.getName());
		assertEquals("Add two numbers together", addTool.getDescription());
		assertEquals("com.example.springai.demo.ToolsWithinComponent", addTool.getContainerBeanType());
		assertEquals(AnnotationType.TOOL, addTool.getAnnotationType());

		Location addLocation = addTool.getLocation();
		assertNotNull(addLocation);
		assertEquals(docUri, addLocation.getUri());
		assertEquals(new Range(new Position(9, 12), new Position(9, 15)), addLocation.getRange());

		DocumentSymbol addSymbol = addTool.getDocumentSymbol();
		assertEquals("@Tool add", addSymbol.getName());
		assertEquals(SymbolKind.Function, addSymbol.getKind());
	}

	@Test
	void testToolMethodWithoutExplicitNameFallsBackToMethodName() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/ToolsWithinComponent.java")
				.toUri().toString();

		Bean[] beans = springIndex.getBeansOfDocument(docUri);
		Bean componentBean = Arrays.stream(beans)
				.filter(bean -> bean.getName().equals("toolsWithinComponent"))
				.findFirst().get();

		SpringAiAnnotationIndexElement weatherTool = componentBean.getChildren().stream()
				.filter(e -> e instanceof SpringAiAnnotationIndexElement)
				.map(e -> (SpringAiAnnotationIndexElement) e)
				.filter(e -> "getWeather".equals(e.getName()))
				.findFirst().orElse(null);
		assertNotNull(weatherTool);
		assertEquals("getWeather", weatherTool.getName());
		assertEquals("Fetch the current weather for a given city", weatherTool.getDescription());

		Location location = weatherTool.getLocation();
		assertEquals(new Range(new Position(14, 15), new Position(14, 25)), location.getRange());
	}

	@Test
	void testMcpToolIndexElementAsChildOfBean() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/McpToolsWithinComponent.java")
				.toUri().toString();

		Bean[] beans = springIndex.getBeansOfDocument(docUri);
		assertEquals(1, beans.length);

		Bean componentBean = beans[0];
		List<SpringIndexElement> children = componentBean.getChildren();
		assertEquals(2, children.size());

		SpringAiAnnotationIndexElement calculateTool = children.stream()
				.filter(e -> e instanceof SpringAiAnnotationIndexElement)
				.map(e -> (SpringAiAnnotationIndexElement) e)
				.filter(e -> e.getAnnotationType() == AnnotationType.MCP_TOOL)
				.findFirst().orElse(null);
		assertNotNull(calculateTool);
		assertEquals("calculate", calculateTool.getName());
		assertEquals("Perform a calculation", calculateTool.getDescription());
		assertEquals("com.example.springai.demo.McpToolsWithinComponent", calculateTool.getContainerBeanType());

		Location location = calculateTool.getLocation();
		assertEquals(docUri, location.getUri());
		assertEquals(new Range(new Position(10, 12), new Position(10, 21)), location.getRange());

		DocumentSymbol symbol = calculateTool.getDocumentSymbol();
		assertEquals("@McpTool calculate", symbol.getName());
		assertEquals(SymbolKind.Function, symbol.getKind());
	}

	@Test
	void testMcpPromptIndexElementAsChildOfBean() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/McpToolsWithinComponent.java")
				.toUri().toString();

		Bean[] beans = springIndex.getBeansOfDocument(docUri);
		Bean componentBean = beans[0];

		SpringAiAnnotationIndexElement promptElement = componentBean.getChildren().stream()
				.filter(e -> e instanceof SpringAiAnnotationIndexElement)
				.map(e -> (SpringAiAnnotationIndexElement) e)
				.filter(e -> e.getAnnotationType() == AnnotationType.MCP_PROMPT)
				.findFirst().orElse(null);
		assertNotNull(promptElement);
		assertEquals("greeting", promptElement.getName());
		assertEquals("Generate a greeting message", promptElement.getDescription());
		assertEquals("com.example.springai.demo.McpToolsWithinComponent", promptElement.getContainerBeanType());

		Location location = promptElement.getLocation();
		assertEquals(docUri, location.getUri());
		assertEquals(new Range(new Position(15, 15), new Position(15, 23)), location.getRange());

		DocumentSymbol symbol = promptElement.getDocumentSymbol();
		assertEquals("@McpPrompt greeting", symbol.getName());
		assertEquals(SymbolKind.Function, symbol.getKind());
	}

	@Test
	void testToolMethodOnStandaloneClassCreatesTopLevelIndexElement() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/StandaloneToolsClass.java")
				.toUri().toString();

		Bean[] beans = springIndex.getBeansOfDocument(docUri);
		assertEquals(0, beans.length);

		DocumentElement document = springIndex.getDocument(docUri);
		assertNotNull(document);

		List<SpringIndexElement> children = document.getChildren();
		assertFalse(children.isEmpty());

		SpringAiAnnotationIndexElement searchTool = children.stream()
				.filter(e -> e instanceof SpringAiAnnotationIndexElement)
				.map(e -> (SpringAiAnnotationIndexElement) e)
				.filter(e -> e.getAnnotationType() == AnnotationType.TOOL)
				.findFirst().orElse(null);
		assertNotNull(searchTool);
		assertEquals("search", searchTool.getName());
		assertEquals("Search for information", searchTool.getDescription());
		assertEquals("com.example.springai.demo.StandaloneToolsClass", searchTool.getContainerBeanType());

		Location location = searchTool.getLocation();
		assertEquals(docUri, location.getUri());
		assertEquals(new Range(new Position(7, 15), new Position(7, 21)), location.getRange());
	}

	@Test
	void testMcpCompleteElicitationSamplingSymbolsWithinComponent() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/McpCompleteElicitationSamplingWithinComponent.java")
				.toUri().toString();

		SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
				SpringIndexerHarness.symbol("McpCompleteElicitationSamplingWithinComponent", "@+ 'mcpCompleteElicitationSamplingWithinComponent' (@Component) McpCompleteElicitationSamplingWithinComponent"),
				SpringIndexerHarness.symbol("completeCode", "@McpComplete completeCode"),
				SpringIndexerHarness.symbol("getUserInfo", "@McpElicitation getUserInfo"),
				SpringIndexerHarness.symbol("sampleText", "@McpSampling sampleText"));
	}

	@Test
	void testMcpCompleteIndexElementAsChildOfBean() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/McpCompleteElicitationSamplingWithinComponent.java")
				.toUri().toString();

		Bean[] beans = springIndex.getBeansOfDocument(docUri);
		assertEquals(1, beans.length);

		Bean componentBean = beans[0];
		List<SpringIndexElement> children = componentBean.getChildren();
		assertEquals(3, children.size());

		SpringAiAnnotationIndexElement completeElement = children.stream()
				.filter(e -> e instanceof SpringAiAnnotationIndexElement)
				.map(e -> (SpringAiAnnotationIndexElement) e)
				.filter(e -> e.getAnnotationType() == AnnotationType.MCP_COMPLETE)
				.findFirst().orElse(null);
		assertNotNull(completeElement);
		assertEquals("completeCode", completeElement.getName());
		assertEquals("Provide code completion suggestions", completeElement.getDescription());
		assertEquals("com.example.springai.demo.McpCompleteElicitationSamplingWithinComponent", completeElement.getContainerBeanType());

		Location location = completeElement.getLocation();
		assertEquals(docUri, location.getUri());

		DocumentSymbol symbol = completeElement.getDocumentSymbol();
		assertEquals("@McpComplete completeCode", symbol.getName());
		assertEquals(SymbolKind.Function, symbol.getKind());
	}

	@Test
	void testMcpElicitationIndexElementAsChildOfBean() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/McpCompleteElicitationSamplingWithinComponent.java")
				.toUri().toString();

		Bean[] beans = springIndex.getBeansOfDocument(docUri);
		Bean componentBean = beans[0];

		SpringAiAnnotationIndexElement elicitationElement = componentBean.getChildren().stream()
				.filter(e -> e instanceof SpringAiAnnotationIndexElement)
				.map(e -> (SpringAiAnnotationIndexElement) e)
				.filter(e -> e.getAnnotationType() == AnnotationType.MCP_ELICITATION)
				.findFirst().orElse(null);
		assertNotNull(elicitationElement);
		assertEquals("getUserInfo", elicitationElement.getName());
		assertEquals("Elicit information from the user", elicitationElement.getDescription());
		assertEquals("com.example.springai.demo.McpCompleteElicitationSamplingWithinComponent", elicitationElement.getContainerBeanType());

		Location location = elicitationElement.getLocation();
		assertEquals(docUri, location.getUri());

		DocumentSymbol symbol = elicitationElement.getDocumentSymbol();
		assertEquals("@McpElicitation getUserInfo", symbol.getName());
		assertEquals(SymbolKind.Function, symbol.getKind());
	}

	@Test
	void testMcpSamplingIndexElementAsChildOfBean() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/McpCompleteElicitationSamplingWithinComponent.java")
				.toUri().toString();

		Bean[] beans = springIndex.getBeansOfDocument(docUri);
		Bean componentBean = beans[0];

		SpringAiAnnotationIndexElement samplingElement = componentBean.getChildren().stream()
				.filter(e -> e instanceof SpringAiAnnotationIndexElement)
				.map(e -> (SpringAiAnnotationIndexElement) e)
				.filter(e -> e.getAnnotationType() == AnnotationType.MCP_SAMPLING)
				.findFirst().orElse(null);
		assertNotNull(samplingElement);
		assertEquals("sampleText", samplingElement.getName());
		assertEquals("Sample text from a language model", samplingElement.getDescription());
		assertEquals("com.example.springai.demo.McpCompleteElicitationSamplingWithinComponent", samplingElement.getContainerBeanType());

		Location location = samplingElement.getLocation();
		assertEquals(docUri, location.getUri());

		DocumentSymbol symbol = samplingElement.getDocumentSymbol();
		assertEquals("@McpSampling sampleText", symbol.getName());
		assertEquals(SymbolKind.Function, symbol.getKind());
	}

	@Test
	void testToolStandaloneElementsFoundViaGlobalIndex() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/StandaloneToolsClass.java")
				.toUri().toString();

		List<SpringAiAnnotationIndexElement> allAnnotationElements = springIndex.getNodesOfType(SpringAiAnnotationIndexElement.class);
		assertFalse(allAnnotationElements.isEmpty());

		List<SpringAiAnnotationIndexElement> toolsFromStandaloneClass = allAnnotationElements.stream()
				.filter(e -> e.getAnnotationType() == AnnotationType.TOOL)
				.filter(e -> e.getLocation().getUri().equals(docUri))
				.toList();
		assertEquals(1, toolsFromStandaloneClass.size());
		assertEquals("search", toolsFromStandaloneClass.get(0).getName());
	}

}
