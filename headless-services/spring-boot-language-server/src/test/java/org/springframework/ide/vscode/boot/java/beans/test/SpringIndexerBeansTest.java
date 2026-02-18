/*******************************************************************************
 * Copyright (c) 2017, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.beans.test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Arrays;
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
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
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
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private File directory;

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
                SpringIndexerHarness.symbol("SimpleConfiguration", "@+ 'simpleConfiguration' (@Configuration <: @Component) SimpleConfiguration"),
                SpringIndexerHarness.symbol("@Bean", "@+ 'simpleBean' (@Bean) BeanClass")
        );
        
        Bean[] beans = springIndex.getBeansOfDocument(docUri);
        assertEquals(2, beans.length);
        
        Bean simpleConfigBean = Arrays.stream(beans).filter(bean -> bean.getName().equals("simpleConfiguration")).findFirst().get();
        Bean simpleBean = Arrays.stream(beans).filter(bean -> bean.getName().equals("simpleBean")).findFirst().get();
        
        assertEquals("org.test.SimpleConfiguration", simpleConfigBean.getType());
        assertTrue(simpleConfigBean.isConfiguration());
        
        assertEquals("org.test.BeanClass", simpleBean.getType());
        
        List<SpringIndexElement> children = simpleConfigBean.getChildren();
        assertEquals(1, children.size());
        assertSame(simpleBean, children.get(0));
    }

    @Test
    void testScanSpecialConfigurationClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SpecialConfiguration.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("SpecialConfiguration", "@+ 'specialConfiguration' (@Configuration <: @Component) SpecialConfiguration"),

                // @Bean("implicitNamedBean")
                SpringIndexerHarness.symbol("implicitNamedBean", "@+ 'implicitNamedBean' (@Bean) BeanClass"),

                // @Bean("implicit" + "NamedBean")
                SpringIndexerHarness.symbol("\"implicit\" + \"NamedBean\" + \"Concatenated\"", "@+ 'implicitNamedBeanConcatenated' (@Bean) BeanClass"),

                // @Bean(value="valueBean")
                SpringIndexerHarness.symbol("valueBean", "@+ 'valueBean' (@Bean) BeanClass"),

                // @Bean(value="value" + "Bean")
                SpringIndexerHarness.symbol("\"valueBean\" + \"Concatenated\"", "@+ 'valueBeanConcatenated' (@Bean) BeanClass"),

                // @Bean(value= {"valueBean1", "valueBean2"})
                SpringIndexerHarness.symbol("valueBean1", "@+ 'valueBean1' (@Bean) BeanClass"),
                SpringIndexerHarness.symbol("valueBean2", "@+ 'valueBean2' (@Bean) BeanClass"),

            	// @Bean(value= {"value" + "Bean1" + "Concatenated", "valueBean2" + "Concatenated"})
                SpringIndexerHarness.symbol("\"value\" + \"Bean1\" + \"Concatenated\"", "@+ 'valueBean1Concatenated' (@Bean) BeanClass"),
                SpringIndexerHarness.symbol("\"valueBean2\" + \"Concatenated\"", "@+ 'valueBean2Concatenated' (@Bean) BeanClass"),

                // @Bean(name="namedBean")
                SpringIndexerHarness.symbol("namedBean", "@+ 'namedBean' (@Bean) BeanClass"),

                // @Bean(name= {"namedBean1", "namedBean2"})
                SpringIndexerHarness.symbol("namedBean1", "@+ 'namedBean1' (@Bean) BeanClass"),
                SpringIndexerHarness.symbol("namedBean2", "@+ 'namedBean2' (@Bean) BeanClass"),

            	// @Bean(name= {"named" + "Bean1" + "Concatenated", "named" + "Bean2" + Constants.SAMPLE_CONSTANT})
                SpringIndexerHarness.symbol("\"named\" + \"Bean1\" + \"Concatenated\"", "@+ 'namedBean1Concatenated' (@Bean) BeanClass"),
                SpringIndexerHarness.symbol("\"named\" + \"Bean2\" + Constants.SAMPLE_CONSTANT", "@+ 'namedBean2SampleConstant' (@Bean) BeanClass")

        	);
    }

    @Test
    void testScanConfigurationClassWithConditionals() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/ConfigurationWithConditionals.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("ConfigurationWithConditionals", "@+ 'configurationWithConditionals' (@Configuration <: @Component) ConfigurationWithConditionals"),
                SpringIndexerHarness.symbol("@Bean", "@+ 'conditionalBean' (@Bean @ConditionalOnJava(JavaVersion.EIGHT)) BeanClass"),
                SpringIndexerHarness.symbol("@Bean", "@+ 'conditionalBeanDifferentSequence' (@Bean @ConditionalOnJava(JavaVersion.EIGHT)) BeanClass"),
                SpringIndexerHarness.symbol("@Bean", "@+ 'conditionalBeanWithJavaAndCloud' (@Bean @ConditionalOnJava(JavaVersion.EIGHT) @Profile(\"cloud\")) BeanClass")
        );
    }

    @Test
    void testScanConfigurationClassWithConditionalsDefaultSymbol() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/ConfigurationWithConditionalsDefaultSymbols.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("ConfigurationWithConditionalsDefaultSymbols", "@+ 'configurationWithConditionalsDefaultSymbols' (@Configuration <: @Component) ConfigurationWithConditionalsDefaultSymbols"),
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
                SpringIndexerHarness.symbol("AbstractBeanConfiguration", "@+ 'abstractBeanConfiguration' (@Configuration <: @Component) AbstractBeanConfiguration")
        );
    }

    @Test
    void testScanSimpleComponentClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SimpleComponent.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("SimpleComponent", "@+ 'simpleComponent' (@Component) SimpleComponent")
        );
    }

    @Test
    void testScanComponentClassWithName() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SpecialNameComponent.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("SpecialNameComponent", "@+ 'specialName' (@Component) SpecialNameComponent")
        );
    }

    @Test
    void testScanComponentClassWithNameAndStringConcatenation() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SpecialNameComponentWithStringConcatenation.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("SpecialNameComponentWithStringConcatenation", "@+ 'specialName' (@Component) SpecialNameComponentWithStringConcatenation")
        );
    }

    @Test
    void testScanComponentClassWithNameAndAttributeName() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SpecialNameComponentWithAttributeName.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("SpecialNameComponentWithAttributeName", "@+ 'specialNameWithAttributeName' (@Component) SpecialNameComponentWithAttributeName")
        );
    }

    @Test
    void testScanComponentClassWithNameAndAttributeNameWithStringConcatenation() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SpecialNameComponentWithAttributeNameWithStringConcatenation.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("SpecialNameComponentWithAttributeNameWithStringConcatenation", "@+ 'specialNameWithAttributeName' (@Component) SpecialNameComponentWithAttributeNameWithStringConcatenation")
        );
    }

    @Test
    void testScanComponentClassWithNameWithStringConcatenationAndConstant() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SpecialNameComponentWithStringConcatenationAndConstant.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("SpecialNameComponentWithStringConcatenationAndConstant", "@+ 'specialNameSampleConstant' (@Component) SpecialNameComponentWithStringConcatenationAndConstant")
        );
    }

    @Test
    void testScanSimpleControllerClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SimpleController.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("SimpleController", "@+ 'simpleController' (@Controller <: @Component) SimpleController")
        );
    }

    @Test
    void testScanRestControllerClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/SimpleRestController.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("SimpleRestController", "@+ 'simpleRestController' (@RestController <: @Controller, @Component) SimpleRestController")
        );
    }

    @Test
    void testCustomAnnotationClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/CustomAnnotation.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("@AliasFor(annotation = Component.class)", "@AliasFor(annotation=Component.class)")
        );
    }
    
    @Test
    void testScanControllerWithTwoNames() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/org/test/ControllerWithTwoNames.java").toUri().toString();
        SpringIndexerHarness.assertDocumentSymbols(indexer, docUri,
                SpringIndexerHarness.symbol("ControllerWithTwoNames", "@+ '(controllerName, configName)' (@Controller, @Configuration <: @Component) ControllerWithTwoNames")
        );
    }

}
