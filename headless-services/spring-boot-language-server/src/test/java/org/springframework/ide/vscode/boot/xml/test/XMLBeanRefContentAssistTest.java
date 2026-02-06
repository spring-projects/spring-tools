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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
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
public class XMLBeanRefContentAssistTest {
	
	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	private IJavaProject project;
	private File directory;

	private String tempJavaDocUri;

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

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-xml-hyperlinks/").toURI());

		String projectDir = directory.toURI().toString();
		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();
		
        tempJavaDocUri = directory.toPath().resolve("src/main/java/tempdoc.xml").toUri().toString();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}
	
    @Test
    void testSimpleBeanRefCompletion() throws Exception {
        Editor editor = harness.newEditor(LanguageId.XML, """
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj" depends-on="<*>"></bean>
			</beans>
			""",
			tempJavaDocUri);
        
        List<CompletionItem> completions = editor.getCompletions();
        
        assertEquals(4, completions.size());
        assertTrue(containsCompletion("testBean", completions));
        assertTrue(containsCompletion("homeController", completions));
        assertTrue(containsCompletion("simpleObj", completions));
        assertTrue(containsCompletion("internalResourceViewResolver", completions));
        CompletionItem completionItem = getCompletion("homeController", completions);
        
        editor.apply(completionItem);
        String editorContent = editor.getText();
        
        assertEquals("""
			<?xml version="1.0" encoding="UTF-8"?>
			<beans xmlns="http://www.springframework.org/schema/beans"
				xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
				xsi:schemaLocation="http://www.springframework.org/schema/beans https://www.springframework.org/schema/beans/spring-beans.xsd">
				
				<!-- Root Context: defines shared resources visible to all other web components -->
				<bean id="simpleObj" class="u.t.r.SimpleObj" depends-on="homeController<*>"></bean>
			</beans>
			""",
        	editorContent);
    }

	private CompletionItem getCompletion(String completionLabel, List<CompletionItem> completions) {
		for (CompletionItem completionItem : completions) {
			if (completionItem.getLabel().equals(completionLabel)) return completionItem;
		}
		
		return null;
	}

	private boolean containsCompletion(String completionLabel, List<CompletionItem> completions) {
		for (CompletionItem completionItem : completions) {
			if (completionItem.getLabel().equals(completionLabel)) return true;
		}
		
		return false;
	}

}
