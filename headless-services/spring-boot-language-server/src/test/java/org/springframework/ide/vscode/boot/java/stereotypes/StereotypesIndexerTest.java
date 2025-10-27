/*******************************************************************************
 * Copyright (c) 2025 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.java.stereotypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.catalog.StereotypeDefinition;
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
import org.springframework.ide.vscode.boot.java.commands.CachedSpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.StructureViewUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
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
public class StereotypesIndexerTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private StereotypeCatalogRegistry stereotypeCatalogRegistry;

	private File directory;
	private IJavaProject project;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		directory = new File(ProjectsHarness.class.getResource("/test-projects/test-stereotypes-support/").toURI());
		String projectDir = directory.toURI().toString();

		project = projectFinder.find(new TextDocumentIdentifier(projectDir)).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

    @Test
    void testOverallExistenceOfTypeElements() throws Exception {
    	List<StereotypeClassElement> stereotypeNodes = springIndex.getNodesOfType(StereotypeClassElement.class);
    	assertFalse(stereotypeNodes.isEmpty());
    }
    
    @Test
    void testPackageElement() throws Exception {
    	// check package elements for package-info
    	List<StereotypePackageElement> packages = springIndex.getNodesOfType(StereotypePackageElement.class);
    	assertEquals(2, packages.size()); // package nodes are created for package declarations in package-info.java files only
    	
    	StereotypePackageElement packageElement1 = packages.stream().filter(packageElement -> packageElement.getPackageName().equals(("example"))).findFirst().get();
    	StereotypePackageElement packageElement2 = packages.stream().filter(packageElement -> packageElement.getPackageName().equals(("example.application"))).findFirst().get();
    	
    	Set<String> annotationTypes1 = packageElement1.getAnnotationTypes();
    	assertEquals(1, annotationTypes1.size());
    	assertTrue(annotationTypes1.contains("org.jmolecules.architecture.hexagonal.Port"));
    	assertTrue(packageElement1.isAnnotatedWith("org.jmolecules.architecture.hexagonal.Port"));
    	assertFalse(packageElement1.isAnnotatedWith("notFound"));
    	
    	Set<String> annotationTypes2 = packageElement2.getAnnotationTypes();
    	assertEquals(1, annotationTypes2.size());
    	assertTrue(annotationTypes2.contains("org.jmolecules.architecture.hexagonal.Application"));
    	assertTrue(packageElement2.isAnnotatedWith("org.jmolecules.architecture.hexagonal.Application"));
    	assertFalse(packageElement2.isAnnotatedWith("notFound"));
    	
    	// check type elements for package-info
    	List<StereotypeClassElement> packagesTypeElements = springIndex.getNodesOfType(StereotypeClassElement.class);
    	StereotypeClassElement packageClassElement1 = packagesTypeElements.stream().filter(packageType -> packageType.getType().equals(("example.package-info"))).findFirst().get();
    	StereotypeClassElement packageClassElement2 = packagesTypeElements.stream().filter(packageType -> packageType.getType().equals(("example.application.package-info"))).findFirst().get();
    	
    	Set<String> packageTypeAnnotations1 = packageClassElement1.getAnnotationTypes();
    	assertEquals(1, packageTypeAnnotations1.size());
    	assertTrue(packageTypeAnnotations1.contains("org.jmolecules.architecture.hexagonal.Port"));
    	assertTrue(packageClassElement1.isAnnotatedWith("org.jmolecules.architecture.hexagonal.Port"));

    	Set<String> packageTypeAnnotations2 = packageClassElement2.getAnnotationTypes();
    	assertEquals(1, packageTypeAnnotations2.size());
    	assertTrue(packageTypeAnnotations2.contains("org.jmolecules.architecture.hexagonal.Application"));
    	assertTrue(packageClassElement2.isAnnotatedWith("org.jmolecules.architecture.hexagonal.Application"));
    }
    
    @Test
    void testInnerClassElements() throws Exception {
    	List<StereotypeClassElement> stereotypeNodes = springIndex.getNodesOfType(StereotypeClassElement.class);
    	
    	List<String> list = stereotypeNodes.stream()
    		.filter(node -> node.getType().startsWith("example.application.TypeWithInnerClass"))
    		.map(node -> node.getType())
    		.toList();
    	
    	assertEquals(3, list.size());
    	assertTrue(list.contains("example.application.TypeWithInnerClass"));
    	assertTrue(list.contains("example.application.TypeWithInnerClass$InnerClass"));
    	assertTrue(list.contains("example.application.TypeWithInnerClass$InnerClass$InnerClassInInnerClass"));
    }
    
    @Test
    void testInnerRecordElements() throws Exception {
    	List<StereotypeClassElement> stereotypeNodes = springIndex.getNodesOfType(StereotypeClassElement.class);
    	
    	List<String> list = stereotypeNodes.stream()
    		.filter(node -> node.getType().startsWith("example.application.ClassWithInnerRecordImplementingInterface"))
    		.map(node -> node.getType())
    		.toList();
    	
    	assertEquals(2, list.size());
    	assertTrue(list.contains("example.application.ClassWithInnerRecordImplementingInterface"));
    	assertTrue(list.contains("example.application.ClassWithInnerRecordImplementingInterface$InnerRecord"));
    	
    	StereotypeClassElement recordElement = stereotypeNodes.stream()
        		.filter(node -> node.getType().startsWith("example.application.ClassWithInnerRecordImplementingInterface$InnerRecord"))
        		.findAny().get();
    	
    	assertNotNull(recordElement);
    	assertTrue(recordElement.doesImplement("example.application.RandomInterface"));
    }
    
    @Test
    void testLocationInformationForTypeElement() throws Exception {
		String docUri = directory.toPath().resolve("src/main/java/example/application/SampleController.java").toUri().toString();
    	
    	StereotypeClassElement element = springIndex.getNodesOfType(StereotypeClassElement.class).stream()
    			.filter(node -> node.getType().equals("example.application.SampleController"))
    			.findFirst()
    			.get();
    	
    	assertNotNull(element);
    	assertEquals("example.application.SampleController", element.getType());
    	
    	Location location = element.getLocation();
    	assertNotNull(location);
    	assertEquals(docUri, location.getUri());
    	assertEquals(7, location.getRange().getStart().getLine());
    	assertEquals(7, location.getRange().getEnd().getLine());
    }
    
    @Test
    void testMethodElementsForType() throws Exception {
    	StereotypeClassElement element = springIndex.getNodesOfType(StereotypeClassElement.class).stream()
    			.filter(node -> node.getType().equals("example.application.ClassWithMethods"))
    			.findFirst()
    			.get();
    	
    	assertNotNull(element);
    	
    	List<StereotypeMethodElement> methods = element.getMethods();
    	assertEquals(1, methods.size());
    	
    	Optional<StereotypeMethodElement> methodWithoutAnnotations = methods.stream().filter(method -> method.getMethodName().equals("methodWithoutAnnotations")).findAny();
    	assertFalse(methodWithoutAnnotations.isPresent());

    	StereotypeMethodElement methodWithAnnotations = methods.stream().filter(method -> method.getMethodName().equals("methodWithAnnotations")).findAny().get();
    	Set<String> annotationTypes = methodWithAnnotations.getAnnotationTypes();
		assertEquals(4, annotationTypes.size());
    	assertTrue(annotationTypes.contains(Annotations.SPRING_GET_MAPPING));
    	assertTrue(annotationTypes.contains(Annotations.SPRING_REQUEST_MAPPING));
    	assertTrue(methodWithAnnotations.isAnnotatedWith(Annotations.SPRING_GET_MAPPING));
    	assertTrue(methodWithAnnotations.isAnnotatedWith(Annotations.SPRING_REQUEST_MAPPING));
    	
    	assertFalse(methodWithAnnotations.isAnnotatedWith(Annotations.BOOT_APP));
    	
    	assertEquals("methodWithAnnotations", methodWithAnnotations.getMethodName());
    	assertEquals("example.application.ClassWithMethods.methodWithAnnotations(java.lang.String) : V", methodWithAnnotations.getMethodSignature());
    	assertEquals("ClassWithMethods.methodWithAnnotations(String) : void", methodWithAnnotations.getMethodLabel());
    }
    
    @Test
    void testIdentifyMainApplicationPackage() throws Exception {
    	StereotypePackageElement mainPackage = StructureViewUtil.identifyMainApplicationPackage(project, new CachedSpringMetamodelIndex(springIndex));
    	assertEquals("example.application", mainPackage.getPackageName());
    }
    
	@Test
	void testFromPackageIndexBasedFactory() {
		
		var catalog = this.stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, project, new CachedSpringMetamodelIndex(springIndex));
		factory.registerStereotypeDefinitions();
		
		StereotypePackageElement packageElement = springIndex.getNodesOfType(StereotypePackageElement.class).stream()
			.filter(pkg -> pkg.getPackageName().equals("example"))
			.findAny().get();
		
		List<Stereotype> allStereotypesFound = factory.fromPackage(packageElement).stream().toList();
		assertEquals(1, allStereotypesFound.size());
		
		assertEquals("architecture.hexagonal.Port", allStereotypesFound.get(0).getIdentifier());

		StereotypePackageElement subpackageElement = springIndex.getNodesOfType(StereotypePackageElement.class).stream()
				.filter(pkg -> pkg.getPackageName().equals("example.application"))
				.findAny().get();
			
		allStereotypesFound = factory.fromPackage(subpackageElement).stream().toList();
		assertEquals(1, allStereotypesFound.size());
		
		assertEquals("architecture.hexagonal.Application", allStereotypesFound.get(0).getIdentifier());
	}
	
	@Test
	void testFromClassIndexBasedFactory() {
		
		var catalog = this.stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, project, new CachedSpringMetamodelIndex(springIndex));
		factory.registerStereotypeDefinitions();
		
		StereotypeClassElement myControllerClassElement = springIndex.getNodesOfType(StereotypeClassElement.class).stream()
			.filter(type -> type.getType().equals("example.MyController"))
			.findAny().get();
		
		List<Stereotype> myControllerStereotypes = factory.fromType(myControllerClassElement).stream().toList();
		assertEquals(2, myControllerStereotypes.size());
		
		assertEquals("spring.web.Controller", myControllerStereotypes.get(0).getIdentifier());
		assertEquals("architecture.hexagonal.Port", myControllerStereotypes.get(1).getIdentifier());


		StereotypeClassElement somePrimaryPortClassElement = springIndex.getNodesOfType(StereotypeClassElement.class).stream()
				.filter(type -> type.getType().equals("example.application.SomePrimaryPort"))
				.findAny().get();
			
		List<Stereotype> somePrimaryPortStereotypes = factory.fromType(somePrimaryPortClassElement).stream().toList();
		assertEquals(2, somePrimaryPortStereotypes.size());

		assertEquals("ddd.ValueObject", somePrimaryPortStereotypes.get(0).getIdentifier());
		assertEquals("architecture.hexagonal.Application", somePrimaryPortStereotypes.get(1).getIdentifier());
	}

	@Test
	void testSelfDefinedStereotypeAsAnnotation() {
		
		var catalog = this.stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, project, new CachedSpringMetamodelIndex(springIndex));
		factory.registerStereotypeDefinitions();
		
		// catalog definition
		Optional<? extends StereotypeDefinition> found = catalog.getDefinitions().stream()
			.filter(definition -> definition.getStereotype().getIdentifier().equals("example.application.MyStereotype"))
			.findAny();
		assertTrue(found.isPresent());

		// stereotype matching
		StereotypeClassElement myStereotypeMarkedClass = springIndex.getNodesOfType(StereotypeClassElement.class).stream()
			.filter(type -> type.getType().equals("example.application.MyStereotypeMarkedClass"))
			.findAny().get();
		
		List<Stereotype> myStereotypeMarkedClassStereotypes = factory.fromType(myStereotypeMarkedClass).stream().toList();
		assertEquals(2, myStereotypeMarkedClassStereotypes.size());
		
		assertEquals("example.application.MyStereotype", myStereotypeMarkedClassStereotypes.get(0).getIdentifier());
		assertEquals("architecture.hexagonal.Application", myStereotypeMarkedClassStereotypes.get(1).getIdentifier());
	}
	
	@Test
	void testSelfDefinedStereotypeAsInterface() {
		
		var catalog = this.stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, project, new CachedSpringMetamodelIndex(springIndex));
		factory.registerStereotypeDefinitions();
		
		// catalog definition
		Optional<? extends StereotypeDefinition> found = catalog.getDefinitions().stream()
			.filter(definition -> definition.getStereotype().getIdentifier().equals("example.application.DirectStereotypeInterface"))
			.findAny();
		assertTrue(found.isPresent());
		
		StereotypeDefinition stereotypeDefinition = found.get();
		assertEquals("example.application.DirectStereotypeInterface", stereotypeDefinition.getStereotype().getIdentifier());
		
		// stereotype matching
		StereotypeClassElement classImplementsStereotypeInterface = springIndex.getNodesOfType(StereotypeClassElement.class).stream()
			.filter(type -> type.getType().equals("example.application.ClassWithDirectStereotypeSuperinterface"))
			.findAny().get();
		
		List<Stereotype> stereotypes = factory.fromType(classImplementsStereotypeInterface).stream().toList();
		assertEquals(2, stereotypes.size());
		
		assertEquals("example.application.DirectStereotypeInterface", stereotypes.get(0).getIdentifier());
		assertEquals("architecture.hexagonal.Application", stereotypes.get(1).getIdentifier());
	}
	
	@Test
	void testSelfDefinedStereotypeViaInterfaceWithAttributes() {
		
		var catalog = this.stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, project, new CachedSpringMetamodelIndex(springIndex));
		factory.registerStereotypeDefinitions();
		
		// catalog definition
		Optional<? extends StereotypeDefinition> found = catalog.getDefinitions().stream()
			.filter(definition -> definition.getStereotype().getIdentifier().equals("example.application.StereotypeInterfaceWithAttributes"))
			.findAny();
		assertTrue(found.isPresent());
		
		StereotypeDefinition stereotypeDefinition = found.get();
		assertEquals("example.application.StereotypeInterfaceWithAttributes", stereotypeDefinition.getStereotype().getIdentifier());
		assertEquals("Super Stereotype", stereotypeDefinition.getStereotype().getDisplayName());
		assertEquals(List.of("group1", "group2"), stereotypeDefinition.getStereotype().getGroups());
	}
	
	@Test
	void testWithStereotypeInSuperclass() {
		
		var catalog = this.stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, project, new CachedSpringMetamodelIndex(springIndex));
		factory.registerStereotypeDefinitions();
		
		StereotypeClassElement classElement = springIndex.getNodesOfType(StereotypeClassElement.class).stream()
			.filter(type -> type.getType().equals("example.application.SubclassWithSuperclassMarkedWithStereotype"))
			.findAny().get();
		
		List<Stereotype> stereotypes = factory.fromType(classElement).stream().toList();
		assertEquals(2, stereotypes.size());
		
		assertEquals("ddd.ValueObject", stereotypes.get(0).getIdentifier());
		assertEquals("architecture.hexagonal.Application", stereotypes.get(1).getIdentifier());
	}

}
