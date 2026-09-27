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
package org.springframework.ide.vscode.boot.java.stereotypes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import org.jmolecules.stereotype.catalog.support.AbstractStereotypeCatalog;
import org.jmolecules.stereotype.catalog.support.CatalogSource;
import org.jmolecules.stereotype.catalog.support.JsonPathStereotypeCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * @author Martin Lippert
 */
public class JarStereotypeFactoryTest {

	@TempDir
	Path tempDir;

	@Test
	void aTypeMatchesBothAnnotationBasedAndImplementsBasedStereotypesInOneGo() throws Exception {
		AbstractStereotypeCatalog catalog = catalogOf("""
				{
					"stereotypes": {
						"annotation.based": { "assignments": ["@com.example.Marker"] },
						"implements.based": { "assignments": ["com.example.SomeInterface"] }
					}
				}
				""");

		StereotypeClassElement byAnnotation = new StereotypeClassElement("com.example.ByAnnotation", null, Set.of(),
				Set.of("com.example.Marker"), null);
		StereotypeClassElement byInterface = new StereotypeClassElement("com.example.ByInterface", null,
				Set.of("com.example.SomeInterface"), Set.of(), null);
		StereotypeClassElement byNeither = new StereotypeClassElement("com.example.Neither", null, Set.of(), Set.of(), null);

		JarStereotypeFactory factory = new JarStereotypeFactory(catalog);

		assertEquals(1, factory.fromType(byAnnotation).stream().count());
		assertEquals(1, factory.fromType(byInterface).stream().count());
		assertEquals(0, factory.fromType(byNeither).stream().count());

		assertTrue(factory.matchesAnyStereotype(byAnnotation));
		assertTrue(factory.matchesAnyStereotype(byInterface));
		assertFalse(factory.matchesAnyStereotype(byNeither));
	}

	/**
	 * The whole point of resolving a JAR's stereotypes live rather than caching them at scan time
	 * (see {@code docs/structure-view-dependencies.md}): the very same scanned element matches
	 * differently once the catalog itself is different - no rescan of the JAR involved.
	 */
	@Test
	void theSameElementMatchesDifferentlyAgainstDifferentCatalogsWithNoRescanning() throws Exception {
		StereotypeClassElement element = new StereotypeClassElement("com.example.Marked", null, Set.of(),
				Set.of("com.example.Marker"), null);

		AbstractStereotypeCatalog withDefinition = catalogOf("""
				{ "stereotypes": { "custom": { "assignments": ["@com.example.Marker"] } } }
				""");
		AbstractStereotypeCatalog withoutDefinition = catalogOf("""
				{ "stereotypes": { } }
				""");

		assertTrue(new JarStereotypeFactory(withDefinition).matchesAnyStereotype(element));
		assertFalse(new JarStereotypeFactory(withoutDefinition).matchesAnyStereotype(element));
	}

	@Test
	void methodsMatchOnlyAnnotationBasedStereotypesLikeIndexBasedElementsDo() throws Exception {
		AbstractStereotypeCatalog catalog = catalogOf("""
				{ "stereotypes": { "mapping": { "assignments": ["@com.example.Mapping"] } } }
				""");

		StereotypeMethodElement method = new StereotypeMethodElement("handle", "handle()", "com.example.Foo.handle()", null,
				Set.of("com.example.Mapping"), null);

		assertEquals(1, new JarStereotypeFactory(catalog).fromMethod(method).stream().count());
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
