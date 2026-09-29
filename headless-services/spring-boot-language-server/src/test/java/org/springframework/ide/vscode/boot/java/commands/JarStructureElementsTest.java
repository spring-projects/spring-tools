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

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.jmolecules.stereotype.catalog.support.CatalogSource;
import org.jmolecules.stereotype.catalog.support.JsonPathStereotypeCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.boot.java.beans.ConfigPropertyIndexElement;
import org.springframework.ide.vscode.boot.java.requestmapping.RequestMappingIndexElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeClassElement;
import org.springframework.ide.vscode.boot.java.stereotypes.StereotypeMethodElement;
import org.springframework.ide.vscode.commons.protocol.spring.Bean;
import org.springframework.ide.vscode.commons.protocol.spring.DefaultValues;

/**
 * @author Martin Lippert
 */
public class JarStructureElementsTest {

	@TempDir
	Path tempDir;

	@Test
	void typesFiltersToWhatCurrentlyMatchesTheGivenCatalog() throws Exception {
		StereotypeClassElement matching = new StereotypeClassElement("com.example.Matching", null, Set.of(),
				Set.of("com.example.Marker"), null);
		StereotypeClassElement notMatching = new StereotypeClassElement("com.example.NotMatching", null, Set.of(), Set.of(), null);

		JarStructureElements elements = new JarStructureElements(List.of(matching, notMatching), Map.of(), catalogWithMarker());

		assertEquals(List.of(matching), elements.types());
	}

	/**
	 * The scanned list handed to the constructor is never re-filtered and cached: two different
	 * {@link JarStructureElements} built from the very same scanned list answer differently once
	 * the catalog differs - exactly what lets a stereotype definition change take effect without
	 * rescanning the JAR (see {@code docs/structure-view-dependencies.md}).
	 */
	@Test
	void theSameScannedListAnswersDifferentlyForADifferentCatalog() throws Exception {
		StereotypeClassElement element = new StereotypeClassElement("com.example.Marked", null, Set.of(), Set.of("com.example.Marker"), null);
		List<StereotypeClassElement> scanned = List.of(element);

		assertEquals(List.of(element), new JarStructureElements(scanned, Map.of(), catalogWithMarker()).types());
		assertEquals(List.of(), new JarStructureElements(scanned, Map.of(), catalogWithoutMarker()).types());
	}

	@Test
	void methodLabelIsTheOneComputedAtScanTimeSinceThereIsNoLiveIndexToConsult() throws Exception {
		StereotypeMethodElement method = new StereotypeMethodElement("handle", "handle() : String", "com.example.Foo.handle()", null,
				Set.of(), null);
		StereotypeClassElement type = new StereotypeClassElement("com.example.Foo", null, Set.of(), Set.of(), null);

		JarStructureElements elements = new JarStructureElements(List.of(type), Map.of(), catalogWithMarker());

		assertEquals("handle() : String", elements.methodLabel(method, type));
	}

	@Test
	void membersOfIsEmptyForATypeWithoutBeans() throws Exception {
		StereotypeClassElement type = new StereotypeClassElement("com.example.Foo", null, Set.of(), Set.of(), null);
		JarStructureElements elements = new JarStructureElements(List.of(type), Map.of(), catalogWithMarker());

		assertEquals(List.of(), elements.membersOf(type));
	}

	/**
	 * Members are the children of the type's beans, turned into members by the same code the live
	 * index's go through ({@link StructureMember#of}) - labels come from the elements themselves.
	 */
	@Test
	void membersAreTheChildrenOfTheTypesBeans() throws Exception {
		StereotypeClassElement type = new StereotypeClassElement("com.example.Foo", null, Set.of(), Set.of(), null);
		Bean bean = bean("com.example.Foo");
		bean.addChild(new ConfigPropertyIndexElement("app.name", "java.lang.String", EMPTY, null));

		JarStructureElements elements = new JarStructureElements(List.of(type), Map.of(type, List.of(bean)), catalogWithMarker());

		assertEquals(List.of(new StructureMember("app.name (String)", null, null)), elements.membersOf(type));
	}

	/**
	 * The shared request-mapping label lookup ({@code StructureViewUtil.getMethodLabel}), fed with
	 * the request mappings of the method's <em>own</em> type - not the contextual one, which in a
	 * group of methods across types is not the declaring type.
	 */
	@Test
	void aMethodDeclaringARequestMappingIsLabelledWithItsRoute() throws Exception {
		StereotypeMethodElement handler = new StereotypeMethodElement("greet", "Foo.greet() : String", "com.example.Foo.greet() : java.lang.String",
				null, Set.of(), null);
		StereotypeClassElement type = new StereotypeClassElement("com.example.Foo", null, Set.of(), Set.of(), null);
		type.addChild(handler);
		StereotypeClassElement otherType = new StereotypeClassElement("com.example.Other", null, Set.of(), Set.of(), null);

		Bean bean = bean("com.example.Foo");
		bean.addChild(new RequestMappingIndexElement("/greet", new String[] { "GET" }, null, null, null, EMPTY, "@/greet -- GET",
				"com.example.Foo.greet() : java.lang.String", null));

		JarStructureElements elements = new JarStructureElements(List.of(type, otherType), Map.of(type, List.of(bean)), catalogWithMarker());

		assertEquals("@/greet -- GET", elements.methodLabel(handler, otherType));
	}

	private static final Range EMPTY = new Range(new Position(0, 0), new Position(0, 0));

	private static Bean bean(String type) {
		return new Bean("foo", type, new Location("jar:file:/x.jar!/Foo.class", EMPTY), DefaultValues.EMPTY_INJECTION_POINTS, Set.of(),
				DefaultValues.EMPTY_ANNOTATIONS, false, "Foo");
	}

	@Test
	void packageNodeIsNeverConsultedByACompositeButStillAnsweredDefensively() throws Exception {
		JarStructureElements elements = new JarStructureElements(List.of(), Map.of(), catalogWithMarker());
		assertEquals("com.example", elements.packageNode("com.example").getPackageName());
	}

	private AbstractStereotypeCatalog catalogWithMarker() throws Exception {
		return catalogOf("""
				{ "stereotypes": { "custom": { "assignments": ["@com.example.Marker"] } } }
				""");
	}

	private AbstractStereotypeCatalog catalogWithoutMarker() throws Exception {
		return catalogOf("""
				{ "stereotypes": { } }
				""");
	}

	private AbstractStereotypeCatalog catalogOf(String json) throws Exception {
		Path file = Files.createTempFile(tempDir, "catalog", ".json");
		Files.writeString(file, json, StandardCharsets.UTF_8);
		URL url = file.toUri().toURL();

		return new JsonPathStereotypeCatalog(new CatalogSource() {
			@Override
			public Stream<URL> getSources() {
				return Stream.of(url);
			}
		});
	}

}
