/*******************************************************************************
 * Copyright (c) 2024, 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.conditionals.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
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
@Import(IndexerTestConf.class)
public class ConditionalOnResourceCompletionTest {

    @Autowired private BootLanguageServerHarness harness;
    @Autowired private JavaProjectFinder projectFinder;
    @Autowired private SpringSymbolIndex indexer;

    private Editor editor;
    private String testSourceUri;

    @BeforeEach
    public void setup() throws Exception {
        harness.intialize(null);

        File directory = new File(ProjectsHarness.class.getResource("/test-projects/test-annotation-conditionalonresource/").toURI());

        String projectDir = directory.toURI().toString();
        projectFinder.find(new TextDocumentIdentifier(projectDir)).get();
        
        testSourceUri = directory.toPath().resolve("src/main/java/org/test/TestConditionalOnResourceCompletion.java").toUri().toString();

        CompletableFuture<Void> initProject = indexer.waitOperation();
        initProject.get(5, TimeUnit.SECONDS);
    }

    @Test
    void testPrefixCompletionWithParamName() throws Exception {
        prepareCase("@ConditionalOnResource(resources=\"onClass\")", "@ConditionalOnResource(resources=foo<*>)");

        assertClasspathCompletions(
                "@ConditionalOnResource(resources=\"classpath:org/test/foo\"<*>)");
    }

    @Test
    void testEmptyBracketsCompletionWithParamName() throws Exception {
        prepareCase("@ConditionalOnResource(resources=\"onClass\")", "@ConditionalOnResource(resources=<*>)");

        assertClasspathCompletions(
                "@ConditionalOnResource(resources=\"classpath:a-random-resource-root.md\"<*>)",
                "@ConditionalOnResource(resources=\"classpath:org/random-resource-org.md\"<*>)",
                "@ConditionalOnResource(resources=\"classpath:org/test/foo\"<*>)",
                "@ConditionalOnResource(resources=\"classpath:org/test/random-resource-org-test.txt\"<*>)");
    }

    @Test
    void testEmptyBracketsCompletionWithWrongParamName() throws Exception {
        prepareCase("@ConditionalOnResource(resources=\"onClass\")", "@ConditionalOnResource(wrong=foo<*>)");
        assertClasspathCompletions();
    }

    @Test
    void testEmptyBracketsCompletionWithoutMandatoryResourcesParam() throws Exception {
        prepareCase("@ConditionalOnResource(resources=\"onClass\")", "@ConditionalOnResource(<*>)");
        assertClasspathCompletions();
    }

    @Test
    void testEmptyStringLiteralCompletionWithParamName() throws Exception {
        prepareCase("@ConditionalOnResource(resources=\"onClass\")", "@ConditionalOnResource(resources=\"<*>\")");

        assertClasspathCompletions(
                "@ConditionalOnResource(resources=\"classpath:a-random-resource-root.md<*>\")",
                "@ConditionalOnResource(resources=\"classpath:org/random-resource-org.md<*>\")",
                "@ConditionalOnResource(resources=\"classpath:org/test/foo<*>\")",
                "@ConditionalOnResource(resources=\"classpath:org/test/random-resource-org-test.txt<*>\")");
    }

    @Test
    void testEmptyStringLiteralCompletionWithoutMandatoryResourcesParam() throws Exception {
        prepareCase("@ConditionalOnResource(resources=\"onClass\")", "@ConditionalOnResource(\"<*>\")");

        assertClasspathCompletions();
    }

    @Test
    void testResourceNameInPrefixWithParamNameCompletion() throws Exception {
        prepareCase("@ConditionalOnResource(resources=\"onClass\")", "@ConditionalOnResource(resources=root<*>)");

        assertClasspathCompletions(
                "@ConditionalOnResource(resources=\"classpath:a-random-resource-root.md\"<*>)");
    }

    @Test
    void testQualifiedResourceNameInPrefixWithParamNameCompletion() throws Exception {
        prepareCase("@ConditionalOnResource(resources=\"onClass\")", "@ConditionalOnResource(resources=root.md<*>)");

        assertClasspathCompletions(
                "@ConditionalOnResource(resources=\"classpath:a-random-resource-root.md\"<*>)");
    }

    @Test
    void testComplexResourceNameInPrefixWithWrongExtensionAndParamNameCompletion() throws Exception {
        prepareCase("@ConditionalOnResource(resources=\"onClass\")", "@ConditionalOnResource(resources=root.xml<*>)");

        assertClasspathCompletions();
    }

    @Test
    void testComplexResourceNameInPrefixWithinQuotesAndParamNameCompletion() throws Exception {
        prepareCase("@ConditionalOnResource(resources=\"onClass\")", "@ConditionalOnResource(resources=\"root.md<*>\")");

        assertClasspathCompletions(
                "@ConditionalOnResource(resources=\"classpath:a-random-resource-root.md<*>\")");
    }

    @Test
    void testComplexResourceNameWithSlashPrefixAndWithResourcesAndParamNameCompletion() throws Exception {
        prepareCase("@ConditionalOnResource(resources=\"onClass\")", "@ConditionalOnResource(resources=\"/root.md<*>\")");

        assertClasspathCompletions();
    }

    private void prepareCase(String selectedAnnotation, String annotationStatementBeforeTest) throws Exception {
        InputStream resource = this.getClass().getResourceAsStream("/test-projects/test-annotation-conditionalonresource/src/main/java/org/test/TestConditionalOnResourceCompletion.java");
        String content = IOUtils.toString(resource, Charset.defaultCharset());

        content = content.replace(selectedAnnotation, annotationStatementBeforeTest);
        editor = harness.newEditor(LanguageId.JAVA, content, testSourceUri);
    }

    private void assertClasspathCompletions(String... completedAnnotations) throws Exception {
        List<CompletionItem> completions = editor.getCompletions();

        List<CompletionItem> filteredCompletions = completions.stream()
                .filter(item -> item.getTextEdit().getLeft().getNewText().contains("classpath"))
                .sorted(new Comparator<CompletionItem>() {
                    @Override
                    public int compare(CompletionItem o1, CompletionItem o2) {
                        return o1.getLabel().compareTo(o2.getLabel());
                    }
                })
                .toList();

        assertEquals(completedAnnotations.length, filteredCompletions.size());

        for (int i = 0; i < completedAnnotations.length; i++) {
            CompletionItem completion = filteredCompletions.get(i);

            Editor clonedEditor = editor.clone();
            clonedEditor.apply(completion);

            String expected = completedAnnotations[i];
            if (!clonedEditor.getText().contains(expected)) {
                fail("Not found '" + expected +"' in \n" + clonedEditor.getText());
            }
        }
    }
}