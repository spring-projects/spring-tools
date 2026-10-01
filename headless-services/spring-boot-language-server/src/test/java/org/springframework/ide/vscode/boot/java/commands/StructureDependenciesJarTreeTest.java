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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * dependency contributes appear in the including project's tree, in the root packages of that tree
 * exactly like a workspace project dependency's ({@link StructureDependenciesTreeTest}).
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
	void aTypeFromTheJarMatchingTheHostsRealCatalogAppearsInTheHostsPackage() throws Exception {
		File jar = fixtureJar("example.application.FromJar");

		Node tree = tree(List.of(jarDependency(jar)));

		assertEquals(1, nodesLabeled(tree, "e.application.FromJar").size());
	}

	/**
	 * A type from the JAR outside of the host's packages gets a root package of its own - the tree's
	 * root packages are the top-most packages with types of their own, whatever part of the tree
	 * the types come from.
	 */
	@Test
	void aTypeFromTheJarOutsideTheHostsPackagesGetsARootPackageOfItsOwn() throws Exception {
		File jar = fixtureJar("com.acme.outside.FromJarOutside");

		Node tree = tree(List.of(jarDependency(jar)));

		List<Node> outside = tree.getChildren().stream()
				.filter(child -> "com.acme.outside".equals(child.getAttribute(JsonNodeHandler.TEXT)))
				.toList();
		assertEquals(1, outside.size());
		assertEquals(1, nodesLabeled(outside.get(0), "c.a.o.FromJarOutside").size());
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

	/**
	 * A node for an element read from the JAR has no location, but a reference the client can have
	 * the IDE resolve and open (docs/structure-view-dependencies.md, 5.8) - by the including
	 * project, whose classpath the JAR is on, and the element's JDT binding key, which also tells it
	 * apart from a same-labelled sibling in its node id.
	 */
	@Test
	void aNodeFromTheJarCarriesAReferenceToOpenItByInsteadOfALocation() throws Exception {
		File jar = fixtureJar("example.application.JarSettings", """
				package example.application;
				import org.springframework.boot.context.properties.ConfigurationProperties;
				@ConfigurationProperties(prefix = "app.jar-settings")
				public class JarSettings implements DescribedStereotype {
					private String name;
				}
				""");

		Node tree = tree(List.of(jarDependency(jar)));
		String projectUri = host.getLocationUri().toASCIIString();

		Node type = nodesLabeled(tree, "e.application.JarSettings").get(0);
		assertNull(type.getAttribute(JsonNodeHandler.LOCATION));
		assertEquals(new JavaElementReference(projectUri, "Lexample/application/JarSettings;"), type.getAttribute(JsonNodeHandler.JAVA_ELEMENT));

		Node member = nodesLabeled(tree, "app.jar-settings.name (String)").get(0);
		assertNull(member.getAttribute(JsonNodeHandler.LOCATION));
		assertEquals(new JavaElementReference(projectUri, "Lexample/application/JarSettings;.name)Ljava/lang/String;"),
				member.getAttribute(JsonNodeHandler.JAVA_ELEMENT));
		assertTrue(((String) member.getAttribute(JsonNodeHandler.NODE_ID)).endsWith("Lexample/application/JarSettings;.name)Ljava/lang/String;"));

		// the MCP tools' view of the same node
		assertEquals(new JavaElementReference(projectUri, "Lexample/application/JarSettings;"),
				findStructureNode(StructureViewProvider.toStructureNode(tree), "e.application.JarSettings").javaElement());
	}

	@Test
	void aNodeOfTheHostsOwnHasALocationAndNoReference() throws Exception {
		Node tree = tree(List.of(jarDependency(fixtureJar("example.application.FromJar"))));

		List<Node> withLocation = new ArrayList<>();
		collect(tree, withLocation);

		assertFalse(withLocation.isEmpty());
		withLocation.forEach(node -> assertNull(node.getAttribute(JsonNodeHandler.JAVA_ELEMENT), node.getAttribute(JsonNodeHandler.TEXT) + " has both"));
	}

	private static void collect(Node node, List<Node> withLocation) {
		if (node.getAttribute(JsonNodeHandler.LOCATION) != null) {
			withLocation.add(node);
		}
		node.getChildren().forEach(child -> collect(child, withLocation));
	}

	private static StructureViewProvider.StructureNode findStructureNode(StructureViewProvider.StructureNode node, String label) {
		if (label.equals(node.text())) {
			return node;
		}
		for (StructureViewProvider.StructureNode child : node.children()) {
			StructureViewProvider.StructureNode found = findStructureNode(child, label);
			if (found != null) {
				return found;
			}
		}
		return null;
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
