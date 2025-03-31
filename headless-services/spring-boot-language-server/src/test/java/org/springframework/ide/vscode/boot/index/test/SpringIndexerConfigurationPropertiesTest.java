/*******************************************************************************
 * Copyright (c) 2025 Broadcom
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.index.test;

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
import org.springframework.ide.vscode.boot.java.beans.ConfigPropertyIndexElement;
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
public class SpringIndexerConfigurationPropertiesTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-configuration-properties-indexing/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testSimpleConfigPropertiesClass() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/com/example/configproperties/ConfigurationPropertiesExample.java").toUri().toString();

        Bean[] beans = springIndex.getBeansOfDocument(docUri);
        assertEquals(1, beans.length);
        
        Bean configPropertiesComponentBean = Arrays.stream(beans).filter(bean -> bean.getName().equals("configurationPropertiesExample")).findFirst().get();
        assertEquals("com.example.configproperties.ConfigurationPropertiesExample", configPropertiesComponentBean.getType());
        
        List<SpringIndexElement> children = configPropertiesComponentBean.getChildren();
        assertEquals(1, children.size());
        
        ConfigPropertyIndexElement configPropElement = (ConfigPropertyIndexElement) children.get(0);
        assertEquals("com.example.config.prefix.simple.simpleConfigProp", configPropElement.getName());
        assertEquals("java.lang.String", configPropElement.getType());
    }
    
    @Test
    void testSimpleConfigPropertiesClassWithAdditionalConfigurationAnnotation() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/com/example/configproperties/ConfigurationPropertiesExampleWithConfigurationAnnotation.java").toUri().toString();

        Bean[] beans = springIndex.getBeansOfDocument(docUri);
        assertEquals(1, beans.length);
        
        Bean configPropertiesComponentBean = Arrays.stream(beans).filter(bean -> bean.getName().equals("configurationPropertiesExampleWithConfigurationAnnotation")).findFirst().get();
        assertEquals("com.example.configproperties.ConfigurationPropertiesExampleWithConfigurationAnnotation", configPropertiesComponentBean.getType());
        
        List<SpringIndexElement> children = configPropertiesComponentBean.getChildren();
        assertEquals(1, children.size());
        
        ConfigPropertyIndexElement configPropElement = (ConfigPropertyIndexElement) children.get(0);
        assertEquals("com.example.config.prefix.simple2.simpleConfigProp", configPropElement.getName());
        assertEquals("java.lang.String", configPropElement.getType());
    }

    @Test
    void testSimpleConfigPropertiesRecord() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/com/example/configproperties/ConfigurationPropertiesWithRecords.java").toUri().toString();

        Bean[] beans = springIndex.getBeansOfDocument(docUri);
        assertEquals(1, beans.length);
        
        Bean configPropertiesComponentBean = Arrays.stream(beans).filter(bean -> bean.getName().equals("configurationPropertiesWithRecords")).findFirst().get();
        assertEquals("com.example.configproperties.ConfigurationPropertiesWithRecords", configPropertiesComponentBean.getType());
        
        List<SpringIndexElement> children = configPropertiesComponentBean.getChildren();
        assertEquals(2, children.size());
        
        ConfigPropertyIndexElement configPropElement1 = (ConfigPropertyIndexElement) children.get(0);
        assertEquals("com.example.config.record.prefix.name", configPropElement1.getName());
        assertEquals("java.lang.String", configPropElement1.getType());

        ConfigPropertyIndexElement configPropElement2 = (ConfigPropertyIndexElement) children.get(1);
        assertEquals("com.example.config.record.prefix.duration", configPropElement2.getName());
        assertEquals("int", configPropElement2.getType());
    }
    
    @Test
    void testSimpleConfigPropertiesRecordAndConfigurationAnnotation() throws Exception {
        String docUri = directory.toPath().resolve("src/main/java/com/example/configproperties/ConfigurationPropertiesWithRecordsAndConfigurationAnnotation.java").toUri().toString();

        Bean[] beans = springIndex.getBeansOfDocument(docUri);
        assertEquals(1, beans.length);
        
        Bean configPropertiesComponentBean = Arrays.stream(beans).filter(bean -> bean.getName().equals("configurationPropertiesWithRecordsAndConfigurationAnnotation")).findFirst().get();
        assertEquals("com.example.configproperties.ConfigurationPropertiesWithRecordsAndConfigurationAnnotation", configPropertiesComponentBean.getType());
        
        List<SpringIndexElement> children = configPropertiesComponentBean.getChildren();
        assertEquals(2, children.size());
        
        ConfigPropertyIndexElement configPropElement1 = (ConfigPropertyIndexElement) children.get(0);
        assertEquals("com.example.config.record.prefix2.name", configPropElement1.getName());
        assertEquals("java.lang.String", configPropElement1.getType());

        ConfigPropertyIndexElement configPropElement2 = (ConfigPropertyIndexElement) children.get(1);
        assertEquals("com.example.config.record.prefix2.duration", configPropElement2.getName());
        assertEquals("int", configPropElement2.getType());

    }
    
    
    
}
