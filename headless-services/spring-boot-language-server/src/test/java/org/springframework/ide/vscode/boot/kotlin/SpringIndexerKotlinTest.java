/*******************************************************************************
 * Copyright (c) 2026 Broadcom, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Broadcom, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.vscode.boot.kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.BootJavaConfig;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.Annotations;
import org.springframework.ide.vscode.boot.java.beans.SpringBootApplicationIndexElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * The stereotype elements that are indexed for a Kotlin project, compared to what is indexed for
 * the same project written in Java.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
@AllKotlinParsers
public class SpringIndexerKotlinTest {

	@Parameter
	String parser;

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private BootJavaConfig config;

	private File kotlinDirectory;
	private IJavaProject kotlinProject;
	private IJavaProject javaProject;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		setKotlinSupport(true);

		kotlinDirectory = new File(ProjectsHarness.class.getResource("/test-projects/test-stereotypes-support-kotlin/").toURI());
		File javaDirectory = new File(ProjectsHarness.class.getResource("/test-projects/test-stereotypes-support/").toURI());

		kotlinProject = projectFinder.find(new TextDocumentIdentifier(kotlinDirectory.toURI().toString())).get();
		javaProject = projectFinder.find(new TextDocumentIdentifier(javaDirectory.toURI().toString())).get();

		waitForIndexing();
	}

	@AfterEach
	public void tearDown() throws Exception {
		// the Spring context is shared with other tests, which should not see anything of the Kotlin project
		setKotlinSupport(false);
		springIndex.removeProject(kotlinProject.getElementName());
	}

	private void setKotlinSupport(boolean enabled) throws Exception {
		harness.changeConfiguration(Map.of("boot-java", Map.of("kotlin-support", Map.of("on", enabled, "parser", parser))));
		waitForIndexing();
	}

	private void waitForIndexing() throws Exception {
		CompletableFuture<Void> operation = indexer.waitOperation();
		operation.get(10, TimeUnit.SECONDS);
	}

	private List<StereotypeClassElement> kotlinTypes() {
		return springIndex.getNodesOfType(kotlinProject.getElementName(), StereotypeClassElement.class);
	}

	private StereotypeClassElement kotlinType(String name) {
		return kotlinTypes().stream().filter(node -> node.getType().equals(name)).findFirst().orElseThrow(() -> new AssertionError("no element for " + name));
	}

	private StereotypeClassElement javaType(String name) {
		return springIndex.getNodesOfType(javaProject.getElementName(), StereotypeClassElement.class).stream()
				.filter(node -> node.getType().equals(name)).findFirst().orElseThrow(() -> new AssertionError("no element for " + name));
	}

	@Test
	void theConfiguredParserIsTheOneThatIsUsed() {
		assertEquals(parser, config.getKotlinParser());
	}

	@Test
	void kotlinTypesAreIndexed() {
		List<String> types = kotlinTypes().stream().map(StereotypeClassElement::getType).toList();

		assertTrue(types.contains("example.MyController"));
		assertTrue(types.contains("example.application.SampleController"));
		assertFalse(types.contains("example.application.MyStereotype"), "annotation types get no element, as for Java sources");
		assertTrue(types.contains("example.application.MyStereotypeMarkedClass"));
		assertTrue(types.contains("example.application.ClassWithMethods"));
		assertTrue(types.contains("example.application.empty.EmptyClass"));
	}

	@Test
	void nestedTypesAreIndexedWithBinaryNames() {
		List<String> types = kotlinTypes().stream().map(StereotypeClassElement::getType)
				.filter(type -> type.startsWith("example.application.TypeWithInnerClass")).toList();

		assertEquals(3, types.size());
		assertTrue(types.contains("example.application.TypeWithInnerClass"));
		assertTrue(types.contains("example.application.TypeWithInnerClass$InnerClass"));
		assertTrue(types.contains("example.application.TypeWithInnerClass$InnerClass$InnerClassInInnerClass"));
	}

	@Test
	void annotationTypesIncludeMetaAnnotations() {
		StereotypeClassElement controller = kotlinType("example.application.SampleController");

		Set<String> annotationTypes = controller.getAnnotationTypes();

		assertTrue(annotationTypes.contains("org.springframework.web.bind.annotation.RestController"));
		assertTrue(annotationTypes.contains("org.springframework.stereotype.Controller"));
		assertTrue(annotationTypes.contains("org.springframework.stereotype.Component"));
		assertTrue(controller.isAnnotatedWith("org.springframework.stereotype.Component"));
		assertFalse(controller.isAnnotatedWith("notFound"));
	}

	@Test
	void annotationsFromKotlinAnnotationClasses() {
		StereotypeClassElement marked = kotlinType("example.application.MyStereotypeMarkedClass");

		assertTrue(marked.isAnnotatedWith("example.application.MyStereotype"));
		assertTrue(marked.isAnnotatedWith("org.jmolecules.stereotype.Stereotype"));
	}

	@Test
	void supertypesAndTheirAnnotations() {
		StereotypeClassElement bySuperclass = kotlinType("example.application.ClassWithDirectStereotypeSuper");
		assertTrue(bySuperclass.doesImplement("example.application.DirectStereotypeClass"));
		assertTrue(bySuperclass.isAnnotatedWith("org.jmolecules.stereotype.Stereotype"));

		StereotypeClassElement byInterface = kotlinType("example.application.ClassWithDirectStereotypeSuperinterface");
		assertTrue(byInterface.doesImplement("example.application.DirectStereotypeInterface"));
		assertTrue(byInterface.isAnnotatedWith("org.jmolecules.stereotype.Stereotype"));

		StereotypeClassElement subclass = kotlinType("example.application.SubclassWithSuperclassMarkedWithStereotype");
		assertTrue(subclass.doesImplement("example.application.SomePrimaryPort"));
		assertTrue(subclass.isAnnotatedWith("org.jmolecules.ddd.annotation.ValueObject"));
		assertFalse(subclass.getSupertypes().contains("java.lang.Object"));
	}

	@Test
	void annotatedMethodsGetElementsOfTheirOwn() {
		List<StereotypeMethodElement> methods = kotlinType("example.application.ClassWithMethods").getMethods();

		assertEquals(1, methods.size());

		StereotypeMethodElement method = methods.get(0);
		assertEquals("methodWithAnnotations", method.getMethodName());
		assertEquals("example.application.ClassWithMethods.methodWithAnnotations(java.lang.String) : V", method.getMethodSignature());
		assertEquals("ClassWithMethods.methodWithAnnotations(String) : void", method.getMethodLabel());
		assertTrue(method.isAnnotatedWith(Annotations.SPRING_GET_MAPPING));
		assertTrue(method.isAnnotatedWith(Annotations.SPRING_REQUEST_MAPPING));
		assertFalse(method.isAnnotatedWith(Annotations.BOOT_APP));
	}

	@Test
	void locationsPointIntoTheKotlinFile() {
		String docUri = kotlinDirectory.toPath().resolve("src/main/kotlin/example/application/SampleController.kt").toUri().toString();

		Location location = kotlinType("example.application.SampleController").getLocation();

		assertNotNull(location);
		assertEquals(docUri, location.getUri());
		assertEquals(6, location.getRange().getStart().getLine());
		assertEquals(6, location.getRange().getEnd().getLine());
		assertEquals(6, location.getRange().getStart().getCharacter()); // "class SampleController"
		assertEquals(22, location.getRange().getEnd().getCharacter());
	}

	@Test
	void contentHashesAreSetAndFollowTheSource() {
		StereotypeClassElement controller = kotlinType("example.application.SampleController");
		StereotypeClassElement other = kotlinType("example.application.ClassWithMethods");

		assertNotNull(controller.getContentHash());
		assertEquals(12, controller.getContentHash().length());
		assertFalse(controller.getContentHash().equals(other.getContentHash()));
	}

	@Test
	void mainApplicationPackageIsIndexed() {
		// the structure view groups the types by the package of the main application class
		List<SpringBootApplicationIndexElement> mainClasses = springIndex.getNodesOfType(kotlinProject.getElementName(), SpringBootApplicationIndexElement.class);

		assertEquals(1, mainClasses.size());
		assertEquals("example.application", mainClasses.get(0).getPackageName());
		assertEquals("MainClass", mainClasses.get(0).getTypeName());
		assertTrue(mainClasses.get(0).isClassDeclaration());
	}

	@Test
	void sameResultsAsForTheJavaVersionOfTheProject() {
		for (StereotypeClassElement kotlin : kotlinTypes()) {
			StereotypeClassElement java = javaType(kotlin.getType());

			assertEquals(java.getAnnotationTypes(), kotlin.getAnnotationTypes(), "annotation types of " + kotlin.getType());
			assertEquals(java.getSupertypes(), kotlin.getSupertypes(), "supertypes of " + kotlin.getType());

			List<StereotypeMethodElement> javaMethods = java.getMethods();
			List<StereotypeMethodElement> kotlinMethods = kotlin.getMethods();
			assertEquals(javaMethods.size(), kotlinMethods.size(), "number of methods of " + kotlin.getType());

			for (StereotypeMethodElement kotlinMethod : kotlinMethods) {
				StereotypeMethodElement javaMethod = javaMethods.stream().filter(m -> m.getMethodName().equals(kotlinMethod.getMethodName())).findFirst().get();
				assertEquals(javaMethod.getMethodSignature(), kotlinMethod.getMethodSignature());
				assertEquals(javaMethod.getMethodLabel(), kotlinMethod.getMethodLabel());
				assertEquals(javaMethod.getAnnotationTypes(), kotlinMethod.getAnnotationTypes());
			}
		}
	}

	@Test
	void newAndDeletedFilesAreIndexed() throws Exception {
		Path file = kotlinDirectory.toPath().resolve("src/main/kotlin/example/application/NewService.kt");
		String docUri = file.toUri().toString();

		Files.writeString(file, """
				package example.application

				import org.springframework.stereotype.Service

				@Service
				class NewService
				""");

		try {
			indexer.createDocument(docUri).get(10, TimeUnit.SECONDS);

			Optional<StereotypeClassElement> element = kotlinTypes().stream().filter(node -> node.getType().equals("example.application.NewService")).findFirst();
			assertTrue(element.isPresent());
			assertTrue(element.get().isAnnotatedWith("org.springframework.stereotype.Component"));
		}
		finally {
			Files.deleteIfExists(file);
		}

		indexer.deleteDocument(docUri).get(10, TimeUnit.SECONDS);
		assertTrue(kotlinTypes().stream().noneMatch(node -> node.getType().equals("example.application.NewService")));
	}

	@Test
	void changedFilesAreIndexedAgain() throws Exception {
		Path file = kotlinDirectory.toPath().resolve("src/main/kotlin/example/application/ClassWithMethods.kt");
		String original = Files.readString(file);
		String docUri = file.toUri().toString();

		try {
			Files.writeString(file, original.replace("fun methodWithoutAnnotations() {", "@org.springframework.web.bind.annotation.PostMapping\n\tfun methodWithoutAnnotations() {"));
			indexer.updateDocument(docUri, Files.readString(file), "test").get(10, TimeUnit.SECONDS);

			List<StereotypeMethodElement> methods = kotlinType("example.application.ClassWithMethods").getMethods();
			assertEquals(2, methods.size());
			assertTrue(methods.stream().anyMatch(m -> m.getMethodName().equals("methodWithoutAnnotations")));
		}
		finally {
			Files.writeString(file, original);
			indexer.updateDocument(docUri, original, "test").get(10, TimeUnit.SECONDS);
		}

		assertEquals(1, kotlinType("example.application.ClassWithMethods").getMethods().size());
	}

	@Test
	void nothingIsIndexedWhileTheKotlinSupportIsSwitchedOff() throws Exception {
		assertFalse(kotlinTypes().isEmpty());

		setKotlinSupport(false);

		assertTrue(kotlinTypes().isEmpty());
		assertFalse(springIndex.getNodesOfType(javaProject.getElementName(), StereotypeClassElement.class).isEmpty(), "the Java files are not affected");

		setKotlinSupport(true);

		assertFalse(kotlinTypes().isEmpty());
	}

}
