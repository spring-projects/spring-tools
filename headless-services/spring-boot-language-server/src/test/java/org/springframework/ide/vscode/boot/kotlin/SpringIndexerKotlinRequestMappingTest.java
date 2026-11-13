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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceSymbol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexElement;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * The request mappings that are indexed for a Kotlin project, compared to what is indexed for the
 * same project written in Java. Both are indexed by the very same request mapping indexer.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
@AllKotlinParsers
public class SpringIndexerKotlinRequestMappingTest {

	@Parameter
	String parser;

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;

	private File kotlinDirectory;
	private IJavaProject kotlinProject;
	private IJavaProject javaProject;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		setKotlinSupport(true);

		kotlinDirectory = new File(ProjectsHarness.class.getResource("/test-projects/test-request-mapping-symbols-kotlin/").toURI());
		File javaDirectory = new File(ProjectsHarness.class.getResource("/test-projects/test-request-mapping-symbols/").toURI());

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
		indexer.waitOperation().get(15, TimeUnit.SECONDS);
	}

	private static List<RequestMappingIndexElement> mappingsOf(Bean bean) {
		return bean.getChildren().stream()
				.filter(child -> child instanceof RequestMappingIndexElement)
				.map(child -> (RequestMappingIndexElement) child)
				.sorted(Comparator.comparing(RequestMappingIndexElement::getMethodSignature).thenComparing(RequestMappingIndexElement::getPath))
				.toList();
	}

	private Bean kotlinBean(String name) {
		Bean[] beans = springIndex.getBeansWithName(kotlinProject.getElementName(), name);
		assertEquals(1, beans.length, "bean " + name);
		return beans[0];
	}

	private List<RequestMappingIndexElement> kotlinMappings(String beanName) {
		return mappingsOf(kotlinBean(beanName));
	}

	private static List<String> paths(List<RequestMappingIndexElement> mappings) {
		return mappings.stream().map(RequestMappingIndexElement::getPath).toList();
	}

	@Test
	void sameRequestMappingsAsForTheJavaVersionOfTheProject() {
		Bean[] kotlinBeans = springIndex.getBeansOfProject(kotlinProject.getElementName());
		assertTrue(kotlinBeans.length > 10, "the Kotlin controllers are indexed");

		int compared = 0;
		for (Bean kotlin : kotlinBeans) {
			Bean[] javaBeans = springIndex.getBeansWithName(javaProject.getElementName(), kotlin.getName());
			if (javaBeans.length != 1) {
				continue; // controllers that only exist in the Kotlin version
			}

			List<RequestMappingIndexElement> expected = mappingsOf(javaBeans[0]);
			List<RequestMappingIndexElement> actual = mappingsOf(kotlin);
			assertEquals(expected.size(), actual.size(), "number of mappings of " + kotlin.getName());

			for (int i = 0; i < expected.size(); i++) {
				String description = kotlin.getName() + ": " + expected.get(i).getMethodSignature();

				assertEquals(expected.get(i).getPath(), actual.get(i).getPath(), "path of " + description);
				assertArrayEquals(expected.get(i).getHttpMethods(), actual.get(i).getHttpMethods(), "methods of " + description);
				assertArrayEquals(expected.get(i).getContentTypes(), actual.get(i).getContentTypes(), "content types of " + description);
				assertArrayEquals(expected.get(i).getAcceptTypes(), actual.get(i).getAcceptTypes(), "accept types of " + description);
				assertEquals(expected.get(i).getVersion(), actual.get(i).getVersion(), "version of " + description);
				assertEquals(expected.get(i).getMethodSignature(), actual.get(i).getMethodSignature(), "signature of " + description);
			}
			compared += expected.size();
		}

		assertTrue(compared > 25, "the mappings of many controllers were compared, but only " + compared);
	}

	@Test
	void simpleRequestMapping() {
		Bean bean = kotlinBean("simpleMappingClass");
		assertEquals("org.test.SimpleMappingClass", bean.getType());

		List<RequestMappingIndexElement> mappings = mappingsOf(bean);

		assertEquals(1, mappings.size());
		assertEquals("/greeting", mappings.get(0).getPath());
		assertEquals("org.test.SimpleMappingClass.hello() : java.lang.String", mappings.get(0).getMethodSignature());
		assertNotNull(mappings.get(0).getContentHash());
	}

	@Test
	void locationsPointIntoTheKotlinFile() {
		String docUri = kotlinDirectory.toPath().resolve("src/main/kotlin/org/test/SimpleMappingClass.kt").toUri().toString();

		RequestMappingIndexElement mapping = kotlinMappings("simpleMappingClass").get(0);

		// the same place as in the Java version of the file: the annotation
		var symbol = mapping.getDocumentSymbol();
		assertEquals(8, symbol.getRange().getStart().getLine());
		assertEquals(1, symbol.getRange().getStart().getCharacter());
		assertEquals(8, symbol.getRange().getEnd().getLine());
		assertEquals(29, symbol.getRange().getEnd().getCharacter());

		assertTrue(indexer.getWorkspaceSymbolsFromMetamodelIndex(docUri).stream().anyMatch(s -> s.getName().equals("@/greeting")));
	}

	@Test
	void controllerBeanIsIndexed() {
		Bean bean = kotlinBean("simpleMappingClass");

		assertEquals("org.test.SimpleMappingClass", bean.getType());
		assertTrue(Arrays.stream(bean.getAnnotations()).anyMatch(a -> a.getAnnotationType().equals("org.springframework.stereotype.Controller")));
		assertTrue(Arrays.stream(bean.getAnnotations()).anyMatch(a -> a.getAnnotationType().equals("org.springframework.stereotype.Component") && a.isMetaAnnotation()));
		assertFalse(bean.isConfiguration());
		assertEquals("simpleMappingClass", bean.getName());
	}

	@Test
	void requestMethods() {
		List<RequestMappingIndexElement> mappings = kotlinMappings("requestMethodClass");

		assertEquals(9, mappings.size());
		assertArrayEquals(new String[] { "GET" }, find(mappings, "getData").getHttpMethods());
		assertArrayEquals(new String[] { "PUT" }, find(mappings, "putData").getHttpMethods());
		assertArrayEquals(new String[] { "DELETE" }, find(mappings, "deleteData").getHttpMethods());
		assertArrayEquals(new String[] { "POST" }, find(mappings, "postData").getHttpMethods());
		assertArrayEquals(new String[] { "PATCH" }, find(mappings, "patchData").getHttpMethods());
		assertArrayEquals(new String[] { "GET" }, find(mappings, "getHello").getHttpMethods());
		assertArrayEquals(new String[] { "POST", "PUT" }, find(mappings, "updateHello").getHttpMethods());
		assertEquals("/", find(mappings, "getDataWithoutPath").getPath());
		assertEquals("/", find(mappings, "getDataWithoutAnything").getPath());
	}

	private static RequestMappingIndexElement find(List<RequestMappingIndexElement> mappings, String methodName) {
		return mappings.stream().filter(m -> m.getMethodSignature().contains("." + methodName + "(")).findFirst().orElseThrow();
	}

	@Test
	void multiplePaths() {
		assertEquals(List.of("/hello1", "/hello2"), paths(kotlinMappings("multiRequestMappingClass")));
	}

	@Test
	void mediaTypes() {
		List<RequestMappingIndexElement> mappings = kotlinMappings("requestMappingMediaTypes");

		assertArrayEquals(new String[] { "text/plain" }, find(mappings, "consume2").getAcceptTypes());
		assertArrayEquals(new String[] { "text/plain", "testconsumetype" }, find(mappings, "consume3").getAcceptTypes());
		assertArrayEquals(new String[] { "text/plain" }, find(mappings, "produce2").getContentTypes());
		assertArrayEquals(new String[] { "application/json", "text/plain", "testconsume" }, find(mappings, "everything").getAcceptTypes());
		assertArrayEquals(new String[] { "application/json" }, find(mappings, "everything").getContentTypes());
	}

	@Test
	void pathOfTheClassAndMethodAreCombined() {
		List<RequestMappingIndexElement> mappings = kotlinMappings("parentMappingClass");

		assertEquals(1, mappings.size());
		assertEquals("/parent/greeting", mappings.get(0).getPath());
		assertArrayEquals(new String[] { "GET" }, mappings.get(0).getHttpMethods());

		// the methods of the class are the ones of the mapping, when the method mapping has none
		assertArrayEquals(new String[] { "GET", "POST", "DELETE" }, kotlinMappings("parentMappingClass2").get(0).getHttpMethods());
		assertEquals("/parent2", kotlinMappings("parentMappingClass2").get(0).getPath());
	}

	@Test
	void constants() {
		assertEquals(List.of("/path/from/constant"), paths(kotlinMappings("simpleMappingClassWithConstantInDifferentClass")));
		assertEquals(List.of("/request/mapping/path/from/same/class/constant"), paths(kotlinMappings("simpleMappingClassWithConstantInSameClass")));
		assertEquals(List.of("/path/from/chain"), paths(kotlinMappings("chainedRequestMappingPathOverMultipleClasses")));
	}

	@Test
	void constantsOfClassesOnTheClasspath() {
		// a constant that is read from a class file
		assertEquals(List.of("/(inferred)"), paths(kotlinMappings("simpleMappingClassWithConstantFromBinaryType")));
	}

	@Test
	void concatenatedStrings() {
		List<String> paths = paths(kotlinMappings("mappingsWithConcatenatedStrings"));

		assertTrue(paths.contains("/path1/path2"));
		assertTrue(paths.contains("/path1/path/from/constant"));
	}

	@Test
	void templatesAndTopLevelConstants() {
		List<RequestMappingIndexElement> mappings = kotlinMappings("mappingsWithTemplates");

		assertEquals(List.of("/base/templates/base/top-level-constant", "/base/templates/named", "/base/templates/template/path/from/constant/end",
				"/base/templates/value-argument"), paths(mappings).stream().sorted().toList());
		assertArrayEquals(new String[] { "text/plain" }, find(mappings, "namedArguments").getContentTypes());
	}

	@Test
	void mappingsOfSuperclasses() {
		assertEquals(List.of("/superclasspath/"), paths(kotlinMappings("subclassWithMappingFromParent")));
		assertEquals(List.of("/path/from/constant/"), paths(kotlinMappings("subclassWithMappingFromParentWithConstant")));
		assertEquals(List.of("/superpath/subclass"), paths(kotlinMappings("subclassWithMappingFromParentWithStringConcatenation")));

		RequestMappingIndexElement withMethods = kotlinMappings("subclassWithMappingFromParentWithMethods").get(0);
		assertEquals("/superclasspath", withMethods.getPath());
		assertArrayEquals(new String[] { "POST", "PUT" }, withMethods.getHttpMethods());
		assertArrayEquals(new String[] { "text/plain" }, withMethods.getContentTypes());
		assertArrayEquals(new String[] { "testconsume" }, withMethods.getAcceptTypes());
	}

	@Test
	void mappingsOfInterfacesAndSuperclasses() {
		// the mapping of the interfaces is looked at before the one of the superclass, as for Java sources
		assertEquals(List.of("/superinterface-path/last-path-segment"), paths(kotlinMappings("controllerAsSubclassAndInterfaceHierarchy")));
	}

	@Test
	void newChangedAndDeletedControllers() throws Exception {
		java.nio.file.Path file = kotlinDirectory.toPath().resolve("src/main/kotlin/org/test/NewController.kt");
		String docUri = file.toUri().toString();

		java.nio.file.Files.writeString(file, """
				package org.test

				import org.springframework.web.bind.annotation.GetMapping
				import org.springframework.web.bind.annotation.RestController

				@RestController
				class NewController {

					@GetMapping("/new")
					fun hello() = "hello"
				}
				""");

		try {
			indexer.createDocument(docUri).get(10, TimeUnit.SECONDS);
			assertEquals(List.of("/new"), paths(kotlinMappings("newController")));

			java.nio.file.Files.writeString(file, java.nio.file.Files.readString(file).replace("\"/new\"", "\"/changed\""));
			indexer.updateDocument(docUri, java.nio.file.Files.readString(file), "test").get(10, TimeUnit.SECONDS);
			assertEquals(List.of("/changed"), paths(kotlinMappings("newController")));
		}
		finally {
			java.nio.file.Files.deleteIfExists(file);
		}

		indexer.deleteDocument(docUri).get(10, TimeUnit.SECONDS);
		assertEquals(0, springIndex.getBeansWithName(kotlinProject.getElementName(), "newController").length);
	}

	@Test
	void nothingIsIndexedWhileTheKotlinSupportIsSwitchedOff() throws Exception {
		assertTrue(springIndex.getBeansOfProject(kotlinProject.getElementName()).length > 0);

		setKotlinSupport(false);

		assertEquals(0, springIndex.getBeansOfProject(kotlinProject.getElementName()).length);
		assertTrue(springIndex.getBeansOfProject(javaProject.getElementName()).length > 0, "the Java files are not affected");
	}

	@Test
	void outlineOfAKotlinFile() throws Exception {
		String docUri = kotlinDirectory.toPath().resolve("src/main/kotlin/org/test/SimpleMappingClass.kt").toUri().toString();
		harness.newEditorFromFileUri(docUri, LanguageId.KOTLIN);

		// the same symbols as for the Java version of the file
		String javaUri = new File(ProjectsHarness.class.getResource("/test-projects/test-request-mapping-symbols/").toURI()).toPath()
				.resolve("src/main/java/org/test/SimpleMappingClass.java").toUri().toString();
		harness.newEditorFromFileUri(javaUri, LanguageId.JAVA);

		List<String> kotlinOutline = symbolNames(documentSymbols(docUri));
		assertTrue(kotlinOutline.contains("@/greeting"), "the mapping is in the outline: " + kotlinOutline);
		assertEquals(symbolNames(documentSymbols(javaUri)), kotlinOutline);
	}

	@Test
	void outlineOfAKotlinFileThatTheClientReportsAsPlainText() throws Exception {
		// VSCode without an extension that defines the Kotlin language
		String docUri = kotlinDirectory.toPath().resolve("src/main/kotlin/org/test/SimpleMappingClass.kt").toUri().toString();
		harness.newEditorFromFileUri(docUri, LanguageId.PLAINTEXT);

		List<String> outline = symbolNames(documentSymbols(docUri));
		assertTrue(outline.contains("@/greeting"), "the mapping is in the outline: " + outline);

		// other plain text files get nothing
		String otherUri = kotlinDirectory.toPath().resolve("pom.xml").toUri().toString().replace("pom.xml", "notes.txt");
		harness.newEditor(LanguageId.PLAINTEXT, "some notes", otherUri);
		assertTrue(documentSymbols(otherUri).isEmpty());
	}

	/** the outline, as the client asks for it */
	private List<DocumentSymbol> documentSymbols(String docUri) throws Exception {
		return harness.getServer().getTextDocumentService().documentSymbol(new DocumentSymbolParams(new TextDocumentIdentifier(docUri))).get()
				.stream().map(either -> either.getRight()).toList();
	}

	@Test
	void workspaceSymbolsIncludeTheKotlinFiles() {
		List<String> kotlinSymbols = indexer.getAllSymbols("").stream()
				.filter(symbol -> symbol.getLocation().getLeft().getUri().endsWith("/SimpleMappingClass.kt"))
				.map(WorkspaceSymbol::getName)
				.toList();

		assertTrue(kotlinSymbols.contains("@/greeting"), "symbols: " + kotlinSymbols);
		assertTrue(kotlinSymbols.stream().anyMatch(name -> name.contains("'simpleMappingClass'")), "symbols: " + kotlinSymbols);
	}

	/** the names of the symbols and their children, depth first */
	private static List<String> symbolNames(List<? extends DocumentSymbol> symbols) {
		List<String> result = new java.util.ArrayList<>();
		for (DocumentSymbol symbol : symbols) {
			result.add(symbol.getName());
			if (symbol.getChildren() != null) {
				result.addAll(symbolNames(symbol.getChildren()));
			}
		}
		return result;
	}

}
