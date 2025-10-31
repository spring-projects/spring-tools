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
package org.springframework.ide.vscode.boot.java.conditionals.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.LocationLink;
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
public class ConditionalOnBeanDefinitionProviderTest {

    @Autowired private BootLanguageServerHarness harness;
    @Autowired private JavaProjectFinder projectFinder;
    @Autowired private SpringMetamodelIndex springIndex;
    @Autowired private SpringSymbolIndex indexer;

    private File directory;
    private IJavaProject project;

    @BeforeEach
    public void setup() throws Exception {
        harness.intialize(null);

        directory = new File(ProjectsHarness.class.getResource("/test-projects/test-annotation-conditionalonbean/").toURI());

        String projectDir = directory.toURI().toString();
        project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

        CompletableFuture<Void> initProject = indexer.waitOperation();
        initProject.get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testConditionalOnBeanWithNameRefersToBeanDefinitionLink() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

				@Configuration
                public class TestConditionalOnBeanCompletion {
                @ConditionalOnBean(name = "bean1")
                @Bean
                public void method() {
                }
				}""", tempJavaDocUri);

        Bean[] beans = springIndex.getBeansWithName(project.getElementName(), "bean1");
        assertEquals(1, beans.length);

        LocationLink expectedLocation = new LocationLink(beans[0].getLocation().getUri(),
                beans[0].getLocation().getRange(), beans[0].getLocation().getRange(),
                null);

        editor.assertDefinitionLinkTargets("bean1", List.of(expectedLocation));
    }

    @Test
    public void testConditionalOnMissingBeanWithNameRefersToBeanDefinitionLink() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

				@Configuration
                public class TestConditionalOnBeanCompletion {
                @ConditionalOnMissingBean(name = "bean1")
                @Bean
                public void method() {
                }
				}""", tempJavaDocUri);

        Bean[] beans = springIndex.getBeansWithName(project.getElementName(), "bean1");
        assertEquals(1, beans.length);

        LocationLink expectedLocation = new LocationLink(beans[0].getLocation().getUri(),
                beans[0].getLocation().getRange(), beans[0].getLocation().getRange(),
                null);

        editor.assertDefinitionLinkTargets("bean1", List.of(expectedLocation));
    }

    @Test
    public void testConditionalOnBeanWithNameArrayRefersToBeanDefinitionLink() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

				@Configuration
                public class TestConditionalOnBeanCompletion {
                @ConditionalOnBean(name = {"bean1", "bean2"})
                @Bean
                public void method() {
                }
				}""", tempJavaDocUri);

        Bean[] beans = springIndex.getBeansWithName(project.getElementName(), "bean2");
        assertEquals(1, beans.length);

        LocationLink expectedLocation = new LocationLink(beans[0].getLocation().getUri(),
                beans[0].getLocation().getRange(), beans[0].getLocation().getRange(),
                null);

        editor.assertDefinitionLinkTargets("bean2", List.of(expectedLocation));
    }

    @Test
    public void testConditionalOnMissingBeanWithNameArrayRefersToBeanDefinitionLink() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

				@Configuration
                public class TestConditionalOnBeanCompletion {
                @ConditionalOnMissingBean(name = {"bean1", "bean2"})
                @Bean
                public void method() {
                }
				}""", tempJavaDocUri);

        Bean[] beans = springIndex.getBeansWithName(project.getElementName(), "bean2");
        assertEquals(1, beans.length);

        LocationLink expectedLocation = new LocationLink(beans[0].getLocation().getUri(),
                beans[0].getLocation().getRange(), beans[0].getLocation().getRange(),
                null);

        editor.assertDefinitionLinkTargets("bean2", List.of(expectedLocation));
    }

    @Test
    public void testConditionalOnBeanRefersToRandomBeanWithoutDefinitionLink() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

				@Configuration
                public class TestConditionalOnBeanCompletion {
                @ConditionalOnBean(name = "bean5")
                @Bean
                public void method() {
                }
				}""", tempJavaDocUri);

        editor.assertNoDefinitionLinkTargets("bean5");
    }

    @Test
    public void testConditionalOnMissingBeanRefersToRandomBeanWithoutDefinitionLink() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

				@Configuration
                public class TestConditionalOnBeanCompletion {
                @ConditionalOnMissingBean(name = "bean5")
                @Bean
                public void method() {
                }
				}""", tempJavaDocUri);

        editor.assertNoDefinitionLinkTargets("bean5");
    }

    @Test
    public void testConditionalOnBeanUsesTypeInsteadOfBeanNameSoNoDefinitionLink() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

				@Configuration
                public class TestConditionalOnBeanCompletion {
                @ConditionalOnBean(type = "bean1")
                @Bean
                public void method() {
                }
				}""", tempJavaDocUri);

        editor.assertNoDefinitionLinkTargets("bean1");
    }

    @Test
    public void testConditionalOnMissingBeanUsesTypeInsteadOfBeanNameSoNoDefinitionLink() throws Exception {
        String tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TempClass.java").toUri().toString();

        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
                import org.springframework.context.annotation.Bean;
                import org.springframework.context.annotation.Configuration;

				@Configuration
                public class TestConditionalOnBeanCompletion {
                @ConditionalOnMissingBean(type = "bean1")
                @Bean
                public void method() {
                }
				}""", tempJavaDocUri);

        editor.assertNoDefinitionLinkTargets("bean1");
    }

}
