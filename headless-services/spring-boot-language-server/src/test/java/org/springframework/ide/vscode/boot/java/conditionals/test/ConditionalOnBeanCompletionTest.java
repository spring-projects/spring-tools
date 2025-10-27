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
package org.springframework.ide.vscode.boot.java.conditionals.test;

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
 * @author Karthik Sankaranarayanan
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class ConditionalOnBeanCompletionTest {

    @Autowired private BootLanguageServerHarness harness;
    @Autowired private JavaProjectFinder projectFinder;
    @Autowired private SpringMetamodelIndex springIndex;
    @Autowired private SpringSymbolIndex indexer;

    private File directory;
    private IJavaProject project;
    private String tempJavaDocUri;
    private Bean bean1;
    private Bean bean2;

    @BeforeEach
    public void setup() throws Exception {
        harness.intialize(null);

        directory = new File(ProjectsHarness.class.getResource("/test-projects/test-annotations/").toURI());

        String projectDir = directory.toURI().toString();
        project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

        CompletableFuture<Void> initProject = indexer.waitOperation();
        initProject.get(5, TimeUnit.SECONDS);

        tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();
        bean1 = new Bean("bean1", "org.example.type1", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
        bean2 = new Bean("bean2", "org.example.type2", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");

        springIndex.updateBeans(project.getElementName(), new Bean[] {bean1, bean2});
    }

    @Test
    public void testConditionalOnBeanCompletionWithoutQuotesWithoutPrefixWithNameAttribute() throws Exception {
        assertCompletions("@ConditionalOnBean(name=<*>)", 2, "@ConditionalOnBean(name=\"bean1\"<*>)");
    }

    @Test
    public void testConditionalOnBeanCompletionWithoutQuotesWithoutPrefixWithTypeAttribute() throws Exception {
        assertCompletions("@ConditionalOnBean(type=<*>)", 2, "@ConditionalOnBean(type=\"org.example.type1\"<*>)");
    }

    @Test
    public void testConditionalOnBeanCompletionWithoutQuotesWithPrefixWithNameAttribute() throws Exception {
        assertCompletions("@ConditionalOnBean(name=be<*>)", 2, "@ConditionalOnBean(name=\"bean1\"<*>)");
    }

    @Test
    public void testConditionalOnBeanCompletionWithoutQuotesWithPrefixWithTypeAttribute() throws Exception {
        assertCompletions("@ConditionalOnBean(type=ty<*>)", 2, "@ConditionalOnBean(type=\"org.example.type1\"<*>)");
    }

    @Test
    public void testConditionalOnBeanCompletionWithoutQuotesWithComplexPrefixForTypeAttribute() throws Exception {
        assertCompletions("@ConditionalOnBean(type=org.exam<*>)", 2, "@ConditionalOnBean(type=\"org.example.type1\"<*>)");
    }

    @Test
    public void testConditionalOnBeanCompletionWithoutQuotesWithPrefixWithoutMatches() throws Exception {
        assertCompletions("@ConditionalOnBean(\"XXX<*>\")", 0, null);
    }

    @Test
    public void testConditionalOnBeanCompletionWithoutQuotesWithPrefixWithoutAttribute() throws Exception {
        assertCompletions("@ConditionalOnBean(be<*>)", 0, null);
    }

    @Test
    public void testConditionalOnBeanCompletionWithoutQuotesWithAttributeNameAndDefaultSpaces() throws Exception {
        assertCompletions("@ConditionalOnBean(name = <*>)", 2, "@ConditionalOnBean(name = \"bean1\"<*>)");
    }

    @Test
    public void testConditionalOnBeanCompletionWithoutQuotesWithAttributeTypeAndDefaultSpaces() throws Exception {
        assertCompletions("@ConditionalOnBean(type = <*>)", 2, "@ConditionalOnBean(type = \"org.example.type1\"<*>)");
    }

    @Test
    public void testConditionalOnBeanCompletionWithoutQuotesWithAttributeNameAndManySpaces() throws Exception {
        assertCompletions("@ConditionalOnBean(name =    <*>  )", 2, "@ConditionalOnBean(name =    \"bean1\"<*>  )");
    }

    @Test
    public void testConditionalOnBeanCompletionInsideOfQuotesWithoutPrefix() throws Exception {
        assertCompletions("@ConditionalOnBean(\"<*>\")", 0, null);
    }

    @Test
    public void testConditionalOnBeanCompletionWithoutQuotesWithoutPrefixInsideArray() throws Exception {
        assertCompletions("@ConditionalOnBean(name={<*>})", 2, "@ConditionalOnBean(name={\"bean1\"<*>})");
    }

    @Test
    public void testConditionalOnBeanCompletionWithoutQuotesWithTypeWithoutPrefixInsideArray() throws Exception {
        assertCompletions("@ConditionalOnBean(type={<*>})", 2, "@ConditionalOnBean(type={\"org.example.type1\"<*>})");
    }

    @Test
    public void testConditionalOnBeanCompletionInsideOfArrayBehindExistingElementWithName() throws Exception {
        assertCompletions("@ConditionalOnBean(name={\"bean1\",<*>})", 1, "@ConditionalOnBean(name={\"bean1\",\"bean2\"<*>})");
    }

    @Test
    public void testConditionalOnBeanCompletionInsideOfArrayBehindExistingElementWithType() throws Exception {
        assertCompletions("@ConditionalOnBean(type={\"org.example.type1\",<*>})", 1, "@ConditionalOnBean(type={\"org.example.type1\",\"org.example.type2\"<*>})");
    }

    @Test
    public void testConditionalOnBeanCompletionInsideOfArrayInFrontOfExistingElementWithName() throws Exception {
        assertCompletions("@ConditionalOnBean(name={<*>\"bean1\"})", 1, "@ConditionalOnBean(name={\"bean2\",<*>\"bean1\"})");
    }

    @Test
    public void testConditionalOnBeanCompletionInsideOfArrayInFrontOfExistingElementWithType() throws Exception {
        assertCompletions("@ConditionalOnBean(type={<*>\"org.example.type1\"})", 1, "@ConditionalOnBean(type={\"org.example.type2\",<*>\"org.example.type1\"})");
    }

    @Test
    public void testConditionalOnBeanCompletionInsideOfArrayBetweenExistingElements() throws Exception {
        Bean bean3 = new Bean("bean3", "org.example.type3", new Location(tempJavaDocUri, new Range(new Position(1,1), new Position(1, 20))), null, null, null, false, "symbolLabel");
        springIndex.updateBeans(project.getElementName(), new Bean[] {bean1, bean2, bean3});

        assertCompletions("@ConditionalOnBean(name={\"bean1\",<*>\"bean2\"})", 1, "@ConditionalOnBean(name={\"bean1\",\"bean3\",<*>\"bean2\"})");
    }
    
    @Test
    public void testConditionalOnBeanTypeCompletions() throws Exception {
        List<CompletionItem> completions = getCompletions("@ConditionalOnBean(type=<*>)");
        
        assertEquals(2, completions.size());
        
        CompletionItem completionItem = completions.get(0);
        
        completionItem = harness.resolveCompletionItem(completionItem);
        assertEquals("type1", completionItem.getLabel());
        assertEquals("org.example.type1", completionItem.getDetail());
        assertEquals("org.example.type1", completionItem.getFilterText());
    }

    private void assertCompletions(String completionLine, int noOfExpectedCompletions, String expectedCompletedLine) throws Exception {
        String editorContent = """
				package org.test;

        		import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                @Configuration
                public class TestConditionalOnBeanCompletion {
				""" +
                completionLine + "\n" +
                """
                @Bean
                public void method() {
                }
                """;

        Editor editor = harness.newEditor(LanguageId.JAVA, editorContent, tempJavaDocUri);

        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(noOfExpectedCompletions, completions.size());

        if (noOfExpectedCompletions > 0) {
            editor.apply(completions.get(0));
            assertEquals("""
				package org.test;

        		import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                @Configuration
                public class TestConditionalOnBeanCompletion {
				""" +
                    expectedCompletedLine + "\n" +
                    """
                    @Bean
                    public void method() {
                    }
                    """, editor.getText());

        }
    }

    private List<CompletionItem> getCompletions(String completionLine) throws Exception {
        String editorContent = """
				package org.test;

        		import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

                @Configuration
                public class TestConditionalOnBeanCompletion {
				""" +
                completionLine + "\n" +
                """
                @Bean
                public void method() {
                }
                """;

        Editor editor = harness.newEditor(LanguageId.JAVA, editorContent, tempJavaDocUri);
        return editor.getCompletions();
    }
}