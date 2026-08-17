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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
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
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.JsonObject;

/**
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class StructureViewReferenceTest {

	private static final String STRUCTURE_CMD = "sts/spring-boot/structure";

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);

		File directory = new File(ProjectsHarness.class.getResource("/test-projects/test-stereotypes-support/").toURI());
		projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();

		CompletableFuture<Void> initProject = indexer.waitOperation();
		initProject.get(5, TimeUnit.SECONDS);
	}

	@Test
	void stereotypeReferencesPointAtTheDefinitionInTheCatalogFile() throws Exception {
		List<Location> references = stereotypeReferences();

		assertFalse(references.isEmpty(), "no stereotype node with a reference to a catalog file found");

		for (Location reference : references) {
			String[] lines = linesOf(reference.getUri());

			Position start = reference.getRange().getStart();
			Position end = reference.getRange().getEnd();

			assertTrue(start.getLine() > 0 || start.getCharacter() > 0,
					"reference doesn't point at the definition, but at the beginning of " + reference.getUri());

			// the definition starts with the quoted stereotype identifier and ends with the closing
			// brace of the definition itself
			String definitionStart = lines[start.getLine()].substring(start.getCharacter());
			assertTrue(definitionStart.startsWith("\""), "unexpected start of definition: " + definitionStart);

			String definitionEnd = lines[end.getLine()].substring(0, end.getCharacter());
			assertTrue(definitionEnd.endsWith("}"), "unexpected end of definition: " + definitionEnd);
		}
	}

	@SuppressWarnings("unchecked")
	private List<Location> stereotypeReferences() throws Exception {
		JsonObject params = new JsonObject();
		params.addProperty("updateMetadata", false);

		List<Node> roots = (List<Node>) harness.getServer().getWorkspaceService()
				.executeCommand(new ExecuteCommandParams(STRUCTURE_CMD, List.of(params))).get();

		List<Location> references = new ArrayList<>();
		roots.forEach(root -> collectReferences(root, references));

		return references;
	}

	private static void collectReferences(Node node, List<Location> references) {
		if (node.attributes.get("reference") instanceof Location location && location.getUri().endsWith(".json")) {
			references.add(location);
		}
		node.children.forEach(child -> collectReferences(child, references));
	}

	private static String[] linesOf(String uri) throws Exception {
		try (InputStream stream = URI.create(uri).toURL().openStream()) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n", -1);
		}
	}

}
