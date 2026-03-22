/*******************************************************************************
 * Copyright (c) 2017, 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.handlers.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.java.handlers.CopilotCodeLensProvider;
import org.springframework.ide.vscode.boot.java.handlers.QueryType;
import org.springframework.ide.vscode.boot.java.spel.SpelSemanticTokens;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.languageserver.util.ExecuteCommandHandler;
import org.springframework.ide.vscode.commons.languageserver.util.SimpleLanguageServer;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.TextDocumentInfo;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.JsonPrimitive;

/**
 * @author Udayani V
 */
@SuppressWarnings("deprecation")
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class CopilotCodeLensProviderTest {

	@Autowired
	private BootLanguageServerHarness harness;
	@Autowired
	private JavaProjectFinder projectFinder;
	@Autowired
	private SpringSymbolIndex indexer;
	private SimpleLanguageServer server;
	
	@Autowired
	private SpelSemanticTokens spelSemanticTokens;

	private ArgumentCaptor<ExecuteCommandHandler> commandHandlerCaptor;
	private CopilotCodeLensProvider queryCodeLensProvider;
	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spel-query-aop-codelenses/").toURI());
		String projectDir = directory.toURI().toString();
		server = mock(SimpleLanguageServer.class);
		commandHandlerCaptor = ArgumentCaptor.forClass(ExecuteCommandHandler.class);
		queryCodeLensProvider = new CopilotCodeLensProvider(projectFinder, server, spelSemanticTokens);

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);

		verify(server).onCommand(eq(CopilotCodeLensProvider.CMD_ENABLE_COPILOT_FEATURES), commandHandlerCaptor.capture());
	}

	@Test
	public void testShowCodeLensesTrueForQuery() throws Exception {

		setCommandParamsHandler(true);

		String docUri = directory.toPath().resolve("src/main/java/org/test/OwnerRepository.java").toUri().toString();
		TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
		TextDocumentInfo openedDoc = harness.openDocument(doc);

		List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);

		assertEquals(3, codeLenses.size());

		assertTrue(containsCodeLens(codeLenses.get(0), QueryType.DEFAULT.getTitle(), 9, 1, 9, 109));
		assertTrue(containsCodeLens(codeLenses.get(1), QueryType.DEFAULT.getTitle(), 13, 1, 13, 40));
		assertTrue(containsCodeLens(codeLenses.get(2), QueryType.DEFAULT.getTitle(), 17, 1, 17, 93));
	}

	@Test
	public void testShowCodeLensesTrueForSpel() throws Exception {

		// Simulate the command execution with true parameter
		setCommandParamsHandler(true);

		String docUri = directory.toPath().resolve("src/main/java/org/test/SpelController.java").toUri().toString();
		TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
		TextDocumentInfo openedDoc = harness.openDocument(doc);

		List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);
		
		String expectedPrompt = """
Explain the following SpEL Expression with a clear summary first, followed by a breakdown of the expression with details: \n
T(org.test.SpelController).isValidVersion('${app.version}') ? 'Valid Version' :'Invalid Version'

   Finally, provide a brief summary of what the following method does, focusing on its role within the SpEL expression.
   The summary should mention key criteria the method checks but avoid detailed implementation steps.
   Please include this summary as an appendix to the main explanation, and avoid repeating information covered earlier.

public static boolean isValidVersion(String version){
  if (version.matches("\\\\d+\\\\.\\\\d+\\\\.\\\\d+")) {
    String[] parts=version.split("\\\\.");
    int major=Integer.parseInt(parts[0]);
    int minor=Integer.parseInt(parts[1]);
    int patch=Integer.parseInt(parts[2]);
    return (major > 3) || (major == 3 && (minor > 0 || (minor == 0 && patch >= 0)));
  }
  return false;
}

				""";

		assertEquals(3, codeLenses.size());
		
		String actualPrompt = codeLenses.get(1).getCommand().getArguments().get(0).toString();

		assertTrue(containsCodeLens(codeLenses.get(0), QueryType.SPEL.getTitle(), 13, 17, 13, 111));
		assertTrue(containsCodeLens(codeLenses.get(1), QueryType.SPEL.getTitle(), 16, 11, 16, 107));
		
		assertEquals(expectedPrompt, actualPrompt);
	}
	
	@Test
	public void testShowCodeLensesTrueForSpelWithMultipleMethods() throws Exception {

		// Simulate the command execution with true parameter
		setCommandParamsHandler(true);

		String docUri = directory.toPath().resolve("src/main/java/org/test/SpelController.java").toUri().toString();
		TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
		TextDocumentInfo openedDoc = harness.openDocument(doc);

		List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);
		
		String expectedPrompt = """
Explain the following SpEL Expression with a clear summary first, followed by a breakdown of the expression with details: \n
T(org.test.SpelController).toUpperCase('hello') + ' ' + T(org.test.SpelController).concat('world', '!')

   Finally, provide a brief summary of what the following method does, focusing on its role within the SpEL expression.
   The summary should mention key criteria the method checks but avoid detailed implementation steps.
   Please include this summary as an appendix to the main explanation, and avoid repeating information covered earlier.

public static String toUpperCase(String input){
  return input.toUpperCase();
}

public static String concat(String str1,String str2){
  return str1 + str2;
}

				""";

		assertEquals(3, codeLenses.size());
		
		String actualPrompt = codeLenses.get(2).getCommand().getArguments().get(0).toString();

		assertTrue(containsCodeLens(codeLenses.get(2), QueryType.SPEL.getTitle(), 19, 11, 19, 114));
		
		assertEquals(expectedPrompt, actualPrompt);
	}
	
	@Test
	public void testShowCodeLensesTrueForAOP() throws Exception {

		setCommandParamsHandler(true);

		String docUri = directory.toPath().resolve("src/main/java/org/test/MyAspect.java").toUri().toString();
		TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
		TextDocumentInfo openedDoc = harness.openDocument(doc);

		List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);

		assertEquals(7, codeLenses.size());

		assertTrue(containsCodeLens(codeLenses.get(0), QueryType.AOP.getTitle(), 9, 1, 9, 53));
		assertTrue(containsCodeLens(codeLenses.get(1), QueryType.AOP.getTitle(), 14, 1, 14, 24));
		assertTrue(containsCodeLens(codeLenses.get(2), QueryType.AOP.getTitle(), 19, 1, 19, 51));
		assertTrue(containsCodeLens(codeLenses.get(3), QueryType.AOP.getTitle(), 27, 1, 27, 50));
		assertTrue(containsCodeLens(codeLenses.get(4), QueryType.AOP.getTitle(), 32, 1, 32, 92));
		assertTrue(containsCodeLens(codeLenses.get(5), QueryType.AOP.getTitle(), 37, 1, 37, 86));
		assertTrue(containsCodeLens(codeLenses.get(6), QueryType.AOP.getTitle(), 42, 1, 42, 65));
	}
	
	@Test
	public void testShowCodeLensesTrueForAopPointcutExamples() throws Exception {

		setCommandParamsHandler(true);

		String docUri = directory.toPath().resolve("src/main/java/org/test/PointcutExamples.java").toUri().toString();
		TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
		TextDocumentInfo openedDoc = harness.openDocument(doc);
		
		String expectedPrompt = """
Explain the following AOP annotation with a clear summary first, followed by a detailed contextual explanation of annotation and its purpose: \n
@Pointcut("cflow(execution(* com.example..*.*(..)))")

								""";
		
		String expectedPromptWithContext = """
Explain the following AOP annotation with a clear summary first, followed by a detailed contextual explanation of annotation and its purpose: \n
@AfterReturning(pointcut="targetService()",returning="result")

   This is the pointcut definition referenced in the above annotation. \n
 @Pointcut("target(com.example.service.MyService)") public void targetService(){
}
 \n
Provide a brief summary of the pointcut's role within the annotation.
   Avoid detailed implementation steps and avoid repeating information covered earlier.
												""";
		String expectedPromptWithMultiPointcutRef = """
Explain the following AOP annotation with a clear summary first, followed by a detailed contextual explanation of annotation and its purpose: \n
@Pointcut("serviceLayer() || repositoryLayer()")

   This is the pointcut definition referenced in the above annotation. \n
 @Pointcut("within(com.example.repository..*)") public void repositoryLayer(){
}
@Pointcut("execution(* com.example.service.*.*(..))") public void serviceLayer(){
}
 \n
Provide a brief summary of the pointcut's role within the annotation.
   Avoid detailed implementation steps and avoid repeating information covered earlier.
												""";

		List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);

		assertEquals(8, codeLenses.size());

		assertTrue(containsCodeLens(codeLenses.get(0), QueryType.AOP.getTitle(), 4, 1, 4, 54));
		assertTrue(containsCodeLens(codeLenses.get(3), QueryType.AOP.getTitle(), 15, 1, 15, 64));
								
		String actualPrompt = codeLenses.get(0).getCommand().getArguments().get(0).toString();
		String actualPromptWithContext = codeLenses.get(3).getCommand().getArguments().get(0).toString();
		String actualPromptWithMultiPointcutRef = codeLenses.get(7).getCommand().getArguments().get(0).toString();

		assertEquals(expectedPrompt, actualPrompt);
		assertEquals(expectedPromptWithContext, actualPromptWithContext);
		assertEquals(expectedPromptWithMultiPointcutRef, actualPromptWithMultiPointcutRef);

	}
	
	@Test
	public void testShowCodeLensesFalseForQuery() throws Exception {
		
		setCommandParamsHandler(false);
		
		String docUri = directory.toPath().resolve("src/main/java/org/test/OwnerRepository.java").toUri().toString();
		TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
		TextDocumentInfo openedDoc = harness.openDocument(doc);
		
		List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);

		assertEquals(0, codeLenses.size());
	}
	
	@Test
	public void testShowCodeLensesFalseForSpel() throws Exception {
		
		setCommandParamsHandler(false);
		
		String docUri = directory.toPath().resolve("src/main/java/org/test/SpelController.java").toUri().toString();
		TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
		TextDocumentInfo openedDoc = harness.openDocument(doc);
		
		List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);

		assertEquals(0, codeLenses.size());
	}
	
	@Test
	public void testShowCodeLensesFalseForAOP() throws Exception {
		
		setCommandParamsHandler(false);
		
		String docUri = directory.toPath().resolve("src/main/java/org/test/MyAspect.java").toUri().toString();
		TextDocumentInfo doc = harness.getOrReadFile(new File(new URI(docUri)), LanguageId.JAVA.getId());
		TextDocumentInfo openedDoc = harness.openDocument(doc);
		
		List<? extends CodeLens> codeLenses = harness.getCodeLenses(openedDoc);

		assertEquals(0, codeLenses.size());
	}
	
	private void setCommandParamsHandler(boolean value) throws InterruptedException, ExecutionException {
		ExecuteCommandHandler handler = commandHandlerCaptor.getValue();
		ExecuteCommandParams params = new ExecuteCommandParams();
		params.setArguments(Collections.singletonList(new JsonPrimitive(value)));
		handler.handle(params).get();
	}

	private boolean containsCodeLens(CodeLens codeLenses, String commandTitle, int startLine, int startPosition,
			int endLine, int endPosition) {
		Command command = codeLenses.getCommand();
		Range range = codeLenses.getRange();
		if (command.getTitle().equals(commandTitle) && range.getStart().getLine() == startLine
				&& range.getStart().getCharacter() == startPosition && range.getEnd().getLine() == endLine
				&& range.getEnd().getCharacter() == endPosition) {
			return true;
		}
		return false;
	}
}