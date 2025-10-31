/*******************************************************************************
 * Copyright (c) 2024, 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.beans.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationAttributeValue;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class ProfileCompletionProviderTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;
	private IJavaProject project;
	private Bean[] indexedBeans;
	private String tempJavaDocUri;
	private Bean bean1;
	private Bean bean2;
	private String[] allProposals;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-indexing/").toURI());

		String projectDir = directory.toURI().toString();
		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
		
		indexedBeans = springIndex.getBeansOfProject(project.getElementName());
		
        tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();
        AnnotationMetadata annotationBean1 = new AnnotationMetadata("org.springframework.context.annotation.Profile", false, null, Map.of("value", new AnnotationAttributeValue[] {new AnnotationAttributeValue("prof1", null), new AnnotationAttributeValue("prof2", null)}));
        AnnotationMetadata annotationBean2 = new AnnotationMetadata("org.springframework.context.annotation.Profile", false, null, Map.of("value", new AnnotationAttributeValue[] {new AnnotationAttributeValue("prof3", null)}));
        
		bean1 = new Bean("bean1", "type1", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, new AnnotationMetadata[] {annotationBean1}, false, "symbolLabel");
		bean2 = new Bean("bean2", "type2", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, new AnnotationMetadata[] {annotationBean2}, false, "symbolLabel");
		
		springIndex.updateBeans(project.getElementName(), new Bean[] {bean1, bean2});
		
		allProposals = new String[] {"prof1", "prof2", "prof3"};
	}
	
	@AfterEach
	public void restoreIndexState() {
		this.springIndex.updateBeans(project.getElementName(), indexedBeans);
	}
	
	@Test
	public void testProfileCompletionWithoutQuotesWithoutPrefix() throws Exception {
		assertCompletions("@Profile(<*>)", allProposals, 0, "@Profile(\"prof1\"<*>)");
	}

	@Test
	public void testProfileCompletionWithoutQuotesWithPrefix() throws Exception {
		assertCompletions("@Profile(prof1<*>)", new String[] {"prof1"}, 0, "@Profile(\"prof1\"<*>)");
	}

	@Test
	public void testProfileCompletionWithoutQuotesWithAttributeName() throws Exception {
		assertCompletions("@Profile(value=<*>)", allProposals, 0, "@Profile(value=\"prof1\"<*>)");
	}

	@Test
	public void testProfileCompletionInsideOfQuotesWithoutPrefix() throws Exception {
		assertCompletions("@Profile(\"<*>\")", allProposals, 0, "@Profile(\"prof1<*>\")");
	}

	@Test
	public void testProfileCompletionInsideOfQuotesWithPrefix() throws Exception {
		assertCompletions("@Profile(\"prof1<*>\")", new String[] {"prof1"}, 0, "@Profile(\"prof1<*>\")");
	}
	
	@Test
	public void testProfileCompletionInsideOfQuotesAndArrayWithPrefix() throws Exception {
		assertCompletions("@Profile({\"pr<*>\"})", allProposals, 0, "@Profile({\"prof1<*>\"})");
	}

	@Test
	public void testProfileCompletionInsideOfQuotesWithoutPrefixInsideArray() throws Exception {
		assertCompletions("@Profile({\"<*>\"})", allProposals, 0, "@Profile({\"prof1<*>\"})");
	}

	@Test
	public void testProfileCompletionWithoutQuotesWithoutPrefixInsideArray() throws Exception {
		assertCompletions("@Profile({<*>})", allProposals, 0, "@Profile({\"prof1\"<*>})");
	}

	@Test
	public void testProfileCompletionInsideOfArrayBehindExistingElement() throws Exception {
		assertCompletions("@Profile({\"prof1\",<*>})", new String[] {"prof2", "prof3"}, 0, "@Profile({\"prof1\",\"prof2\"<*>})");
	}

	@Test
	public void testProfileCompletionInsideOfArrayInFrontOfExistingElement() throws Exception {
		assertCompletions("@Profile({<*>\"prof1\"})", new String[] {"prof2", "prof3"}, 0, "@Profile({\"prof2\",<*>\"prof1\"})");
	}
	
	@Test
	public void testProfileCompletionInsideOfArrayBetweenExistingElements() throws Exception {
		assertCompletions("@Profile({\"prof1\",<*>\"prof2\"})", new String[] {"prof3"}, 0, "@Profile({\"prof1\",\"prof3\",<*>\"prof2\"})");
	}

	@Test
	public void testProfileCompletionInsideOfQuotesWithPrefixButWithoutMatches() throws Exception {
		assertCompletions("@Profile(\"XXX<*>\")", 0, null);
	}

	@Test
	public void testProfileCompletionOutsideOfAnnotation1() throws Exception {
		assertCompletions("@Profile(\"XXX\")<*>", 0, null);
	}

	@Test
	public void testProfileCompletionOutsideOfAnnotation2() throws Exception {
		assertCompletions("@Profile<*>(\"XXX\")", 0, null);
	}

	@Test
	public void testProfileCompletionInsideOfQuotesWithPrefixAndReplacedPostfix() throws Exception {
		assertCompletions("@Profile(\"pro<*>xxx\")", allProposals, 0, "@Profile(\"prof1<*>\")");
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

        		import org.springframework.stereotype.Component;
				import org.springframework.context.annotation.Profile;

				@Component
				""" +
				completionLine + "\n" +
				"""
				public class TestDependsOnClass {
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
	
	        		import org.springframework.stereotype.Component;
					import org.springframework.context.annotation.Profile;
	
					@Component
					""" + expectedCompletedLine + "\n" +
					"""
					public class TestDependsOnClass {
					}
	        		""", editor.getText());
        }
	}


}
