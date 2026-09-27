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
package org.springframework.ide.vscode.boot.java.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.stereotypes.JarFixtureBuilder;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Step 4 of {@code docs/structure-view-dependencies.md}: the stereotype elements a selected JAR
 * dependency contributes appear in the including project's tree, scoped by the project's main
 * package exactly like a workspace project dependency's ({@link StructureDependenciesTreeTest}).
 *
 * <p>The fixture JAR's class implements {@code example.application.DescribedStereotype} - the
 * fixture project's own, real, already-catalog-assigned marker interface - so the match happens
 * against {@code test-stereotypes-support}'s real, unmodified catalog, with no fixture-specific
 * stereotype definition of this test's own. The interface itself is compiled alongside the fixture
 * class (from a copy of its source) purely so the fixture class compiles - it is deliberately left
 * out of the packaged JAR, to also prove that a supertype does not need to be indexed itself to be
 * matched by name (see {@code JarStereotypeScannerTest}).
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class StructureDependenciesJarTreeTest {

	private static final String DESCRIBED_STEREOTYPE_SOURCE = "package example.application; public interface DescribedStereotype {}";

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private StructureViewProvider structureViewProvider;

	@TempDir
	Path tempDir;

	private IJavaProject host;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		File directory = new File(ProjectsHarness.class.getResource("/test-projects/test-stereotypes-support/").toURI());
		host = projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();

		indexer.waitOperation().get(15, TimeUnit.SECONDS);
	}

	@Test
	void aTypeFromTheJarMatchingTheHostsRealCatalogAppearsInTheHostsMainPackage() throws Exception {
		File jar = fixtureJar("example.application.FromJar");

		Node tree = tree(List.of(jarDependency(jar)));

		assertEquals(1, nodesLabeled(tree, "e.a.FromJar").size());
	}

	@Test
	void aTypeFromTheJarOutsideTheHostsMainPackageDoesNotAppearYet() throws Exception {
		File jar = fixtureJar("com.acme.outside.FromJarOutside");

		Node tree = tree(List.of(jarDependency(jar)));

		assertFalse(render(tree).contains("FromJarOutside"));
	}

	@Test
	void withoutASelectionTheJarContributesNothing() throws Exception {
		fixtureJar("example.application.FromJar"); // built but never selected below

		Node tree = tree(List.of());

		assertFalse(render(tree).contains("FromJar"));
	}

	/**
	 * The "members" piece added on top of step 4 - a {@code @ConfigurationProperties} class's
	 * fields, read straight from the JAR by {@code JarConfigurationPropertiesScanner}, reach the
	 * rendered tree the same way a workspace project's would: as plain member nodes under the
	 * type, not through stereotype matching.
	 */
	@Test
	void aJarScannedConfigurationPropertiesClassesFieldsAppearAsMembers() throws Exception {
		File jar = fixtureJar("example.application.JarSettings", """
				package example.application;
				import org.springframework.boot.context.properties.ConfigurationProperties;
				@ConfigurationProperties(prefix = "app.jar-settings")
				public class JarSettings {
					private String name;
				}
				""");

		Node tree = tree(List.of(jarDependency(jar)));

		assertEquals(1, nodesLabeled(tree, "app.jar-settings.name (String)").size());
	}

	private File fixtureJar(String fqn) throws Exception {
		return fixtureJar(fqn, "package " + fqn.substring(0, fqn.lastIndexOf('.')) + "; public class "
				+ fqn.substring(fqn.lastIndexOf('.') + 1) + " implements example.application.DescribedStereotype {}");
	}

	private File fixtureJar(String fqn, String source) throws Exception {
		Path classesDir = JarFixtureBuilder.compileAll(tempDir, Map.of(
				"example.application.DescribedStereotype", DESCRIBED_STEREOTYPE_SOURCE,
				fqn, source));

		return JarFixtureBuilder.packageJar(classesDir, tempDir, "fixture-" + fqn, List.of(fqn), Map.of());
	}

	private Node tree(List<DependencyDescriptor> dependencies) {
		return structureViewProvider.createTree(host, new CachedSpringMetamodelIndex(springIndex), false, null, dependencies);
	}

	private static DependencyDescriptor jarDependency(File jar) {
		return DependencyDescriptor.jar("fixture", jar.getAbsolutePath());
	}

	private static String render(Node tree) {
		return AsciiStructureRenderer.render(StructureViewProvider.toStructureNode(tree));
	}

	private static List<Node> nodesLabeled(Node root, String label) {
		List<Node> result = new ArrayList<>();
		if (label.equals(root.getAttribute(JsonNodeHandler.TEXT))) {
			result.add(root);
		}
		root.getChildren().forEach(child -> result.addAll(nodesLabeled(child, label)));
		return result;
	}

}
