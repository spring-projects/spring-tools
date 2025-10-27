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
import org.junit.jupiter.api.Disabled;
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
public class QualifierCompletionProviderTest {

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
        AnnotationMetadata annotationBean1 = new AnnotationMetadata("org.springframework.beans.factory.annotation.Qualifier", false, null, Map.of("value", new AnnotationAttributeValue[] {new AnnotationAttributeValue("quali1", null)}));
        AnnotationMetadata annotationBean2 = new AnnotationMetadata("org.springframework.beans.factory.annotation.Qualifier", false, null, Map.of("value", new AnnotationAttributeValue[] {new AnnotationAttributeValue("quali2", null)}));
        
		bean1 = new Bean("bean1", "type1", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, new AnnotationMetadata[] {annotationBean1}, false, "symbolLabel");
		bean2 = new Bean("bean2", "type2", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, new AnnotationMetadata[] {annotationBean2}, false, "symbolLabel");
		
		springIndex.updateBeans(project.getElementName(), new Bean[] {bean1, bean2});
	}
	
	@AfterEach
	public void restoreIndexState() {
		this.springIndex.updateBeans(project.getElementName(), indexedBeans);
	}
	
	@Test
	public void testQualifierCompletionWithoutQuotesWithoutPrefixAtInjecitonPoint() throws Exception {
		assertCompletions("@Qualifier(<*>)", new String[] {"quali1", "quali2", "bean1", "bean2"}, 0, "@Qualifier(\"quali1\"<*>)", PositionInCode.ON_INJECTION_POINT);
	}

	@Test
	public void testQualifierCompletionWithoutQuotesWithoutPrefixAtTypeDeclaration() throws Exception {
		assertCompletions("@Qualifier(<*>)", new String[] {"quali1", "quali2"}, 0, "@Qualifier(\"quali1\"<*>)", PositionInCode.ON_TYPE);
	}

	@Test
	public void testQualifierCompletionWithoutQuotesWithoutPrefixAtBeanMethod() throws Exception {
		assertCompletions("@Qualifier(<*>)", new String[] {"quali1", "quali2"}, 0, "@Qualifier(\"quali1\"<*>)", PositionInCode.ON_BEAN_METHOD);
	}

	@Test
	public void testQualifierCompletionWithoutQuotesWithPrefixAtInjecitonPoint() throws Exception {
		assertCompletions("@Qualifier(be<*>)", 2, "@Qualifier(\"bean1\"<*>)", PositionInCode.ON_INJECTION_POINT);
	}

	@Test
	public void testQualifierCompletionWithoutQuotesWithPrefixAtTypeDeclaration() throws Exception {
		assertCompletions("@Qualifier(be<*>)", 0, null, PositionInCode.ON_TYPE);
	}

	@Test
	public void testQualifierCompletionWithoutQuotesWithPrefixAtBeanMethod() throws Exception {
		assertCompletions("@Qualifier(be<*>)", 0, null, PositionInCode.ON_BEAN_METHOD);
	}

	@Test
	public void testQualifierCompletionWithoutQuotesWithPrefixFromExistingQualifierAtInjecitonPoint() throws Exception {
		assertCompletions("@Qualifier(qu<*>)", new String[] {"quali1", "quali2"}, 0, "@Qualifier(\"quali1\"<*>)", PositionInCode.ON_INJECTION_POINT);
	}

	@Test
	@Disabled // impossible to implement, AST does not contain any nodes at all for the parameter in this case 
	public void testQualifierCompletionWithoutQuotesWithAttributeNameAtInjecitonPoint() throws Exception {
		assertCompletions("@Qualifier(value=<*>)", 4, "@Qualifier(value=\"quali1\"<*>)", PositionInCode.ON_INJECTION_POINT);
	}

	@Test
	public void testQualifierCompletionWithoutQuotesWithAttributeNameAtTypeDeclaration() throws Exception {
		assertCompletions("@Qualifier(value=<*>)", 2, "@Qualifier(value=\"quali1\"<*>)", PositionInCode.ON_TYPE);
	}

	@Test
	public void testQualifierCompletionWithoutQuotesWithAttributeNameAtBeanMethod() throws Exception {
		assertCompletions("@Qualifier(value=<*>)", 2, "@Qualifier(value=\"quali1\"<*>)", PositionInCode.ON_BEAN_METHOD);
	}

	@Test
	@Disabled // impossible to implement, AST does not contain any nodes at all for the parameter in this case 
	public void testQualifierCompletionWithoutQuotesWithAttributeNameAndSpacesAtInjecitonPoint() throws Exception {
		assertCompletions("@Qualifier(value = <*>)", 4, "@Qualifier(value = \"quali1\"<*>)", PositionInCode.ON_INJECTION_POINT);
	}

	@Test
	public void testQualifierCompletionWithoutQuotesWithAttributeNameAndSpacesAtTypeDeclaration() throws Exception {
		assertCompletions("@Qualifier(value = <*>)", 2, "@Qualifier(value = \"quali1\"<*>)", PositionInCode.ON_TYPE);
	}

	@Test
	public void testQualifierCompletionInsideOfQuotesWithoutPrefixAtInjecitonPoint() throws Exception {
		assertCompletions("@Qualifier(\"<*>\")", 4, "@Qualifier(\"quali1<*>\")", PositionInCode.ON_INJECTION_POINT);
	}

	@Test
	public void testQualifierCompletionInsideOfQuotesWithPrefixAtInjecitonPoint() throws Exception {
		assertCompletions("@Qualifier(\"be<*>\")", 2, "@Qualifier(\"bean1<*>\")", PositionInCode.ON_INJECTION_POINT);
	}

	@Test
	public void testQualifierCompletionInsideOfQuotesWithPrefixButWithoutMatchesAtInjecitonPoint() throws Exception {
		assertCompletions("@Qualifier(\"XXX<*>\")", 0, null, PositionInCode.ON_INJECTION_POINT);
	}

	@Test
	public void testQualifierCompletionOutsideOfAnnotation1AtInjecitonPoint() throws Exception {
		assertCompletions("@Qualifier(\"XXX\")<*>", 0, null, PositionInCode.ON_INJECTION_POINT);
	}

	@Test
	public void testQualifierCompletionOutsideOfAnnotation2AtInjecitonPoint() throws Exception {
		assertCompletions("@Qualifier<*>(\"XXX\")", 0, null, PositionInCode.ON_INJECTION_POINT);
	}

	@Test
	public void testQualifierCompletionInsideOfQuotesWithPrefixAndReplacedPostfixAtInjecitonPoint() throws Exception {
		assertCompletions("@Qualifier(\"be<*>xxx\")", 2, "@Qualifier(\"bean1<*>\")", PositionInCode.ON_INJECTION_POINT);
	}
	
	private void assertCompletions(String completionLine, int noOfExpectedCompletions, String expectedCompletedLine, PositionInCode position) throws Exception {
		assertCompletions(completionLine, noOfExpectedCompletions, null, 0, expectedCompletedLine, position);
	}

	private void assertCompletions(String completionLine, String[] expectedCompletions, int chosenCompletion, String expectedCompletedLine, PositionInCode position) throws Exception {
		assertCompletions(completionLine, expectedCompletions.length, expectedCompletions, chosenCompletion, expectedCompletedLine, position);
	}

	private void assertCompletions(String completionLine, int noOfExcpectedCompletions, String[] expectedCompletions, int chosenCompletion, String expectedCompletedLine, PositionInCode position) throws Exception {
		String editorContent = createEditorContent(completionLine, position);
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
	        assertEquals(createEditorContent(expectedCompletedLine, position), editor.getText());
        }
	}
	
	private String createEditorContent(String placeholderValue, PositionInCode position) {
		switch (position) {
		case ON_TYPE:
			return """
					package org.test;

	        		import org.springframework.stereotype.Component;
					import org.springframework.beans.factory.annotation.Qualifier;

					@Component
					""" +
					placeholderValue + "\n" +
					"""
					public class TestDependsOnClass {
					}
					""";
		case ON_INJECTION_POINT:
			return """
					package org.test;

	        		import org.springframework.stereotype.Component;
					import org.springframework.beans.factory.annotation.Qualifier;

					@Component
					public class TestDependsOnClass {
					
						public TestDependsOnClass(""" + placeholderValue + " Object someBean) {" +
					"""
						}
					
					}
					""";
		case ON_BEAN_METHOD:
			return """
					package org.test;

	        		import org.springframework.context.annotation.Bean;
	        		import org.springframework.context.annotation.Configuration;
					import org.springframework.beans.factory.annotation.Qualifier;

					@Configuration
					public class TestDependsOnClass {

						@Bean
						""" +
						placeholderValue + "\n" +
						"""
						public Object myBean() {
							return new Object();
						}
					
					}
					""";
		default: return null;
		}
	}
	
	private static enum PositionInCode {
		ON_TYPE, ON_BEAN_METHOD, ON_INJECTION_POINT
	}


}
