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
package org.springframework.ide.vscode.boot.java.utils;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import jdk.jfr.Configuration;
import jdk.jfr.Recording;

import org.eclipse.lsp4j.Diagnostic;
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
import org.springframework.ide.vscode.boot.index.cache.IndexCacheVoid;
import org.springframework.ide.vscode.boot.index.cache.IndexGsonTypeFactories;
import org.springframework.ide.vscode.boot.java.handlers.SpringComponentIndexer;
import org.springframework.ide.vscode.boot.java.reconcilers.JdtReconciler;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.maven.java.MavenJavaProject;
import org.springframework.ide.vscode.commons.protocol.spring.SpringIndexElement;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness.CustomizableProjectContent;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

/**
 * Not a test, but a timing harness for indexing the Java sources of a large project from scratch:
 * generates a project with many Spring and jMolecules types on top of
 * {@code test-stereotypes-support}, then times a full, uncached scan of it with a
 * {@link SpringIndexerJava} of its own - one that publishes into nothing but this harness, so the
 * language server's own index is left alone.
 *
 * <p>Prints a fingerprint of the index elements and diagnostics a scan published, so that an
 * optimization can be checked to leave them exactly as they were.
 *
 * <p>Only runs when asked for:
 * {@code ./mvnw test -pl spring-boot-language-server -Dtest=SpringIndexerJavaTimingTest -Dindexing-timing=true},
 * with {@code -Dindexing-timing.files=<n>} for the number of generated source files (2000 by
 * default), {@code -Dindexing-timing.dump=<directory>} to write the fingerprinted results there and
 * {@code -Dindexing-timing.jfr=<file>} to record a Flight Recorder profile of the measured runs.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
@EnabledIfSystemProperty(named = "indexing-timing", matches = "true")
public class SpringIndexerJavaTimingTest {

	private static final int WARMUP_RUNS = 2;
	private static final int MEASURED_RUNS = 5;

	private static final int PACKAGES = 20;

	private static final Pattern IDENTITY_HASH = Pattern.compile("@[0-9a-f]+\\b");

	private static final Type INDEX_ELEMENTS = new TypeToken<List<SpringIndexElement>>() {}.getType();

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringComponentIndexer[] componentIndexers;
	@Autowired private JdtReconciler jdtReconciler;
	@Autowired private CompilationUnitCache cuCache;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
	}

	@Test
	void timeFullScan() throws Exception {
		int files = Integer.getInteger("indexing-timing.files", 2000);

		MavenJavaProject project = ProjectsHarness.INSTANCE.mavenProject("test-stereotypes-support", false,
				content -> generateSources(content, files));
		harness.useProject(project);
		harness.getProjectFinder().find(new TextDocumentIdentifier(project.getLocationUri().toASCIIString())).get();
		indexer.waitOperation().get(5, TimeUnit.MINUTES);

		RecordingSymbolHandler published = new RecordingSymbolHandler();
		SpringIndexerJava javaIndexer = new SpringIndexerJava(published, componentIndexers, new IndexCacheVoid(), harness.getProjectFinder(),
				harness.getServer().getProgressService(), jdtReconciler,
				(doc, aggregator) -> harness.getServer().createProblemCollector(doc, aggregator), new JsonObject(), cuCache);

		for (int i = 0; i < WARMUP_RUNS; i++) {
			javaIndexer.initializeProject(project, true);
		}

		// a profile of the measured runs, if asked for - to see where the time goes
		String jfrFile = System.getProperty("indexing-timing.jfr");
		Recording recording = jfrFile == null ? null : new Recording(Configuration.getConfiguration("profile"));
		if (recording != null) {
			recording.start();
		}

		long min = Long.MAX_VALUE;
		long total = 0;
		for (int i = 0; i < MEASURED_RUNS; i++) {
			published.clear();

			long start = System.nanoTime();
			javaIndexer.initializeProject(project, true);
			long duration = System.nanoTime() - start;

			min = Math.min(min, duration);
			total += duration;
		}

		if (recording != null) {
			recording.stop();
			recording.dump(Path.of(jfrFile));
			recording.close();
		}

		System.out.println("[indexing-timing] full scan: avg %d ms, min %d ms (%d runs)".formatted(
				TimeUnit.NANOSECONDS.toMillis(total / MEASURED_RUNS), TimeUnit.NANOSECONDS.toMillis(min), MEASURED_RUNS));

		assertFalse(published.elements.isEmpty());

		// the project lands in a fresh temp directory on every run, which every location contains
		String projectDirectory = new File(project.getLocationUri()).getName();

		int elementCount = published.elements.values().stream().mapToInt(List::size).sum();
		int diagnosticCount = published.diagnostics.values().stream().mapToInt(List::size).sum();

		System.out.println("[indexing-timing] index elements: %d in %d documents, fingerprint %s".formatted(elementCount,
				published.elements.size(), fingerprint(elementsJson(published.elements), projectDirectory, "index-elements")));
		System.out.println("[indexing-timing] diagnostics: %d, fingerprint %s".formatted(diagnosticCount,
				fingerprint(diagnosticsText(published.diagnostics), projectDirectory, "diagnostics")));
	}

	/**
	 * Every document's elements, by document - serialized the way the index cache serializes them.
	 */
	private static String elementsJson(Map<String, List<SpringIndexElement>> elements) {
		Gson gson = new GsonBuilder()
				.registerTypeAdapterFactory(IndexGsonTypeFactories.springIndexElements())
				.setPrettyPrinting()
				.create();

		// sets - of supertypes, for example - come out in no particular order
		StringBuilder result = new StringBuilder();
		elements.forEach((docURI, docElements) -> result.append(docURI).append('\n')
				.append(gson.toJson(canonical(gson.toJsonTree(docElements, INDEX_ELEMENTS)))).append('\n'));
		return result.toString();
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

	private static String diagnosticsText(Map<String, List<Diagnostic>> diagnostics) {
		StringBuilder result = new StringBuilder();
		diagnostics.forEach((docURI, docDiagnostics) -> {
			result.append(docURI).append('\n');
			// the data of a diagnostic holds objects that print their identity hash code
			docDiagnostics.forEach(diagnostic -> result.append(IDENTITY_HASH.matcher(diagnostic.toString()).replaceAll("@")).append('\n'));
		});
		return result.toString();
	}

	private static String fingerprint(String text, String projectDirectory, String name) throws Exception {
		String canonical = text.replace(projectDirectory, "PROJECT");

		String dumpDirectory = System.getProperty("indexing-timing.dump");
		if (dumpDirectory != null) {
			Files.writeString(Path.of(dumpDirectory, name + ".txt"), canonical);
		}

		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
	}

	/**
	 * Keeps what a scan publishes, by document and in document order, instead of handing it to an index.
	 */
	private static class RecordingSymbolHandler implements SymbolHandler {

		final Map<String, List<SpringIndexElement>> elements = new TreeMap<>();
		final Map<String, List<Diagnostic>> diagnostics = new TreeMap<>();

		void clear() {
			elements.clear();
			diagnostics.clear();
		}

		@Override
		public synchronized void addSymbols(IJavaProject project, String docURI, List<SpringIndexElement> beanDefinitions, List<Diagnostic> diagnostics) {
			if (beanDefinitions != null) {
				this.elements.put(docURI, beanDefinitions);
			}
			if (diagnostics != null) {
				this.diagnostics.put(docURI, diagnostics);
			}
		}

		@Override
		public synchronized void addSymbols(IJavaProject project, Map<String, List<SpringIndexElement>> beanDefinitionsByDoc, Map<String, List<Diagnostic>> diagnosticsByDoc) {
			if (beanDefinitionsByDoc != null) {
				this.elements.putAll(beanDefinitionsByDoc);
			}
			if (diagnosticsByDoc != null) {
				this.diagnostics.putAll(diagnosticsByDoc);
			}
		}

		@Override
		public synchronized void removeSymbols(IJavaProject project, String docURI) {
			elements.remove(docURI);
			diagnostics.remove(docURI);
		}
	}

	/**
	 * Spreads the given number of source files across {@link #PACKAGES} packages below the project's
	 * main application package, cycling through a mix of what real projects have - including what
	 * sends a file to the second, full-AST pass (bean methods, event publishing) and what the
	 * reconcilers report problems for (field injection).
	 */
	private static void generateSources(CustomizableProjectContent content, int files) throws Exception {
		for (int i = 0; i < files; i++) {
			String packageName = "example.application.generated.p" + (i % PACKAGES);
			String name = "Generated" + i;
			content.createType(packageName + "." + name, source(packageName, name, i));
		}
	}

	private static String source(String packageName, String name, int i) {
		return switch (i % 8) {
			case 0 -> """
					package %1$s;

					import org.springframework.web.bind.annotation.GetMapping;
					import org.springframework.web.bind.annotation.PostMapping;
					import org.springframework.web.bind.annotation.RequestMapping;
					import org.springframework.web.bind.annotation.RestController;

					@RestController
					@RequestMapping("/%2$s")
					public class %2$s {

						@GetMapping("/one")
						public String one() {
							return "one";
						}

						@GetMapping("/two")
						public String two(String value) {
							return value.trim();
						}

						@PostMapping("/three")
						public void three(String value) {
							System.out.println(value);
						}
					}
					""".formatted(packageName, name);
			case 1 -> """
					package %1$s;

					import org.springframework.beans.factory.annotation.Autowired;
					import org.springframework.core.env.Environment;
					import org.springframework.stereotype.Service;

					@Service
					public class %2$s {

						@Autowired
						private Environment environment;

						public String doSomething() {
							return environment.getProperty("some.property");
						}
					}
					""".formatted(packageName, name);
			case 2 -> """
					package %1$s;

					import org.springframework.context.annotation.Bean;
					import org.springframework.context.annotation.Configuration;

					@Configuration
					public class %2$s {

						@Bean
						public StringBuilder builder%2$s() {
							return new StringBuilder("%2$s");
						}

						@Bean
						public Runnable runnable%2$s() {
							return () -> System.out.println("%2$s");
						}
					}
					""".formatted(packageName, name);
			case 3 -> """
					package %1$s;

					import org.springframework.context.ApplicationEventPublisher;
					import org.springframework.stereotype.Component;

					@Component
					public class %2$s {

						private final ApplicationEventPublisher publisher;

						public %2$s(ApplicationEventPublisher publisher) {
							this.publisher = publisher;
						}

						public void publish() {
							publisher.publishEvent("%2$s");
						}
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
							System.out.println(event);
						}
					}
					""".formatted(packageName, name);
			case 5 -> """
					package %1$s;

					@org.jmolecules.ddd.annotation.AggregateRoot
					public class %2$s {

						@org.jmolecules.ddd.annotation.Identity
						private Long id;

						public Long getId() {
							return id;
						}
					}
					""".formatted(packageName, name);
			case 6 -> """
					package %1$s;

					import org.springframework.boot.context.properties.ConfigurationProperties;

					@ConfigurationProperties("generated.%3$s")
					public record %2$s(String name, int size) {
					}
					""".formatted(packageName, name, name.toLowerCase());
			default -> """
					package %1$s;

					import java.util.ArrayList;
					import java.util.List;

					public class %2$s {

						private final List<String> values = new ArrayList<>();

						public void add(String value) {
							values.add(value);
						}

						public int count() {
							return values.size();
						}
					}
					""".formatted(packageName, name);
		};
	}

}
