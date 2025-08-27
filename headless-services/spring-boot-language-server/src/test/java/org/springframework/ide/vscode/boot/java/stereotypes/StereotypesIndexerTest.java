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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jmolecules.stereotype.api.Stereotype;
import org.jmolecules.stereotype.catalog.StereotypeDefinition;
import org.jmolecules.stereotype.tooling.AsciiArtNodeHandler;
import org.jmolecules.stereotype.tooling.HierarchicalNodeHandler;
import org.jmolecules.stereotype.tooling.ProjectTree;
import org.jmolecules.stereotype.tooling.SimpleLabelProvider;
import org.jmolecules.stereotype.tooling.StructureProvider.SimpleStructureProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.SymbolProviderTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.commands.SpringIndexCommands;
import org.springframework.ide.vscode.boot.java.commands.ToolsJsonNodeHandler;
import org.springframework.ide.vscode.boot.java.commands.ToolsJsonNodeHandler.Node;
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
	@Autowired private SpringIndexCommands indexCommands;

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
    	
    	List<String> annotationTypes1 = packageElement1.getAnnotationTypes();
    	assertEquals(1, annotationTypes1.size());
    	assertEquals("org.jmolecules.architecture.hexagonal.Port", annotationTypes1.get(0));
    	
    	List<String> annotationTypes2 = packageElement2.getAnnotationTypes();
    	assertEquals(1, annotationTypes2.size());
    	assertEquals("org.jmolecules.architecture.hexagonal.Application", annotationTypes2.get(0));
    	
    	// check type elements for package-info
    	List<StereotypeClassElement> packagesTypeElements = springIndex.getNodesOfType(StereotypeClassElement.class);
    	StereotypeClassElement packageClassElement1 = packagesTypeElements.stream().filter(packageType -> packageType.getType().equals(("example.package-info"))).findFirst().get();
    	StereotypeClassElement packageClassElement2 = packagesTypeElements.stream().filter(packageType -> packageType.getType().equals(("example.application.package-info"))).findFirst().get();
    	
    	List<String> packageTypeAnnotations1 = packageClassElement1.getAnnotationTypes();
    	assertEquals(1, packageTypeAnnotations1.size());
    	assertEquals("org.jmolecules.architecture.hexagonal.Port", packageTypeAnnotations1.get(0));

    	List<String> packageTypeAnnotations2 = packageClassElement2.getAnnotationTypes();
    	assertEquals(1, packageTypeAnnotations2.size());
    	assertEquals("org.jmolecules.architecture.hexagonal.Application", packageTypeAnnotations2.get(0));
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
    	List<String> annotationTypes = methodWithAnnotations.getAnnotationTypes();
		assertEquals(4, annotationTypes.size());
    	assertTrue(annotationTypes.contains(Annotations.SPRING_GET_MAPPING));
    	assertTrue(annotationTypes.contains(Annotations.SPRING_REQUEST_MAPPING));
    	
    	assertEquals("methodWithAnnotations", methodWithAnnotations.getMethodName());
    	assertEquals("example.application.ClassWithMethods.methodWithAnnotations(java.lang.String) : V", methodWithAnnotations.getMethodSignature());
    	assertEquals("ClassWithMethods.methodWithAnnotations(String) : void", methodWithAnnotations.getMethodLabel());
    }
    
    @Test
    void testIdentifyMainApplicationPackage() throws Exception {
    	StereotypePackageElement mainPackage = indexCommands.identifyMainApplicationPackage(project, springIndex);
    	assertEquals("example.application", mainPackage.getPackageName());
    }
    
	@Test
	void testFromPackageIndexBasedFactory() {
		
		var catalog = this.stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, springIndex);
		factory.registerStereotypeDefinitions();
		
		StereotypePackageElement packageElement = springIndex.getNodesOfType(StereotypePackageElement.class).stream()
			.filter(pkg -> pkg.getPackageName().equals("example"))
			.findAny().get();
		
		List<Stereotype> allStereotypesFound = factory.fromPackage(packageElement).stream().toList();
		assertEquals(1, allStereotypesFound.size());
		
		assertEquals("org.jmolecules.architecture.hexagonal.Port", allStereotypesFound.get(0).getIdentifier());

		StereotypePackageElement subpackageElement = springIndex.getNodesOfType(StereotypePackageElement.class).stream()
				.filter(pkg -> pkg.getPackageName().equals("example.application"))
				.findAny().get();
			
		allStereotypesFound = factory.fromPackage(subpackageElement).stream().toList();
		assertEquals(1, allStereotypesFound.size());
		
		assertEquals("org.jmolecules.architecture.hexagonal.Application", allStereotypesFound.get(0).getIdentifier());
	}
	
	@Test
	void testFromClassIndexBasedFactory() {
		
		var catalog = this.stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, springIndex);
		factory.registerStereotypeDefinitions();
		
		StereotypeClassElement myControllerClassElement = springIndex.getNodesOfType(StereotypeClassElement.class).stream()
			.filter(type -> type.getType().equals("example.MyController"))
			.findAny().get();
		
		List<Stereotype> myControllerStereotypes = factory.fromType(myControllerClassElement).stream().toList();
		assertEquals(2, myControllerStereotypes.size());
		
		assertEquals("org.springframework.stereotype.Controller", myControllerStereotypes.get(0).getIdentifier());
		assertEquals("org.jmolecules.architecture.hexagonal.Port", myControllerStereotypes.get(1).getIdentifier());


		StereotypeClassElement somePrimaryPortClassElement = springIndex.getNodesOfType(StereotypeClassElement.class).stream()
				.filter(type -> type.getType().equals("example.application.SomePrimaryPort"))
				.findAny().get();
			
		List<Stereotype> somePrimaryPortStereotypes = factory.fromType(somePrimaryPortClassElement).stream().toList();
		assertEquals(2, somePrimaryPortStereotypes.size());

		assertEquals("org.jmolecules.ddd.ValueObject", somePrimaryPortStereotypes.get(0).getIdentifier());
		assertEquals("org.jmolecules.architecture.hexagonal.Application", somePrimaryPortStereotypes.get(1).getIdentifier());
	}

	@Test
	void testSelfDefinedAnnotationStereotype() {
		
		var catalog = this.stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, springIndex);
		factory.registerStereotypeDefinitions();
		
		// stereotype matching
		StereotypeClassElement myStereotypeMarkedClass = springIndex.getNodesOfType(StereotypeClassElement.class).stream()
			.filter(type -> type.getType().equals("example.application.MyStereotypeMarkedClass"))
			.findAny().get();
		
		List<Stereotype> myStereotypeMarkedClassStereotypes = factory.fromType(myStereotypeMarkedClass).stream().toList();
		assertEquals(2, myStereotypeMarkedClassStereotypes.size());
		
		assertEquals("example.application.MyStereotype", myStereotypeMarkedClassStereotypes.get(0).getIdentifier());
		assertEquals("org.jmolecules.architecture.hexagonal.Application", myStereotypeMarkedClassStereotypes.get(1).getIdentifier());
		
		// catalog definition
		Optional<? extends StereotypeDefinition> found = catalog.getDefinitions().stream()
			.filter(definition -> definition.getStereotype().getIdentifier().equals("example.application.MyStereotype"))
			.findAny();
		assertTrue(found.isPresent());
	}
	
	@Test
	void testWithStereotypeInSuperclass() {
		
		var catalog = this.stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, springIndex);
		factory.registerStereotypeDefinitions();
		
		StereotypeClassElement classElement = springIndex.getNodesOfType(StereotypeClassElement.class).stream()
			.filter(type -> type.getType().equals("example.application.SubclassWithSuperclassMarkedWithStereotype"))
			.findAny().get();
		
		List<Stereotype> stereotypes = factory.fromType(classElement).stream().toList();
		assertEquals(2, stereotypes.size());
		
		assertEquals("org.jmolecules.ddd.ValueObject", stereotypes.get(0).getIdentifier());
		assertEquals("org.jmolecules.architecture.hexagonal.Application", stereotypes.get(1).getIdentifier());
	}

	@Test
	@Disabled
	void testTree() {
		
		var catalog = this.stereotypeCatalogRegistry.getCatalogOf(project);
		var factory = new IndexBasedStereotypeFactory(catalog, springIndex);
		factory.registerStereotypeDefinitions();

		var labels = SimpleLabelProvider.forPackage(StereotypePackageElement::getPackageName, StereotypeClassElement::getType,
				(StereotypeMethodElement m, StereotypeClassElement __) -> m.getMethodName(), Object::toString);

		SimpleStructureProvider<StereotypePackageElement, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement> structureProvider =
				new SimpleStructureProvider<StereotypePackageElement, StereotypePackageElement, StereotypeClassElement, StereotypeMethodElement>() {

			@Override
			public Collection<StereotypePackageElement> extractPackages(StereotypePackageElement pkg) {
				return getAllPackageElements().stream()
					.filter(packageElement -> packageElement.getPackageName().startsWith(pkg.getPackageName()))
					.toList();

				// return extractTypes(pkg).stream()
				// .map(Class::getPackage)
				// .distinct()
				// .toList();
			}

			@Override
			public Collection<StereotypeMethodElement> extractMethods(StereotypeClassElement type) {
				return List.of();
			}

			@Override
			public Collection<StereotypeClassElement> extractTypes(StereotypePackageElement pkg) {
				return getAllClassElements().stream()
					.filter(element -> element.getType().startsWith(pkg.getPackageName()))
					.toList();
			}
		};
		
		// ascii art output
		var asciiHandler = new AsciiArtNodeHandler<>(labels);
		var tree = new ProjectTree<>(factory, catalog, asciiHandler)
				.withStructureProvider(structureProvider)
				.withGrouper("org.jmolecules.architecture")
				.withGrouper("org.jmolecules.ddd", "org.jmolecules.event", "spring", "jpa", "java");

		tree.process(new StereotypePackageElement("example.application", null));
		System.out.println(asciiHandler.getWriter().toString());

		// json output
		BiConsumer<Node, Object> consumer = (node, c) -> {
			node.withAttribute(HierarchicalNodeHandler.TEXT, labels.getCustomLabel(c))
			 .withAttribute("icon", "fa-named-interface");
		};
		
		var jsonHandler = new ToolsJsonNodeHandler(labels, consumer);

		var jsonTree = new ProjectTree<>(factory, catalog, jsonHandler)
				.withStructureProvider(structureProvider)
				.withGrouper("org.jmolecules.architecture")
				.withGrouper("org.jmolecules.ddd", "org.jmolecules.event", "spring", "jpa", "java");
		
		jsonTree.process(new StereotypePackageElement("example.application", null));

		tree.process(new StereotypePackageElement("example.application", null));
		System.out.println(jsonHandler.toString());
	}

	private List<StereotypeClassElement> getAllClassElements() {
		return springIndex.getNodesOfType(StereotypeClassElement.class);
	}
	
	private List<StereotypePackageElement> getAllPackageElements() {
		return springIndex.getNodesOfType(StereotypePackageElement.class);
	}
	
}
