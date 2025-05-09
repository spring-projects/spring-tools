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

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.LocationLink;
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
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class ConditionalOnResourceDefinitionProviderTest {

    @Autowired private BootLanguageServerHarness harness;
    @Autowired private JavaProjectFinder projectFinder;
    @Autowired private SpringSymbolIndex indexer;

	private String testSourceUri;
	private String testResourceUri;

    @BeforeEach
    public void setup() throws Exception {
        harness.intialize(null);

        File directory = new File(ProjectsHarness.class.getResource("/test-projects/test-annotation-conditionalonresource/").toURI());

        String projectDir = directory.toURI().toString();
        projectFinder.find(new TextDocumentIdentifier(projectDir)).get();
        
        testResourceUri = directory.toPath().resolve("src/main/java/a-random-resource-root.md").toUri().toASCIIString();
        testSourceUri = directory.toPath().resolve("src/main/java/org/test/TestConditionalOnResourceCompletion.java").toUri().toASCIIString();

        CompletableFuture<Void> initProject = indexer.waitOperation();
        initProject.get(5, TimeUnit.SECONDS);
    }

    @Test
    void testFindClasspathResource() throws Exception {
        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;
				import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
				import org.springframework.context.annotation.Configuration;
				
				@Configuration
				@ConditionalOnResource("classpath:a-random-resource-root.md")
				public class TestConditionalOnResourceCompletion {
					private String value1;
				}""", testSourceUri);

        LocationLink expectedLocation = new LocationLink(testResourceUri,
                new Range(new Position(0, 0), new Position(0, 0)),
                new Range(new Position(0, 0), new Position(0, 0)),
                new Range(new Position(5, 23), new Position(5, 60)));

        editor.assertDefinitionLinkTargets("classpath:a-random-resource-root.md", List.of(expectedLocation));
    }

}