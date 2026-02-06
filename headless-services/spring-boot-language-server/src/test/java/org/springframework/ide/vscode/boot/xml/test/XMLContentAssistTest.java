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
package org.springframework.ide.vscode.boot.xml.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.CompletionItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.OverrideAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.BootLanguageServerBootApp;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.XmlBeansTestConf;
import org.springframework.ide.vscode.boot.java.utils.test.MockProjectObserver;
import org.springframework.ide.vscode.boot.xml.completions.NamespaceCompletionProvider;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.starter.LanguageServerAutoConf;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

/**
 * @author Martin Lippert
 */
@OverrideAutoConfiguration(enabled=false)
@Import({LanguageServerAutoConf.class, XmlBeansTestConf.class})
@SpringBootTest(classes={
		BootLanguageServerBootApp.class
})
@DirtiesContext(classMode=ClassMode.AFTER_EACH_TEST_METHOD)
public class XMLContentAssistTest {
		
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private MockProjectObserver projectObserver;
	
	private ProjectsHarness projects = ProjectsHarness.INSTANCE;
	private MavenJavaProject project;
	
	private int ALL_NAMESPACE_COMPLETIONS = NamespaceCompletionProvider.getNamespaces().length;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
		
		Map<String, Object> supportXML = new HashMap<>();
		supportXML.put("on", true);
		supportXML.put("hyperlinks", true);
		supportXML.put("content-assist", true);
		supportXML.put("scan-folders", "/src/main/");
		Map<String, Object> bootJavaObj = new HashMap<>();
		bootJavaObj.put("support-spring-xml-config", supportXML);
		Map<String, Object> settings = new HashMap<>();
		settings.put("boot-java", bootJavaObj);
		
		harness.changeConfiguration(settings);
		// Configuration change updates indexer hence we need to wait until this occurs as well
		indexer.waitOperation();

		project = projects.mavenProject("test-xml-hyperlinks");
		
		harness.useProject(project);
		
		projectObserver.doWithListeners(l -> l.created(project));

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(1500, TimeUnit.SECONDS);
	}
	
    @Test
    void testEmptyXMLFileCompletions() throws Exception {
        Editor editor = new Editor(harness, "<*>", LanguageId.XML);
        
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(1, completions.size());
        assertEquals("Spring XML config file skeleton", completions.get(0).getLabel());
    }

    @Test
    void testNoSkeletonSnippetForNonEmptyXMLFile() throws Exception {
        Editor editor = new Editor(harness, "<*><?xml version=\\\"1.0\\\" encoding=\\\"UTF-8\\\"?>\\n", LanguageId.XML);
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(0, completions.size());
    }

    @Test
    void testAddNamespaceCompletion1() throws Exception {
        Editor editor = new Editor(harness, """
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				<*>xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"></bean>
			</beans>
			""",
			LanguageId.XML);
        
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(ALL_NAMESPACE_COMPLETIONS, completions.size());
        
        CompletionItem contextItem = getContextCompletionItem(completions);
        assertNotNull(contextItem);
        
        editor.apply(contextItem);
        String editorContent = editor.getText();
        
        assertEquals("""
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xmlns:context="http://www.springframework.org/schema/context"
				<*>xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd
					http://www.springframework.org/schema/context https://www.springframework.org/schema/context/spring-context.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"></bean>
			</beans>
			""",
        	editorContent);
    }

    @Test
    void testAddNamespaceCompletion2() throws Exception {
        Editor editor = new Editor(harness, """
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				<*>
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"></bean>
			</beans>
			""",
			LanguageId.XML);
        
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(ALL_NAMESPACE_COMPLETIONS, completions.size());
        
        CompletionItem contextItem = getContextCompletionItem(completions);
        assertNotNull(contextItem);
        
        editor.apply(contextItem);
        String editorContent = editor.getText();
        
        assertEquals("""
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xmlns:context="http://www.springframework.org/schema/context"
				<*>
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd
					http://www.springframework.org/schema/context https://www.springframework.org/schema/context/spring-context.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"></bean>
			</beans>
			""",
        	editorContent);
    }
    
    @Test
    void testAddNamespaceCompletion3() throws Exception {
        Editor editor = new Editor(harness, """
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xmlns:<*>
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"></bean>
			</beans>
			""",
			LanguageId.XML);
        
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(ALL_NAMESPACE_COMPLETIONS, completions.size());
        
        CompletionItem contextItem = getContextCompletionItem(completions);
        assertNotNull(contextItem);
        
        editor.apply(contextItem);
        String editorContent = editor.getText();
        
        assertEquals("""
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xmlns:context="http://www.springframework.org/schema/context"
				<*>
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd
					http://www.springframework.org/schema/context https://www.springframework.org/schema/context/spring-context.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"></bean>
			</beans>
			""",
        	editorContent);
    }

    @Test
    void testAddNamespaceCompletion4() throws Exception {
        Editor editor = new Editor(harness, """
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xmlns<*>
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"></bean>
			</beans>
			""",
			LanguageId.XML);
        
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(ALL_NAMESPACE_COMPLETIONS, completions.size());
        
        CompletionItem contextItem = getContextCompletionItem(completions);
        assertNotNull(contextItem);
        
        editor.apply(contextItem);
        String editorContent = editor.getText();
        
        assertEquals("""
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xmlns:context="http://www.springframework.org/schema/context"
				<*>
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd
					http://www.springframework.org/schema/context https://www.springframework.org/schema/context/spring-context.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"></bean>
			</beans>
			""",
        	editorContent);
    }

    @Test
    void testAddNamespaceCompletion5() throws Exception {
        Editor editor = new Editor(harness, """
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xm<*>
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"></bean>
			</beans>
			""",
			LanguageId.XML);
        
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(ALL_NAMESPACE_COMPLETIONS, completions.size());
        
        CompletionItem contextItem = getContextCompletionItem(completions);
        assertNotNull(contextItem);
        
        editor.apply(contextItem);
        String editorContent = editor.getText();
        
        assertEquals("""
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xmlns:context="http://www.springframework.org/schema/context"
				<*>
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd
					http://www.springframework.org/schema/context https://www.springframework.org/schema/context/spring-context.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"></bean>
			</beans>
			""",
        	editorContent);
    }

    @Test
    void testAddNamespaceCompletionWhenBeansElementFullyQualified() throws Exception {
        Editor editor = new Editor(harness, """
			<beans:beans xmlns="http://www.springframework.org/schema/mvc"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xmlns:beans="http://www.springframework.org/schema/beans"
				xmlns:context="http://www.springframework.org/schema/context"
				<*>
				
				xsi:schemaLocation="http://www.springframework.org/schema/mvc https://www.springframework.org/schema/mvc/spring-mvc.xsd
					http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd
					http://www.springframework.org/schema/context https://www.springframework.org/schema/context/spring-context.xsd">

				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"></bean>
			</beans>
			""",
			LanguageId.XML);
        
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(ALL_NAMESPACE_COMPLETIONS, completions.size());
    }

    @Test
    void testNoNamespaceCompletionOutsideOfMainElement1() throws Exception {
        Editor editor = new Editor(harness, """
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"></bean>
				<*>
			</beans>
			""",
			LanguageId.XML);
        
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(0, completions.size());
    }

    @Test
    void testNoNamespaceCompletionOutsideOfMainElement2() throws Exception {
        Editor editor = new Editor(harness, """
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<*><bean id="simpleObj" class="u.t.r.SimpleObj"></bean>
			</beans>
			""",
			LanguageId.XML);
        
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(0, completions.size());
    }

    @Test
    void testNoNamespaceCompletionOutsideOfMainElement3() throws Exception {
        Editor editor = new Editor(harness, """
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<<*>bean id="simpleObj" class="u.t.r.SimpleObj"></bean>
			</beans>
			""",
			LanguageId.XML);
        
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(0, completions.size());
    }

    @Test
    void testNoNamespaceCompletionOutsideOfMainElement4() throws Exception {
        Editor editor = new Editor(harness, """
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean <*>id="simpleObj" class="u.t.r.SimpleObj"></bean>
			</beans>
			""",
			LanguageId.XML);
        
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(0, completions.size());
    }

    @Test
    void testNoNamespaceCompletionOutsideOfMainElement5() throws Exception {
        Editor editor = new Editor(harness, """
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"><<*>/bean>
			</beans>
			""",
			LanguageId.XML);
        
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(0, completions.size());
    }
    
    @Test
    void testNoNamespaceCompletionOutsideOfMainElement6() throws Exception {
        Editor editor = new Editor(harness, """
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"><<*>/bean>
			</beans>
			""",
			LanguageId.XML);
        
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(0, completions.size());
    }

    @Test
    void testNoNamespaceCompletionOutsideOfMainElement7() throws Exception {
        Editor editor = new Editor(harness, """
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"></be<*>an>
			</beans>
			""",
			LanguageId.XML);
        
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(0, completions.size());
    }

    @Test
    void testNoNamespaceCompletionOutsideOfMainElement8() throws Exception {
        Editor editor = new Editor(harness, """
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj"></bean>
			</beans<*>>
			""",
			LanguageId.XML);
        
        List<CompletionItem> completions = editor.getCompletions();
        assertEquals(0, completions.size());
    }
    
    private CompletionItem getContextCompletionItem(List<CompletionItem> completions) {
        for (CompletionItem completionItem : completions) {
			if (completionItem.getLabel().contains("/schema/context")) {
				return completionItem;
			}
		}
        
        return null;
    }

}
