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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;

import com.google.gson.JsonArray;
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
	List<Node> structureTrees(Map<String, String> compareAgainst) throws Exception {
		return structureTrees(compareAgainst, null);
	}

	/**
	 * @param compareAgainst the snapshot to compare each project against, keyed by project name -
	 *        null to let the server use each project's most recent one
	 * @param groups the stereotype group identifiers to structure each project's tree by, keyed by
	 *        project name - null (for a project, or for the whole map) to use every group of that
	 *        project's catalog, matching {@link StructureViewProvider#createTree}'s own default
	 */
	@SuppressWarnings("unchecked")
	List<Node> structureTrees(Map<String, String> compareAgainst, Map<String, Set<String>> groups) throws Exception {
		JsonObject params = new JsonObject();
		params.addProperty("updateMetadata", false);

		if (compareAgainst != null) {
			JsonObject compareAgainstJson = new JsonObject();
			compareAgainst.forEach(compareAgainstJson::addProperty);
			params.add("compareAgainst", compareAgainstJson);
		}

		if (groups != null) {
			JsonObject groupsJson = new JsonObject();
			groups.forEach((projectName, ids) -> {
				JsonArray idsJson = new JsonArray();
				ids.forEach(idsJson::add);
				groupsJson.add(projectName, idsJson);
			});
			params.add("groups", groupsJson);
		}

		return (List<Node>) harness.getServer().getWorkspaceService()
				.executeCommand(new ExecuteCommandParams(STRUCTURE_CMD, List.of(params))).get();
	}

	/**
	 * The stereotype groups the given project's tree can be structured by, as returned by
	 * {@code sts/spring-boot/structure/groups} - the identifiers a {@code groups} argument of
	 * {@link #structureTrees(Map, Map)} can select among.
	 */
	static List<String> groupIdentifiersOf(BootLanguageServerHarness harness, String projectName) throws Exception {
		StructureViewProvider.Groups result = (StructureViewProvider.Groups) harness.getServer().getWorkspaceService()
				.executeCommand(new ExecuteCommandParams("sts/spring-boot/structure/groups", List.of(projectName))).get();

		return result.groups().stream().map(StructureViewProvider.Group::identifier).toList();
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

	/**
	 * Adds a brand-new source file to the project and waits for it to be indexed - for scenarios
	 * {@link #edit} can't set up, since it requires the file to already exist.
	 */
	void addType(String relativeFile, String content) throws Exception {
		File file = new File(projectDirectory, relativeFile);
		FileUtils.writeStringToFile(file, content, Charset.defaultCharset());
		indexer.createDocument(file.toURI().toString()).get(15, TimeUnit.SECONDS);
	}

	/**
	 * Removes a source file from the project and waits for the removal to be indexed - the
	 * counterpart to {@link #addType}.
	 */
	void removeType(String relativeFile) throws Exception {
		File file = new File(projectDirectory, relativeFile);
		String uri = file.toURI().toString();
		if (!file.delete()) {
			throw new IllegalStateException("test setup problem: could not delete " + relativeFile);
		}
		indexer.deleteDocument(uri).get(15, TimeUnit.SECONDS);
	}

	/**
	 * The one node of the given kind whose label contains the given text, searched depth first.
	 *
	 * <p>Fails loudly if more than one node matches, rather than silently returning whichever one
	 * traversal happens to reach first - a {@code labelPart} that isn't actually unique (e.g.
	 * "EventListener" also matching "EventListenerOnBean") has previously caused a test to find the
	 * wrong node depending on tree/traversal order, which can differ between platforms (e.g. file
	 * system directory enumeration order affects the order nodes get indexed and added in).
	 */
	static Node findNode(List<Node> roots, String kind, String labelPart) {
		return findNode(roots, kind, labelPart, label -> label.contains(labelPart), "containing");
	}

	/** Same, but only below the given node - the tree usually holds several similar members. */
	static Node findNode(Node within, String kind, String labelPart) {
		return findNode(within, kind, labelPart, label -> label.contains(labelPart), "containing");
	}

	/**
	 * Same as {@link #findNode(List, String, String)}, but requires the label to <em>end with</em>
	 * the given text rather than merely contain it.
	 *
	 * <p>Needed for a type whose simple name is a prefix of a sibling's (e.g.
	 * "CustomEventPublisher" is a prefix of "CustomEventPublisherWithAdditionalElements") - no
	 * amount of spelling out more of the label helps there, since a prefix of a prefix is still a
	 * prefix and so still matches both. A simple class name, on the other hand, is never a suffix
	 * of a genuinely different sibling's.
	 */
	static Node findNodeEndingWith(List<Node> roots, String kind, String labelSuffix) {
		return findNode(roots, kind, labelSuffix, label -> label.endsWith(labelSuffix), "ending with");
	}

	private static Node findNode(List<Node> roots, String kind, String labelPart, Predicate<String> labelMatches, String relation) {
		List<Node> matches = new ArrayList<>();
		roots.forEach(root -> collectMatches(root, kind, labelMatches, matches));
		return theOneMatch(matches, kind, labelPart, relation, "in the structure tree");
	}

	private static Node findNode(Node within, String kind, String labelPart, Predicate<String> labelMatches, String relation) {
		List<Node> matches = new ArrayList<>();
		collectMatches(within, kind, labelMatches, matches);
		return theOneMatch(matches, kind, labelPart, relation, "below " + within.getAttribute(JsonNodeHandler.TEXT));
	}

	private static Node theOneMatch(List<Node> matches, String kind, String labelPart, String relation, String scope) {
		if (matches.isEmpty()) {
			throw new AssertionError("no " + kind + " node with a label " + relation + " '" + labelPart + "' " + scope);
		}
		if (matches.size() > 1) {
			String labels = matches.stream()
					.map(node -> String.valueOf(node.getAttribute(JsonNodeHandler.TEXT)))
					.collect(Collectors.joining("', '", "'", "'"));
			throw new AssertionError(matches.size() + " " + kind + " nodes have a label " + relation + " '" + labelPart
					+ "' " + scope + " - use a more specific search to tell them apart: " + labels);
		}
		return matches.get(0);
	}

	private static void collectMatches(Node node, String kind, Predicate<String> labelMatches, List<Node> matches) {
		Object label = node.getAttribute(JsonNodeHandler.TEXT);
		if (kind.equals(node.getAttribute(JsonNodeHandler.KIND)) && label != null && labelMatches.test(label.toString())) {
			matches.add(node);
		}

		node.getChildren().forEach(child -> collectMatches(child, kind, labelMatches, matches));
	}

	/**
	 * A deterministic, sorted, one-line-per-changed-node description of every {@code change}
	 * attribute in the given tree (added/removed/modified/containsChanges), qualified by the kind
	 * and label path leading to it - e.g. {@code "type:MyApp > example > SampleController=removed"}.
	 * Unchanged nodes contribute nothing, since {@code JsonNodeHandler} only ever sets the attribute
	 * when there is a change to report.
	 *
	 * <p>This is the golden-master this refactoring is checked against (see "R0" in
	 * {@code docs/structure-diff-elements.md}): a scenario's recorded output here must come out
	 * byte-for-byte identical before and after the element-based snapshot rework, which is what
	 * turns "pure refactoring" from a claim into something a test enforces.
	 */
	static String describeChanges(Node root) {
		List<String> lines = new ArrayList<>();
		collectChangeDescriptions(root, "", lines);
		lines.sort(null);
		return String.join("\n", lines);
	}

	private static void collectChangeDescriptions(Node node, String path, List<String> lines) {
		String label = String.valueOf(node.getAttribute(JsonNodeHandler.TEXT));
		String here = path.isEmpty() ? label : path + " > " + label;

		Object change = node.getAttribute(JsonNodeHandler.CHANGE);
		if (change != null) {
			lines.add(node.getAttribute(JsonNodeHandler.KIND) + ":" + here + "=" + change);
		}

		node.getChildren().forEach(child -> collectChangeDescriptions(child, here, lines));
	}

}
