/*******************************************************************************
 * Copyright (c) 2017, 2022 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.beans.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.java.beans.BeansSymbolAddOnInformation;
import org.springframework.ide.vscode.boot.java.handlers.SymbolAddOnInformation;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class SpringIndexerBeansTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;

	private File directory;
	@Autowired private SpringSymbolIndex indexer;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-annotation-indexing-beans/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testScanSimpleConfigurationClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SimpleConfiguration.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("@Configuration", "@+ 'simpleConfiguration' (@Configuration <: @Component) SimpleConfiguration"),
                SpringIndexerHarness.symbol("@Bean", "@+ 'simpleBean' (@Bean) BeanClass")
        );

        List<? extends SymbolAddOnInformation> addon = indexer.getAdditonalInformation(docUri);
        assertEquals(2, addon.size());

        assertEquals(1, addon.stream()
                .filter(info -> info instanceof BeansSymbolAddOnInformation)
                .filter(info -> "simpleConfiguration".equals(((BeansSymbolAddOnInformation) info).getBeanID()))
                .count());

        assertEquals(1, addon.stream()
                .filter(info -> info instanceof BeansSymbolAddOnInformation)
                .filter(info -> "simpleBean".equals(((BeansSymbolAddOnInformation) info).getBeanID()))
                .count());
    }

    @Test
    void testScanSpecialConfigurationClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SpecialConfiguration.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("@Configuration", "@+ 'specialConfiguration' (@Configuration <: @Component) SpecialConfiguration"),

                // @Bean("implicitNamedBean")
                SpringIndexerHarness.symbol("implicitNamedBean", "@+ 'implicitNamedBean' (@Bean) BeanClass"),

                // @Bean(value="valueBean")
                SpringIndexerHarness.symbol("valueBean", "@+ 'valueBean' (@Bean) BeanClass"),

                // @Bean(value= {"valueBean1", "valueBean2"})
                SpringIndexerHarness.symbol("valueBean1", "@+ 'valueBean1' (@Bean) BeanClass"),
                SpringIndexerHarness.symbol("valueBean2", "@+ 'valueBean2' (@Bean) BeanClass"),

                // @Bean(name="namedBean")
                SpringIndexerHarness.symbol("namedBean", "@+ 'namedBean' (@Bean) BeanClass"),

                // @Bean(name= {"namedBean1", "namedBean2"})
                SpringIndexerHarness.symbol("namedBean1", "@+ 'namedBean1' (@Bean) BeanClass"),
                SpringIndexerHarness.symbol("namedBean2", "@+ 'namedBean2' (@Bean) BeanClass")
        );
    }

    @Test
    void testScanConfigurationClassWithConditionals() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/ConfigurationWithConditionals.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("@Configuration", "@+ 'configurationWithConditionals' (@Configuration <: @Component) ConfigurationWithConditionals"),
                SpringIndexerHarness.symbol("@Bean", "@+ 'conditionalBean' (@Bean @ConditionalOnJava(JavaVersion.EIGHT)) BeanClass"),
                SpringIndexerHarness.symbol("@Bean", "@+ 'conditionalBeanDifferentSequence' (@Bean @ConditionalOnJava(JavaVersion.EIGHT)) BeanClass"),
                SpringIndexerHarness.symbol("@Bean", "@+ 'conditionalBeanWithJavaAndCloud' (@Bean @ConditionalOnJava(JavaVersion.EIGHT) @Profile(\"cloud\")) BeanClass")
        );
    }

    @Test
    void testScanConfigurationClassWithConditionalsDefaultSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/ConfigurationWithConditionalsDefaultSymbols.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("@Configuration", "@+ 'configurationWithConditionalsDefaultSymbols' (@Configuration <: @Component) ConfigurationWithConditionalsDefaultSymbols"),
                SpringIndexerHarness.symbol("@ConditionalOnJava(JavaVersion.EIGHT)", "@ConditionalOnJava(JavaVersion.EIGHT)"),
                SpringIndexerHarness.symbol("@Profile(\"cloud\")", "@Profile(\"cloud\")"),
                SpringIndexerHarness.symbol("@ConditionalOnJava(JavaVersion.EIGHT)", "@ConditionalOnJava(JavaVersion.EIGHT)"),
                SpringIndexerHarness.symbol("@Profile(\"cloud\")", "@Profile(\"cloud\")")
        );
    }

    @Test
    void testScanAbstractBeanConfiguration() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/AbstractBeanConfiguration.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("@Configuration", "@+ 'abstractBeanConfiguration' (@Configuration <: @Component) AbstractBeanConfiguration")
        );
    }

    @Test
    void testScanSimpleComponentClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SimpleComponent.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("@Component", "@+ 'simpleComponent' (@Component) SimpleComponent")
        );
    }

    @Test
    void testScanSimpleControllerClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SimpleController.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("@Controller", "@+ 'simpleController' (@Controller <: @Component) SimpleController")
        );
    }

    @Test
    void testScanRestControllerClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SimpleRestController.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("@RestController", "@+ 'simpleRestController' (@RestController <: @Controller, @Component) SimpleRestController")
        );
    }

    @Test
    void testCustomAnnotationClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/CustomAnnotation.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("@AliasFor(annotation = Component.class)", "@AliasFor(annotation=Component.class)")
        );
    }

}
