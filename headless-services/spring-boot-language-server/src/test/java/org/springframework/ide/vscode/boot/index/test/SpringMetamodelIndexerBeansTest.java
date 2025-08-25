/*******************************************************************************
 * Copyright (c) 2023, 2025 VMware, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     VMware, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.index.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationAttributeValue;
import org.springframework.ide.vscode.commons.protocol.spring.AnnotationMetadata;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.BeanMethodContainerElement;
import org.springframework.ide.vscode.commons.protocol.spring.DefaultValues;
import org.springframework.ide.vscode.commons.protocol.spring.DocumentElement;
import org.springframework.ide.vscode.commons.protocol.spring.InjectionPoint;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.commons.protocol.spring.SymbolElement;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(SymbolProviderTestConf.class)
public class SpringMetamodelIndexerBeansTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private SpringSymbolIndex indexer;

	private File directory;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-spring-indexing/").toURI());

		String projectDir = directory.toURI().toString();

		// trigger project creation
		projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(55555555, TimeUnit.SECONDS);
	}

	@Test
	void testSpringIndexExists() throws Exception {
		assertNotNull(springIndex);
	}

	@Test
	void testBeansNameAndTypeFromBeanAnnotatedMethod() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "bean1");

		assertEquals(1, beans.length);
		assertEquals("bean1", beans[0].getName());
		assertEquals("org.test.BeanClass1", beans[0].getType());
		
		assertFalse(beans[0].isConfiguration());
	}

	@Test
	void testBeansDefintionLocationFromBeanAnnotatedMethod() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "bean1");

		String docUri = directory.toPath().resolve("src/main/java/org/test/MainClass.java").toUri().toString();
		Location location = new Location(docUri, new Range(new Position(15, 1), new Position(15, 6)));
		assertEquals(location, beans[0].getLocation());
	}

	@Test
	void testBeansNameAndTypeFromComponentAnnotatedClassExists() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "constructorInjectionService");

		assertEquals(1, beans.length);
		assertEquals("constructorInjectionService", beans[0].getName());
		assertEquals("org.test.injections.ConstructorInjectionService", beans[0].getType());
	}

	@Test
	void testBeansDefintionLocationFromComponentAnnotatedClass() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "constructorInjectionService");

		String docUri = directory.toPath().resolve("src/main/java/org/test/injections/ConstructorInjectionService.java").toUri().toString();
		Location location = new Location(docUri, new Range(new Position(6, 0), new Position(6, 8)));
		assertEquals(location, beans[0].getLocation());
	}

	@Test
	void testBeansNameAndTypeFromConfigurationAnnotatedClassExists() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "configurationWithoutInjection");

		assertEquals(1, beans.length);
		assertEquals("configurationWithoutInjection", beans[0].getName());
		assertEquals("org.test.injections.ConfigurationWithoutInjection", beans[0].getType());
		assertTrue(beans[0].isConfiguration());
	}

	@Test
	void testBeansDefinitionLocationFromConfigurationAnnotatedClass() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "configurationWithoutInjection");
		assertEquals(1, beans.length);

		String docUri = directory.toPath().resolve("src/main/java/org/test/injections/ConfigurationWithoutInjection.java").toUri().toString();
		assertEquals(docUri, beans[0].getLocation().getUri());
		
		List<SpringIndexElement> children = beans[0].getChildren();
		assertEquals(1, children.size());
		assertEquals("beanWithoutInjections", ((Bean) children.get(0)).getName());
	}
	
	@Test
	void testBeanNoInjectionPointsFromBeanAnnotatedMethod() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "beanWithoutInjections");
		assertEquals(1, beans.length);

		InjectionPoint[] injectionPoints = beans[0].getInjectionPoints();
		assertEquals(0, injectionPoints.length);
		assertSame(DefaultValues.EMPTY_INJECTION_POINTS, injectionPoints);
	}
	
	@Test
	void testBeanInjectionPointsFromBeanAnnotatedMethod() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "manualBeanWithConstructor");
		assertEquals(1, beans.length);

		String docUri = directory.toPath().resolve("src/main/java/org/test/injections/ConfigurationWithInjections.java").toUri().toString();

		InjectionPoint[] injectionPoints = beans[0].getInjectionPoints();
		assertEquals(2, injectionPoints.length);
		
		assertEquals("bean1", injectionPoints[0].getName());
		assertEquals("org.test.BeanClass1", injectionPoints[0].getType());
		Location ip1Location = new Location(docUri, new Range(new Position(12, 73), new Position(12, 78)));
		assertEquals(ip1Location, injectionPoints[0].getLocation());
		
		assertEquals("bean2", injectionPoints[1].getName());
		assertEquals("org.test.BeanClass2", injectionPoints[1].getType());
		Location ip2Location = new Location(docUri, new Range(new Position(12, 91), new Position(12, 96)));
		assertEquals(ip2Location, injectionPoints[1].getLocation());
	}

	@Test
	void testBeanInjectionPointsFromConstructor() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "constructorInjectionService");
		assertEquals(1, beans.length);

		String docUri = directory.toPath().resolve("src/main/java/org/test/injections/ConstructorInjectionService.java").toUri().toString();

		InjectionPoint[] injectionPoints = beans[0].getInjectionPoints();
		assertEquals(2, injectionPoints.length);
		
		assertEquals("bean1", injectionPoints[0].getName());
		assertEquals("org.test.BeanClass1", injectionPoints[0].getType());
		Location ip1Location = new Location(docUri, new Range(new Position(12, 47), new Position(12, 52)));
		assertEquals(ip1Location, injectionPoints[0].getLocation());
		
		assertEquals("bean2", injectionPoints[1].getName());
		assertEquals("org.test.BeanClass2", injectionPoints[1].getType());
		Location ip2Location = new Location(docUri, new Range(new Position(12, 65), new Position(12, 70)));
		assertEquals(ip2Location, injectionPoints[1].getLocation());
	}

	@Test
	void testSetterInjectionPointsFromConstructor() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "setterInjectionService");
		assertEquals(1, beans.length);

		String docUri = directory.toPath().resolve("src/main/java/org/test/injections/SetterInjectionService.java").toUri().toString();

		InjectionPoint[] injectionPoints = beans[0].getInjectionPoints();
		assertEquals(2, injectionPoints.length);
		
		assertEquals("bean1", injectionPoints[0].getName());
		assertEquals("org.test.BeanClass1", injectionPoints[0].getType());
		assertEquals(new Location(docUri, new Range(new Position(21, 33), new Position(21, 38))), injectionPoints[0].getLocation());
		
		AnnotationMetadata[] point1Annotations = injectionPoints[0].getAnnotations();
		assertEquals(2, point1Annotations.length);
		
		assertEquals(Annotations.AUTOWIRED, point1Annotations[0].getAnnotationType());
		assertEquals(new Location(docUri, new Range(new Position(19, 1), new Position(19, 11))), point1Annotations[0].getLocation());
		assertEquals(0, point1Annotations[0].getAttributes().size());
		
		assertEquals(Annotations.QUALIFIER, point1Annotations[1].getAnnotationType());
		assertEquals(new Location(docUri, new Range(new Position(20, 1), new Position(20, 41))), point1Annotations[1].getLocation());
		assertEquals(1, point1Annotations[1].getAttributes().size());
		assertEquals(1, point1Annotations[1].getAttributes().get("value").length);
		assertEquals("setter-injection-qualifier", point1Annotations[1].getAttributes().get("value")[0].getName());
		assertEquals(new Location(docUri, new Range(new Position(20, 12), new Position(20, 40))), point1Annotations[1].getAttributes().get("value")[0].getLocation());
		
		assertEquals("bean2", injectionPoints[1].getName());
		assertEquals("org.test.BeanClass2", injectionPoints[1].getType());
		assertEquals(new Location(docUri, new Range(new Position(26, 83), new Position(26, 88))), injectionPoints[1].getLocation());

		AnnotationMetadata[] point2Annotations = injectionPoints[1].getAnnotations();
		assertEquals(2, point2Annotations.length);
		assertEquals(Annotations.AUTOWIRED, point2Annotations[0].getAnnotationType());
		assertEquals(new Location(docUri, new Range(new Position(25, 1), new Position(25, 11))), point2Annotations[0].getLocation());
		assertEquals(0, point2Annotations[0].getAttributes().size());

		assertEquals(Annotations.QUALIFIER, point2Annotations[1].getAnnotationType());
		assertEquals(new Location(docUri, new Range(new Position(26, 22), new Position(26, 71))), point2Annotations[1].getLocation());
		assertEquals(1, point2Annotations[1].getAttributes().size());
		assertEquals(1, point2Annotations[1].getAttributes().get("value").length);
		assertEquals("setter-injection-qualifier-on-param", point2Annotations[1].getAttributes().get("value")[0].getName());
		assertEquals(new Location(docUri, new Range(new Position(26, 33), new Position(26, 70))), point2Annotations[1].getAttributes().get("value")[0].getLocation());
	}

	@Test
	void testBeanInjectionPointsFromAutowiredFields() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "autowiredInjectionService");
		assertEquals(1, beans.length);

		String docUri = directory.toPath().resolve("src/main/java/org/test/injections/AutowiredInjectionService.java").toUri().toString();

		InjectionPoint[] injectionPoints = beans[0].getInjectionPoints();
		assertEquals(2, injectionPoints.length);
		
		assertEquals("bean1", injectionPoints[0].getName());
		assertEquals("org.test.BeanClass1", injectionPoints[0].getType());
		Location ip1Location = new Location(docUri, new Range(new Position(12, 20), new Position(12, 25)));
		assertEquals(ip1Location, injectionPoints[0].getLocation());
		
		assertEquals("bean2", injectionPoints[1].getName());
		assertEquals("org.test.BeanClass2", injectionPoints[1].getType());
		Location ip2Location = new Location(docUri, new Range(new Position(16, 20), new Position(16, 25)));
		assertEquals(ip2Location, injectionPoints[1].getLocation());
	}
	
	@Test
	void testBeanFromSpringDataRepository() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "customerRepository");

		assertEquals(1, beans.length);
		Bean repositoryBean = beans[0];

		assertEquals("customerRepository", repositoryBean.getName());
		assertEquals("org.test.springdata.CustomerRepository", repositoryBean.getType());
		
		assertTrue(repositoryBean.isTypeCompatibleWith("org.test.springdata.CustomerRepository"));
		assertTrue(repositoryBean.isTypeCompatibleWith("org.springframework.data.repository.CrudRepository"));

		InjectionPoint[] injectionPoints = repositoryBean.getInjectionPoints();
		assertEquals(0, injectionPoints.length);
		assertSame(DefaultValues.EMPTY_INJECTION_POINTS, injectionPoints);
	}

	@Test
	void testBeansWithSupertypes() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "beanWithSupertypes");
		assertEquals(1, beans.length);
		
		assertTrue(beans[0].isTypeCompatibleWith("java.lang.Object"));
		assertTrue(beans[0].isTypeCompatibleWith("org.test.supertypes.AbstractBeanWithSupertypes"));
		assertTrue(beans[0].isTypeCompatibleWith("org.test.supertypes.Interface1OfBeanWithSupertypes"));
		assertTrue(beans[0].isTypeCompatibleWith("org.test.supertypes.Interface2OfBeanWithSupertypes"));
		assertTrue(beans[0].isTypeCompatibleWith("org.test.supertypes.InterfaceOfAbstractBean"));
		assertTrue(beans[0].isTypeCompatibleWith("org.test.supertypes.BaseClassOfAbstractBeanWithSupertypes"));
		assertTrue(beans[0].isTypeCompatibleWith("org.test.BeanWithSupertypes"));
		
		assertFalse(beans[0].isTypeCompatibleWith("java.lang.String"));
		assertFalse(beans[0].isTypeCompatibleWith("java.util.Comparator"));
	}
	
	@Test
	void testAnnotationMetadataFromComponentBeans() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "mainClass");
		assertEquals(1, beans.length);
		
		Bean mainClassBean = beans[0];
		AnnotationMetadata[] annotations = mainClassBean.getAnnotations();
		
		assertEquals(4, annotations.length);
		assertEquals("org.springframework.boot.autoconfigure.SpringBootApplication", annotations[0].getAnnotationType());
		assertFalse(annotations[0].isMetaAnnotation());
		
		assertEquals("org.springframework.boot.SpringBootConfiguration", annotations[1].getAnnotationType());
		assertTrue(annotations[1].isMetaAnnotation());

		assertEquals("org.springframework.context.annotation.Configuration", annotations[2].getAnnotationType());
		assertTrue(annotations[2].isMetaAnnotation());

		assertEquals("org.springframework.stereotype.Component", annotations[3].getAnnotationType());
		assertTrue(annotations[3].isMetaAnnotation());
	}

	@Test
	void testAnnotationMetadataFromBeanMethodBean() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "bean3");
		assertEquals(1, beans.length);
		
		Bean mainClassBean = beans[0];
		AnnotationMetadata[] annotations = mainClassBean.getAnnotations();
		
		assertEquals(3, annotations.length);
		
		AnnotationMetadata beanAnnotation = annotations[0];
		assertEquals("org.springframework.context.annotation.Bean", beanAnnotation.getAnnotationType());
		assertFalse(annotations[0].isMetaAnnotation());
		assertEquals(0, annotations[0].getAttributes().size());
		assertEquals(new Location(mainClassBean.getLocation().getUri(), new Range(new Position(25, 1), new Position(25, 6))), beanAnnotation.getLocation());
		
		AnnotationMetadata qualifierAnnotation = annotations[1];
		assertEquals("org.springframework.beans.factory.annotation.Qualifier", qualifierAnnotation.getAnnotationType());
		assertEquals(new Location(mainClassBean.getLocation().getUri(), new Range(new Position(26, 1), new Position(26, 25))), qualifierAnnotation.getLocation());

		Map<String, AnnotationAttributeValue[]> attributes = qualifierAnnotation.getAttributes();
		assertEquals(1, attributes.size());
		assertTrue(attributes.containsKey("value"));
		
		AnnotationAttributeValue[] valueAttributes = attributes.get("value");
		assertEquals(1, valueAttributes.length);
		assertEquals("qualifier1", valueAttributes[0].getName());
		Location expectedValueAttributeLocation = new Location(mainClassBean.getLocation().getUri(), new Range(new Position(26, 12), new Position(26, 24)));
		assertEquals(expectedValueAttributeLocation, valueAttributes[0].getLocation());

		AnnotationMetadata profileAnnotation = annotations[2];
		assertEquals("org.springframework.context.annotation.Profile", profileAnnotation.getAnnotationType());
		assertFalse(profileAnnotation.isMetaAnnotation());
		assertEquals(new Location(mainClassBean.getLocation().getUri(), new Range(new Position(27, 1), new Position(27, 41))), profileAnnotation.getLocation());
		
		attributes = profileAnnotation.getAttributes();
		assertEquals(1, attributes.size());

		AnnotationAttributeValue[] profileValueAttributes = attributes.get("value");
		assertEquals(2, profileValueAttributes.length);
		
		assertEquals("testprofile", profileValueAttributes[0].getName());
		assertEquals("testprofile2", profileValueAttributes[1].getName());
	
		Location profileValueLocation1 = new Location(mainClassBean.getLocation().getUri(), new Range(new Position(27, 11), new Position(27, 24)));
		Location profileValueLocation2 = new Location(mainClassBean.getLocation().getUri(), new Range(new Position(27, 25), new Position(27, 39)));

		assertEquals(profileValueLocation1, profileValueAttributes[0].getLocation());
		assertEquals(profileValueLocation2, profileValueAttributes[1].getLocation());
	}
	
	@Test
	void testAnnotationMetadataFromBeanMethodWithInjectionPointAnnotations() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "beanWithAnnotationsOnInjectionPoints");
		assertEquals(1, beans.length);
		
		Bean bean = beans[0];
		AnnotationMetadata[] annotations = bean.getAnnotations();
		
		assertEquals(2, annotations.length);
		
		AnnotationMetadata beanAnnotation = annotations[0];
		AnnotationMetadata dependsonAnnotation = annotations[1];

		assertEquals("org.springframework.context.annotation.Bean", beanAnnotation.getAnnotationType());
		assertEquals(new Location(bean.getLocation().getUri(), new Range(new Position(16, 1), new Position(16, 6))), beanAnnotation.getLocation());
		
		assertEquals("org.springframework.context.annotation.DependsOn", dependsonAnnotation.getAnnotationType());
		
		Map<String, AnnotationAttributeValue[]> dependsOnAttributes = dependsonAnnotation.getAttributes();
		assertEquals(1, dependsOnAttributes.size());
		AnnotationAttributeValue[] dependsOnValueAttribute = dependsOnAttributes.get("value");
		assertEquals(2, dependsOnValueAttribute.length);
		assertEquals("bean1", dependsOnValueAttribute[0].getName());
		assertEquals("bean2", dependsOnValueAttribute[1].getName());
		assertEquals(new Location(bean.getLocation().getUri(), new Range(new Position(17, 13), new Position(17, 20))), dependsOnValueAttribute[0].getLocation());
		assertEquals(new Location(bean.getLocation().getUri(), new Range(new Position(17, 22), new Position(17, 29))), dependsOnValueAttribute[1].getLocation());
		
		InjectionPoint[] injectionPoints = bean.getInjectionPoints();
		assertEquals(2, injectionPoints.length);
		
		AnnotationMetadata[] annotationsFromPoint1 = injectionPoints[0].getAnnotations();
		AnnotationMetadata[] annotationsFromPoint2 = injectionPoints[1].getAnnotations();
		
		assertEquals(1, annotationsFromPoint1.length);
		assertEquals(1, annotationsFromPoint2.length);
		
		assertEquals("org.springframework.beans.factory.annotation.Qualifier", annotationsFromPoint1[0].getAnnotationType());
		Map<String, AnnotationAttributeValue[]> attributesFromParam1 = annotationsFromPoint1[0].getAttributes();
		assertEquals(1, attributesFromParam1.size());
		AnnotationAttributeValue[] qualifier1ValueAttributes = attributesFromParam1.get("value");
		assertEquals(1, qualifier1ValueAttributes.length);
		assertEquals("q1", qualifier1ValueAttributes[0].getName());
		assertEquals(new Location(bean.getLocation().getUri(), new Range(new Position(18, 84), new Position(18, 88))), qualifier1ValueAttributes[0].getLocation());
		
		assertEquals("org.springframework.beans.factory.annotation.Qualifier", annotationsFromPoint2[0].getAnnotationType());
		Map<String, AnnotationAttributeValue[]> attributesFromParam2 = annotationsFromPoint2[0].getAttributes();
		assertEquals(1, attributesFromParam2.size());
		AnnotationAttributeValue[] qualifier2ValueAttributes = attributesFromParam2.get("value");
		assertEquals(1, qualifier2ValueAttributes.length);
		assertEquals("q2", qualifier2ValueAttributes[0].getName());
		assertEquals(new Location(bean.getLocation().getUri(), new Range(new Position(18, 119), new Position(18, 123))), qualifier2ValueAttributes[0].getLocation());
	}
	
	@Test
	void testAnnotationMetadataFromComponentClass() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "configurationWithInjectionsAndAnnotations");
		assertEquals(1, beans.length);
		
		Bean bean = beans[0];

		InjectionPoint[] injectionPoints = bean.getInjectionPoints();
		assertEquals(0, injectionPoints.length);
		
		AnnotationMetadata[] annotations = bean.getAnnotations();
		
		assertEquals(4, annotations.length);
		
		AnnotationMetadata configurationAnnotation= annotations[0];
		AnnotationMetadata qualifierAnnotation = annotations[1];
		AnnotationMetadata runtimeHintsAnnotation = annotations[2];
		AnnotationMetadata componentMetaAnnotation = annotations[3];

		assertEquals("org.springframework.context.annotation.Configuration", configurationAnnotation.getAnnotationType());
		assertEquals("org.springframework.beans.factory.annotation.Qualifier", qualifierAnnotation.getAnnotationType());
		assertEquals("org.springframework.context.annotation.ImportRuntimeHints", runtimeHintsAnnotation.getAnnotationType());
		assertEquals("org.springframework.stereotype.Component", componentMetaAnnotation.getAnnotationType());
		
		Map<String, AnnotationAttributeValue[]> qualifierAttributes = qualifierAnnotation.getAttributes();
		assertEquals(1, qualifierAttributes.size());
		
		AnnotationAttributeValue[] valueAttributes = qualifierAttributes.get("value");
		assertNotNull(valueAttributes);
		assertEquals(1, valueAttributes.length);
		
		Location qualifierValueLocation = new Location(bean.getLocation().getUri(), new Range(new Position(12, 11), new Position(12, 22)));
		assertEquals("qualifier", valueAttributes[0].getName());
		assertEquals(qualifierValueLocation, valueAttributes[0].getLocation());
		
		Map<String, AnnotationAttributeValue[]> runtimeHintsAttributes = runtimeHintsAnnotation.getAttributes();
		assertEquals(1, runtimeHintsAttributes.size());
		AnnotationAttributeValue[] runtimeHintsValueAttribute = runtimeHintsAttributes.get("value");
		assertNotNull(runtimeHintsValueAttribute);
		assertEquals(1, runtimeHintsValueAttribute.length);

		Location runtimeHintsValueLocation = new Location(bean.getLocation().getUri(), new Range(new Position(13, 20), new Position(13, 52)));
		assertEquals("org.test.injections.DummyRuntimeHintsRegistrar", runtimeHintsValueAttribute[0].getName());
		assertEquals(runtimeHintsValueLocation, runtimeHintsValueAttribute[0].getLocation());
		
		// child bean
		List<SpringIndexElement> children = bean.getChildren();
		assertEquals(1, children.size());
		assertEquals("beanWithAnnotationsOnInjectionPoints", ((Bean) children.get(0)).getName());
	}
	
	@Test
	void testAnnotationMetadataFromInjectionPointsFromAutowiredFields() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "autowiredInjectionService");
		assertEquals(1, beans.length);

		InjectionPoint[] injectionPoints = beans[0].getInjectionPoints();
		assertEquals(2, injectionPoints.length);
		
		AnnotationMetadata[] annotationsPoint1 = injectionPoints[0].getAnnotations();
		assertEquals(1, annotationsPoint1.length);
		assertEquals("org.springframework.beans.factory.annotation.Autowired", annotationsPoint1[0].getAnnotationType());
		assertFalse(annotationsPoint1[0].isMetaAnnotation());
		assertEquals(0, annotationsPoint1[0].getAttributes().size());

		AnnotationMetadata[] annotationsPoint2 = injectionPoints[1].getAnnotations();
		assertEquals(2, annotationsPoint2.length);
		
		AnnotationMetadata autowiredFromPoint2 = annotationsPoint2[0];
		assertEquals("org.springframework.beans.factory.annotation.Autowired", autowiredFromPoint2.getAnnotationType());
		assertFalse(autowiredFromPoint2.isMetaAnnotation());
		assertEquals(0, autowiredFromPoint2.getAttributes().size());

		AnnotationMetadata qualifierFromPoint2 = annotationsPoint2[1];
		assertEquals("org.springframework.beans.factory.annotation.Qualifier", qualifierFromPoint2.getAnnotationType());
		assertFalse(qualifierFromPoint2.isMetaAnnotation());
		assertEquals(1, qualifierFromPoint2.getAttributes().size());
		
		Map<String, AnnotationAttributeValue[]> qualifierAttributes = qualifierFromPoint2.getAttributes();
		AnnotationAttributeValue[] qualifierAttributeValues = qualifierAttributes.get("value");
		assertEquals(1, qualifierAttributeValues.length);
		assertEquals("qual1", qualifierAttributeValues[0].getName());
		assertEquals(new Location(beans[0].getLocation().getUri(), new Range(new Position(15, 12), new Position(15, 19))), qualifierAttributeValues[0].getLocation());
	}
	
	@Test
	void testAnnotationMetadataFromSpringDataRepository() {
		Bean[] beans = springIndex.getBeansWithName("test-spring-indexing", "customerRepository");

		assertEquals(1, beans.length);
		
		AnnotationMetadata[] annotations = beans[0].getAnnotations();
		assertEquals(2, annotations.length);
		
		AnnotationMetadata qualifierAnnotation = annotations[0];
		AnnotationMetadata profileAnnotation = annotations[1];
		
		assertEquals("org.springframework.beans.factory.annotation.Qualifier", qualifierAnnotation.getAnnotationType());
		assertFalse(qualifierAnnotation.isMetaAnnotation());
		
		Map<String, AnnotationAttributeValue[]> qualifierAttributes = qualifierAnnotation.getAttributes();
		assertEquals(1, qualifierAttributes.size());
		AnnotationAttributeValue[] qualifierAttributeValues = qualifierAttributes.get("value");
		assertEquals(1, qualifierAttributeValues.length);
		assertEquals("repoQualifier", qualifierAttributeValues[0].getName());
		assertEquals(new Location(beans[0].getLocation().getUri(), new Range(new Position(8, 11), new Position(8, 26))), qualifierAttributeValues[0].getLocation());
		
		assertEquals("org.springframework.context.annotation.Profile", profileAnnotation.getAnnotationType());
		assertFalse(profileAnnotation.isMetaAnnotation());
		
		Map<String, AnnotationAttributeValue[]> profileAttributes = profileAnnotation.getAttributes();
		assertEquals(1, profileAttributes.size());
		AnnotationAttributeValue[] profileAttributeValues = profileAttributes.get("value");
		assertEquals(2, profileAttributeValues.length);

		assertEquals("prof1", profileAttributeValues[0].getName());
		assertEquals(new Location(beans[0].getLocation().getUri(), new Range(new Position(9, 10), new Position(9, 17))), profileAttributeValues[0].getLocation());

		assertEquals("prof2", profileAttributeValues[1].getName());
		assertEquals(new Location(beans[0].getLocation().getUri(), new Range(new Position(9, 19), new Position(9, 26))), profileAttributeValues[1].getLocation());
	}
	
	@Test
	void testBeanMethodsWithoutConfiguration() {
		String docUri = directory.toPath().resolve("src/main/java/org/test/BeanMethodsWithoutConfiguration.java").toUri().toString();
		
		DocumentElement document = springIndex.getDocument(docUri);
        List<BeanMethodContainerElement> docChildren = SpringMetamodelIndex.getNodesOfType(BeanMethodContainerElement.class, List.of(document));
		assertEquals(1, docChildren.size());
		
		BeanMethodContainerElement container = docChildren.get(0);
		assertEquals(docUri, container.getLocation().getUri());
		assertEquals("org.test.BeanMethodsWithoutConfiguration", container.getType());
		assertFalse(container instanceof SymbolElement);
		
		List<SpringIndexElement> beans = container.getChildren();
		assertEquals(2, beans.size());
		
		Bean bean1 = (Bean) beans.get(0);
		Bean bean2 = (Bean) beans.get(1);
		
		assertEquals("beanWithoutConfig", bean1.getName());
		assertEquals("beanWithoutConfig2", bean2.getName());
		
		Bean[] beansFound = springIndex.getBeansWithName("test-spring-indexing", "beanWithoutConfig");
		assertEquals(1, beansFound.length);
		assertSame(bean1, beansFound[0]);
	}


	
}
