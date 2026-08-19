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
package org.springframework.ide.vscode.boot.java.spel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.util.text.LanguageId;
import org.springframework.ide.vscode.languageserver.testharness.Editor;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Go to definition for methods in SpEL expressions has to find methods that are
 * inherited from a super class or an interface, not only the ones that are declared in
 * the type of the bean itself.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class SpelDefinitionProviderHierarchyTest {

	@Autowired
	private BootLanguageServerHarness harness;
	@Autowired
	private JavaProjectFinder projectFinder;
	@Autowired
	private SpringMetamodelIndex springIndex;
	@Autowired
	private SpringSymbolIndex indexer;

	private static final String TEST_PROJECT = "test-spel-hierarchy";

	private ProjectsHarness projects = ProjectsHarness.INSTANCE;

	private Path projectPath;
	private IJavaProject project;

	@BeforeEach
	public void setup() throws Exception {
		// The types of this project are resolved against its compiled output, so the
		// project has to be built. It belongs to this test alone: a test that gets to it
		// first via 'mavenProjectAlreadyBuilt' would win the project cache entry of the
		// harness - which ignores the build flag - and leave the project uncompiled.
		projects.mavenProject(TEST_PROJECT);

		harness.intialize(null);

		File directory = new File(ProjectsHarness.class.getResource("/test-projects/" + TEST_PROJECT + "/").toURI());
		projectPath = directory.toPath();
		project = projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(10, TimeUnit.SECONDS);
	}

	private Editor editorWithSpel(String spelExpression) throws Exception {
		String tempJavaDocUri = projectPath.resolve("src/main/java/org/test/TempClass.java").toUri().toString();

		return harness.newEditor(LanguageId.JAVA, """
				package org.test;

				import org.springframework.beans.factory.annotation.Value;

				public class TempClass {

					@Value("#{%s}")
					private String value;
				}""".formatted(spelExpression), tempJavaDocUri);
	}

	/**
	 * Computes the expected location of the method declaration from the source file of
	 * the given type, so that the expectations don't have to be counted by hand.
	 */
	private LocationLink methodLink(String typeName, String methodName) throws Exception {
		Path file = projectPath.resolve("src/main/java/org/test/hierarchy/" + typeName + ".java");
		List<String> lines = Files.readAllLines(file);
		Pattern declaration = Pattern.compile("\\b" + Pattern.quote(methodName) + "\\s*\\(");

		for (int line = 0; line < lines.size(); line++) {
			Matcher matcher = declaration.matcher(lines.get(line));
			if (matcher.find()) {
				Range range = new Range(new Position(line, matcher.start()),
						new Position(line, matcher.start() + methodName.length()));
				return new LocationLink(file.toUri().toString(), range, range, null);
			}
		}
		throw new IllegalStateException("no declaration of '" + methodName + "' found in " + typeName);
	}

	@Test
	public void testBeanIsIndexed() throws Exception {
		Bean[] beans = springIndex.getBeansWithName(project.getElementName(), "childVisitService");
		assertEquals(1, beans.length);
		assertEquals("org.test.hierarchy.ChildVisitService", beans[0].getType());
	}

	@Test
	public void testMethodDeclaredInBeanType() throws Exception {
		Editor editor = editorWithSpel("@childVisitService.childMethod()");
		editor.assertDefinitionLinkTargets("childMethod",
				List.of(methodLink("ChildVisitService", "childMethod")));
	}

	@Test
	public void testMethodInheritedFromSuperClass() throws Exception {
		Editor editor = editorWithSpel("@childVisitService.baseMethod()");
		editor.assertDefinitionLinkTargets("baseMethod",
				List.of(methodLink("BaseVisitService", "baseMethod")));
	}

	@Test
	public void testStaticMethodInheritedFromSuperClass() throws Exception {
		Editor editor = editorWithSpel("@childVisitService.staticBaseMethod()");
		editor.assertDefinitionLinkTargets("staticBaseMethod",
				List.of(methodLink("BaseVisitService", "staticBaseMethod")));
	}

	@Test
	public void testMethodInheritedFromInterface() throws Exception {
		Editor editor = editorWithSpel("@childVisitService.defaultInterfaceMethod()");
		editor.assertDefinitionLinkTargets("defaultInterfaceMethod",
				List.of(methodLink("VisitServiceInterface", "defaultInterfaceMethod")));
	}

	@Test
	public void testOverriddenMethodResolvesToTheOverride() throws Exception {
		Editor editor = editorWithSpel("@childVisitService.overriddenMethod()");
		editor.assertDefinitionLinkTargets("overriddenMethod",
				List.of(methodLink("ChildVisitService", "overriddenMethod")));
	}

	@Test
	public void testStaticMethodViaTypeReferenceInheritedFromSuperClass() throws Exception {
		Editor editor = editorWithSpel("T(org.test.hierarchy.ChildVisitService).staticBaseMethod()");
		editor.assertDefinitionLinkTargets("staticBaseMethod",
				List.of(methodLink("BaseVisitService", "staticBaseMethod")));
	}

	@Test
	public void testUnknownMethodHasNoDefinition() throws Exception {
		Editor editor = editorWithSpel("@childVisitService.thisMethodDoesNotExist()");
		editor.assertDefinitionLinkTargets("thisMethodDoesNotExist", List.of());
	}

}
