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

import java.io.File;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;

import com.google.gson.JsonObject;

/**
 * The moves every structure tree test makes: ask the server for the trees, edit a source file and
 * wait for it to be re-indexed, and find a node in the result.
 *
 * @author Martin Lippert
 */
class StructureTreeTestFixture {

	private static final String STRUCTURE_CMD = "sts/spring-boot/structure";

	private final BootLanguageServerHarness harness;
	private final SpringSymbolIndex indexer;
	private final File projectDirectory;

	StructureTreeTestFixture(BootLanguageServerHarness harness, SpringSymbolIndex indexer, File projectDirectory) {
		this.harness = harness;
		this.indexer = indexer;
		this.projectDirectory = projectDirectory;
	}

	List<Node> structureTrees() throws Exception {
		return structureTrees(null);
	}

	/**
	 * @param compareAgainst the snapshot to compare each project against, keyed by project name -
	 *        null to let the server use each project's most recent one
	 */
	@SuppressWarnings("unchecked")
	List<Node> structureTrees(Map<String, String> compareAgainst) throws Exception {
		JsonObject params = new JsonObject();
		params.addProperty("updateMetadata", false);

		if (compareAgainst != null) {
			JsonObject compareAgainstJson = new JsonObject();
			compareAgainst.forEach(compareAgainstJson::addProperty);
			params.add("compareAgainst", compareAgainstJson);
		}

		return (List<Node>) harness.getServer().getWorkspaceService()
				.executeCommand(new ExecuteCommandParams(STRUCTURE_CMD, List.of(params))).get();
	}

	/**
	 * Replaces text in a source file of the project and waits for the re-index, failing loudly if
	 * the text wasn't there - a silently unmatched replacement makes a test pass for the wrong
	 * reason.
	 *
	 * <p>{@code from}/{@code to} are always written with a bare {@code \n}, but the checked-out
	 * file may use {@code \r\n} (e.g. on Windows, depending on {@code core.autocrlf}). Translating
	 * the patterns to whatever the file actually uses - rather than normalizing the file's own
	 * content - keeps the match working either way, and just as importantly keeps a {@code to} that
	 * inserts a new line from leaving a document with a mix of both line endings, which the AST
	 * parser accepts but throws content-hash offset accounting off enough to make the edit silently
	 * invisible to the diff.
	 */
	void edit(String relativeFile, String from, String to) throws Exception {
		String uri = new File(projectDirectory, relativeFile).toURI().toString();
		String original = FileUtils.readFileToString(new File(new URI(uri)), Charset.defaultCharset());

		String lineSeparator = original.contains("\r\n") ? "\r\n" : "\n";
		String changed = original.replace(from.replace("\n", lineSeparator), to.replace("\n", lineSeparator));

		if (original.equals(changed)) {
			throw new IllegalStateException("test setup problem: replacement did not match in " + relativeFile);
		}

		indexer.updateDocument(uri, changed, "test triggered").get(15, TimeUnit.SECONDS);
	}

	/** The first node of the given kind whose label contains the given text, searched depth first. */
	static Node findNode(List<Node> roots, String kind, String labelPart) {
		for (Node root : roots) {
			Node found = findNodeOrNull(root, kind, labelPart);
			if (found != null) {
				return found;
			}
		}
		throw new AssertionError("no " + kind + " node with a label containing '" + labelPart + "' in the structure tree");
	}

	/** Same, but only below the given node - the tree usually holds several similar members. */
	static Node findNode(Node within, String kind, String labelPart) {
		Node found = findNodeOrNull(within, kind, labelPart);
		if (found == null) {
			throw new AssertionError("no " + kind + " node with a label containing '" + labelPart + "' below "
					+ within.getAttribute(JsonNodeHandler.TEXT));
		}
		return found;
	}

	private static Node findNodeOrNull(Node node, String kind, String labelPart) {
		Object label = node.getAttribute(JsonNodeHandler.TEXT);
		if (kind.equals(node.getAttribute(JsonNodeHandler.KIND)) && label != null && label.toString().contains(labelPart)) {
			return node;
		}

		for (Node child : node.getChildren()) {
			Node found = findNodeOrNull(child, kind, labelPart);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

}
