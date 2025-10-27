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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Location;
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
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
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
public class NamedReferencesProviderTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;
	private IJavaProject project;
	private Bean bean1;

	private String tempJavaDocUri1;
	private String tempJavaDocUri;
	private String tempJavaDocUri2;
	private Location locationNamedAnnotation1;
	private Location locationNamedAnnotation2;
	private Bean bean2;
	private Location point1NamedAnnotationLocation;

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

        point1NamedAnnotationLocation = new Location(tempJavaDocUri1, new Range(new Position(20, 10), new Position(20, 20)));
		AnnotationMetadata point1Metadata = new AnnotationMetadata("jakarta.inject.Named", false, null, Map.of("value", new AnnotationAttributeValue[] {new AnnotationAttributeValue("namedAtPoint1", point1NamedAnnotationLocation)}));

		InjectionPoint point1 = new InjectionPoint("point1", "type1", null, new AnnotationMetadata[] {point1Metadata});
        
        bean1 = new Bean("bean1", "type1", new Location(tempJavaDocUri1, new Range(new Position(1,1), new Position(1, 20))), new InjectionPoint[] {point1}, null, new AnnotationMetadata[] {annotationBean1}, false, "symbolLabel");
		bean2 = new Bean("bean2", "type2", new Location(tempJavaDocUri2, new Range(new Position(1,1), new Position(1, 20))), null, null, new AnnotationMetadata[] {annotationBean2}, false, "symbolLabel");
		
		springIndex.updateBeans(project.getElementName(), new Bean[] {bean1, bean2});
	}
	
	@Test
	public void testNamedRefersToNamedBean() throws Exception {
        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import jakarta.inject.Named;

				@Named("na<*>med1")
				public class TestDependsOnClass {
				}""", tempJavaDocUri);
		
		List<? extends Location> references = editor.getReferences();
		assertEquals(1, references.size());
		
		Location foundLocation = references.get(0);
		assertEquals(locationNamedAnnotation1, foundLocation);
	}

	@Test
	public void testNamedRefersToNamedBeanWithConcatenatedString() throws Exception {
        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import jakarta.inject.Named;

				@Named("na" + "m<*>ed1")
				public class TestDependsOnClass {
				}""", tempJavaDocUri);
		
		List<? extends Location> references = editor.getReferences();
		assertEquals(1, references.size());
		
		Location foundLocation = references.get(0);
		assertEquals(locationNamedAnnotation1, foundLocation);
	}

	@Test
	public void testNamedNotRefersToPureSpringBean() throws Exception {
        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import jakarta.inject.Named;

				@Named("be<*>an1")
				public class TestDependsOnClass {
				}""", tempJavaDocUri);
		
		List<? extends Location> references = editor.getReferences();
		assertNull(references);
	}

	@Test
	public void testNamedRefersToNamedInjectionPoints() throws Exception {
        Editor editor = harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import jakarta.inject.Named;

				@Named("namedAt<*>Point1")
				public class TestDependsOnClass {
				}""", tempJavaDocUri);
		
		List<? extends Location> references = editor.getReferences();
		assertEquals(1, references.size());
		
		Location foundLocation = references.get(0);
		assertEquals(point1NamedAnnotationLocation, foundLocation);
	}

}
