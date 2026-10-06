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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.ide.vscode.boot.app.SpringSymbolIndex;
import org.springframework.ide.vscode.boot.bootiful.BootLanguageServerTest;
import org.springframework.ide.vscode.boot.bootiful.IndexerTestConf;
import org.springframework.ide.vscode.boot.index.SpringMetamodelIndex;
import org.springframework.ide.vscode.boot.java.commands.JsonNodeHandler.Node;
import org.springframework.ide.vscode.boot.java.commands.StructureViewProvider.StructureNode;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness.CustomizableProjectContent;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Not a test, but a timing harness for building the structure tree of a large project: generates a
 * project with many stereotyped and plain types on top of {@code test-stereotypes-support}, then
 * times building its tree, capturing a snapshot of it and rebuilding a tree from that snapshot.
 *
 * <p>Prints a fingerprint of each result too, so that an optimization can be checked to leave the
 * trees exactly as they were.
 *
 * <p>Only runs when asked for:
 * {@code ./mvnw test -pl spring-boot-language-server -Dtest=StructureTreeTimingTest -Dstructure-timing=true},
 * with {@code -Dstructure-timing.types=<n>} for the number of generated types (2000 by default) and
 * {@code -Dstructure-timing.dump=<directory>} to write the fingerprinted results there.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
@EnabledIfSystemProperty(named = "structure-timing", matches = "true")
public class StructureTreeTimingTest {

	private static final int WARMUP_RUNS = 5;
	private static final int MEASURED_RUNS = 10;

	private static final int PACKAGES = 20;

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private StructureViewProvider structureViewProvider;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
	}

	@Test
	void timeStructureTree() throws Exception {
		int types = Integer.getInteger("structure-timing.types", 2000);

		MavenJavaProject project = ProjectsHarness.INSTANCE.mavenProject("test-stereotypes-support", false,
				content -> generateTypes(content, types));
		harness.useProject(project);
		harness.getProjectFinder().find(new TextDocumentIdentifier(project.getLocationUri().toASCIIString())).get();
		indexer.waitOperation().get(5, TimeUnit.MINUTES);

		// the project lands in a fresh temp directory on every run, which the locations in the tree contain
		String projectDirectory = new File(project.getLocationUri()).getName();

		Node tree = time("createTree", () -> structureViewProvider.createTree(project, new CachedSpringMetamodelIndex(springIndex), false, null));
		assertNotNull(tree);
		System.out.println("[structure-timing] live tree: " + nodeCount(tree) + " nodes, fingerprint " + fingerprint(tree, projectDirectory, "live-tree"));
		System.out.println("[structure-timing] stereotype groups: " + stereotypeLabels(tree, new TreeSet<>()));

		StructureElementSnapshot snapshot = time("captureSnapshot", () -> structureViewProvider.captureSnapshot(project));
		System.out.println("[structure-timing] snapshot: " + snapshot.types().size() + " types, fingerprint " + fingerprint(snapshot, projectDirectory, "snapshot"));

		Node rebuilt = time("createTree from snapshot", () -> structureViewProvider.createTree(project, new SnapshotStructureElements(snapshot), null, false));
		assertNotNull(rebuilt);
		System.out.println("[structure-timing] rebuilt tree: " + nodeCount(rebuilt) + " nodes, fingerprint " + fingerprint(rebuilt, projectDirectory, "rebuilt-tree"));
	}

	private static <T> T time(String label, Supplier<T> task) {
		for (int i = 0; i < WARMUP_RUNS; i++) {
			task.get();
		}

		T result = null;
		long min = Long.MAX_VALUE;
		long total = 0;
		for (int i = 0; i < MEASURED_RUNS; i++) {
			long start = System.nanoTime();
			result = task.get();
			long duration = System.nanoTime() - start;
			min = Math.min(min, duration);
			total += duration;
		}

		System.out.println("[structure-timing] %s: avg %d ms, min %d ms (%d runs)".formatted(label,
				TimeUnit.NANOSECONDS.toMillis(total / MEASURED_RUNS), TimeUnit.NANOSECONDS.toMillis(min), MEASURED_RUNS));
		return result;
	}

	private static int nodeCount(Node node) {
		return 1 + node.getChildren().stream().mapToInt(StructureTreeTimingTest::nodeCount).sum();
	}

	private static Set<String> stereotypeLabels(Node node, Set<String> labels) {
		if (JsonNodeHandler.KIND_STEREOTYPE.equals(node.getAttribute(JsonNodeHandler.KIND))) {
			labels.add(String.valueOf(node.getAttribute(JsonNodeHandler.TEXT)));
		}
		node.getChildren().forEach(child -> stereotypeLabels(child, labels));
		return labels;
	}

	private static String fingerprint(Node tree, String projectDirectory, String name) throws Exception {
		StructureNode structureNode = StructureViewProvider.toStructureNode(tree);
		return fingerprint((Object) structureNode, projectDirectory, name);
	}

	/**
	 * A hash of the given value's JSON, with the project directory taken out and every array sorted:
	 * the index delivers types in no particular order, so siblings can come in any order from one run
	 * to the next. Writes that JSON to {@code structure-timing.dump}, if set, for a closer comparison.
	 */
	private static String fingerprint(Object value, String projectDirectory, String name) throws Exception {
		JsonElement canonical = canonical(JsonParser.parseString(new Gson().toJson(value).replace(projectDirectory, "PROJECT")));
		String json = new GsonBuilder().setPrettyPrinting().create().toJson(canonical);

		String dumpDirectory = System.getProperty("structure-timing.dump");
		if (dumpDirectory != null) {
			Files.writeString(Path.of(dumpDirectory, name + ".json"), json);
		}

		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
	}

	private static JsonElement canonical(JsonElement element) {
		if (element.isJsonObject()) {
			JsonObject result = new JsonObject();
			element.getAsJsonObject().entrySet().forEach(entry -> result.add(entry.getKey(), canonical(entry.getValue())));
			return result;
		}
		if (element.isJsonArray()) {
			List<JsonElement> children = new ArrayList<>();
			element.getAsJsonArray().forEach(child -> children.add(canonical(child)));
			children.sort(Comparator.comparing(JsonElement::toString));
			JsonArray result = new JsonArray();
			children.forEach(result::add);
			return result;
		}
		return element;
	}

	/**
	 * Spreads the given number of types across {@link #PACKAGES} packages below the project's main
	 * application package, cycling through a mix of what real projects have: Spring stereotypes with
	 * request mappings, jMolecules annotations and interfaces, a package-level stereotype, and plain
	 * classes that end up in "Others".
	 */
	private static void generateTypes(CustomizableProjectContent content, int types) throws Exception {
		for (int pkg = 0; pkg < PACKAGES; pkg++) {
			String packageName = "example.application.generated.p" + pkg;
			if (pkg % 5 == 0) {
				content.createType(packageName + ".package-info", """
						@org.jmolecules.architecture.hexagonal.SecondaryAdapter
						package %s;
						""".formatted(packageName));
			}
		}

		for (int i = 0; i < types; i++) {
			String packageName = "example.application.generated.p" + (i % PACKAGES);
			String name = "Generated" + i;
			content.createType(packageName + "." + name, typeSource(packageName, name, i));
		}
	}

	private static String typeSource(String packageName, String name, int i) {
		return switch (i % 6) {
			case 0 -> """
					package %1$s;

					import org.springframework.web.bind.annotation.GetMapping;
					import org.springframework.web.bind.annotation.PostMapping;
					import org.springframework.web.bind.annotation.RestController;

					@RestController
					public class %2$s {

						@GetMapping("/%2$s/one")
						public String one() {
							return "one";
						}

						@GetMapping("/%2$s/two")
						public String two(String value) {
							return value;
						}

						@PostMapping("/%2$s/three")
						public void three(String value) {
						}
					}
					""".formatted(packageName, name);
			case 1 -> """
					package %1$s;

					import org.springframework.stereotype.Service;

					@Service
					public class %2$s {

						public void doSomething() {
						}
					}
					""".formatted(packageName, name);
			case 2 -> """
					package %1$s;

					@org.jmolecules.ddd.annotation.AggregateRoot
					public class %2$s {

						@org.jmolecules.ddd.annotation.Identity
						private Long id;
					}
					""".formatted(packageName, name);
			case 3 -> """
					package %1$s;

					public class %2$s implements org.jmolecules.ddd.types.ValueObject {
					}
					""".formatted(packageName, name);
			case 4 -> """
					package %1$s;

					import org.springframework.context.event.EventListener;
					import org.springframework.stereotype.Component;

					@Component
					public class %2$s {

						@EventListener
						public void on(String event) {
						}
					}
					""".formatted(packageName, name);
			default -> """
					package %1$s;

					public class %2$s {

						public void plain() {
						}
					}
					""".formatted(packageName, name);
		};
	}

}
