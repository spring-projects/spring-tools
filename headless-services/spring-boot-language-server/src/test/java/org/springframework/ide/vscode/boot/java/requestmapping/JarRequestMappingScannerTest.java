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
package org.springframework.ide.vscode.boot.java.requestmapping;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ide.vscode.boot.java.stereotypes.JarFixtureBuilder;
import org.springframework.ide.vscode.boot.java.stereotypes.JarType;
import org.springframework.ide.vscode.boot.java.stereotypes.JarTypes;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@link JarRequestMappingScanner} in isolation - its parity with {@link RequestMappingIndexer}
 * over a whole fixture project is {@code StructureParityTest}'s.
 *
 * @author Martin Lippert
 */
public class JarRequestMappingScannerTest {

	@TempDir
	Path tempDir;

	@Test
	void mappingsCombineWithTheClassLevelPathAndAreReferencedByTheirMethod() throws Exception {
		JarType controller = scan("com.example.Api", """
				package com.example;
				import org.springframework.web.bind.annotation.*;
				@RestController
				@RequestMapping("/api")
				public class Api {
					@GetMapping("/items") public String items() { return null; }
					@RequestMapping(value = {"/a", "/b"}, method = RequestMethod.POST, produces = "application/json") public void post(String body) {}
				}
				""");

		assertEquals(List.of("@/api/items -- GET", "@/api/a -- POST - Content-Type: application/json", "@/api/b -- POST - Content-Type: application/json"),
				JarTypes.memberLabels(controller));
		assertEquals(List.of("Lcom/example/Api;.items()Ljava/lang/String;", "Lcom/example/Api;.post(Ljava/lang/String;)V", "Lcom/example/Api;.post(Ljava/lang/String;)V"),
				JarTypes.memberKeys(controller));
	}

	@Test
	void aClassThatIsNoControllerHasNoMappings() throws Exception {
		JarType service = scan("com.example.NotAController", """
				package com.example;
				import org.springframework.stereotype.Service;
				import org.springframework.web.bind.annotation.GetMapping;
				@Service
				public class NotAController {
					@GetMapping("/items") public String items() { return null; }
				}
				""");

		assertEquals(List.of(), JarTypes.memberLabels(service));
	}

	private JarType scan(String fqn, String source) throws Exception {
		File jar = JarFixtureBuilder.buildJar(tempDir, "fixture", Map.of(fqn, source), Map.of());

		// where @RestController's and @GetMapping's meta-annotations are declared
		return JarTypes.scan(jar, List.of(jarOf(RestController.class), jarOf(Controller.class))).get(fqn);
	}

	private static File jarOf(Class<?> type) throws Exception {
		return new File(type.getProtectionDomain().getCodeSource().getLocation().toURI());
	}

}
