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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
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
import org.springframework.ide.vscode.boot.java.stereotypes.JarFixtureBuilder;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeCatalogRegistry;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.commons.java.IClasspathUtil;
import org.springframework.ide.vscode.commons.java.IJavaProject;
import org.springframework.ide.vscode.commons.languageserver.java.JavaProjectFinder;
import org.springframework.ide.vscode.commons.protocol.java.Classpath;
import org.springframework.ide.vscode.project.harness.BootLanguageServerHarness;
import org.springframework.ide.vscode.project.harness.ProjectsHarness;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * The parity harness of {@code docs/structure-view-dependencies.md} (5.4): the same fixture
 * project, once indexed from source (the live index) and once compiled and scanned as a JAR, has to
 * produce the same method labels and the same members for every type - the two pipelines are
 * separate implementations of the same rules, and this is what keeps them from drifting apart.
 *
 * <p>Known, accepted divergences are listed per fixture, each with its reason. An entry that no
 * longer diverges fails the test as well, so the list cannot go stale.
 *
 * @author Martin Lippert
 */
@ExtendWith(SpringExtension.class)
@BootLanguageServerTest
@Import(IndexerTestConf.class)
public class StructureParityTest {

	@Autowired private BootLanguageServerHarness harness;
	@Autowired private JavaProjectFinder projectFinder;
	@Autowired private SpringSymbolIndex indexer;
	@Autowired private SpringMetamodelIndex springIndex;
	@Autowired private StereotypeCatalogRegistry catalogRegistry;
	@Autowired private JarDependencySource jarDependencySource;

	@TempDir
	Path tempDir;

	@BeforeEach
	public void setup() throws Exception {
		harness.intialize(null);
	}

	private static final String REQUEST_MAPPINGS_PENDING = "request mappings are not read from JARs yet (step 5.5, item 1)";
	private static final String BEAN_METHODS_PENDING = "@Bean methods are not read from JARs yet (step 5.5, item 3)";

	@Test
	void stereotypesSupport() throws Exception {
		assertParity("test-stereotypes-support", Map.of(
				"example.application.SampleController: members source=[@/greeting -- GET] jar=[]", REQUEST_MAPPINGS_PENDING,
				"example.application.SampleController: methods source=[@/greeting -- GET] jar=[SampleController.sayHello() : String]", REQUEST_MAPPINGS_PENDING));
	}

	@Test
	void configurationProperties() throws Exception {
		assertParity("test-configuration-properties-indexing", Map.of(
				"com.example.configproperties.MyController: members source=[@/configs -- GET, @/bundleconfigs -- GET, @/recordconfigs -- GET] jar=[]",
				REQUEST_MAPPINGS_PENDING,
				"com.example.configproperties.MyController: methods source=[@/configs -- GET, @/bundleconfigs -- GET, @/recordconfigs -- GET] "
						+ "jar=[MyController.getExampleConfig() : String, MyController.getBundleConfig() : String, MyController.getRecordConfig() : String]",
				REQUEST_MAPPINGS_PENDING));
	}

	@Test
	void dataRepositories() throws Exception {
		assertParity("test-spring-data-symbols", Map.of(
				"org.test.Application: members source=[@+ 'demo' (@Bean) CommandLineRunner] jar=[]", BEAN_METHODS_PENDING,
				"org.test.web.DataRestController: members source=[@/something -- GET] jar=[]", REQUEST_MAPPINGS_PENDING,
				"org.test.web.DataRestController: methods source=[@/something -- GET] jar=[DataRestController.saySomething() : String]",
				REQUEST_MAPPINGS_PENDING));
	}

	/**
	 * @param knownDivergences the differences that are accepted, keyed as the failure message
	 *        reports them, with the reason as value
	 */
	private void assertParity(String fixture, Map<String, String> knownDivergences) throws Exception {
		File directory = new File(ProjectsHarness.class.getResource("/test-projects/" + fixture + "/").toURI());
		IJavaProject project = projectFinder.find(new TextDocumentIdentifier(directory.toURI().toString())).get();
		indexer.waitOperation().get(30, TimeUnit.SECONDS);

		AbstractStereotypeCatalog catalog = catalogRegistry.getCatalogOf(project);

		IndexStructureElements source = IndexStructureElements.of(project, new CachedSpringMetamodelIndex(springIndex), catalog);
		Map<String, TypeView> fromSource = views(source, source.types());

		JarDependencySource.ScanResult scan = jarDependencySource.scan(project, compileToJar(fixture, directory, project));
		JarStructureElements jar = new JarStructureElements(scan.types(), scan.beans(), catalog);
		Map<String, TypeView> fromJar = views(jar, scan.types());

		Set<String> divergences = divergences(fromSource, fromJar);

		Set<String> unexpected = new TreeSet<>(divergences);
		unexpected.removeAll(knownDivergences.keySet());

		Set<String> stale = new TreeSet<>(knownDivergences.keySet());
		stale.removeAll(divergences);

		assertEquals(Set.of(), unexpected, fixture + ": source and JAR differ");
		assertEquals(Set.of(), stale, fixture + ": listed as known divergences, but no longer differ");
	}

	private record TypeView(List<String> methods, List<String> members) {
	}

	private static Map<String, TypeView> views(StructureElements elements, List<StereotypeClassElement> types) {
		Map<String, TypeView> result = new LinkedHashMap<>();
		for (StereotypeClassElement type : types) {
			List<String> methods = type.getMethods().stream().map(method -> elements.methodLabel(method, type)).toList();
			List<String> members = elements.membersOf(type).stream().map(StructureMember::label).toList();
			result.put(type.getType(), new TypeView(methods, members));
		}
		return result;
	}

	private static Set<String> divergences(Map<String, TypeView> fromSource, Map<String, TypeView> fromJar) {
		Set<String> result = new LinkedHashSet<>();

		Set<String> types = new TreeSet<>(fromSource.keySet());
		types.addAll(fromJar.keySet());

		for (String type : types) {
			TypeView source = fromSource.get(type);
			TypeView jar = fromJar.get(type);

			if (source == null || jar == null) {
				result.add(type + ": only in " + (source == null ? "JAR" : "source"));
				continue;
			}
			if (!source.methods().equals(jar.methods())) {
				result.add(type + ": methods source=" + source.methods() + " jar=" + jar.methods());
			}
			if (!source.members().equals(jar.members())) {
				result.add(type + ": members source=" + source.members() + " jar=" + jar.members());
			}
		}

		return result;
	}

	/**
	 * The fixture's main sources, compiled against the project's own resolved classpath and
	 * packaged as the JAR a dependency on it would be.
	 */
	private File compileToJar(String fixture, File directory, IJavaProject project) throws Exception {
		Path sourceRoot = directory.toPath().resolve("src/main/java");
		Map<String, String> sources = new LinkedHashMap<>();
		try (Stream<Path> files = Files.walk(sourceRoot)) {
			for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
				String relative = sourceRoot.relativize(file).toString().replace(File.separatorChar, '/');
				sources.put(relative.substring(0, relative.length() - ".java".length()).replace('/', '.'), Files.readString(file, StandardCharsets.UTF_8));
			}
		}

		List<File> classpath = new ArrayList<>();
		for (Classpath.CPE cpe : project.getClasspath().getClasspathEntries()) {
			if (Classpath.isBinary(cpe) && !cpe.isSystem()) {
				classpath.add(IClasspathUtil.binaryLocation(cpe));
			}
		}

		Path fixtureDir = Files.createDirectories(tempDir.resolve(fixture));
		Path classes = JarFixtureBuilder.compileAll(fixtureDir, sources, classpath);
		return JarFixtureBuilder.packageJar(classes, fixtureDir, fixture, sources.keySet(), Map.of());
	}

}
