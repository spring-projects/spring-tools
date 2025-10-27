/*******************************************************************************
 * Copyright (c) 2024 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.cron;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.CompletionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Udayani V
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class CronExpressionCompletionProviderTest {
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;
	private String tempJavaDocUri;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-annotations/").toURI());

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
		
        tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();
		
	}
	
	@Test
	public void testCronExpressionCompletionWithoutPrefix() throws Exception {
		assertCompletions("@Scheduled(<*>)", new String[] {}, 0, null);
	}
	
	@Test
	public void testCronExpressionCompletionInsideOfQuotesWithoutPrefix() throws Exception {
		assertCompletions("@Scheduled(\"<*>\")", new String[] {}, 0, null);
	}
	
	@Test
	public void testCronExpressionCompletionWithoutQuotesWithPrefix() throws Exception {
		assertCompletions("@Scheduled(0<*>)", new String[] {}, 0, null);
	}
	
	@Test
	public void testCronExpressionCompletionInsideOfQuotesWithPrefix() throws Exception {
		assertCompletions("@Scheduled(\"10<*>\")", new String[] {}, 0, null);
	}

	@Test
	public void testCronExpressionCompletionWithoutQuotesWithAttributeName() throws Exception {
		assertCompletions("@Scheduled(cron=<*>)", 24, "@Scheduled(cron=\"0 0 * * * *\"<*>)");
	}
	
	@Test
	public void testCronExpressionCompletionWithAttributeNameAndPrefix() throws Exception {
		assertCompletions("@Scheduled(cron=\"0<*>\")", 24, "@Scheduled(cron=\"0 0 * * * *<*>\")");
	}
	
	@Test
	public void testCronExpressionCompletionWithFilteredMatches() throws Exception {
		assertCompletions("@Scheduled(cron=\"MON<*>\")", 3, "@Scheduled(cron=\"0 0 9 * * MON<*>\")");
	}
	
	@Test
	public void testCronExpressionCompletionPrefixWithFilteredMatches() throws Exception {
		assertCompletions("@Scheduled(cron=\"W<*>\")", 1, "@Scheduled(cron=\"0 0 0 1W * *<*>\")");
	}

	@Test
	public void testCronExpressionCompletionWithNoMatches() throws Exception {
		assertCompletions("@Scheduled(cron=\"WED<*>\")", 0, null);
	}

	@Test
	public void testCronExpressionCompletionWithMultipleAttributes() throws Exception {
		assertCompletions("@Scheduled(cron=\"JAN<*>\", fixedDelay = 1000)", 1, "@Scheduled(cron=\"0 30 9 * JAN MON<*>\", fixedDelay = 1000)");
	}
	
	private void assertCompletions(String completionLine, int noOfExpectedCompletions, String expectedCompletedLine) throws Exception {
		assertCompletions(completionLine, noOfExpectedCompletions, null, 0, expectedCompletedLine);
	}

	private void assertCompletions(String completionLine, String[] expectedCompletions, int chosenCompletion, String expectedCompletedLine) throws Exception {
		assertCompletions(completionLine, expectedCompletions.length, expectedCompletions, chosenCompletion, expectedCompletedLine);
	}

	private void assertCompletions(String completionLine, int noOfExcpectedCompletions, String[] expectedCompletions, int chosenCompletion, String expectedCompletedLine) throws Exception {
		String editorContent = """
				package org.test;

				import org.springframework.scheduling.annotation.Scheduled;

				public class CronScheduler {

				""" +
				completionLine + "\n" +
				"""
				public void cronCompletionTest() {
				}
				""";
		
		Editor editor = harness.newEditor(LanguageId.JAVA, editorContent, tempJavaDocUri);

        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(noOfExcpectedCompletions, completions.size());

        if (expectedCompletions != null) {
	        String[] completionItems = completions.stream()
	        	.map(item -> item.getLabel())
	        	.toArray(size -> new String[size]);
	        
	        assertArrayEquals(expectedCompletions, completionItems);
        }
        
        if (noOfExcpectedCompletions > 0) {
	        editor.apply(completions.get(chosenCompletion));
	        assertEquals("""
				package org.test;

				import org.springframework.scheduling.annotation.Scheduled;

				public class CronScheduler {

				""" + expectedCompletedLine + "\n" +
				"""
				public void cronCompletionTest() {
				}
	        		""", editor.getText());
        }
	}
}
