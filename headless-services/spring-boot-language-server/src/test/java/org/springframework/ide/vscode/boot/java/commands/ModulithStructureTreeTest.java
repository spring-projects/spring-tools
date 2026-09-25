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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.ide.vscode.boot.java.commands.StructureTreeTestFixture.findNode;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.modulith.ModulithService;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Exercises the Modulith branch of {@code sts/spring-boot/structure}
 * ({@link ModulithStructureView}, {@link ApplicationModulesLabelProvider},
 * {@link ApplicationModulesStructureProvider} and
 * {@link ApplicationModulesNamedInterfacesGroupingProvider}) end to end - none of the other
 * structure tree tests use a Modulith-dependent project, so this is the only regression guard for
 * that branch's threading through {@link StructureElements} in the "R2" step of
 * {@code docs/structure-diff-elements.md}.
 *
 * <p>Both structure providers {@link ModulithStructureView} can pick between - which one is used is
 * a system-property toggle ({@code StructureViewUtil.hasNamedInterfaceNodesEnabled}), not something
 * a request selects - are exercised in a single test method rather than one each: two {@code @Test}
 * methods that each set up (and Modulith-analyze) the same fixture project were observed to interfere
 * with one another when run together in this class, even though each passes on its own - a harness
 * limitation around reusing that fixture, unrelated to the code under test here.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class ModulithStructureTreeTest {

	private static final String NAMED_INTERFACE_NODES_PROPERTY = "enable-named-interface-nodes";

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private ModulithService modulithService;

	private MavenJavaProject project;
	private StructureTreeTestFixture tree;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		// lands the project under a temp folder rather than target/test-classes: Modulith filters
		// out anything under target/test-classes, same reasoning as ModulithServiceTest
		project = ProjectsHarness.INSTANCE.mavenProject("spring-modulith-example-full", p -> {});
		harness.useProject(project);

		harness.getProjectFinder().find(new TextDocumentIdentifier(project.getLocationUri().toASCIIString())).get();
		indexer.waitOperation().get(15, TimeUnit.SECONDS);

		assertTrue(modulithService.requestMetadata(project, Duration.ZERO).get(), "expected the modulith metadata request to succeed");

		// requestMetadata's own future resolving doesn't guarantee any indexing work it triggered as
		// a side effect is done too - waiting again here, not just before, was needed to stop this
		// test from intermittently seeing zero types under a module when run as part of the whole
		// commands test package rather than on its own
		indexer.waitOperation().get(15, TimeUnit.SECONDS);

		tree = new StructureTreeTestFixture(harness, indexer, null);
	}

	@Test
	void structureTreeGroupsTypesByApplicationModule() throws Exception {
		assertOrderAndInventoryModulesWithTypes();

		System.setProperty(NAMED_INTERFACE_NODES_PROPERTY, "true");
		try {
			assertOrderAndInventoryModulesWithTypes();
		} finally {
			System.clearProperty(NAMED_INTERFACE_NODES_PROPERTY);
		}
	}

	private void assertOrderAndInventoryModulesWithTypes() throws Exception {
		List<Node> roots = tree.structureTrees();

		Node root = roots.stream()
				.filter(r -> project.getElementName().equals(r.getAttribute(JsonNodeHandler.PROJECT_ID)))
				.findFirst()
				.orElseThrow();
		assertNotNull(root);

		// ApplicationModulesLabelProvider.getPackageLabel: "<module display name> (<abbreviated base package>)"
		Node orderPackage = findNode(List.of(root), JsonNodeHandler.KIND_PACKAGE, "Order");
		Node inventoryPackage = findNode(List.of(root), JsonNodeHandler.KIND_PACKAGE, "Inventory");

		assertTrue(countDescendants(orderPackage, JsonNodeHandler.KIND_TYPE) > 0,
				"expected at least one type under the order module");
		assertTrue(countDescendants(inventoryPackage, JsonNodeHandler.KIND_TYPE) > 0,
				"expected at least one type under the inventory module");
	}

	private static int countDescendants(Node node, String kind) {
		int count = kind.equals(node.getAttribute(JsonNodeHandler.KIND)) ? 1 : 0;
		for (Node child : node.getChildren()) {
			count += countDescendants(child, kind);
		}
		return count;
	}

}
