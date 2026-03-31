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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
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

		List<SpringAiToolParameter> addParams = addTool.getParameters();
		assertEquals(2, addParams.size());

		SpringAiToolParameter paramA = addParams.get(0);
		assertEquals("a", paramA.getName());
		assertEquals("int", paramA.getType());
		assertNotNull(paramA.getLocation());
		assertEquals(docUri, paramA.getLocation().getUri());

		SpringAiToolParameter paramB = addParams.get(1);
		assertEquals("b", paramB.getName());
		assertEquals("int", paramB.getType());
		assertNotNull(paramB.getLocation());
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
	void testMcpResourceSymbolsWithinComponent() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/McpResourceWithinComponent.java")
				.toUri().toString();

		SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
				SpringIndexerHarness.symbol("McpResourceWithinComponent", "@+ 'mcpResourceWithinComponent' (@Component) McpResourceWithinComponent"),
				SpringIndexerHarness.symbol("getProfile", "@McpResource getProfile"));
	}

	@Test
	void testMcpResourceIndexElementAsChildOfBean() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/McpResourceWithinComponent.java")
				.toUri().toString();

		Bean[] beans = springIndex.getBeansOfDocument(docUri);
		assertEquals(1, beans.length);

		Bean componentBean = beans[0];
		List<SpringIndexElement> children = componentBean.getChildren();
		assertEquals(1, children.size());

		SpringAiAnnotationIndexElement resourceElement = children.stream()
				.filter(e -> e instanceof SpringAiAnnotationIndexElement)
				.map(e -> (SpringAiAnnotationIndexElement) e)
				.filter(e -> e.getAnnotationType() == AnnotationType.MCP_RESOURCE)
				.findFirst().orElse(null);
		assertNotNull(resourceElement);
		assertEquals("getProfile", resourceElement.getName());
		assertEquals("Get the user profile resource", resourceElement.getDescription());
		assertEquals("com.example.springai.demo.McpResourceWithinComponent", resourceElement.getContainerBeanType());

		Location location = resourceElement.getLocation();
		assertEquals(docUri, location.getUri());
		assertEquals(new Range(new Position(9, 15), new Position(9, 25)), location.getRange());

		DocumentSymbol symbol = resourceElement.getDocumentSymbol();
		assertEquals("@McpResource getProfile", symbol.getName());
		assertEquals(SymbolKind.Function, symbol.getKind());
	}

	@Test
	void testMcpResourceElementsFoundViaGlobalIndex() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/McpResourceWithinComponent.java")
				.toUri().toString();

		List<SpringAiAnnotationIndexElement> allAnnotationElements = springIndex.getNodesOfType(SpringAiAnnotationIndexElement.class);
		assertFalse(allAnnotationElements.isEmpty());

		List<SpringAiAnnotationIndexElement> resourceElements = allAnnotationElements.stream()
				.filter(e -> e.getAnnotationType() == AnnotationType.MCP_RESOURCE)
				.filter(e -> e.getLocation().getUri().equals(docUri))
				.toList();
		assertEquals(1, resourceElements.size());
		assertEquals("getProfile", resourceElements.get(0).getName());
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

	@Test
	void testToolParameterAnnotationMetadataIsCaptured() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/ToolWithAnnotatedParam.java")
				.toUri().toString();

		Bean[] beans = springIndex.getBeansOfDocument(docUri);
		assertEquals(1, beans.length);

		Bean componentBean = beans[0];
		List<SpringIndexElement> children = componentBean.getChildren();
		assertEquals(1, children.size());

		SpringAiAnnotationIndexElement convertTool = (SpringAiAnnotationIndexElement) children.get(0);
		assertEquals("convertTemperature", convertTool.getName());
		assertEquals(AnnotationType.TOOL, convertTool.getAnnotationType());

		List<SpringAiToolParameter> params = convertTool.getParameters();
		assertEquals(1, params.size());

		SpringAiToolParameter celsiusParam = params.get(0);
		assertEquals("celsius", celsiusParam.getName());
		assertEquals("double", celsiusParam.getType());
		assertNotNull(celsiusParam.getLocation());
		assertEquals(docUri, celsiusParam.getLocation().getUri());

		AnnotationMetadata[] paramAnnotations = celsiusParam.getAnnotations();
		assertEquals(1, paramAnnotations.length);
		assertEquals("com.fasterxml.jackson.annotation.JsonProperty", paramAnnotations[0].getAnnotationType());
	}

	@Test
	void testMcpToolParamAnnotationIsCaptured() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/ToolWithMcpToolParam.java")
				.toUri().toString();

		Bean[] beans = springIndex.getBeansOfDocument(docUri);
		assertEquals(1, beans.length);

		Bean componentBean = beans[0];
		List<SpringIndexElement> children = componentBean.getChildren();
		assertEquals(1, children.size());

		SpringAiAnnotationIndexElement divideTool = (SpringAiAnnotationIndexElement) children.get(0);
		assertEquals("divide", divideTool.getName());
		assertEquals(AnnotationType.MCP_TOOL, divideTool.getAnnotationType());

		List<SpringAiToolParameter> params = divideTool.getParameters();
		assertEquals(2, params.size());

		SpringAiToolParameter dividendParam = params.get(0);
		assertEquals("dividend", dividendParam.getName());
		assertEquals("double", dividendParam.getType());
		assertEquals("The dividend", dividendParam.getDescription());
		assertNotNull(dividendParam.getLocation());
		assertEquals(docUri, dividendParam.getLocation().getUri());
		AnnotationMetadata[] dividendAnnotations = dividendParam.getAnnotations();
		assertEquals(1, dividendAnnotations.length);
		assertEquals("org.springframework.ai.mcp.annotation.McpToolParam", dividendAnnotations[0].getAnnotationType());

		SpringAiToolParameter divisorParam = params.get(1);
		assertEquals("divisor", divisorParam.getName());
		assertEquals("double", divisorParam.getType());
		assertEquals("The divisor", divisorParam.getDescription());
		AnnotationMetadata[] divisorAnnotations = divisorParam.getAnnotations();
		assertEquals(1, divisorAnnotations.length);
		assertEquals("org.springframework.ai.mcp.annotation.McpToolParam", divisorAnnotations[0].getAnnotationType());
	}

	@Test
	void testMcpToolParametersAreIndexed() throws Exception {
		String docUri = directory.toPath()
				.resolve("src/main/java/com/example/springai/demo/McpToolsWithinComponent.java")
				.toUri().toString();

		Bean[] beans = springIndex.getBeansOfDocument(docUri);
		Bean componentBean = beans[0];

		SpringAiAnnotationIndexElement calculateTool = componentBean.getChildren().stream()
				.filter(e -> e instanceof SpringAiAnnotationIndexElement)
				.map(e -> (SpringAiAnnotationIndexElement) e)
				.filter(e -> e.getAnnotationType() == AnnotationType.MCP_TOOL)
				.findFirst().orElse(null);
		assertNotNull(calculateTool);

		List<SpringAiToolParameter> params = calculateTool.getParameters();
		assertEquals(2, params.size());

		SpringAiToolParameter paramX = params.get(0);
		assertEquals("x", paramX.getName());
		assertEquals("int", paramX.getType());
		assertNotNull(paramX.getLocation());
		assertTrue(paramX.getAnnotations().length == 0);

		SpringAiToolParameter paramY = params.get(1);
		assertEquals("y", paramY.getName());
		assertEquals("int", paramY.getType());
	}

}
