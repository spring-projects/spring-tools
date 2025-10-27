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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;
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
public class DependsOnCompletionProviderTest {

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
		bean1 = new Bean("bean1", "type1", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
		bean2 = new Bean("bean2", "type2", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
		
		springIndex.updateBeans(project.getElementName(), new Bean[] {bean1, bean2});
	}
	
	@AfterEach
	public void restoreIndexState() {
		this.springIndex.updateBeans(project.getElementName(), indexedBeans);
	}
	
	@Test
	public void testDependsOnCompletionWithoutQuotesWithoutPrefix() throws Exception {
		assertCompletions("@DependsOn(<*>)", 2, "@DependsOn(\"bean1\"<*>)");
	}

	@Test
	public void testDependsOnCompletionWithoutQuotesWithoutPrefixOnBeanMethod() throws Exception {
		assertCompletionsOnBeanMethod("@DependsOn(<*>)", 2, "@DependsOn(\"bean1\"<*>)");
	}

	// TODO: not yet working, needs more groundwork due to the parser skipping these non-valid parts of the AST
//	@Test
//	public void testDependsOnCompletionWithoutQuotesWithoutPrefixWithoutClosingBracket() throws Exception {
//		assertCompletions("@DependsOn(<*>", 2, "@DependsOn(\"bean1\")<*>");
//	}

	@Test
	public void testDependsOnCompletionWithoutQuotesWithPrefix() throws Exception {
		assertCompletions("@DependsOn(be<*>)", 2, "@DependsOn(\"bean1\"<*>)");
	}

	@Test
	public void testDependsOnCompletionWithoutQuotesWithNotExactPrefix() throws Exception {
		assertCompletions("@DependsOn(ea<*>)", 2, "@DependsOn(\"bean1\"<*>)");
	}

	@Test
	public void testDependsOnCompletionWithoutQuotesWithAttributeName() throws Exception {
		assertCompletions("@DependsOn(value=<*>)", 2, "@DependsOn(value=\"bean1\"<*>)");
	}

	@Test
	public void testDependsOnCompletionWithoutQuotesWithAttributeNameAndDefaultSpaces() throws Exception {
		assertCompletions("@DependsOn(value = <*>)", 2, "@DependsOn(value = \"bean1\"<*>)");
	}

	@Test
	public void testDependsOnCompletionWithoutQuotesWithAttributeNameAndManySpaces() throws Exception {
		assertCompletions("@DependsOn(value =    <*>  )", 2, "@DependsOn(value =    \"bean1\"<*>  )");
	}

	@Test
	public void testDependsOnCompletionInsideOfQuotesWithoutPrefix() throws Exception {
		assertCompletions("@DependsOn(\"<*>\")", 2, "@DependsOn(\"bean1<*>\")");
	}

	@Test
	public void testDependsOnCompletionWithQuotesWithAttributeName() throws Exception {
		assertCompletions("@DependsOn(value=\"<*>\")", 2, "@DependsOn(value=\"bean1<*>\")");
	}

	// TODO: not yet working, needs more groundwork due to the parser skipping these non-valid parts of the AST
//	@Test
//	public void testDependsOnCompletionOpeningQuoteOnlyWithoutPrefix() throws Exception {
//		assertCompletions("@DependsOn(\"<*>)", 2, "@DependsOn(\"bean1<*>\")");
//	}

	@Test
	public void testDependsOnCompletionWithoutQuotesWithoutPrefixInsideArray() throws Exception {
		assertCompletions("@DependsOn({<*>})", 2, "@DependsOn({\"bean1\"<*>})");
	}

	@Test
	public void testDependsOnCompletionInsideOfQuotesWithoutPrefixInsideArray() throws Exception {
		assertCompletions("@DependsOn({\"<*>\"})", 2, "@DependsOn({\"bean1<*>\"})");
	}

	@Test
	public void testDependsOnCompletionInsideOfQuotesWithPrefix() throws Exception {
		assertCompletions("@DependsOn(\"be<*>\")", 2, "@DependsOn(\"bean1<*>\")");
	}

	@Test
	public void testDependsOnCompletionInsideOfQuotesAndArrayWithPrefix() throws Exception {
		assertCompletions("@DependsOn({\"be<*>\"})", 2, "@DependsOn({\"bean1<*>\"})");
	}

	@Test
	public void testDependsOnCompletionInsideOfQuotesWithPrefixButWithoutMatches() throws Exception {
		assertCompletions("@DependsOn(\"XXX<*>\")", 0, null);
	}

	@Test
	public void testDependsOnCompletionOutsideOfAnnotation1() throws Exception {
		assertCompletions("@DependsOn(\"XXX\")<*>", 0, null);
	}

	@Test
	public void testDependsOnCompletionOutsideOfAnnotation2() throws Exception {
		assertCompletions("@DependsOn<*>(\"XXX\")", 0, null);
	}

	@Test
	public void testDependsOnCompletionInsideOfAttributeName() throws Exception {
		assertCompletions("@DependsOn(<*>value=\"bean1\")", 0, null);
	}
	
	@Test
	public void testDependsOnCompletionInsideOfQuotesWithPrefixAndReplacedPostfix() throws Exception {
		assertCompletions("@DependsOn(\"be<*>xxx\")", 2, "@DependsOn(\"bean1<*>\")");
	}
	
	@Test
	public void testDependsOnCompletionInsideOfArrayBehindExistingElement() throws Exception {
		assertCompletions("@DependsOn({\"bean1\",<*>})", 1, "@DependsOn({\"bean1\",\"bean2\"<*>})");
	}

	@Test
	public void testDependsOnCompletionInsideOfArrayInFrontOfExistingElement() throws Exception {
		assertCompletions("@DependsOn({<*>\"bean1\"})", 1, "@DependsOn({\"bean2\",<*>\"bean1\"})");
	}
	
	@Test
	public void testDependsOnCompletionInsideOfArrayBetweenExistingElements() throws Exception {
		Bean bean3 = new Bean("bean3", "type3", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
		springIndex.updateBeans(project.getElementName(), new Bean[] {bean1, bean2, bean3});
		
		assertCompletions("@DependsOn({\"bean1\",<*>\"bean2\"})", 1, "@DependsOn({\"bean1\",\"bean3\",<*>\"bean2\"})");
	}

	@Test
	public void testDependsOnCompletionWithinQuotesExcludeDefaultBeanNameFromComponent() throws Exception {
		Bean componentBean = new Bean("testDependsOnClass", "org.test.TestDependsOnClass", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
		springIndex.updateBeans(project.getElementName(), new Bean[] {bean1, bean2, componentBean});
		
		assertCompletions("@DependsOn(\"<*>\")", 2, "@DependsOn(\"bean1<*>\")");
	}

	@Test
	public void testDependsOnCompletionExcludeDefaultBeanNameFromComponent() throws Exception {
		Bean componentBean = new Bean("testDependsOnClass", "org.test.TestDependsOnClass", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
		springIndex.updateBeans(project.getElementName(), new Bean[] {bean1, bean2, componentBean});
		
		assertCompletions("@DependsOn(<*>)", 2, "@DependsOn(\"bean1\"<*>)");
	}

	@Test
	public void testDependsOnCompletionExcludeExplicitBeanNameFromComponent() throws Exception {
		Bean componentBeanWithName = new Bean("explicitBeanName", "org.test.TestDependsOnClass", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
		springIndex.updateBeans(project.getElementName(), new Bean[] {bean1, bean2, componentBeanWithName});
		
		assertCompletionsWithComponentBeanName("@DependsOn(<*>)", 2, "@DependsOn(\"bean1\"<*>)");
	}

	@Test
	public void testDependsOnCompletionExcludeDefaultBeanNameFromBeanMethod() throws Exception {
		Bean beanFromMethod = new Bean("beanFromMethod", "org.test.TestDependsOnClass", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
		springIndex.updateBeans(project.getElementName(), new Bean[] {bean1, bean2, beanFromMethod});
		
		assertCompletionsOnBeanMethod("@DependsOn(<*>)", 2, "@DependsOn(\"bean1\"<*>)");
	}

	@Test
	public void testDependsOnCompletionExcludeExplicitBeanNameFromBeanMethod() throws Exception {
		Bean beanFromMethodWithName = new Bean("beanFromMethodWithName", "org.test.TestDependsOnClass", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
		springIndex.updateBeans(project.getElementName(), new Bean[] {bean1, bean2, beanFromMethodWithName});
		
		assertCompletionsOnBeanMethodWithName("@DependsOn(<*>)", 2, "@DependsOn(\"bean1\"<*>)");
	}

	private void assertCompletions(String completionLine, int noOfExpectedCompletions, String expectedCompletedLine) throws Exception {
		String editorContent = """
				package org.test;

        		import org.springframework.stereotype.Component;
				import org.springframework.context.annotation.DependsOn;

				@Component
				""" +
				completionLine + "\n" +
				"""
				public class TestDependsOnClass {
				}
				""";
		
		Editor editor = harness.newEditor(LanguageId.JAVA, editorContent, tempJavaDocUri);

        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(noOfExpectedCompletions, completions.size());
        
        if (noOfExpectedCompletions > 0) {
	        editor.apply(completions.get(0));
	        assertEquals("""
					package org.test;
	
	        		import org.springframework.stereotype.Component;
					import org.springframework.context.annotation.DependsOn;
	
					@Component
					""" + expectedCompletedLine + "\n" +
					"""
					public class TestDependsOnClass {
					}
	        		""", editor.getText());
        }
	}

	private void assertCompletionsWithComponentBeanName(String completionLine, int noOfExpectedCompletions, String expectedCompletedLine) throws Exception {
		String editorContent = """
				package org.test;

        		import org.springframework.stereotype.Component;
				import org.springframework.context.annotation.DependsOn;

				@Component("explicitBeanName")
				""" +
				completionLine + "\n" +
				"""
				public class TestDependsOnClass {
				}
				""";
		
		Editor editor = harness.newEditor(LanguageId.JAVA, editorContent, tempJavaDocUri);

        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(noOfExpectedCompletions, completions.size());
        
        if (noOfExpectedCompletions > 0) {
	        editor.apply(completions.get(0));
	        assertEquals("""
					package org.test;
	
	        		import org.springframework.stereotype.Component;
					import org.springframework.context.annotation.DependsOn;
	
					@Component("explicitBeanName")
					""" + expectedCompletedLine + "\n" +
					"""
					public class TestDependsOnClass {
					}
	        		""", editor.getText());
        }
	}

	private void assertCompletionsOnBeanMethod(String completionLine, int noOfExpectedCompletions, String expectedCompletedLine) throws Exception {
		String editorContent = """
				package org.test;

				import org.springframework.context.annotation.DependsOn;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.context.annotation.Bean;

				@Configuration
				public class TestDependsOnClass {

					@Bean
					""" +
					completionLine + "\n" +
					"""
					public Object beanFromMethod() {
						return new Object();
					}
												
				}
				""";
		
		Editor editor = harness.newEditor(LanguageId.JAVA, editorContent, tempJavaDocUri);

        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(noOfExpectedCompletions, completions.size());
        
        if (noOfExpectedCompletions > 0) {
	        editor.apply(completions.get(0));
	        assertEquals("""
					package org.test;

					import org.springframework.context.annotation.DependsOn;
					import org.springframework.context.annotation.Configuration;
					import org.springframework.context.annotation.Bean;

					@Configuration
					public class TestDependsOnClass {

						@Bean
						""" + 
						expectedCompletedLine + "\n" +
						"""
						public Object beanFromMethod() {
							return new Object();
						}
												
					}
					""", editor.getText());
        }
	}

	private void assertCompletionsOnBeanMethodWithName(String completionLine, int noOfExpectedCompletions, String expectedCompletedLine) throws Exception {
		String editorContent = """
				package org.test;

				import org.springframework.context.annotation.DependsOn;
				import org.springframework.context.annotation.Configuration;
				import org.springframework.context.annotation.Bean;

				@Configuration
				public class TestDependsOnClass {

					@Bean("beanFromMethodWithName")
					""" +
					completionLine + "\n" +
					"""
					public Object beanFromMethod() {
						return new Object();
					}
												
				}
				""";
		
		Editor editor = harness.newEditor(LanguageId.JAVA, editorContent, tempJavaDocUri);

        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(noOfExpectedCompletions, completions.size());
        
        if (noOfExpectedCompletions > 0) {
	        editor.apply(completions.get(0));
	        assertEquals("""
					package org.test;

					import org.springframework.context.annotation.DependsOn;
					import org.springframework.context.annotation.Configuration;
					import org.springframework.context.annotation.Bean;

					@Configuration
					public class TestDependsOnClass {

						@Bean("beanFromMethodWithName")
						""" +
						expectedCompletedLine + "\n" +
						"""
						public Object beanFromMethod() {
							return new Object();
						}
												
					}
					""", editor.getText());
        }
	}


}
