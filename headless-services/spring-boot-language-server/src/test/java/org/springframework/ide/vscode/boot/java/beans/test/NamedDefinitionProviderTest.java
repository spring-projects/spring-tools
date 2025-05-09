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
package org.springframework.ide.vscode.boot.java.beans.test;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Location;
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
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationAttributeValue;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
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
public class NamedDefinitionProviderTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;
	private IJavaProject project;
	private Bean bean1;
	private Bean bean2;

	private String tempJavaDocUri1;
	private String tempJavaDocUri2;
	private String tempJavaDocUri;
	private Location locationNamedAnnotation1;
	private Location locationNamedAnnotation2;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-annotation-symbols-for-jakarta-javax/").toURI());

		String projectDir = directory.toURI().toString();
		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();
		
		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);

        tempJavaDocUri = directory.toPath().resolve("src/main/java/org/test/TestDependsOnClass.java").toUri().toString();
        tempJavaDocUri1 = directory.toPath().resolve("src/main/java/org/test/TempClass1.java").toUri().toString();
        tempJavaDocUri2 = directory.toPath().resolve("src/main/java/org/test/TempClass2.java").toUri().toString();

        locationNamedAnnotation1 = new Location(tempJavaDocUri1, new Range(new Position(1, 10), new Position(1, 20)));
        locationNamedAnnotation2 = new Location(tempJavaDocUri2, new Range(new Position(2, 10), new Position(2, 20)));
        
        AnnotationMetadata annotationBean1 = new AnnotationMetadata("jakarta.inject.Named", false, null, Map.of("value", new AnnotationAttributeValue[] {new AnnotationAttributeValue("named1", locationNamedAnnotation1)}));
        AnnotationMetadata annotationBean2 = new AnnotationMetadata("jakarta.inject.Named", false, null, Map.of("value", new AnnotationAttributeValue[] {new AnnotationAttributeValue("named2", locationNamedAnnotation2)}));

        bean1 = new Bean("bean1", "type1", new Location(tempJavaDocUri1, new Range(new Position(1,1), new Position(1, 20))), null, null, new AnnotationMetadata[] {annotationBean1}, false, "symbolLabel");
		bean2 = new Bean("bean2", "type2", new Location(tempJavaDocUri2, new Range(new Position(1,1), new Position(1, 20))), null, null, new AnnotationMetadata[] {annotationBean2}, false, "symbolLabel");
		
		springIndex.updateBeans(project.getElementName(), new Bean[] {bean1, bean2});
	}
	
	@Test
	public void testNamedClassRefersToBeanDefinitionLink() throws Exception {
        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import jakarta.inject.Named;

				@Named("named1")
				public class TestDependsOnClass {
				}""", tempJavaDocUri);
		
		LocationLink expectedLocation = new LocationLink(locationNamedAnnotation1.getUri(),
				locationNamedAnnotation1.getRange(), locationNamedAnnotation1.getRange(),
				null);

		editor.assertDefinitionLinkTargets("named1", List.of(expectedLocation));
	}

	@Test
	public void testNamedDependencyRefersToBeanDefinitionLink() throws Exception {
        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.stereotype.Component;
				import jakarta.inject.Named;

				@Component
				public class TestDependsOnClass {
				
					public void setDependency(@Named("named1") Object bean) {
					}
				
				}""", tempJavaDocUri);
		
		LocationLink expectedLocation = new LocationLink(locationNamedAnnotation1.getUri(),
				locationNamedAnnotation1.getRange(), locationNamedAnnotation1.getRange(),
				null);

		editor.assertDefinitionLinkTargets("named1", List.of(expectedLocation));
	}

	@Test
	public void testNamedDependencyRefersToNonExistingBeanDefinitionLink() throws Exception {
        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

        		import org.springframework.stereotype.Component;
				import jakarta.inject.Named;

				@Component
				public class TestDependsOnClass {
				
					public void setDependency(@Named("bean1") Object bean1) {
					}
				
				}""", tempJavaDocUri1);
		
		editor.assertNoDefinitionLinkTargets("bean1");
	}

}
