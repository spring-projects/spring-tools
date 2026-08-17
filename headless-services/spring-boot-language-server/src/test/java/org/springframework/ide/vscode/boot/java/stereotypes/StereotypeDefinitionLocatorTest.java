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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * @author Martin Lippert
 */
public class StereotypeDefinitionLocatorTest {

	@TempDir File tempDir;

	private final StereotypeDefinitionLocator locator = new StereotypeDefinitionLocator();

	@Test
	void identifiesDefinitionsInCatalogFile() throws Exception {
		URL catalog = catalogFile("catalog.json",
				"{",                                                    // line 0
				"  \"stereotypes\" : {",                                // line 1
				"    \"my.first.Stereotype\" : {",                      // line 2
				"      \"assignments\" : [ \"@org.example.First\" ]",    // line 3
				"    },",                                               // line 4
				"    \"my.second.Stereotype\" : {",                     // line 5
				"      \"assignments\" : [ \"@org.example.Second\" ],",  // line 6
				"      \"groups\" : [ \"my\" ]",                        // line 7
				"    }",                                                // line 8
				"  }",                                                  // line 9
				"}");                                                   // line 10

		assertEquals(new Range(new Position(2, 4), new Position(4, 5)),
				locator.findDefinition(catalog, "my.first.Stereotype").get());

		assertEquals(new Range(new Position(5, 4), new Position(8, 5)),
				locator.findDefinition(catalog, "my.second.Stereotype").get());
	}

	@Test
	void identifiesDefinitionsRegardlessOfOtherMembersOfTheCatalogFile() throws Exception {
		URL catalog = catalogFile("catalog-with-groups.json",
				"{",                                                    // line 0
				"  \"groups\" : {",                                     // line 1
				"    \"my\" : { \"displayName\" : \"My Group\" }",      // line 2
				"  },",                                                 // line 3
				"  \"stereotypes\" : {",                                // line 4
				"    \"my.Stereotype\" : { \"priority\" : 10 }",        // line 5
				"  }",                                                  // line 6
				"}");                                                   // line 7

		assertEquals(new Range(new Position(5, 4), new Position(5, 41)),
				locator.findDefinition(catalog, "my.Stereotype").get());

		// group identifiers are not stereotype definitions
		assertEquals(Optional.empty(), locator.findDefinition(catalog, "my"));
	}

	@Test
	void noDefinitionForUnknownStereotype() throws Exception {
		URL catalog = catalogFile("catalog-without-match.json",
				"{",
				"  \"stereotypes\" : {",
				"    \"my.Stereotype\" : { }",
				"  }",
				"}");

		assertEquals(Optional.empty(), locator.findDefinition(catalog, "my.other.Stereotype"));
	}

	@Test
	void noDefinitionForBrokenCatalogFile() throws Exception {
		URL catalog = catalogFile("broken-catalog.json", "this is not JSON at all");

		assertEquals(Optional.empty(), locator.findDefinition(catalog, "my.Stereotype"));
	}

	@Test
	void identifiesDefinitionsAgainAfterCatalogFileChanged() throws Exception {
		File file = new File(tempDir, "changing-catalog.json");

		writeTo(file, "{", "  \"stereotypes\" : {", "    \"my.Stereotype\" : { }", "  }", "}");
		assertEquals(new Range(new Position(2, 4), new Position(2, 25)),
				locator.findDefinition(file.toURI().toURL(), "my.Stereotype").get());

		writeTo(file, "{", "", "  \"stereotypes\" : {", "    \"my.Stereotype\" : { }", "  }", "}");
		file.setLastModified(file.lastModified() + 1000);

		assertEquals(new Range(new Position(3, 4), new Position(3, 25)),
				locator.findDefinition(file.toURI().toURL(), "my.Stereotype").get());
	}

	@Test
	void identifiesDefinitionsInDefaultCatalogOfLanguageServer() throws Exception {
		URL catalog = StereotypeDefinitionLocator.class.getResource("/stereotype-defaults/spring-jmolecules-stereotypes.json");

		Range range = locator.findDefinition(catalog, "spring.web.Controller").get();
		List<String> lines = Files.readAllLines(new File(catalog.toURI()).toPath(), StandardCharsets.UTF_8);

		String start = lines.get(range.getStart().getLine()).substring(range.getStart().getCharacter());
		assertTrue(start.startsWith("\"spring.web.Controller\""), "definition starts at: " + start);

		String end = lines.get(range.getEnd().getLine()).substring(0, range.getEnd().getCharacter());
		assertTrue(end.endsWith("}"), "definition ends at: " + end);
	}

	private URL catalogFile(String name, String... lines) throws Exception {
		File file = new File(tempDir, name);
		writeTo(file, lines);
		return file.toURI().toURL();
	}

	private static void writeTo(File file, String... lines) throws Exception {
		Files.writeString(file.toPath(), String.join("\n", lines), StandardCharsets.UTF_8);
	}

}
