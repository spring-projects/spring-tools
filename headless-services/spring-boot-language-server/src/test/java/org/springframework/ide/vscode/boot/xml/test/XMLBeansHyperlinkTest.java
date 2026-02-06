/*******************************************************************************
 * Copyright (c) 2019, 2025 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.xml.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.BootLanguageServerBootApp;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.XmlBeansTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.utils.test.MockProjectObserver;
import org.springframework.ide.vscode.boot.test.DefinitionLinkAsserts;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.UriUtil;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.starter.LanguageServerAutoConf;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

/**
 * @author Alex Boyko
 */
@OverrideAutoConfiguration(enabled=false)
@Import({LanguageServerAutoConf.class, XmlBeansTestConf.class})
@SpringBootTest(classes={
		BootLanguageServerBootApp.class
})
@DirtiesContext(classMode=ClassMode.AFTER_EACH_TEST_METHOD)
public class XMLBeansHyperlinkTest {
	
	
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(XMLBeansHyperlinkTest.class);
	
	private static final int DEFAULT_INDEX_WAIT_TIME = 5;
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private DefinitionLinkAsserts definitionLinkAsserts;
	@Autowired private MockProjectObserver projectObserver;
	@Autowired private SpringMetamodelIndex springIndex;
	
	private ProjectsHarness projects = ProjectsHarness.INSTANCE;
	private MavenJavaProject project;
	
	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		
		Map<String, Object> supportXML = new HashMap<>();
		supportXML.put("on", true);
		supportXML.put("hyperlinks", true);
		supportXML.put("scan-folders", "/src/main/");
		Map<String, Object> bootJavaObj = new HashMap<>();
		bootJavaObj.put("support-spring-xml-config", supportXML);
		Map<String, Object> settings = new HashMap<>();
		settings.put("boot-java", bootJavaObj);
		
		harness.changeConfiguration(settings);
		// Configuration change updates indexer hence we need to wait until this occurs as well
		indexer.waitOperation().get();

		project = projects.mavenProject("test-xml-hyperlinks");
		
		harness.useProject(project);
		
		projectObserver.doWithListeners(l -> l.created(project));

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(1500, TimeUnit.SECONDS);
	}
	
    @Test
    void testBeanClassHyperlink() throws Exception {
        Path xmlFilePath = Paths.get(project.getLocationUri()).resolve("beans.xml");
        Editor editor = harness.newEditor(LanguageId.XML,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<beans xmlns=\"http://www.springframework.org/schema/beans\"\n" +
                        "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "xsi:schemaLocation=\"http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd\">\n" +

                        "<bean id=\"someId\" class=\"u.t.r.SimpleObj\"></bean>\n" +
                        "</beans>\n",
                UriUtil.toUri(xmlFilePath.toFile()).toString()
        );
        definitionLinkAsserts.assertLinkTargets(editor, "u.t.r.SimpleObj", project, editor.rangeOf("u.t.r.SimpleObj", "u.t.r.SimpleObj"), "u.t.r.SimpleObj");
    }

    @Test
    void testBeanPropertyNameHyperlink() throws Exception {
        Path xmlFilePath = Paths.get(project.getLocationUri()).resolve("beans.xml");
        Editor editor = harness.newEditor(LanguageId.XML,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<beans xmlns=\"http://www.springframework.org/schema/beans\"\n" +
                        "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "xsi:schemaLocation=\"http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd\">\n" +

                        "<bean id=\"someBean\" class=\"u.t.r.TestBean\"\n" +
                        "<property name=\"age\" value=\"10\" />\n" +
                        "</bean>\n" +
                        "</beans>\n",
                UriUtil.toUri(xmlFilePath.toFile()).toString()
        );
        definitionLinkAsserts.assertLinkTargets(editor, "age", project,
                editor.rangeOf("<property name=\"age\" value=\"10\" />", "age"),
                DefinitionLinkAsserts.method("u.t.r.TestBean", "setAge", "int"));
    }

    @Test
    void testBeanPropertyNameFromSuperClassHyperlink() throws Exception {
        Path xmlFilePath = Paths.get(project.getLocationUri()).resolve("beans.xml");
        Editor editor = harness.newEditor(LanguageId.XML,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<beans xmlns=\"http://www.springframework.org/schema/beans\"\n" +
                        "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "xsi:schemaLocation=\"http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd\">\n" +

                        "<bean id=\"someBean\" class=\"u.t.r.TestBean\"\n" +
                        "<property name=\"message\" value=\"Hello\" />\n" +
                        "</bean>\n" +
                        "</beans>\n",
                UriUtil.toUri(xmlFilePath.toFile()).toString()
        );
        
        definitionLinkAsserts.assertLinkTargets(editor, "message", project,
                editor.rangeOf("<property name=\"message\" value=\"Hello\" />", "message"),
                DefinitionLinkAsserts.method("u.t.r.SuperTestBean", "setMessage", "java.lang.String"));
    }

    @Test
//    @Disabled
    void testBeanRefHyperlink() throws Exception {
    	Bean[] allBeans = springIndex.getBeans();
    	assertEquals(4, springIndex.getBeans().length, "All beans are: %s".formatted(Arrays.stream(allBeans).map(b -> "(name=%s, type=%s)".formatted(b.getName(), b.getType())).collect(Collectors.joining(", "))));
		
		Bean[] beans = springIndex.getBeansWithName(project.getElementName(), "simpleObj");
		assertEquals(1, beans.length, "Found beans are: %s".formatted(Arrays.stream(beans).map(b -> "(name=%s, type=%s)".formatted(b.getName(), b.getType())).collect(Collectors.joining(", "))));
		assertEquals("simpleObj", beans[0].getName());
		assertEquals("u.t.r.SimpleObj", beans[0].getType());
		
    	log.debug("------------------ testBeanRefHyperlink ----------------------");
        Path xmlFilePath = Paths.get(project.getLocationUri()).resolve("beans.xml");
        Editor editor = harness.newEditor(LanguageId.XML,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<beans xmlns=\"http://www.springframework.org/schema/beans\"\n" +
                        "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "xsi:schemaLocation=\"http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd\">\n" +

                        "<bean id=\"someBean\" class=\"u.t.r.TestBean\"\n" +
                        "<property name=\"simple\" ref=\"simpleObj\"></property>\n" +
                        "</bean>\n" +
                        "</beans>\n",
                UriUtil.toUri(xmlFilePath.toFile()).toString()
        );
        Path rootContextFilePath = Paths.get(project.getLocationUri()).resolve("src/main/webapp/WEB-INF/spring/root-context.xml");
        Range targetRange = new Range(new Position(6, 7), new Position(6, 21));
        LocationLink expectedLocation = new LocationLink(
                UriUtil.toUri(rootContextFilePath.toFile()).toString(),
                targetRange,
                targetRange,
                editor.rangeOf("name=\"simple\" ref=\"simpleObj\"", "simpleObj")
        );
        editor.assertDefinitionLinkTargets("simpleObj", Collections.singletonList(expectedLocation));
    }

    @Test
    void testBeanRefNoHyperlink_FolderNotScanned() throws Exception {
        Map<String, Object> supportXML = new HashMap<>();
        supportXML.put("on", true);
        supportXML.put("hyperlinks", true);
        supportXML.put("scan-folders", "  ");
        Map<String, Object> bootJavaObj = new HashMap<>();
        bootJavaObj.put("support-spring-xml-config", supportXML);
        Map<String, Object> settings = new HashMap<>();
        settings.put("boot-java", bootJavaObj);

		harness.changeConfiguration(settings);
		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(DEFAULT_INDEX_WAIT_TIME, TimeUnit.SECONDS);

        Path xmlFilePath = Paths.get(project.getLocationUri()).resolve("beans.xml");
        Editor editor = harness.newEditor(LanguageId.XML,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<beans xmlns=\"http://www.springframework.org/schema/beans\"\n" +
                        "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "xsi:schemaLocation=\"http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd\">\n" +

                        "<bean id=\"someBean\" class=\"u.t.r.TestBean\"\n" +
                        "<property name=\"simple\" ref=\"simpleObj\"></property>\n" +
                        "</bean>\n" +
                        "</beans>\n",
                UriUtil.toUri(xmlFilePath.toFile()).toString()
        );
        Path rootContextFilePath = Paths.get(project.getLocationUri()).resolve("src/main/webapp/WEB-INF/spring/root-context.xml");
        Location expectedLocation = new Location();
        expectedLocation.setUri(UriUtil.toUri(rootContextFilePath.toFile()).toString());
        expectedLocation.setRange(new Range(new Position(6, 7), new Position(6, 21)));
        editor.assertNoDefinitionLinkTargets("simpleObj");
    }

    @Test
    void testBeanRefHyperlink_SpecifyScanFolderDifferently() throws Exception {
        Map<String, Object> supportXML = new HashMap<>();
        supportXML.put("on", true);
        supportXML.put("hyperlinks", true);
        supportXML.put("scan-folders", "  src/main/  ");
        Map<String, Object> bootJavaObj = new HashMap<>();
        bootJavaObj.put("support-spring-xml-config", supportXML);
        Map<String, Object> settings = new HashMap<>();
        settings.put("boot-java", bootJavaObj);

		harness.changeConfiguration(settings);

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(DEFAULT_INDEX_WAIT_TIME, TimeUnit.SECONDS);

        Path xmlFilePath = Paths.get(project.getLocationUri()).resolve("beans.xml");
        Editor editor = harness.newEditor(LanguageId.XML,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<beans xmlns=\"http://www.springframework.org/schema/beans\"\n" +
                        "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "xsi:schemaLocation=\"http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd\">\n" +

                        "<bean id=\"someBean\" class=\"u.t.r.TestBean\"\n" +
                        "<property name=\"simple\" ref=\"simpleObj\"></property>\n" +
                        "</bean>\n" +
                        "</beans>\n",
                UriUtil.toUri(xmlFilePath.toFile()).toString()
        );
        Path rootContextFilePath = Paths.get(project.getLocationUri()).resolve("src/main/webapp/WEB-INF/spring/root-context.xml");
        Range targetRange = new Range(new Position(6, 7), new Position(6, 21));
        LocationLink expectedLocation = new LocationLink(
                UriUtil.toUri(rootContextFilePath.toFile()).toString(),
                targetRange,
                targetRange,
                editor.rangeOf("name=\"simple\" ref=\"simpleObj\"", "simpleObj")
        );
        editor.assertDefinitionLinkTargets("simpleObj", Collections.singletonList(expectedLocation));
    }

    @Test
    void testNoHyperlinkWhenXmlSupportOff() throws Exception {
        Map<String, Object> supportXML = new HashMap<>();
        supportXML.put("on", false);
        supportXML.put("hyperlinks", true);
        supportXML.put("scan-folders", "src/main");
        Map<String, Object> bootJavaObj = new HashMap<>();
        bootJavaObj.put("support-spring-xml-config", supportXML);
        Map<String, Object> settings = new HashMap<>();
        settings.put("boot-java", bootJavaObj);

		harness.changeConfiguration(settings);
		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(DEFAULT_INDEX_WAIT_TIME, TimeUnit.SECONDS);

        Path xmlFilePath = Paths.get(project.getLocationUri()).resolve("beans.xml");
        Editor editor = harness.newEditor(LanguageId.XML,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<beans xmlns=\"http://www.springframework.org/schema/beans\"\n" +
                        "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "xsi:schemaLocation=\"http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd\">\n" +

                        "<bean id=\"someId\" class=\"u.t.r.SimpleObj\"></bean>\n" +
                        "</beans>\n",
                UriUtil.toUri(xmlFilePath.toFile()).toString()
        );
        editor.assertNoDefinitionLinkTargets("u.t.r.SimpleObj");
    }

    @Test
    void testNoHyperlinkWhenHyperlinksOff() throws Exception {
        Map<String, Object> supportXML = new HashMap<>();
        supportXML.put("on", true);
        supportXML.put("hyperlinks", false);
        supportXML.put("scan-folders", "src/main");
        Map<String, Object> bootJavaObj = new HashMap<>();
        bootJavaObj.put("support-spring-xml-config", supportXML);
        Map<String, Object> settings = new HashMap<>();
        settings.put("boot-java", bootJavaObj);

		harness.changeConfiguration(settings);
		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(DEFAULT_INDEX_WAIT_TIME, TimeUnit.SECONDS);

        Path xmlFilePath = Paths.get(project.getLocationUri()).resolve("beans.xml");
        Editor editor = harness.newEditor(LanguageId.XML,
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<beans xmlns=\"http://www.springframework.org/schema/beans\"\n" +
                        "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                        "xsi:schemaLocation=\"http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd\">\n" +

                        "<bean id=\"someId\" class=\"u.t.r.SimpleObj\"></bean>\n" +
                        "</beans>\n",
                UriUtil.toUri(xmlFilePath.toFile()).toString()
        );
        editor.assertNoDefinitionLinkTargets("u.t.r.SimpleObj");
    }
}
